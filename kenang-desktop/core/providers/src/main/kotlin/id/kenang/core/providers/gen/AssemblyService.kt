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
import id.kenang.core.data.ffmpeg.AssBuilder
import id.kenang.core.data.ffmpeg.FfmpegGraphBuilder
import id.kenang.core.data.ffmpeg.VideoAssembler
import id.kenang.core.providers.CostTracker
import io.github.aakira.napier.Napier
import java.io.File

/**
 * Phase 04 §4.2/4.3 driver: narration TTS (once per project, cached on disk),
 * the F5.3 duration rule, .ass subtitle generation, and the local FFmpeg
 * assembly — watermark strictly behind [LicenseGate] (D-002).
 */
class AssemblyService(
    private val tts: TtsService,
    private val assembler: VideoAssembler,
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

    /** Final video length from the DONE scenes (xfade overlaps subtracted). */
    suspend fun videoDurationMs(projectId: String, crossfadeS: Double = 0.6): Long {
        val done = doneScenes(projectId)
        if (done.isEmpty()) return 0
        val total = done.sumOf { it.duration_s.toDouble() } - crossfadeS * (done.size - 1)
        return (total * 1000).toLong()
    }

    /** Synthesizes (or reuses) the narration and applies the duration rule. */
    suspend fun prepareAudio(projectId: String): AudioPrep {
        val project = projects.get(projectId)
            ?: return AudioPrep.Failed(AppError.Unknown("project missing"))
        val text = project.narration?.trim().orEmpty()
        if (text.isBlank()) return AudioPrep.Ready(null)

        val cached = File(File(AppDirs.projectDir(projectId), "audio"), "narration.mp3")
        val narration: Narration = if (cached.isFile && cached.length() > 0) {
            val ms = assembler.runner()?.probeDurationMs(cached) ?: 0L
            Narration(cached, ms, text)
        } else {
            when (val r = tts.synthesize(projectId, text)) {
                is AppResult.Ok -> Narration(r.value.file, r.value.durationMs, text)
                is AppResult.Err -> return AudioPrep.Failed(r.error)
            }
        }
        val measuredMs = if (narration.durationMs > 0) {
            narration.durationMs
        } else {
            assembler.runner()?.probeDurationMs(narration.file) ?: 0L
        }
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
     * Assembles the final MP4 from all DONE scenes. [narrationTempo] carries
     * the F5.3 tempo fix (e.g. 1.1); subtitles are generated from the
     * narration text with tempo-corrected timing.
     */
    suspend fun assemble(
        projectId: String,
        narration: Narration?,
        includeSubtitles: Boolean = true,
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

        val runner = assembler.runner()
            ?: return AppError.AssemblyFailed("ffmpeg unavailable").err()

        val clips = done.map { scene ->
            val file = scene.local_clip_path?.let(::File)
                ?: return AppError.AssemblyFailed("clip missing for ${scene.scene_id}").err()
            if (!file.isFile) return AppError.AssemblyFailed("clip file gone: ${file.name}").err()
            // Real clip length beats the nominal scene duration (fal pads a few ms).
            val actualS = runner.probeDurationMs(file)?.let { it / 1000.0 } ?: scene.duration_s.toDouble()
            FfmpegGraphBuilder.ClipSpec(file, actualS)
        }

        val subtitleFile = if (includeSubtitles && narration != null && narration.durationMs > 0) {
            val effectiveMs = (narration.durationMs / (narrationTempo ?: 1.0)).toLong()
            File(File(AppDirs.projectDir(projectId), "audio").apply { mkdirs() }, "subtitles.ass").apply {
                writeText(AssBuilder.build(narration.text, effectiveMs, ratioLabel))
            }
        } else null

        // TODO(D-002): Phase 05 — before exporting, consult
        // licenseGate.state().exportsRemaining (Trial = max 3 exports) and
        // refuse with an upgrade CTA when exhausted. DevFull stub: unlimited.

        // Watermark ONLY via the license seam (D-002) — DevFull stub = never.
        val watermarkFile = if (licenseGate.state().watermarkRequired) {
            VideoAssembler.stageWatermark(File(AppDirs.tools, "watermark"))
        } else null

        val ratio = FfmpegGraphBuilder.Ratio.fromLabel(ratioLabel)
        val safeName = project.name.replace(Regex("[^A-Za-z0-9_\\- ]"), "").trim()
            .replace(' ', '_').ifBlank { "kenang" }
        val outFile = File(AppDirs.projectOutput(projectId), "${safeName}_${ratio.label.replace(':', 'x')}.mp4")

        val spec = FfmpegGraphBuilder.AssemblySpec(
            clips = clips,
            ratio = ratio,
            output = outFile,
            musicFile = project.music_path?.let(::File)?.takeIf { it.isFile },
            narrationFile = narration?.file,
            subtitleFile = subtitleFile,
            watermarkFile = watermarkFile,
            narrationTempo = narrationTempo,
        )
        return when (val result = assembler.assemble(spec, onProgress)) {
            is AppResult.Ok -> {
                outputs.record(
                    projectId, result.value.absolutePath, ratioLabel, project.tier,
                    costTracker.projectTotalUsd(projectId),
                )
                projects.updateStatus(projectId, "done")
                runCatching {
                    // Per-output thumbnail so a re-export never clobbers the
                    // other ratio's preview (legacy name kept as fallback).
                    runner.extractThumbnail(
                        result.value,
                        File(result.value.parentFile, result.value.nameWithoutExtension + "_thumb.jpg"),
                    )
                }.onFailure { Napier.w("thumbnail extract failed: ${it.message}") }
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
