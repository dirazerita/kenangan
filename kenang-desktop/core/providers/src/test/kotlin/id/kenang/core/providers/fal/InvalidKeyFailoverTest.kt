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
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Owner requirement (dogfood 2026-08-27): an invalid/deactivated key must not
 * kill the run — submits fail over to the next key exactly like on
 * balance-exhausted (AD-14 extension).
 */
class InvalidKeyFailoverTest {

    private fun pool(vararg keys: Pair<String, String>): FalKeyPool {
        val vault = KeyVault(InMemoryStore())
        vault.saveFalKeys(keys.map { FalKey(it.first, it.second) })
        return FalKeyPool(vault)
    }

    private val submitOk =
        """{"request_id":"req-1","status_url":"https://q/x/requests/req-1/status","response_url":"https://q/x/requests/req-1"}"""

    @Test
    fun `401 on the first key fails over to the second`() = runBlocking {
        val usedKeys = mutableListOf<String>()
        val engine = MockEngine { request ->
            val auth = request.headers[HttpHeaders.Authorization].orEmpty()
            usedKeys += auth
            if (auth.contains("dead-key")) {
                respond("""{"detail":"Unauthorized"}""", HttpStatusCode.Unauthorized)
            } else {
                respond(submitOk, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }
        val p = pool("carlos" to "dead-key", "Utama" to "live-key")
        val client = FalQueueClient(HttpClient(engine), p)

        val result = client.submit("fal-ai/test/model", kotlinx.serialization.json.buildJsonObject {})

        assertTrue(result is AppResult.Ok, "expected failover success, got $result")
        assertEquals("Utama", result.value.keyLabel)
        assertEquals(2, usedKeys.size)
        // The dead key is cooling down — the next submit must not touch it.
        assertEquals("Utama", p.currentKey()?.label)
    }

    @Test
    fun `rotateKey moves the next submit onto the next key`() = runBlocking {
        val engine = MockEngine {
            respond(submitOk, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val p = pool("first" to "k1", "second" to "k2")
        val client = FalQueueClient(HttpClient(engine), p)

        client.rotateKey()

        assertEquals("second", p.currentKey()?.label)
        val result = client.submit("fal-ai/test/model", kotlinx.serialization.json.buildJsonObject {})
        assertTrue(result is AppResult.Ok)
        assertEquals("second", result.value.keyLabel)
    }

    @Test
    fun `all keys invalid returns InvalidKey, not ProviderBalance`() = runBlocking {
        val engine = MockEngine {
            respond("""{"detail":"Forbidden"}""", HttpStatusCode.Forbidden)
        }
        val p = pool("a" to "k1", "b" to "k2")
        val client = FalQueueClient(HttpClient(engine), p)

        val result = client.submit("fal-ai/test/model", kotlinx.serialization.json.buildJsonObject {})

        assertTrue(result is AppResult.Err)
        assertTrue(result.error is AppError.InvalidKey, "got ${result.error}")
    }
}
