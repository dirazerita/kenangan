package id.kenang.core.providers.story

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.Provider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import id.kenang.core.data.AppDirs
import id.kenang.core.data.PhotoRepository
import id.kenang.core.data.SceneRepository
import id.kenang.core.data.SceneStatus
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.data.story.UploadPrep
import id.kenang.core.db.Scene
import id.kenang.core.providers.CostTracker
import id.kenang.core.providers.PriceBook
import id.kenang.core.providers.fal.FalQueueClient
import id.kenang.core.providers.fal.FalStorage
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File

/**
 * Generates one scene's keyframe via Nano Banana (tier-routed slug from
 * config), downloads it locally, tracks cost, and drives the scene state
 * machine: * → keyframe_pending → keyframe_ready|keyframe_failed.
 */
class KeyframeService(
    private val falClient: FalQueueClient,
    private val storage: FalStorage,
    private val http: HttpClient,
    private val configRepository: ConfigRepository,
    private val priceBook: PriceBook,
    private val costTracker: CostTracker,
    private val sceneRepository: SceneRepository,
    private val photoRepository: PhotoRepository,
    private val projectRepository: id.kenang.core.data.ProjectRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Estimated cost of one (re)generation for the UI chip ("±$0.04"). */
    fun regenEstimate(tier: String): Double {
        val model = configRepository.current().tierRouting.resolve(tier).keyframe
        return priceBook.estimate(model, 1.0)?.usd ?: 0.0
    }

    /**
     * Runs the keyframe job for [sceneId]. [isRegen] counts toward regen_count
     * (regens feed the cost estimator).
     */
    suspend fun generate(sceneId: String, tier: String, isRegen: Boolean): AppResult<Scene> {
        val scene = sceneRepository.scene(sceneId)
            ?: return AppError.Unknown("scene $sceneId missing").err()
        if (scene.status !in setOf(SceneStatus.DRAFT, SceneStatus.KEYFRAME_FAILED, SceneStatus.KEYFRAME_READY)) {
            return AppError.Unknown("scene ${scene.status} not eligible for keyframe").err()
        }
        sceneRepository.transition(sceneId, SceneStatus.KEYFRAME_PENDING)

        var result = runJob(scene, tier)
        // Troubled provider call → rotate to the next key and retry once
        // (owner requirement: the process must not stop on one bad call).
        val err = result.errorOrNull()
        if (err is AppError.ProviderFailed || err is AppError.Timeout || err is AppError.RateLimited) {
            falClient.rotateKey()
            result = runJob(scene, tier)
        }
        return when (result) {
            is AppResult.Ok -> {
                // The user's own photo WINS (owner 2026-09-08): dropping one on
                // a scene whose job was queued or running used to look like it
                // worked, then the AI image silently replaced it minutes later.
                val now = sceneRepository.scene(sceneId)
                val userReplaced = now != null && now.keyframe_url == null &&
                    now.local_keyframe_path != null &&
                    now.local_keyframe_path != scene.local_keyframe_path
                if (userReplaced) {
                    Napier.i("scene $sceneId: user photo arrived while the job ran — AI image discarded")
                    now.ok()
                } else {
                    sceneRepository.setKeyframeResult(
                        sceneId, result.value.first, result.value.second, isRegen,
                    )
                    sceneRepository.scene(sceneId)!!.ok()
                }
            }
            is AppResult.Err -> {
                // A failed regen must NOT throw away the image the scene
                // already had, nor its still-valid clip (D-045).
                sceneRepository.setKeyframeFailed(sceneId)
                result
            }
        }
    }

    /** Returns (remoteUrl, localPath) on success. */
    private suspend fun runJob(scene: Scene, tier: String): AppResult<Pair<String, String>> {
        val config = configRepository.current()
        val model = config.tierRouting.resolve(tier).keyframe

        // Resolve source photo upload URLs (upload lazily if the cache is cold).
        val sourceIds: List<String> = runCatching {
            json.decodeFromString<List<String>>(scene.source_photos_json)
        }.getOrDefault(emptyList())
        val photos = photoRepository.photos(scene.project_id).associateBy { it.id }
        val urls = mutableListOf<String>()
        for (id in sourceIds) {
            val photo = photos[id] ?: continue
            val cached = photo.upload_id
            if (cached != null) urls += cached
            else when (val up = storage.uploadBytes(
                UploadPrep.prepareJpeg(File(photo.local_path)),
                "${photo.id}.jpg", "image/jpeg",
            )) {
                is AppResult.Ok -> {
                    photoRepository.setUploadUrl(id, up.value)
                    urls += up.value
                }
                is AppResult.Err -> return up
            }
        }
        if (urls.isEmpty()) return AppError.Unknown("no source photos for scene ${scene.scene_id}").err()

        // The video's ratio is decided in the wizard, so the KEYFRAME must be
        // generated at that ratio too (owner 2026-09-02): nano-banana defaults
        // to aspect_ratio=auto (follows the source photo), and the mismatched
        // frame then got people cropped out when the i2v step forced the
        // project ratio. The prompt's "9:16 portrait" text alone does nothing.
        val project = projectRepository.get(scene.project_id)
        val aspectRatio = if (project?.ratio == "16:9") "16:9" else "9:16"

        val body = buildJsonObject {
            // Retrofit the anti-twin guard onto prompts stored before the fix
            // (owner 2026-09-01), so regens on old projects benefit too; the
            // project-level negative prompt rides every submit (owner
            // 2026-09-06) so "Buat ulang gambar" honors it immediately.
            // Per-scene edits ride every submit (owner 2026-09-06 rev 2):
            // the user's description override + the scene's own ban list
            // (project-level negative kept as legacy fallback).
            put(
                "prompt",
                KeyframePrompts.ensureNoDuplicateGuard(scene.keyframe_prompt_en ?: "") +
                    KeyframePrompts.descriptionOverrideClause(scene.user_description) +
                    KeyframePrompts.negativeClause(
                        scene.negative_prompt ?: project?.negative_prompt,
                    ),
            )
            putJsonArray("image_urls") { urls.forEach { add(it) } }
            put("num_images", 1)
            put("output_format", "jpeg")
            put("aspect_ratio", aspectRatio)
        }

        val submitted = when (val s = falClient.submit(model, body)) {
            is AppResult.Ok -> s.value
            is AppResult.Err -> return s
        }
        val payload = when (val r = falClient.awaitResult(submitted, timeoutMillis = 4 * 60_000)) {
            is AppResult.Ok -> r.value.payload
            is AppResult.Err -> return r
        }
        val imageUrl = runCatching {
            payload["images"]!!.jsonArray[0].jsonObject["url"]!!.jsonPrimitive.content
        }.getOrNull() ?: return AppError.ProviderFailed(Provider.FAL, "no image in keyframe result").err()

        // Unique per attempt (D-045): `_r<regen_count>` was read BEFORE the
        // counter increments, so a regen overwrote the previous image at an
        // identical path — path-keyed image caches then kept showing the old
        // pixels, and a half-written file replaced a good one. A fresh name
        // also keeps the previous image on disk as a fallback.
        val outFile = File(
            AppDirs.projectKeyframes(scene.project_id),
            "${scene.scene_id}_r${scene.regen_count}_${System.currentTimeMillis()}.jpg",
        )
        val written = runCatching {
            val bytes = http.get(imageUrl).readRawBytes()
            val tmp = File(outFile.parentFile, outFile.name + ".part")
            tmp.writeBytes(bytes)
            check(tmp.length() > 0) { "empty image" }
            if (!tmp.renameTo(outFile)) { tmp.copyTo(outFile, overwrite = true); tmp.delete() }
            outFile
        }.onFailure {
            Napier.w("keyframe image write failed: ${it.message}")
        }.getOrNull()

        // The local file is part of the success contract: without it the scene
        // would go KEYFRAME_READY pointing at a missing or PREVIOUS image
        // while still being billed (D-045).
        if (written == null || !written.isFile || written.length() == 0L) {
            return AppError.ProviderFailed(Provider.FAL, "gambar gagal disimpan").err()
        }

        val est = priceBook.estimate(model, 1.0)?.usd ?: 0.0
        costTracker.record(
            scene.project_id, submitted.requestId, model, submitted.keyLabel,
            1.0, "per_image", est,
        )
        return (imageUrl to outFile.absolutePath).ok()
    }
}
