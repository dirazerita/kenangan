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
import kotlin.test.assertTrue

/**
 * Regression for the owner's 2026-09-14 report: five keys showed "saldo
 * habis" while each still read 5.00 USD, and the run then failed as if the
 * money had run out.
 *
 * The cause was one cooldown for every kind of trouble. A timeout, a 5xx or
 * a content rejection put a funded key out of action for ten minutes under
 * the balance label, and once every key had collected one, the next submit
 * reported a spent balance. These tests pin each refusal to its own reason,
 * its own rest and its own words.
 */
class KeyCooldownReasonTest {

    private fun vaultWith(vararg keys: FalKey): KeyVault =
        KeyVault(InMemoryStore()).apply { saveFalKeys(keys.toList()) }

    private val submitBody = buildJsonObject { put("prompt", "test") }
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(pool: FalKeyPool, engine: MockEngine) =
        FalQueueClient(HttpClient(engine), pool, baseUrl = "https://queue.fal.run")

    @Test
    fun `a troubled call parks the key briefly, not as spent`() = runTest {
        var now = 0L
        val pool = FalKeyPool(
            vaultWith(FalKey("A", "k1"), FalKey("B", "k2")),
            cooldownMillis = 600_000, troubleCooldownMillis = 90_000, clock = { now },
        )

        client(pool, MockEngine { respond("{}", HttpStatusCode.OK, jsonHeaders) }).rotateKey()

        assertEquals(FalKeyStatus.JEDA, pool.statuses()["A"], "a timeout must not read as 'saldo habis'")
        assertEquals(CooldownReason.TROUBLE, pool.reason("A"))
        assertTrue(pool.restSeconds("A") in 1..90)

        // Back in service in a minute and a half, not ten minutes.
        now = 90_001
        assertEquals("A", pool.currentKey()?.label)
        assertEquals(FalKeyStatus.AKTIF, pool.statuses()["A"])
    }

    @Test
    fun `a spent balance and a top-up lock are told apart`() = runTest {
        val spent = FalKeyPool(vaultWith(FalKey("A", "k1")))
        client(spent, MockEngine { respond("""{"detail":"Exhausted balance"}""", HttpStatusCode.Forbidden, jsonHeaders) })
            .submit("fal-ai/x", submitBody)
        assertEquals(FalKeyStatus.SALDO_HABIS, spent.statuses()["A"])

        val locked = FalKeyPool(vaultWith(FalKey("A", "k1")))
        client(locked, MockEngine { respond("""{"detail":"User is locked. Reason: TOP_UP."}""", HttpStatusCode.Forbidden, jsonHeaders) })
            .submit("fal-ai/x", submitBody)
        assertEquals(
            FalKeyStatus.PERLU_TOPUP, locked.statuses()["A"],
            "an account that still shows credit must not be labelled empty",
        )
    }

    @Test
    fun `a wrong key reads as rejected, not as out of money`() = runTest {
        val pool = FalKeyPool(vaultWith(FalKey("A", "bad")))
        client(pool, MockEngine { respond("""{"detail":"Unauthorized"}""", HttpStatusCode.Unauthorized, jsonHeaders) })
            .submit("fal-ai/x", submitBody)
        assertEquals(FalKeyStatus.DITOLAK, pool.statuses()["A"])
    }

    @Test
    fun `when every key is merely resting the error says so, not 'balance'`() = runTest {
        var now = 0L
        val pool = FalKeyPool(
            vaultWith(FalKey("A", "k1"), FalKey("B", "k2")),
            troubleCooldownMillis = 90_000, clock = { now },
        )
        val c = client(pool, MockEngine { respond("{}", HttpStatusCode.OK, jsonHeaders) })
        c.rotateKey() // parks A
        c.rotateKey() // parks B
        assertTrue(pool.availableKeys().isEmpty())

        val result = c.submit("fal-ai/x", submitBody)
        val error = assertIs<AppResult.Err>(result).error
        val failed = assertIs<AppError.ProviderFailed>(error)
        assertTrue(
            failed.detail?.contains("jeda") == true,
            "a provider hiccup must not be reported as a spent balance: ${failed.detail}",
        )
    }

    @Test
    fun `a real refusal outlives a brief pause on the same key`() = runTest {
        var now = 0L
        val pool = FalKeyPool(
            vaultWith(FalKey("A", "k1")),
            cooldownMillis = 600_000, troubleCooldownMillis = 90_000, clock = { now },
        )
        pool.markExhausted("A", CooldownReason.BALANCE)
        pool.markExhausted("A", CooldownReason.TROUBLE)

        assertEquals(
            FalKeyStatus.SALDO_HABIS, pool.statuses()["A"],
            "a short pause must not overwrite a refusal the provider actually made",
        )
        now = 90_001
        assertTrue(pool.availableKeys().isEmpty(), "the 10-minute rest was shortened by a pause")
    }
}
