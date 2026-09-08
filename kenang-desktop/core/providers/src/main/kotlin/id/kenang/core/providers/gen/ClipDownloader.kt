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

    /**
     * [overwrite] = this download produces a FRESH artifact and must replace
     * whatever sits at [target]. Clip files are named `<sceneId>.mp4`, so a
     * regenerated scene downloads onto the path its previous clip occupies —
     * without this the reuse short-circuit below kept the OLD file and the
     * paid render was silently discarded (owner 2026-09-08: replaced two
     * photos, paid for two Kling renders, still saw the old clips).
     */
    suspend fun download(
        url: String,
        target: File,
        expectedSha256: String? = null,
        overwrite: Boolean = false,
    ): AppResult<File> {
        target.parentFile?.mkdirs()
        // The partial is bound to the URL: resuming one video's bytes into a
        // DIFFERENT video would splice two clips into one corrupt file, and
        // the scene's clip path is reused across renders.
        val part = partFile(target, url)
        if (overwrite) {
            target.delete()
            staleParts(target, keep = part).forEach { it.delete() }
        } else if (target.isFile && target.length() > 0) {
            return target.ok()
        }

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

    /** Partials of EARLIER renders of this same clip. */
    private fun staleParts(target: File, keep: File): List<File> =
        target.parentFile?.listFiles()?.filter {
            it.name.startsWith("${target.name}.") && it.name.endsWith(".part") && it != keep
        } ?: emptyList()

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

    companion object {
        /**
         * `<clip>.<url fingerprint>.part` — one partial per source video, so a
         * half-downloaded clip is only ever resumed against the URL it came
         * from (a cross-URL resume would splice two videos together).
         */
        internal fun partFile(target: File, url: String): File {
            val tag = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
                .joinToString("") { "%02x".format(it) }.take(12)
            return File(target.parentFile, "${target.name}.$tag.part")
        }
    }
}
