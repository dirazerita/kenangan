package id.kenang.core.providers.optional

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class OptionalClientsTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `gemini models list 200 means key ok`() = runTest {
        val engine = MockEngine { respond("""{"models":[{"name":"models/gemini-2.5-flash"}]}""", HttpStatusCode.OK, jsonHeaders) }
        val result = GeminiClient(HttpClient(engine)).testKey("good-key")
        assertIs<AppResult.Ok<Unit>>(result)
    }

    @Test
    fun `gemini 400 invalid key maps to InvalidKey`() = runTest {
        val engine = MockEngine { respond("""{"error":{"code":400,"message":"API key not valid"}}""", HttpStatusCode.BadRequest, jsonHeaders) }
        val result = GeminiClient(HttpClient(engine)).testKey("bad-key")
        assertIs<AppError.InvalidKey>(assertIs<AppResult.Err>(result).error)
    }

    @Test
    fun `elevenlabs voices 200 means key ok`() = runTest {
        val engine = MockEngine { respond("""{"voices":[{"voice_id":"OKanSStS6li6xyU1WdXa","name":"Meraki"}]}""", HttpStatusCode.OK, jsonHeaders) }
        val result = ElevenLabsClient(HttpClient(engine)).testKey("good-key")
        assertIs<AppResult.Ok<Unit>>(result)
    }

    @Test
    fun `elevenlabs 401 maps to InvalidKey`() = runTest {
        val engine = MockEngine { respond("""{"detail":{"status":"invalid_api_key"}}""", HttpStatusCode.Unauthorized, jsonHeaders) }
        val result = ElevenLabsClient(HttpClient(engine)).testKey("bad-key")
        assertIs<AppError.InvalidKey>(assertIs<AppResult.Err>(result).error)
    }

    @Test
    fun `elevenlabs 402 maps to ProviderBalance - id voices need paid plan`() = runTest {
        val engine = MockEngine { respond("""{"detail":{"status":"payment_required"}}""", HttpStatusCode.PaymentRequired, jsonHeaders) }
        val result = ElevenLabsClient(HttpClient(engine)).testKey("free-tier-key")
        assertIs<AppError.ProviderBalance>(assertIs<AppResult.Err>(result).error)
    }
}
