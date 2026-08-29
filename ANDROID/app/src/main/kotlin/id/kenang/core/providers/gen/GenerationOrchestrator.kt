package id.kenang.core.providers.gen

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.Provider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import id.kenang.core.data.AppDirs
import id.kenang.core.data.GenJobRepository
import id.kenang.core.data.GenJobStatus
import id.kenang.core.data.ProjectRepository
import id.kenang.core.data.SceneRepository
import id.kenang.core.data.SceneStatus
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.db.Scene
import id.kenang.core.providers.CostTracker
import id.kenang.core.providers.PriceBook
import id.kenang.core.providers.fal.FalQueueClient
import id.kenang.core.providers.fal.FalStorage
import id.kenang.core.providers.fal.SubmittedFalJob
import io.github.aakira.napier.Napier
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Phase 04 §4.1: submits every confirmed scene straight to the fal queue
 * (tier-routed model, per-scene key failover at submit — AD-14), persists
 * GenJob rows BEFORE polling (crash-resume without resubmitting), polls with
 * 5s→10s→15s backoff pinned to the submitting key, downloads finished MP4s to
 * `projects/<id>/clips/`, and appends CostTracker entries.
 *
 * Network loss during polling is survived in place: status errors of type
 * Offline/Timeout keep polling until the deadline, so "kill network
 * mid-generate → resume" needs no user action.
 */
class GenerationOrchestrator(
    private val falClient: FalQueueClient,
    private val storage: FalStorage,
    private val downloader: ClipDownloader,
    private val configRepository: ConfigRepository,
    private val priceBook: PriceBook,
    private val costTracker: CostTracker,
    private val sceneRepository: SceneRepository,
    private val jobRepository: GenJobRepository,
    private val projects: ProjectRepository,
    private val settings: id.kenang.core.data.SettingsRepository,
) {
    /** Settings → Model AI override; falls back to the tier's routed model. */
    private fun resolveI2v(tierCfg: id.kenang.core.data.config.TierConfig): Pair<String, JsonObject?> {
        val key = settings.modelI2v ?: return tierCfg.i2v to tierCfg.i2vParams
        val option = configRepository.current().modelCatalog.i2v
            .firstOrNull { it.selectionKey() == key }
            ?: return tierCfg.i2v to tierCfg.i2vParams
        Napier.i("i2v model override active: ${option.labelId} (${option.id})")
        return option.id to option.params
    }
    /** Error codes persisted on job rows (UI maps them per §4.1). */
    object ErrorCodes {
        const val CONTENT_BLOCKED = "content_blocked"
        const val INVALID_KEY = "invalid_key"
        const val PROVIDER_BALANCE = "provider_balance"
        const val PROVIDER_FAILED = "provider_failed"
        const val TIMEOUT = "timeout"
    }

    data class Outcome(
        val doneScenes: Int,
        val failedScenes: Int,
        /** invalid_key / provider_balance — everything pauses, partial kept. */
        val fatal: AppError? = null,
    ) {
        val allDone: Boolean get() = failedScenes == 0 && fatal == null && doneScenes > 0
    }

    /**
     * Generates every pending scene of [projectId] (max 3 in flight). Scenes
     * already DONE are skipped; GENERATING scenes with a live fal request id
     * resume polling instead of resubmitting (real money).
     */
    suspend fun run(projectId: String, tier: String): Outcome {
        val project = projects.get(projectId)
            ?: return Outcome(0, 0, AppError.Unknown("project $projectId missing"))
        projects.updateStatus(projectId, "generating")

        val tierCfg = configRepository.current().tierRouting.resolve(tier)
        val (i2vSlug, i2vParams) = resolveI2v(tierCfg)
        val fatal = AtomicReference<AppError?>(null)
        val semaphore = Semaphore(3)

        val pending = sceneRepository.scenes(projectId)
            .sortedBy { it.order_index }
            .filter { it.status != SceneStatus.DONE }

        val results = coroutineScope {
            pending.map { scene ->
                async {
                    semaphore.withPermit {
                        if (fatal.get() != null) return@withPermit false
                        val ok = generateScene(project.id, project.ratio, scene, i2vSlug, i2vParams)
                        if (!ok) {
                            val latest = jobRepository.latestForScene(scene.scene_id)
                            if (latest?.error_code in setOf(ErrorCodes.INVALID_KEY, ErrorCodes.PROVIDER_BALANCE)) {
                                fatal.compareAndSet(
                                    null,
                                    if (latest?.error_code == ErrorCodes.INVALID_KEY) {
                                        AppError.InvalidKey(Provider.FAL, latest.key_label)
                                    } else {
                                        AppError.ProviderBalance(Provider.FAL)
                                    },
                                )
                            }
                        }
                        ok
                    }
                }
            }.awaitAll()
        }

        val done = sceneRepository.scenes(projectId).count { it.status == SceneStatus.DONE }
        val failed = sceneRepository.scenes(projectId).count { it.status == SceneStatus.FAILED }
        Napier.i("generation outcome for $projectId: done=$done failed=$failed fatal=${fatal.get()}")
        return Outcome(done, failed, fatal.get()).also { _ -> results /* keep */ }
    }

    /** Retries a single failed scene (manual retry button). */
    suspend fun retryScene(projectId: String, sceneId: String, tier: String): Boolean {
        val project = projects.get(projectId) ?: return false
        val scene = sceneRepository.scene(sceneId) ?: return false
        if (scene.status != SceneStatus.FAILED) return false
        val tierCfg = configRepository.current().tierRouting.resolve(tier)
        val (i2vSlug, i2vParams) = resolveI2v(tierCfg)
        return generateScene(projectId, project.ratio, scene, i2vSlug, i2vParams)
    }

    // ------------------------------------------------------------------ scene

    /** Returns true when the scene reached DONE. */
    private suspend fun generateScene(
        projectId: String,
        ratio: String,
        scene: Scene,
        i2vSlug: String,
        i2vParams: JsonObject?,
    ): Boolean {
        // Resume path: still GENERATING with a submitted fal request → just poll.
        if (scene.status == SceneStatus.GENERATING) {
            val open = jobRepository.latestForScene(scene.scene_id)
            if (open != null && open.status == GenJobStatus.RUNNING && open.backend_job_id != null &&
                open.key_label != null && open.model != null
            ) {
                Napier.i("resuming fal job ${open.backend_job_id} for scene ${scene.scene_id} (key '${open.key_label}')")
                val resumed = SubmittedFalJob(open.backend_job_id!!, open.model!!, open.key_label!!)
                return finishJob(projectId, scene, open.id, resumed, attemptLeft = 1)
            }
            // Crashed before the submit round-tripped: sweep to FAILED and resubmit fresh.
            open?.let { jobRepository.setStatus(it.id, GenJobStatus.FAILED_RETRYABLE, ErrorCodes.PROVIDER_FAILED) }
            sceneRepository.transition(scene.scene_id, SceneStatus.FAILED)
        }

        if (sceneRepository.scene(scene.scene_id)?.status != SceneStatus.GENERATING) {
            sceneRepository.transition(scene.scene_id, SceneStatus.GENERATING)
        }
        return submitAndFinish(projectId, ratio, scene, i2vSlug, i2vParams, attemptLeft = 2)
    }

    private suspend fun submitAndFinish(
        projectId: String,
        ratio: String,
        scene: Scene,
        i2vSlug: String,
        i2vParams: JsonObject?,
        attemptLeft: Int,
    ): Boolean {
        val imageUrl = resolveKeyframeUrl(scene)
            ?: return failScene(scene.scene_id, null, GenJobStatus.FAILED_PERMANENT, ErrorCodes.PROVIDER_FAILED)

        val durationS = scene.duration_s.toDouble()
        val estUsd = priceBook.estimate(i2vSlug, durationS)?.usd ?: 0.0
        val jobId = jobRepository.create(scene.scene_id, i2vSlug, estUsd)

        val submitSlug = submitSlug(i2vSlug, i2vParams)
        val body = buildI2vBody(submitSlug, imageUrl, scene.motion_prompt_en ?: "", scene.duration_s, ratio, i2vParams)

        val submitted = when (val s = falClient.submit(submitSlug, body)) {
            is AppResult.Ok -> s.value
            is AppResult.Err -> return handleSceneError(
                projectId, ratio, scene, i2vSlug, i2vParams, jobId, s.error, attemptLeft,
            )
        }
        // Persist request id + key BEFORE polling: force-kill resumes, never resubmits.
        jobRepository.markSubmitted(jobId, submitted.requestId, submitted.keyLabel)
        return finishJob(projectId, scene, jobId, submitted, attemptLeft)
    }

    /** Polls (5s→10s→15s, key-pinned), downloads, records cost, closes the scene. */
    private suspend fun finishJob(
        projectId: String,
        scene: Scene,
        jobId: String,
        submitted: SubmittedFalJob,
        attemptLeft: Int,
    ): Boolean {
        val payload = when (val r = pollUntilComplete(submitted, timeoutMillis = 15 * 60_000)) {
            is AppResult.Ok -> r.value
            is AppResult.Err -> {
                val project = projects.get(projectId)
                return handleSceneError(
                    projectId, project?.ratio ?: "9:16", scene,
                    submitted.modelSlug, null, jobId, r.error, attemptLeft,
                )
            }
        }

        val video = payload["video"]?.jsonObject
        val videoUrl = video?.get("url")?.jsonPrimitive?.contentOrNull
            ?: return failScene(scene.scene_id, jobId, GenJobStatus.FAILED_RETRYABLE, ErrorCodes.PROVIDER_FAILED)
        val actualDurationS = video["duration"]?.jsonPrimitive?.doubleOrNull ?: scene.duration_s.toDouble()

        val clipFile = File(AppDirs.projectClips(projectId), "${scene.scene_id}.mp4")
        when (val dl = downloader.download(videoUrl, clipFile)) {
            is AppResult.Ok -> Unit
            is AppResult.Err -> {
                Napier.w("clip download failed for ${scene.scene_id}: ${dl.error}")
                return failScene(scene.scene_id, jobId, GenJobStatus.FAILED_RETRYABLE, ErrorCodes.TIMEOUT, videoUrl)
            }
        }

        val job = jobRepository.job(jobId)
        val model = job?.model ?: submitted.modelSlug
        costTracker.record(
            projectId, submitted.requestId, model, submitted.keyLabel,
            actualDurationS, "per_second", priceBook.estimate(model, actualDurationS)?.usd ?: 0.0,
        )
        jobRepository.setStatus(jobId, GenJobStatus.DONE, outputUrl = videoUrl)
        sceneRepository.setClipPath(scene.scene_id, clipFile.absolutePath)
        sceneRepository.transition(scene.scene_id, SceneStatus.DONE)
        return true
    }

    /**
     * §4.1 error map: content_blocked → permanent (edit motion prompt, no
     * auto-retry) · invalid_key / provider_balance → pause (fatal, retryable
     * later) · provider_failed / timeout → auto-retry once, then manual.
     */
    private suspend fun handleSceneError(
        projectId: String,
        ratio: String,
        scene: Scene,
        i2vSlug: String,
        i2vParams: JsonObject?,
        jobId: String,
        error: AppError,
        attemptLeft: Int,
    ): Boolean {
        val (status, code) = when (error) {
            is AppError.ContentBlocked -> GenJobStatus.FAILED_PERMANENT to ErrorCodes.CONTENT_BLOCKED
            is AppError.InvalidKey -> GenJobStatus.FAILED_RETRYABLE to ErrorCodes.INVALID_KEY
            is AppError.ProviderBalance -> GenJobStatus.FAILED_RETRYABLE to ErrorCodes.PROVIDER_BALANCE
            is AppError.Timeout, AppError.Offline -> GenJobStatus.FAILED_RETRYABLE to ErrorCodes.TIMEOUT
            else -> GenJobStatus.FAILED_RETRYABLE to ErrorCodes.PROVIDER_FAILED
        }
        Napier.w("scene ${scene.scene_id} error: $code ($error), attemptLeft=$attemptLeft")

        val autoRetry = code in setOf(ErrorCodes.PROVIDER_FAILED, ErrorCodes.TIMEOUT) && attemptLeft > 1
        if (autoRetry) {
            jobRepository.setStatus(jobId, status, code)
            // Owner requirement: a troubled call retries on the NEXT key.
            falClient.rotateKey()
            delay(2_000)
            return submitAndFinish(projectId, ratio, scene, i2vSlug, i2vParams, attemptLeft - 1)
        }
        return failScene(scene.scene_id, jobId, status, code)
    }

    private suspend fun failScene(
        sceneId: String,
        jobId: String?,
        jobStatus: String,
        errorCode: String,
        outputUrl: String? = null,
    ): Boolean {
        jobId?.let { jobRepository.setStatus(it, jobStatus, errorCode, outputUrl) }
        if (sceneRepository.scene(sceneId)?.status == SceneStatus.GENERATING) {
            sceneRepository.transition(sceneId, SceneStatus.FAILED)
        }
        return false
    }

    // ------------------------------------------------------------------ bits

    /** Prefers the remote keyframe URL; uploads the local file when absent. */
    private suspend fun resolveKeyframeUrl(scene: Scene): String? {
        scene.keyframe_url?.takeIf { it.isNotBlank() }?.let { return it }
        val local = scene.local_keyframe_path?.let(::File)?.takeIf { it.isFile } ?: return null
        return storage.uploadFile(local).getOrNull()
    }

    /**
     * Polls with fixed 5s→10s→15s backoff (MASTER_PROMPT_04). Transient
     * network failures (Offline/Timeout) do NOT abort — polling continues
     * until the deadline so a dropped connection resumes in place.
     */
    internal suspend fun pollUntilComplete(job: SubmittedFalJob, timeoutMillis: Long): AppResult<JsonObject> {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var delayMs = 5_000L
        while (System.currentTimeMillis() < deadline) {
            when (val st = falClient.status(job)) {
                is AppResult.Err -> when (st.error) {
                    is AppError.Timeout, AppError.Offline ->
                        Napier.w("status poll offline/timeout for ${job.requestId} — retrying")
                    else -> return st
                }
                is AppResult.Ok -> when (st.value.status) {
                    "COMPLETED" -> return falClient.result(job).map { it.payload }
                    "IN_QUEUE", "IN_PROGRESS" -> Unit
                    else -> return AppError.ProviderFailed(
                        Provider.FAL, "unexpected status ${st.value.status}",
                    ).err()
                }
            }
            delay(delayMs)
            delayMs = (delayMs + 5_000).coerceAtMost(15_000)
        }
        return AppError.Timeout().err()
    }

    companion object {
        /**
         * fal exposes model variants as slug sub-paths (e.g. Wan 2.6 `/flash`);
         * config carries them as `i2v_params.variant` on the base slug so
         * PriceBook keys stay on the base (D-006).
         */
        internal fun submitSlug(baseSlug: String, params: JsonObject?): String {
            val variant = params?.get("variant")?.jsonPrimitive?.contentOrNull
            return if (variant.isNullOrBlank()) baseSlug else "$baseSlug/$variant"
        }

        /** Model-family-specific request body (payload shapes proven in Phase 00 T4). */
        internal fun buildI2vBody(
            slug: String,
            imageUrl: String,
            motionPrompt: String,
            durationS: Long,
            ratioLabel: String,
            extraParams: JsonObject?,
        ): JsonObject = buildJsonObject {
            put("prompt", if (slug.contains("seedance")) "@Image1 $motionPrompt" else motionPrompt)
            put("duration", durationS.toString())
            when {
                slug.contains("kling") -> put("start_image_url", imageUrl)
                slug.contains("seedance") -> {
                    putJsonArray("image_urls") { add(imageUrl) }
                    put("aspect_ratio", ratioLabel)
                    put("resolution", "480p")
                }
                else -> { // wan & other single-image i2v models
                    put("image_url", imageUrl)
                    put("resolution", "720p")
                }
            }
            extraParams?.forEach { (k, v) -> if (k != "variant") put(k, v) }
        }
    }
}
