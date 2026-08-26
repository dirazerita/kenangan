package id.kenang.core.providers.fal

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.Provider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Remaining credit on a fal account, as reported by fal's billing API. */
data class FalBalance(val username: String?, val currentBalance: Double?, val currency: String)

/**
 * Reads a fal account's remaining credit:
 * `GET https://api.fal.ai/v1/account/billing?expand=credits` with
 * `Authorization: Key <key>` →
 * `{"username":"…","credits":{"current_balance":24.5,"currency":"USD"}}`.
 *
 * fal documents this as an ADMIN-key endpoint, so a plain generation key may
 * legitimately answer 401/403. That is NOT a broken key — callers must treat
 * a missing balance as "unknown" and never fail a connection test over it.
 */
class FalBilling(
    private val http: HttpClient,
    private val baseUrl: String = "https://api.fal.ai",
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun balance(apiKey: String): AppResult<FalBalance> {
        val response = try {
            http.get("$baseUrl/v1/account/billing?expand=credits") {
                header("Authorization", "Key $apiKey")
            }
        } catch (t: Throwable) {
            return FalQueueClient.mapTransportError(t).err()
        }
        val text = response.bodyAsText()
        if (response.status.value !in 200..299) {
            Napier.i("fal billing -> HTTP ${response.status.value} (balance unavailable for this key)")
            return FalQueueClient.mapHttpError(response.status, text, null).err()
        }
        return runCatching {
            val root = json.parseToJsonElement(text).jsonObject
            val credits = root["credits"]?.jsonObject
            FalBalance(
                username = root["username"]?.jsonPrimitive?.contentOrNullSafe(),
                currentBalance = credits?.get("current_balance")?.jsonPrimitive?.doubleOrNull,
                currency = credits?.get("currency")?.jsonPrimitive?.contentOrNullSafe() ?: "USD",
            )
        }.fold(
            onSuccess = { it.ok() },
            onFailure = { AppError.ProviderFailed(Provider.FAL, "malformed billing response", it).err() },
        )
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    runCatching { content }.getOrNull()
