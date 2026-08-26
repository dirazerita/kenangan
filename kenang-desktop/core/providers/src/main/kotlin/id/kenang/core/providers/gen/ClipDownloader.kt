package id.kenang.core.providers.gen

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.Provider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import id.kenang.core.providers.fal.FalQueueClient
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.jvm.javaio.copyTo
import java.io.File
import java.security.MessageDigest

/**
 * Resume-capable MP4 download (MASTER_PROMPT_04 §4.1): a partial `.part` file
 * is continued with an HTTP Range request; on completion it is moved into
 * place (and sha256-checked when a hash is provided).
 */
class ClipDownloader(private val http: HttpClient) {

    suspend fun download(url: String, target: File, expectedSha256: String? = null): AppResult<File> {
        target.parentFile?.mkdirs()
        if (target.isFile && target.length() > 0) return target.ok()
        val part = File(target.parentFile, target.name + ".part")

        try {
            val existing = if (part.isFile) part.length() else 0L
            val response = http.get(url) {
                if (existing > 0) header("Range", "bytes=$existing-")
            }
            if (response.status.value !in 200..299) {
                return AppError.ProviderFailed(Provider.FAL, "download HTTP ${response.status.value}").err()
            }
            val append = response.status == HttpStatusCode.PartialContent && existing > 0
            if (!append && existing > 0) part.delete() // server ignored Range — start over
            java.io.FileOutputStream(part, append).use { out ->
                response.bodyAsChannel().copyTo(out)
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            Napier.w("clip download interrupted (partial kept for resume): ${t.message}")
            return FalQueueClient.mapTransportError(t).err()
        }

        if (expectedSha256 != null) {
            val actual = sha256(part)
            if (!actual.equals(expectedSha256, ignoreCase = true)) {
                part.delete()
                return AppError.ProviderFailed(Provider.FAL, "clip sha256 mismatch").err()
            }
        }
        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        return target.ok()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { ins ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = ins.read(buf); if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
