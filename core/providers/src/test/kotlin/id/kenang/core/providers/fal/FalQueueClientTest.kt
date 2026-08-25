package id.kenang.core.providers.fal

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.providers.InMemoryStore
import id.kenang.core.providers.vault.FalKey
import id.kenang.core.providers.vault.KeyVault
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Multi-key failover (AD-14) against recorded fal fixtures — zero network.
 */
class FalQueueClientTest {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/fixtures/$name")) { "missing fixture $name" }
            .bufferedReader().readText()

    private fun vaultWith(vararg keys: FalKey): KeyVault =
        KeyVault(InMemoryStore()).apply { saveFalKeys(keys.toList()) }

    private val submitBody = buildJsonObject { put("prompt", "test") }

    private fun clientWith(pool: FalKeyPool, handler: MockEngine): FalQueueClient =
        FalQueueClient(HttpClient(handler), pool, baseUrl = "https://queue.fal.run")

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `submit succeeds with first key`() = runTest {
        val pool = FalKeyPool(vaultWith(FalKey("Utama", "fal_key_1")))
        val engine = MockEngine { respond(fixture("submit_ok.json"), HttpStatusCode.OK, jsonHeaders) }
        val result = clientWith(pool, engine).submit("fal-ai/kling-video/v3/standard/image-to-video", submitBody)

        val job = assertIs<AppResult.Ok<SubmittedFalJob>>(result).value
        assertEquals("Utama", job.keyLabel)
        assertEquals("req-11111111-2222-3333-4444-555555555555", job.requestId)
    }

    @Test
    fun `balance-exhausted key fails over to next key and emits KeySwitched`() = runTest {
        val pool = FalKeyPool(vaultWith(FalKey("Utama", "fal_key_1"), FalKey("Cadangan", "fal_key_2")))
        val switches = mutableListOf<KeySwitched>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            pool.keySwitched.collect { switches.add(it) }
        }

        val engine = MockEngine { request ->
            val auth = request.headers["Authorization"] ?: ""
            if (auth.contains("fal_key_1")) {
                // Exact Phase 00 T4 signature (poc/results.csv)
                respond(fixture("balance_locked.json"), HttpStatusCode.Forbidden, jsonHeaders)
            } else {
                respond(fixture("submit_ok.json"), HttpStatusCode.OK, jsonHeaders)
            }
        }

        val result = clientWith(pool, engine).submit("fal-ai/kling-video/v3/standard/image-to-video", submitBody)
        advanceUntilIdle()

        val job = assertIs<AppResult.Ok<SubmittedFalJob>>(result).value
        assertEquals("Cadangan", job.keyLabel)
        assertEquals(listOf(KeySwitched("Utama", "Cadangan")), switches)
        assertEquals(FalKeyStatus.SALDO_HABIS, pool.statuses()["Utama"])
        collector.cancel()
    }

    @Test
    fun `all keys exhausted maps to ProviderBalance`() = runTest {
        val pool = FalKeyPool(vaultWith(FalKey("A", "fal_key_1"), FalKey("B", "fal_key_2")))
        val engine = MockEngine {
            respond(fixture("balance_locked_topup.json"), HttpStatusCode.Forbidden, jsonHeaders)
        }
        val result = clientWith(pool, engine).submit("fal-ai/kling-video/v3/standard/image-to-video", submitBody)

        val err = assertIs<AppResult.Err>(result).error
        assertIs<AppError.ProviderBalance>(err)
    }

    @Test
    fun `exhausted key becomes available again after cooldown`() = runTest {
        var now = 0L
        val pool = FalKeyPool(vaultWith(FalKey("A", "k")), cooldownMillis = 600_000, clock = { now })
        pool.markExhausted("A")
        assertTrue(pool.availableKeys().isEmpty())
        now = 600_001
        assertEquals(1, pool.availableKeys().size)
    }

    @Test
    fun `401 maps to InvalidKey`() = runTest {
        val pool = FalKeyPool(vaultWith(FalKey("Utama", "bad")))
        val engine = MockEngine { respond(fixture("invalid_key.json"), HttpStatusCode.Unauthorized, jsonHeaders) }
        val result = clientWith(pool, engine).submit("openrouter/router", submitBody)

        val err = assertIs<AppResult.Err>(result).error
        assertEquals("Utama", assertIs<AppError.InvalidKey>(err).keyLabel)
    }

    @Test
    fun `429 maps to RateLimited`() = runTest {
        val pool = FalKeyPool(vaultWith(FalKey("Utama", "k")))
        val engine = MockEngine { respond("""{"detail":"rate limit"}""", HttpStatusCode.TooManyRequests, jsonHeaders) }
        val result = clientWith(pool, engine).submit("openrouter/router", submitBody)
        assertIs<AppError.RateLimited>(assertIs<AppResult.Err>(result).error)
    }

    @Test
    fun `status and result use the job's own key`() = runTest {
        val pool = FalKeyPool(vaultWith(FalKey("A", "key_a"), FalKey("B", "key_b")))
        val seenAuth = mutableListOf<String>()
        val engine = MockEngine { request ->
            seenAuth.add(request.headers["Authorization"] ?: "")
            if (request.url.encodedPath.endsWith("/status")) {
                respond(fixture("status_completed.json"), HttpStatusCode.OK, jsonHeaders)
            } else {
                respond(fixture("result_video.json"), HttpStatusCode.OK, jsonHeaders)
            }
        }
        val client = clientWith(pool, engine)
        // Job submitted by key B — polling must NOT use key A even though A is first.
        val job = SubmittedFalJob("req-1", "fal-ai/kling-video/v3/standard/image-to-video", "B")
        assertIs<AppResult.Ok<FalStatusResponse>>(client.status(job))
        assertIs<AppResult.Ok<FalJobResult>>(client.result(job))
        assertTrue(seenAuth.all { it == "Key key_b" }, "polling must stick to the submitting key, saw: $seenAuth")
    }
}
