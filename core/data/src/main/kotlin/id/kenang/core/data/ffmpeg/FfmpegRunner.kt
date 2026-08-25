package id.kenang.core.data.ffmpeg

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.DispatcherProvider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.concurrent.thread

/**
 * Runs the bundled ffmpeg.exe with `-progress pipe:1` percent reporting.
 * The UI never freezes: everything runs on the IO dispatcher and the process
 * is killed when the coroutine is cancelled.
 */
class FfmpegRunner(
    private val exe: File,
    private val dispatchers: DispatcherProvider,
) {
    /**
     * Executes ffmpeg with [args]. [expectedDurationMs] drives [onProgress]
     * (0–100); without it only 0/100 are reported.
     */
    suspend fun run(
        args: List<String>,
        expectedDurationMs: Long? = null,
        onProgress: (Int) -> Unit = {},
    ): AppResult<Unit> = withContext(dispatchers.io) {
        val cmd = listOf(exe.absolutePath, "-hide_banner", "-loglevel", "error", "-nostats", "-progress", "pipe:1") + args
        Napier.i("ffmpeg: ${cmd.joinToString(" ").take(500)}")
        val process = try {
            ProcessBuilder(cmd).start()
        } catch (t: Throwable) {
            return@withContext AppError.AssemblyFailed("cannot start ffmpeg: ${t.message}", t).err()
        }
        val stderr = StringBuilder()
        try {
            thread(isDaemon = true, name = "ffmpeg-stderr") {
                runCatching {
                    process.errorStream.bufferedReader().forEachLine { line ->
                        synchronized(stderr) { if (stderr.length < 8192) stderr.appendLine(line) }
                    }
                }
            }
            onProgress(0)
            val exit = runInterruptible {
                process.inputStream.bufferedReader().forEachLine { line ->
                    // out_time_us (µs) is authoritative; out_time_ms is µs too (ffmpeg quirk).
                    val us = line.substringAfter("out_time_us=", "").ifBlank {
                        line.substringAfter("out_time_ms=", "")
                    }.toLongOrNull()
                    if (us != null && expectedDurationMs != null && expectedDurationMs > 0) {
                        onProgress(((us / 1000.0) / expectedDurationMs * 100).toInt().coerceIn(0, 99))
                    }
                }
                process.waitFor()
            }
            if (exit == 0) {
                onProgress(100)
                Unit.ok()
            } else {
                val tail = synchronized(stderr) { stderr.toString().takeLast(600) }
                AppError.AssemblyFailed("ffmpeg exit $exit: $tail").err()
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            AppError.AssemblyFailed(t.message, t).err()
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    /** Media duration via `ffmpeg -i` stderr parse (no ffprobe bundled). */
    suspend fun probeDurationMs(media: File): Long? = withContext(dispatchers.io) {
        runCatching {
            val process = ProcessBuilder(exe.absolutePath, "-hide_banner", "-i", media.absolutePath)
                .redirectErrorStream(true).start()
            val out = process.inputStream.bufferedReader().readText()
            process.waitFor()
            val m = Regex("Duration: (\\d+):(\\d{2}):(\\d{2})\\.(\\d{2})").find(out) ?: return@runCatching null
            val (h, min, s, cs) = m.destructured
            ((h.toLong() * 3600 + min.toLong() * 60 + s.toLong()) * 1000) + cs.toLong() * 10
        }.getOrNull()
    }

    /** Extracts a single JPEG frame (result-screen thumbnail). */
    suspend fun extractThumbnail(video: File, out: File, atSeconds: Double = 0.5): Boolean =
        run(
            listOf(
                "-y", "-ss", atSeconds.toString(), "-i", video.absolutePath,
                "-frames:v", "1", "-q:v", "3", out.absolutePath,
            ),
        ) is AppResult.Ok
}
