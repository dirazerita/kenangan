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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Owner 2026-09-15: every scene of a six-person project failed within
 * fifteen seconds "while the balance was there". The log: Kling answered
 * HTTP 422 "Maximum three image elements are allowed" (four were sent), the
 * app treated that as provider trouble, retried the same body, rotated
 * through all five keys in ten seconds, and then refused the remaining
 * scenes with "semua key sedang jeda". These pin every link of that chain.
 */
class RequestRejectionTest {

    /** The exact body fal returned. */
    private val klingRejection = """{"detail":[{"type":"value_error","loc":["body"],"msg":"Value error, Maximum three image elements are allowed.","input":{"prompt":"@Element1 is Man"}}]}"""

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun vaultWith(vararg keys: FalKey): KeyVault =
        KeyVault(InMemoryStore()).apply { saveFalKeys(keys.toList()) }

    @Test
    fun `a 422 is a rejected request, not provider trouble`() {
        val error = FalQueueClient.mapHttpError(HttpStatusCode.UnprocessableEntity, klingRejection, "A")
        assertIs<AppError.BadRequest>(error)
        assertEquals("Value error, Maximum three image elements are allowed.", error.detail)

        assertIs<AppError.ProviderFailed>(FalQueueClient.mapHttpError(HttpStatusCode.BadGateway, "upstream", "A"))
        assertIs<AppError.BadRequest>(FalQueueClient.mapHttpError(HttpStatusCode.BadRequest, "nope", "A"))
        // Moderation keeps its own class even on a 422.
        assertIs<AppError.ContentBlocked>(
            FalQueueClient.mapHttpError(HttpStatusCode.UnprocessableEntity, """{"detail":"content policy violation"}""", "A"),
        )
        assertEquals("plain text", FalQueueClient.requestDetail("plain text"))
    }

    @Test
    fun `a rejected submit leaves the key in service`() = runTest {
        val pool = FalKeyPool(vaultWith(FalKey("A", "k1"), FalKey("B", "k2")))
        val client = FalQueueClient(
            HttpClient(MockEngine { respond(klingRejection, HttpStatusCode.UnprocessableEntity, jsonHeaders) }),
            pool, baseUrl = "https://queue.fal.run",
        )
        val result = client.submit("fal-ai/kling-video/v3/pro/image-to-video", buildJsonObject { put("prompt", "x") })
        assertIs<AppResult.Err>(result)
        assertIs<AppError.BadRequest>(result.error)
        assertEquals("A", pool.currentKey()?.label, "the key did nothing wrong and must not rest")
        assertEquals(FalKeyStatus.AKTIF, pool.statuses()["A"])
    }

    @Test
    fun `keys resting only for trouble still serve the next submit`() {
        var now = 0L
        val pool = FalKeyPool(
            vaultWith(FalKey("A", "k1"), FalKey("B", "k2"), FalKey("C", "k3")),
            cooldownMillis = 600_000, troubleCooldownMillis = 90_000, clock = { now },
        )
        pool.markExhausted("A", CooldownReason.TROUBLE)
        now = 10_000
        pool.markExhausted("B", CooldownReason.TROUBLE)
        now = 20_000
        pool.markExhausted("C", CooldownReason.TROUBLE)

        // Every key rests - the one that rested first is used, not nobody.
        assertEquals("A", pool.currentKey()?.label)
        assertTrue(pool.restingReasons().all { it == CooldownReason.TROUBLE })

        // A key refused for money is never the fallback.
        pool.markExhausted("A", CooldownReason.BALANCE)
        assertEquals("B", pool.currentKey()?.label)
        pool.markExhausted("B", CooldownReason.TOPUP_LOCK)
        pool.markExhausted("C", CooldownReason.REJECTED)
        assertNull(pool.currentKey(), "money and identity refusals still block")
    }

    @Test
    fun `rotating the only key keeps it in use`() = runTest {
        val pool = FalKeyPool(vaultWith(FalKey("solo", "k1")))
        val client = FalQueueClient(HttpClient(MockEngine { respond("{}", HttpStatusCode.OK, jsonHeaders) }), pool)
        client.rotateKey()
        assertEquals("solo", pool.currentKey()?.label, "a single troubled key is still the key to use")
    }
}
