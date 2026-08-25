package id.kenang.core.providers.optional

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.Provider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

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
}
