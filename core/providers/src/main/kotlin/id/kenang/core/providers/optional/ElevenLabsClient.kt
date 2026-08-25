package id.kenang.core.providers.optional

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.Provider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode

/**
 * Thin optional ElevenLabs client. Phase 02 only needs the key test
 * (GET /v1/voices is free); Phase 04 adds premium TTS.
 * Note: Indonesian library voices need a PAID plan for API use (MEMORY §3).
 */
class ElevenLabsClient(
    private val http: HttpClient,
    private val baseUrl: String = "https://api.elevenlabs.io",
) {
    suspend fun testKey(apiKey: String): AppResult<Unit> {
        val response = try {
            http.get("$baseUrl/v1/voices") {
                header("xi-api-key", apiKey)
            }
        } catch (t: Throwable) {
            return id.kenang.core.providers.fal.FalQueueClient.mapTransportError(t).err()
        }
        return when {
            response.status.value in 200..299 -> Unit.ok()
            response.status == HttpStatusCode.Unauthorized -> AppError.InvalidKey(Provider.ELEVENLABS).err()
            response.status == HttpStatusCode.PaymentRequired -> AppError.ProviderBalance(Provider.ELEVENLABS).err()
            response.status == HttpStatusCode.TooManyRequests -> AppError.RateLimited(Provider.ELEVENLABS).err()
            else -> AppError.ProviderFailed(Provider.ELEVENLABS, response.bodyAsText().take(300)).err()
        }
    }
}
