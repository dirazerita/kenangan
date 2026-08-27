package id.kenang.app.ui.storyboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import id.kenang.core.common.AppResult
import id.kenang.core.common.ErrorTranslator
import id.kenang.core.common.events.GenerationEvents
import id.kenang.core.common.events.StartGenerationRequest
import id.kenang.core.common.i18n.Strings
import id.kenang.core.common.story.MotionSpec
import id.kenang.core.common.story.MotionTemplates
import id.kenang.core.data.ProjectRepository
import id.kenang.core.data.SceneRepository
import id.kenang.core.data.SceneStatus
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.db.Project
import id.kenang.core.db.Scene
import id.kenang.core.providers.story.CostEstimator
import id.kenang.core.providers.story.KeyframeService
import id.kenang.core.providers.story.StoryboardEstimate
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class StoryboardState(
    private val projectId: String,
    private val projects: ProjectRepository,
    private val sceneRepository: SceneRepository,
    private val keyframeService: KeyframeService,
    private val estimator: CostEstimator,
    private val configRepository: ConfigRepository,
    private val generationEvents: GenerationEvents,
    private val scope: CoroutineScope,
) {
    var project by mutableStateOf<Project?>(null)
    var scenes by mutableStateOf<List<Scene>>(emptyList())
    var estimate by mutableStateOf<StoryboardEstimate?>(null)
    var snackMessage by mutableStateOf<String?>(null)
    var showConfirm by mutableStateOf(false)
    var confirmTier by mutableStateOf("standar")
    var confirmed by mutableStateOf(false)

    val config get() = configRepository.current()

    private val keyframeLimiter = Semaphore(2) // max 2 concurrent keyframe jobs
    private val inFlight = mutableSetOf<String>()

    fun tier(): String = project?.tier ?: config.tierRouting.defaultTier

    fun regenCostUsd(): Double = keyframeService.regenEstimate(tier())

    fun start() {
        scope.launch {
            project = projects.get(projectId)
            confirmTier = tier()
            // Crash recovery: stale keyframe_pending rows have no live job → mark failed, retried below.
            sceneRepository.scenes(projectId)
                .filter { it.status == SceneStatus.KEYFRAME_PENDING }
                .forEach { sceneRepository.setKeyframeResult(it.scene_id, null, null, false) }
            sceneRepository.observeScenes(projectId).collect { list ->
                scenes = list
                recomputeEstimate(list)
                autoTriggerKeyframes(list)
            }
        }
    }

    private fun recomputeEstimate(list: List<Scene>) {
        estimate = estimator.estimate(list, tier())
    }

    /** Auto-generate keyframes for all draft/failed scenes (first storyboard entry + retries). */
    private fun autoTriggerKeyframes(list: List<Scene>) {
        list.filter { it.status == SceneStatus.DRAFT }.forEach { launchKeyframe(it.scene_id, isRegen = false) }
    }

    private fun launchKeyframe(sceneId: String, isRegen: Boolean) {
        synchronized(inFlight) {
            if (!inFlight.add(sceneId)) return
        }
        scope.launch {
            try {
                keyframeLimiter.withPermit {
                    when (val r = keyframeService.generate(sceneId, tier(), isRegen)) {
                        is AppResult.Ok -> Unit
                        is AppResult.Err -> {
                            Napier.w("keyframe $sceneId failed")
                            snackMessage = ErrorTranslator.translate(r.error).message
                        }
                    }
                }
            } finally {
                synchronized(inFlight) { inFlight.remove(sceneId) }
            }
        }
    }

    fun retryKeyframe(scene: Scene) = launchKeyframe(scene.scene_id, isRegen = false)

    /** Unlimited regens; each counts into the estimator (regen × per-image). */
    fun regenerateKeyframe(scene: Scene) = launchKeyframe(scene.scene_id, isRegen = true)

    fun saveMotion(scene: Scene, spec: MotionSpec) {
        scope.launch {
            sceneRepository.updateMotion(
                scene.scene_id,
                MotionTemplates.buildPromptEn(spec),
                MotionTemplates.buildSummaryId(spec),
            )
        }
    }

    fun move(scene: Scene, delta: Int) {
        val ordered = scenes.sortedBy { it.order_index }.toMutableList()
        val idx = ordered.indexOfFirst { it.scene_id == scene.scene_id }
        val to = idx + delta
        if (idx < 0 || to !in ordered.indices) return
        ordered.add(to, ordered.removeAt(idx))
        scope.launch { sceneRepository.reorder(ordered.map { it.scene_id }) }
    }

    /** Replaces a scene's keyframe with the user's own image (free, no API). */
    fun replaceKeyframe(scene: Scene, file: java.io.File) {
        scope.launch {
            val check = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                id.kenang.core.data.story.ImageQuality.check(file)
            }
            if (!check.ok) {
                snackMessage = "${file.name}: ${check.rejectReasonId}"
                return@launch
            }
            val target = java.io.File(
                id.kenang.core.data.AppDirs.projectKeyframes(projectId),
                "${scene.scene_id}_custom_${System.currentTimeMillis()}.${file.extension.ifBlank { "jpg" }}",
            )
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                file.copyTo(target, overwrite = true)
            }
            sceneRepository.setCustomKeyframe(scene.scene_id, target.absolutePath)
        }
    }

    fun delete(scene: Scene) {
        scope.launch {
            val ok = sceneRepository.delete(scene.scene_id, projectId)
            if (!ok) snackMessage = Strings.SB_DELETE_LAST_SCENE
        }
    }

    fun allReady(): Boolean =
        scenes.isNotEmpty() && scenes.all { it.status == SceneStatus.KEYFRAME_READY || it.status == SceneStatus.CONFIRMED }

    fun estimateFor(tierKey: String): StoryboardEstimate = estimator.estimate(scenes, tierKey)

    /** Confirm dialog "Buat Video": persist tier, confirm scenes, emit the Phase-04 event. */
    fun confirm() {
        scope.launch {
            runCatching {
                projects.updateTier(projectId, confirmTier)
                sceneRepository.confirmAll(projectId)
                confirmed = true
                showConfirm = false
                // App.kt collects this and navigates to the generation screen (Phase 04).
                generationEvents.requestStart(StartGenerationRequest(projectId, confirmTier))
            }.onFailure {
                Napier.e("confirm failed: ${it.message}")
                snackMessage = it.message
            }
        }
    }
}
