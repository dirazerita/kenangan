package id.kenang.core.common

import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import io.github.aakira.napier.Antilog
import io.github.aakira.napier.LogLevel

/**
 * Logging setup. SECURITY RULE (AD-11): API keys must NEVER be logged —
 * always pass keys through [maskKey] before including them in any message.
 */
object Logging {
    fun init(logDir: File) {
        val fileLog = runCatching {
            logDir.mkdirs()
            FileAntilog(File(logDir, "kenang-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))}.log"))
        }.getOrNull()
        Napier.base(fileLog ?: DebugAntilog())
    }
}

/** Masks a secret for UI/log display: `fal_…a1b2` style (AD-11). */
fun maskKey(key: String, prefixHint: String = ""): String {
    if (key.isBlank()) return ""
    val tail = key.takeLast(4)
    val head = prefixHint.ifBlank { key.take(4).takeWhile { it.isLetter() } }
    return "${head}…$tail"
}

private class FileAntilog(private val file: File) : Antilog() {
    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    override fun performLog(priority: LogLevel, tag: String?, throwable: Throwable?, message: String?) {
        runCatching {
            val line = "${LocalDateTime.now().format(fmt)} [${priority.name}] ${tag ?: ""} ${message ?: ""}" +
                (throwable?.let { " | ${it::class.simpleName}: ${it.message}" } ?: "")
            file.appendText(line + System.lineSeparator())
        }
        // Also echo to console in dev.
        println("${priority.name}: ${tag ?: ""} ${message ?: ""}")
    }
}
