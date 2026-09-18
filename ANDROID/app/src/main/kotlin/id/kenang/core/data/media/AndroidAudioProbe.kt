package id.kenang.core.data.media

import io.github.aakira.napier.Napier
import java.io.File

/**
 * Android [AudioProbe]: MediaMetadataRetriever measures; MP3/WAV/M4A/AAC go
 * to the provider as they are; nothing is trimmed here (no ffmpeg), so a
 * recording over the ceiling is refused with a clear message instead.
 */
class AndroidAudioProbe : AudioProbe {

    override suspend fun durationMs(file: File): Long? = MediaProbe.durationMs(file)

    override suspend fun prepare(file: File, out: File, maxSeconds: Double): File? {
        val ms = durationMs(file)
        val ext = file.extension.lowercase()
        val accepted = ext in setOf("mp3", "wav", "m4a", "aac")
        if (!accepted) return null
        return when (AudioIntake.plan(if (accepted) "mp3" else ext, ms, maxSeconds, canTranscode = false)) {
            AudioIntake.Action.COPY -> runCatching {
                val target = File(out.parentFile, out.nameWithoutExtension + "." + ext)
                target.parentFile?.mkdirs()
                file.copyTo(target, overwrite = true)
            }.onFailure { Napier.w("audio copy failed: ${it.message}") }.getOrNull()
            else -> null
        }
    }
}
