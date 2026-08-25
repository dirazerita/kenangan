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
            is AppResult.Ok -> runCatching {
                try {
                    Files.move(
                        tempFile.toPath(), finalFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING,
                    )
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(tempFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                finalFile
            }.fold(
                onSuccess = { it.ok() },
                onFailure = { AppError.AssemblyFailed("move failed: ${it.message}", it).err() },
            )
            is AppResult.Err -> {
                tempFile.delete()
                result
            }
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
