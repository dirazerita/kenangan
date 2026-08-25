package id.kenang.core.providers.fal

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.Provider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * fal storage upload (same flow as the proven PoC helper / fal_client):
 * POST {rest}/storage/upload/initiate?storage_type=gcs → {upload_url, file_url},
 * then PUT the bytes. Uploads are free; any available key from the pool works.
 */
class FalStorage(
    private val http: HttpClient,
    private val keyPool: FalKeyPool,
    private val restUrl: String = "https://rest.fal.ai",
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun uploadFile(file: File): AppResult<String> {
        val key = keyPool.currentKey()
            ?: return AppError.ProviderBalance(Provider.FAL).err()
        val contentType = when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }
        return try {
            val init = http.post("$restUrl/storage/upload/initiate?storage_type=gcs") {
                header("Authorization", "Key ${key.key}")
                contentType(ContentType.Application.Json)
                setBody("""{"file_name":"${file.name}","content_type":"$contentType"}""")
            }
            val initText = init.bodyAsText()
            if (init.status.value !in 200..299) {
                return FalQueueClient.mapHttpError(init.status, initText, key.label).err()
            }
            val obj = json.parseToJsonElement(initText).jsonObject
            val uploadUrl = obj["upload_url"]?.jsonPrimitive?.content
                ?: return AppError.ProviderFailed(Provider.FAL, "no upload_url").err()
            val fileUrl = obj["file_url"]?.jsonPrimitive?.content
                ?: return AppError.ProviderFailed(Provider.FAL, "no file_url").err()

            val put = http.put(uploadUrl) {
                contentType(ContentType.parse(contentType))
                setBody(file.readBytes())
            }
            if (put.status.value !in 200..299) {
                return AppError.ProviderFailed(Provider.FAL, "upload PUT ${put.status.value}").err()
            }
            fileUrl.ok()
        } catch (t: Throwable) {
            FalQueueClient.mapTransportError(t).err()
        }
    }
}
