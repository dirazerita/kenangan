package id.kenang.core.data.media

import id.kenang.core.common.DispatcherProvider
import id.kenang.core.data.ffmpeg.FfmpegLocator
import id.kenang.core.data.ffmpeg.FfmpegRunner
import io.github.aakira.napier.Napier
import java.io.File

/** Desktop [AudioProbe]: the bundled ffmpeg measures, re-encodes and trims. */
class FfmpegAudioProbe(
    private val locator: FfmpegLocator,
    private val dispatchers: DispatcherProvider,
) : AudioProbe {

    private fun runner(): FfmpegRunner? = locator.locate()?.let { FfmpegRunner(it, dispatchers) }

    override suspend fun durationMs(file: File): Long? {
        if (!file.isFile) return null
        return runner()?.probeDurationMs(file)
    }

    override suspend fun prepare(file: File, out: File, maxSeconds: Double): File? {
        val ffmpeg = runner()
        val ms = durationMs(file)
        return when (AudioIntake.plan(file.extension, ms, maxSeconds, canTranscode = ffmpeg != null)) {
            AudioIntake.Action.REJECT -> null
            AudioIntake.Action.COPY -> runCatching {
                val target = File(out.parentFile, out.nameWithoutExtension + "." + file.extension.lowercase())
                target.parentFile?.mkdirs()
                file.copyTo(target, overwrite = true)
            }.onFailure { Napier.w("audio copy failed: ${it.message}") }.getOrNull()
            AudioIntake.Action.TRANSCODE, AudioIntake.Action.TRIM -> {
                val target = File(out.parentFile, out.nameWithoutExtension + ".mp3")
                target.parentFile?.mkdirs()
                val args = mutableListOf("-y", "-i", file.absolutePath)
                if (ms != null && ms / 1000.0 > maxSeconds) args += listOf("-t", "%.2f".format(java.util.Locale.US, maxSeconds))
                args += listOf("-vn", "-acodec", "libmp3lame", "-b:a", "128k", target.absolutePath)
                when (ffmpeg!!.run(args)) {
                    is id.kenang.core.common.AppResult.Ok -> target.takeIf { it.isFile && it.length() > 0 }
                    is id.kenang.core.common.AppResult.Err -> null
                }
            }
        }
    }
}
