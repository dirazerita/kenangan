package id.kenang.app.ui.wizard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import id.kenang.core.common.AppResult
import id.kenang.core.common.ErrorTranslator
import id.kenang.core.common.i18n.Strings
import id.kenang.core.data.AppDirs
import id.kenang.core.data.PhotoRepository
import id.kenang.core.data.ProjectRepository
import id.kenang.core.data.SettingsRepository
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.data.story.ImageQuality
import id.kenang.core.data.story.PhotoCheck
import id.kenang.core.db.Photo
import id.kenang.core.providers.story.TtsPreviewService
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class PhotoUi(val photo: Photo, val check: PhotoCheck)

/**
 * Wizard state — every mutation autosaves to the DB (back-safe; a force-kill
 * during the wizard resumes exactly where the user left off).
 */
class WizardState(
    private val projects: ProjectRepository,
    private val photosRepo: PhotoRepository,
    private val settings: SettingsRepository,
    private val configRepository: ConfigRepository,
    private val ttsPreview: TtsPreviewService,
    private val scope: CoroutineScope,
    private val existingProjectId: String?,
) {
    val config get() = configRepository.current()

    var projectId by mutableStateOf<String?>(existingProjectId)
        private set
    var step by mutableStateOf(1)
    var name by mutableStateOf("Kenangan " + LocalDate.now().format(DateTimeFormatter.ofPattern("d MMM yyyy")))
    val photos = mutableStateListOf<PhotoUi>()
    var rejectionMessage by mutableStateOf<String?>(null)
    var narration by mutableStateOf("")
    /** Explicit "Tanpa narasi" choice: clears the text and hides voice options. */
    var noNarration by mutableStateOf(false)
        private set
    var voiceId by mutableStateOf(configRepository.current().tts.voice)
    var previewPlaying by mutableStateOf(false)
    var previewError by mutableStateOf<String?>(null)
    var musicPath by mutableStateOf<String?>(null)
    var ratio by mutableStateOf("9:16")
    var vibeId by mutableStateOf(configRepository.current().vibes.firstOrNull()?.id ?: "asli")
    var durationS by mutableStateOf(5L)
    var showConsent by mutableStateOf(false)
    var finished by mutableStateOf(false)

    /** MiniMax voices proven in Phase 00; default first (locked Calm_Woman). */
    val voiceOptions: List<String> =
        listOf(configRepository.current().tts.voice, "Wise_Woman").distinct()

    fun chooseNoNarration(enabled: Boolean) {
        noNarration = enabled
        if (enabled) {
            narration = ""
            persistMeta()
        }
    }

    fun start() {
        scope.launch {
            val id = projectId
            if (id == null) {
                projectId = projects.create(name, ratio, vibeId, config.tierRouting.defaultTier)
            } else {
                // Resume: restore all wizard fields from DB.
                projects.get(id)?.let { p ->
                    name = p.name; ratio = p.ratio; vibeId = p.vibe
                    narration = p.narration ?: ""; musicPath = p.music_path
                    durationS = p.scene_duration_s
                }
                reloadPhotos()
            }
        }
    }

    private suspend fun reloadPhotos() {
        val id = projectId ?: return
        val list = photosRepo.photos(id)
        val checks = withContext(Dispatchers.IO) {
            list.map { PhotoUi(it, ImageQuality.check(File(it.local_path))) }
        }
        photos.clear(); photos.addAll(checks)
    }

    fun addPhotos(files: List<File>) {
        val id = projectId ?: return
        scope.launch {
            val remaining = config.limits.maxPhotos - photos.size
            val toAdd = files.take(remaining)
            if (files.size > remaining) {
                rejectionMessage = Strings.WIZARD_PHOTO_LIMIT
            }
            for (file in toAdd) {
                val check = withContext(Dispatchers.IO) { ImageQuality.check(file) }
                if (!check.ok) {
                    rejectionMessage = "${file.name}: ${check.rejectReasonId}"
                    continue
                }
                val photo = photosRepo.addPhoto(id, file)
                photos.add(PhotoUi(photo, check))
            }
        }
    }

    fun removePhoto(ui: PhotoUi) {
        scope.launch {
            photosRepo.removePhoto(ui.photo)
            photos.remove(ui)
        }
    }

    fun movePhoto(index: Int, delta: Int) {
        val to = index + delta
        if (index !in photos.indices || to !in photos.indices) return
        photos.add(to, photos.removeAt(index))
        // Order is presentation-only for now; the story plan sees analyses in this order.
    }

    fun playPreview() {
        previewError = null
        previewPlaying = true
        scope.launch {
            when (val result = ttsPreview.preview(narration, voiceId)) {
                is AppResult.Ok -> {
                    runCatching { ttsPreview.play(result.value) }
                        .onFailure { Napier.w("preview play failed: ${it.message}") }
                }
                is AppResult.Err ->
                    previewError = ErrorTranslator.translate(result.error).message
            }
            previewPlaying = false
        }
    }

    /** Copies uploaded music into the project folder (survives source deletion). */
    fun setMusic(file: File?) {
        val id = projectId ?: return
        scope.launch {
            musicPath = if (file == null) null else withContext(Dispatchers.IO) {
                val dir = File(AppDirs.projectDir(id), "music").apply { mkdirs() }
                val target = File(dir, file.name)
                file.copyTo(target, overwrite = true)
                target.absolutePath
            }
            persistMeta()
        }
    }

    fun persistMeta() {
        val id = projectId ?: return
        scope.launch {
            projects.updateMeta(id, name, ratio, vibeId, config.tierRouting.defaultTier,
                narration.ifBlank { null }, musicPath, durationS)
        }
    }

    fun canProceedFromStep1(): Boolean = photos.isNotEmpty()

    /** Finish → consent gate (first project only) → analysis. */
    fun requestFinish(onGoAnalysis: (String) -> Unit) {
        persistMeta()
        if (!settings.consentAccepted) {
            showConsent = true
        } else {
            finish(onGoAnalysis)
        }
    }

    fun acceptConsent(onGoAnalysis: (String) -> Unit) {
        settings.recordConsent()
        showConsent = false
        finish(onGoAnalysis)
    }

    private fun finish(onGoAnalysis: (String) -> Unit) {
        val id = projectId ?: return
        scope.launch {
            projects.updateStatus(id, "analyzing")
            finished = true
            onGoAnalysis(id)
        }
    }
}
