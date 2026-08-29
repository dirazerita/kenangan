package id.kenang.core.providers.optional

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.Provider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.Base64

/**
 * Thin optional Gemini client. Phase 02 only needs the key test
 * (`models.list` is free); Phase 03 adds the analysis call.
 */
class GeminiClient(
    private val http: HttpClient,
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
) {
    /** Free key check: GET /v1beta/models. */
    suspend fun testKey(apiKey: String): AppResult<Unit> {
        val response = try {
            http.get("$baseUrl/v1beta/models") {
                url.parameters.append("key", apiKey)
            }
        } catch (t: Throwable) {
            return id.kenang.core.providers.fal.FalQueueClient.mapTransportError(t).err()
        }
        return when {
            response.status.value in 200..299 -> Unit.ok()
            response.status == HttpStatusCode.Unauthorized ||
                response.status == HttpStatusCode.Forbidden ||
                response.status == HttpStatusCode.BadRequest -> AppError.InvalidKey(Provider.GEMINI).err()
            response.status == HttpStatusCode.TooManyRequests -> AppError.RateLimited(Provider.GEMINI).err()
            else -> AppError.ProviderFailed(Provider.GEMINI, response.bodyAsText().take(300)).err()
        }
    }

    /**
     * Vision call with forced JSON output (the "analisis lebih tajam" path).
     * [model] is the bare Gemini model id (config's analysis.model without the
     * "google/" router prefix). Returns the raw JSON text of the first candidate.
     */
    suspend fun generateVisionJson(
        apiKey: String,
        model: String,
        prompt: String,
        imageBytes: ByteArray?,
        mimeType: String = "image/jpeg",
    ): AppResult<String> {
        val body = buildJsonObject {
            putJsonArray("contents") {
                add(buildJsonObject {
                    putJsonArray("parts") {
                        if (imageBytes != null && imageBytes.isNotEmpty()) {
                            add(buildJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", mimeType)
                                    put("data", Base64.getEncoder().encodeToString(imageBytes))
                                }
                            })
                        }
                        add(buildJsonObject { put("text", prompt) })
                    }
                })
            }
            putJsonObject("generationConfig") {
                put("response_mime_type", "application/json")
                put("temperature", 0.2)
            }
        }
        val response = try {
            http.post("$baseUrl/v1beta/models/$model:generateContent") {
                header("x-goog-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(body.toString())
            }
        } catch (t: Throwable) {
            return id.kenang.core.providers.fal.FalQueueClient.mapTransportError(t).err()
        }
        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            return when (response.status) {
                HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden, HttpStatusCode.BadRequest ->
                    AppError.InvalidKey(Provider.GEMINI).err()
                HttpStatusCode.TooManyRequests -> AppError.RateLimited(Provider.GEMINI).err()
                else -> AppError.ProviderFailed(Provider.GEMINI, text.take(300)).err()
            }
        }
        return runCatching {
            Json { ignoreUnknownKeys = true }.parseToJsonElement(text).jsonObject["candidates"]!!
                .jsonArray[0].jsonObject["content"]!!.jsonObject["parts"]!!
                .jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        }.fold(
            onSuccess = { it.ok() },
            onFailure = { AppError.ProviderFailed(Provider.GEMINI, "Malformed response", it).err() },
        )
    }
}
