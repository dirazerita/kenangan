package id.kenang.core.providers.gen

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.err
import id.kenang.core.common.license.LicenseGate
import id.kenang.core.data.AppDirs
import id.kenang.core.data.OutputRepository
import id.kenang.core.data.ProjectRepository
import id.kenang.core.data.SceneRepository
import id.kenang.core.data.SceneStatus
import id.kenang.core.data.media.GalleryExporter
import id.kenang.core.data.media.MediaProbe
import id.kenang.core.data.media.VideoAssembler
import id.kenang.core.providers.CostTracker
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android port of the Phase-04 assembly driver: narration TTS (once per
 * project, cached on disk), the F5.3 duration rule, and the Media3 assembly.
 * Same public surface as the desktop service so the screens are shared.
 *
 * Differences from desktop, both Transformer limitations:
 *  - no burned-in subtitles (the includeSubtitles flag is accepted and ignored);
 *  - scenes hard-cut instead of the 0.6s xfade, so the video is the exact sum
 *    of the scene durations.
 */
class AssemblyService(
    private val tts: TtsService,
    private val assembler: VideoAssembler,
    private val gallery: GalleryExporter,
    private val licenseGate: LicenseGate,
    private val sceneRepository: SceneRepository,
    private val projects: ProjectRepository,
    private val outputs: OutputRepository,
    private val costTracker: CostTracker,
) {
    data class Narration(val file: File, val durationMs: Long, val text: String)

    sealed class AudioPrep {
        /** No narration, or narration fits the video — go assemble. */
        data class Ready(val narration: Narration?) : AudioPrep()

        /** F5.3: narration longer than the video — UI must offer the 3 fixes. */
        data class DurationMismatch(
            val narration: Narration,
            val videoDurationMs: Long,
            /** true when tempo +10% alone closes the gap (auto-suggested default). */
            val tempoFixes: Boolean,
        ) : AudioPrep()

        data class Failed(val error: AppError) : AudioPrep()
    }

    /** Final video length from the DONE scenes (hard cuts: plain sum). */
    suspend fun videoDurationMs(projectId: String): Long {
        val done = doneScenes(projectId)
        if (done.isEmpty()) return 0
        return done.sumOf { it.duration_s } * 1000
    }

    /** Synthesizes (or reuses) the narration and applies the duration rule. */
    suspend fun prepareAudio(projectId: String): AudioPrep {
        val project = projects.get(projectId)
            ?: return AudioPrep.Failed(AppError.Unknown("project missing"))
        val text = project.narration?.trim().orEmpty()
        if (text.isBlank()) return AudioPrep.Ready(null)

        val cached = File(File(AppDirs.projectDir(projectId), "audio"), "narration.mp3")
        val narration: Narration = if (cached.isFile && cached.length() > 0) {
            Narration(cached, MediaProbe.durationMs(cached) ?: 0L, text)
        } else {
            when (val r = tts.synthesize(projectId, text)) {
                is AppResult.Ok -> Narration(r.value.file, r.value.durationMs, text)
                is AppResult.Err -> return AudioPrep.Failed(r.error)
            }
        }
        val measuredMs = narration.durationMs.takeIf { it > 0 }
            ?: MediaProbe.durationMs(narration.file) ?: 0L
        val effective = narration.copy(durationMs = measuredMs)

        val videoMs = videoDurationMs(projectId)
        return if (measuredMs > 0 && videoMs in 1 until measuredMs) {
            AudioPrep.DurationMismatch(
                effective, videoMs,
                tempoFixes = measuredMs / 1.1 <= videoMs,
            )
        } else {
            AudioPrep.Ready(effective)
        }
    }

    /**
     * Assembles the final MP4 from all DONE scenes and publishes it to the
     * gallery. [includeSubtitles] is ignored on Android (see class docs).
     */
    suspend fun assemble(
        projectId: String,
        narration: Narration?,
        @Suppress("UNUSED_PARAMETER") includeSubtitles: Boolean = true,
        narrationTempo: Double? = null,
        /** PRD F6.5: re-export in another ratio from the SAME clips (no API cost). */
        ratioOverride: String? = null,
        onProgress: (Int) -> Unit = {},
    ): AppResult<File> {
        val project = projects.get(projectId)
            ?: return AppError.Unknown("project missing").err()
        val ratioLabel = ratioOverride ?: project.ratio
        val done = doneScenes(projectId)
        if (done.isEmpty()) return AppError.AssemblyFailed("no finished scenes").err()

        val clips = done.map { scene ->
            val file = scene.local_clip_path?.let(::File)
                ?: return AppError.AssemblyFailed("clip missing for ${scene.scene_id}").err()
            if (!file.isFile) return AppError.AssemblyFailed("clip file gone: ${file.name}").err()
            // Real clip length beats the nominal scene duration (fal pads a few ms).
            val actualS = MediaProbe.durationMs(file)?.let { it / 1000.0 } ?: scene.duration_s.toDouble()
            VideoAssembler.ClipSpec(file, actualS)
        }

        // TODO(D-002): Phase 05 — consult licenseGate.state().exportsRemaining
        // before exporting. DevFull stub: unlimited, and never watermarked.
        if (licenseGate.state().watermarkRequired) {
            Napier.w("watermark requested but not implemented on Android yet")
        }

        val ratio = VideoAssembler.Ratio.fromLabel(ratioLabel)
        val safeName = project.name.replace(Regex("[^A-Za-z0-9_\\- ]"), "").trim()
            .replace(' ', '_').ifBlank { "kenang" }
        val outFile = File(
            AppDirs.projectOutput(projectId),
            "${safeName}_${ratio.label.replace(':', 'x')}.mp4",
        )

        val spec = VideoAssembler.AssemblySpec(
            clips = clips,
            ratio = ratio,
            output = outFile,
            musicFile = project.music_path?.let(::File)?.takeIf { it.isFile },
            narrationFile = narration?.file,
            narrationTempo = narrationTempo,
        )
        return when (val result = assembler.assemble(spec, onProgress)) {
            is AppResult.Ok -> {
                outputs.record(
                    projectId, result.value.absolutePath, ratioLabel, project.tier,
                    costTracker.projectTotalUsd(projectId),
                )
                runCatching {
                    MediaProbe.extractThumbnail(
                        result.value,
                        File(result.value.parentFile, result.value.nameWithoutExtension + "_thumb.jpg"),
                    )
                }.onFailure { Napier.w("thumbnail extract failed: ${it.message}") }

                // Owner requirement: the merged video AND the per-scene clips
                // land in the user's gallery under Movies/Kenang/<project>/.
                val folder = project.name
                gallery.export(result.value, folder, result.value.name)
                withContext(Dispatchers.IO) {
                    done.forEachIndexed { index, scene ->
                        scene.local_clip_path?.let(::File)?.takeIf { it.isFile }?.let { clip ->
                            gallery.export(clip, folder, "Adegan_%02d.mp4".format(index + 1), subFolder = "Adegan")
                        }
                    }
                }

                projects.updateStatus(projectId, "done")
                result
            }
            is AppResult.Err -> result
        }
    }

    private suspend fun doneScenes(projectId: String) =
        sceneRepository.scenes(projectId)
            .filter { it.status == SceneStatus.DONE }
            .sortedBy { it.order_index }
}
