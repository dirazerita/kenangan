package id.kenang.core.data.ffmpeg

import id.kenang.core.data.AppDirs
import io.github.aakira.napier.Napier
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Locates (and on first run, stages) the bundled ffmpeg.exe.
 * Phase 04 uses this for final assembly (AD-05). GPL/LGPL attribution in About.
 *
 * Staging flow: the packaged app ships ffmpeg.exe as a resource
 * (app/ffmpeg-dist, downloaded+sha256-verified by the Gradle task
 * `downloadFfmpeg`); first run copies it to %APPDATA%/Kenang/tools/ffmpeg/
 * and smoke-checks with `ffmpeg -version`.
 */
class FfmpegLocator {

    private val staged: File get() = File(AppDirs.ffmpegDir, "ffmpeg.exe")

    /** Returns the ffmpeg executable, staging it on first run. Null if unavailable. */
    fun locate(): File? {
        if (staged.isFile && smokeCheck(staged)) return staged
        return stageFromResources()?.takeIf { smokeCheck(it) }
    }

    fun isAvailable(): Boolean = locate() != null

    private fun stageFromResources(): File? = runCatching {
        val res = javaClass.getResourceAsStream("/ffmpeg/ffmpeg.exe") ?: run {
            Napier.w("ffmpeg.exe not present in app resources (dev build without downloadFfmpeg?)")
            return null
        }
        staged.parentFile.mkdirs()
        res.use { input -> staged.outputStream().use { input.copyTo(it) } }
        Napier.i("ffmpeg staged to ${staged.absolutePath}")
        staged
    }.onFailure { Napier.e("ffmpeg staging failed: ${it.message}") }.getOrNull()

    private fun smokeCheck(exe: File): Boolean = runCatching {
        val proc = ProcessBuilder(exe.absolutePath, "-version")
            .redirectErrorStream(true)
            .start()
        val ok = proc.waitFor(10, TimeUnit.SECONDS) && proc.exitValue() == 0
        if (!ok) Napier.w("ffmpeg smoke check failed (exit=${runCatching { proc.exitValue() }.getOrNull()})")
        ok
    }.getOrDefault(false)
}
