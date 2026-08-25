package id.kenang.core.providers.fal

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.Provider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.Instant

/**
 * fal v3 CDN upload (same flow as the current fal_client default "fal_v3"):
 *   1. POST {rest}/storage/auth/token?storage_type=fal-cdn-v3  → {token, token_type, base_url}
 *   2. POST {cdn}/files/upload with "Authorization: {token_type} {token}",
 *      X-Fal-File-Name and the raw bytes                        → {access_url}
 * Uploads are free; any available key from the pool works. The short-lived
 * CDN token is cached until close to expiry.
 */
class FalStorage(
    private val http: HttpClient,
    private val keyPool: FalKeyPool,
    private val restUrl: String = "https://rest.fal.ai",
    private val cdnUrl: String = "https://v3.fal.media",
) {
    private val json = Json { ignoreUnknownKeys = true }

    private data class CdnToken(val header: String, val expiresAtEpochS: Long)
    private var cached: CdnToken? = null

    override fun toString() = "FalStorage($cdnUrl)"

    suspend fun uploadFile(file: File): AppResult<String> {
        val contentType = when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }
        return uploadBytes(file.readBytes(), file.name, contentType)
    }

    suspend fun uploadBytes(data: ByteArray, fileName: String, contentType: String): AppResult<String> {
        var lastError: AppError = AppError.Unknown("no attempt")
        repeat(3) { attempt ->
            try {
                val token = when (val t = getToken()) {
                    is AppResult.Ok -> t.value
                    is AppResult.Err -> return t
                }
                val upload = http.post("$cdnUrl/files/upload") {
                    // Slow uplinks need far more than the default request timeout.
                    timeout { requestTimeoutMillis = 5 * 60_000L }
                    header("Authorization", token.header)
                    header("X-Fal-File-Name", fileName)
                    contentType(ContentType.parse(contentType))
                    setBody(data)
                }
                val text = upload.bodyAsText()
                if (upload.status.value in 200..299) {
                    val url = json.parseToJsonElement(text).jsonObject["access_url"]?.jsonPrimitive?.content
                        ?: return AppError.ProviderFailed(Provider.FAL, "no access_url").err()
                    return url.ok()
                }
                lastError = AppError.ProviderFailed(Provider.FAL, "upload ${upload.status.value}: ${text.take(200)}")
                cached = null // token might be the problem — refresh on retry
            } catch (t: Throwable) {
                // Transient EOF/timeout on slow links — retry with a fresh connection.
                lastError = FalQueueClient.mapTransportError(t)
                cached = null
            }
            io.github.aakira.napier.Napier.w("upload attempt ${attempt + 1} for $fileName failed — retrying")
        }
        return lastError.err()
    }

    private suspend fun getToken(): AppResult<CdnToken> {
        cached?.let { if (Instant.now().epochSecond < it.expiresAtEpochS - 30) return it.ok() }
        val key = keyPool.currentKey()
            ?: return AppError.ProviderBalance(Provider.FAL).err()
        val response = http.post("$restUrl/storage/auth/token?storage_type=fal-cdn-v3") {
            header("Authorization", "Key ${key.key}")
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            return FalQueueClient.mapHttpError(response.status, text, key.label).err()
        }
        val obj = json.parseToJsonElement(text).jsonObject
        val tokenValue = obj["token"]?.jsonPrimitive?.content
            ?: return AppError.ProviderFailed(Provider.FAL, "no token").err()
        val tokenType = obj["token_type"]?.jsonPrimitive?.content ?: "Bearer"
        val expiresAt = obj["expires_at"]?.jsonPrimitive?.content
            ?.let { runCatching { Instant.parse(it).epochSecond }.getOrNull() }
            ?: (Instant.now().epochSecond + 300)
        val token = CdnToken("$tokenType $tokenValue", expiresAt)
        cached = token
        return token.ok()
    }
}
