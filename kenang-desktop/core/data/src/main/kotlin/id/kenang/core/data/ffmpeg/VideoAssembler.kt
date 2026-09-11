package id.kenang.core.data.ffmpeg

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.DispatcherProvider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import io.github.aakira.napier.Napier
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Local final assembly (AD-05): builds the FFmpeg command via
 * [FfmpegGraphBuilder], runs it against a temp file, and atomically moves the
 * result into place on success. Free and private — no API calls here.
 */
class VideoAssembler(
    private val locator: FfmpegLocator,
    private val dispatchers: DispatcherProvider,
) {
    fun available(): Boolean = locator.isAvailable()

    fun runner(): FfmpegRunner? = locator.locate()?.let { FfmpegRunner(it, dispatchers) }

    /**
     * Assembles [spec] (whose `output` is the FINAL path). Progress 0–100.
     * Returns the final file on success; temp artifacts are cleaned up.
     */
    suspend fun assemble(spec: FfmpegGraphBuilder.AssemblySpec, onProgress: (Int) -> Unit = {}): AppResult<File> {
        val exe = locator.locate()
            ?: return AppError.AssemblyFailed("ffmpeg unavailable (skipFfmpeg build?)").err()
        val runner = FfmpegRunner(exe, dispatchers)

        val finalFile = spec.output
        finalFile.parentFile?.mkdirs()
        val tempFile = File(finalFile.parentFile, ".${finalFile.name}.tmp.mp4")
        tempFile.delete()

        val tempSpec = spec.copy(output = tempFile)
        val expectedMs = (FfmpegGraphBuilder.totalDurationS(tempSpec) * 1000).toLong()
        val result = runner.run(FfmpegGraphBuilder.build(tempSpec), expectedMs, onProgress)
        return when (result) {
            is AppResult.Ok -> moveIntoPlace(tempFile, finalFile)
            is AppResult.Err -> {
                Napier.e("assembly failed: ${(result.error as? AppError.AssemblyFailed)?.detail ?: result.error}")
                tempFile.delete()
                result
            }
        }
    }

    /**
     * Writes a watermarked twin of [source] next to it (owner 2026-09-11: the
     * copy sent to a customer before payment). One overlay pass over the
     * finished video - the clips are never re-assembled, and the audio is
     * copied untouched, so the two files differ only by the mark.
     */
    suspend fun watermarkedCopy(
        source: File,
        watermark: File,
        target: File,
        expectedDurationMs: Long? = null,
        onProgress: (Int) -> Unit = {},
    ): AppResult<File> {
        val exe = locator.locate()
            ?: return AppError.AssemblyFailed("ffmpeg unavailable").err()
        val runner = FfmpegRunner(exe, dispatchers)

        target.parentFile?.mkdirs()
        val tempFile = File(target.parentFile, ".${target.name}.tmp.mp4")
        tempFile.delete()

        val args = listOf(
            "-y",
            "-i", source.absolutePath,
            "-i", watermark.absolutePath,
            // The PNG is rendered at the frame size, so it lands at 0:0 as-is.
            "-filter_complex", "[0:v][1:v]overlay=0:0[v]",
            "-map", "[v]",
            // Audio is optional (a project without narration or music has none).
            "-map", "0:a?",
            "-c:v", "libx264", "-crf", "20", "-preset", "medium", "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart",
            "-metadata", "comment=AI-generated (Kenang) - watermarked preview",
            tempFile.absolutePath,
        )
        return when (val result = runner.run(args, expectedDurationMs, onProgress)) {
            is AppResult.Ok -> moveIntoPlace(tempFile, target)
            is AppResult.Err -> {
                tempFile.delete()
                result
            }
        }
    }

    /**
     * Windows trap (owner 2026-09-09, "selalu gagal di langkah ini"): a video
     * player keeps an exclusive handle on the PREVIOUS export, so replacing it
     * throws AccessDenied — and a finished, expensive render was deleted with a
     * message that blamed disk space. The render is never thrown away again:
     * the replace is retried briefly, then the video is kept under a free name
     * beside the locked one.
     */
    internal suspend fun moveIntoPlace(temp: File, target: File): AppResult<File> {
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            val moved = runCatching {
                try {
                    Files.move(
                        temp.toPath(), target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                target
            }
            moved.getOrNull()?.let { return it.ok() }
            lastError = moved.exceptionOrNull()
            Napier.w("assembly move attempt ${attempt + 1} failed: ${lastError?.message}")
            if (attempt < 2) kotlinx.coroutines.delay(800)
        }

        // Still locked — keep the finished video rather than lose it.
        val alternative = freeSibling(target)
        return runCatching {
            Files.move(temp.toPath(), alternative.toPath(), StandardCopyOption.REPLACE_EXISTING)
            alternative
        }.fold(
            onSuccess = {
                Napier.w("previous export is locked by another program — saved as ${it.name}")
                it.ok()
            },
            onFailure = {
                temp.delete()
                AppError.AssemblyFailed(
                    "target locked and no alternative name worked: ${lastError?.message}",
                    lastError,
                ).err()
            },
        )
    }

    /** `<name>_2.mp4`, `_3`… — the first name nothing occupies. */
    private fun freeSibling(target: File): File {
        val base = target.nameWithoutExtension
        val ext = target.extension.ifBlank { "mp4" }
        var n = 2
        while (true) {
            val candidate = File(target.parentFile, "${base}_$n.$ext")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    companion object {
        /**
         * Stages the Trial watermark PNG from resources. Callers pass it into
         * the spec ONLY when `LicenseGate.state().watermarkRequired` — with the
         * DevFull stub that is never (D-002); the path stays covered by a
         * flag-forced unit test so Phase 05 flips it without FFmpeg changes.
         */
        fun stageWatermark(targetDir: File): File? = runCatching {
            val target = File(targetDir.apply { mkdirs() }, "kenang_trial.png")
            if (!target.isFile) {
                val res = VideoAssembler::class.java.getResourceAsStream("/watermark/kenang_trial.png")
                    ?: return null
                res.use { input -> target.outputStream().use { input.copyTo(it) } }
            }
            target
        }.onFailure { Napier.w("watermark staging failed: ${it.message}") }.getOrNull()
    }
}
