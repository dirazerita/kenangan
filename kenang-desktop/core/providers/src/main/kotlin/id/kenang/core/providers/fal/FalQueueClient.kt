package id.kenang.core.providers.fal

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.Provider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import io.github.aakira.napier.Napier
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.random.Random

/**
 * Direct fal queue client (AD-07) with multi-key failover (AD-14).
 *
 * Submit: first non-exhausted key from the ordered pool. On fal's
 * balance-exhausted signature (exact strings from Phase 00 T4 results):
 * mark the key exhausted (10-min cooldown), emit [KeySwitched], retry with
 * the next key. All keys exhausted → AppError.ProviderBalance.
 *
 * Polling ALWAYS uses the key stored on the job — in-flight jobs are never
 * migrated between keys.
 */
class FalQueueClient(
    private val http: HttpClient,
    private val keyPool: FalKeyPool,
    private val baseUrl: String = "https://queue.fal.run",
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Same client against a different key pool (used by KeyTester for single-key probes). */
    fun withPool(pool: FalKeyPool): FalQueueClient = FalQueueClient(http, pool, baseUrl)

    suspend fun submit(modelSlug: String, body: JsonObject): AppResult<SubmittedFalJob> {
        var previousLabel: String? = null
        var lastKeyError: AppError? = null
        while (true) {
            val key = keyPool.currentKey()
                ?: return (lastKeyError ?: AppError.ProviderBalance(Provider.FAL)).err()

            if (previousLabel != null && previousLabel != key.label) {
                keyPool.emitSwitch(previousLabel, key.label)
                Napier.i("fal submit failover: '$previousLabel' -> '${key.label}'")
            }

            val response = try {
                http.post("$baseUrl/$modelSlug") {
                    header("Authorization", "Key ${key.key}")
                    contentType(ContentType.Application.Json)
                    setBody(body.toString())
                }
            } catch (t: Throwable) {
                Napier.w("fal submit $modelSlug via '${key.label}' transport error: ${t::class.simpleName}: ${t.message}")
                return mapTransportError(t).err()
            }

            val text = response.bodyAsText()
            when {
                response.status.isSuccess() -> {
                    val parsed = runCatching { json.decodeFromString<FalSubmitResponse>(text) }.getOrNull()
                        ?: return AppError.ProviderFailed(Provider.FAL, "Malformed submit response").err()
                    Napier.i("fal submit $modelSlug via '${key.label}' -> ${parsed.requestId}")
                    return SubmittedFalJob(
                        parsed.requestId, modelSlug, key.label,
                        statusUrl = parsed.statusUrl, responseUrl = parsed.responseUrl,
                    ).ok()
                }
                isBalanceExhausted(response.status, text) -> {
                    Napier.w("fal key '${key.label}' balance exhausted — cooling down 10 min")
                    lastKeyError = AppError.ProviderBalance(Provider.FAL)
                    keyPool.markExhausted(key.label)
                    previousLabel = key.label
                    // loop: try the next available key
                }
                isKeyRejected(response.status) -> {
                    // Owner requirement (dogfood 2026-08-27): an INVALID/disabled
                    // key must fail over exactly like an exhausted one — mark it,
                    // toast the switch, and keep going with the next key.
                    Napier.w("fal key '${key.label}' rejected (HTTP ${response.status.value}) — failing over")
                    lastKeyError = AppError.InvalidKey(Provider.FAL, key.label)
                    keyPool.markExhausted(key.label)
                    previousLabel = key.label
                    // loop: try the next available key
                }
                else -> {
                    Napier.w(
                        "fal submit $modelSlug via '${key.label}' -> HTTP ${response.status.value}: ${text.take(300)}",
                    )
                    return mapHttpError(response.status, text, key.label).err()
                }
            }
        }
    }

    /**
     * Queue request base: sub-paths after {owner}/{alias} are part of the
     * SUBMIT URL only — status/result live at {owner}/{alias}/requests/{id}
     * (same rule as fal_client; "workflows"/"comfy" namespaces keep 3 segments).
     */
    internal fun requestBase(modelSlug: String, requestId: String): String {
        val parts = modelSlug.split("/")
        val keep = if (parts.firstOrNull() in setOf("workflows", "comfy")) 3 else 2
        val appBase = parts.take(keep).joinToString("/")
        return "$baseUrl/$appBase/requests/$requestId"
    }

    /** Polls status using the job's own key (never another). */
    suspend fun status(job: SubmittedFalJob): AppResult<FalStatusResponse> {
        val key = keyPool.keyByLabel(job.keyLabel)
            ?: return AppError.InvalidKey(Provider.FAL, job.keyLabel).err()
        val url = job.statusUrl ?: (requestBase(job.modelSlug, job.requestId) + "/status")
        return request(key.key, url) { text ->
            json.decodeFromString<FalStatusResponse>(text)
        }
    }

    /** Fetches the terminal result payload using the job's own key. */
    suspend fun result(job: SubmittedFalJob): AppResult<FalJobResult> {
        val key = keyPool.keyByLabel(job.keyLabel)
            ?: return AppError.InvalidKey(Provider.FAL, job.keyLabel).err()
        val url = job.responseUrl ?: requestBase(job.modelSlug, job.requestId)
        return request(key.key, url) { text ->
            FalJobResult(json.decodeFromString(text))
        }
    }

    /**
     * Polls until COMPLETED with exponential backoff (base 2s, cap 15s, jitter),
     * then fetches the result. [timeoutMillis] bounds the whole wait.
     */
    suspend fun awaitResult(job: SubmittedFalJob, timeoutMillis: Long = 5 * 60 * 1000): AppResult<FalJobResult> {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var delayMs = 2000L
        while (System.currentTimeMillis() < deadline) {
            when (val st = status(job)) {
                is AppResult.Err -> return st
                is AppResult.Ok -> when (st.value.status) {
                    "COMPLETED" -> return result(job)
                    "IN_QUEUE", "IN_PROGRESS" -> {
                        delay(delayMs + Random.nextLong(0, 500))
                        delayMs = (delayMs * 2).coerceAtMost(15_000L)
                    }
                    else -> return AppError.ProviderFailed(Provider.FAL, "Unexpected status ${st.value.status}").err()
                }
            }
        }
        return AppError.Timeout().err()
    }

    private suspend fun <T> request(apiKey: String, url: String, parse: (String) -> T): AppResult<T> {
        val response = try {
            http.get(url) { header("Authorization", "Key $apiKey") }
        } catch (t: Throwable) {
            return mapTransportError(t).err()
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            Napier.w("fal request $url -> HTTP ${response.status.value}: ${text.take(200)}")
            return mapHttpError(response.status, text, null).err()
        }
        return runCatching { parse(text) }.fold(
            onSuccess = { it.ok() },
            onFailure = { AppError.ProviderFailed(Provider.FAL, "Malformed response", it).err() },
        )
    }

    companion object {
        /** Exact signatures captured in Phase 00 T4 (poc/results.csv). */
        private val BALANCE_SIGNATURES = listOf(
            "User is locked. Reason: TOP_UP",
            "Exhausted balance",
        )

        fun isBalanceExhausted(status: HttpStatusCode, body: String): Boolean =
            BALANCE_SIGNATURES.any { body.contains(it, ignoreCase = true) } ||
                status == HttpStatusCode.PaymentRequired

        /** 401/403 = wrong or deactivated key — also a failover trigger. */
        fun isKeyRejected(status: HttpStatusCode): Boolean =
            status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden

        fun mapHttpError(status: HttpStatusCode, body: String, keyLabel: String?): AppError = when {
            status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden ->
                AppError.InvalidKey(Provider.FAL, keyLabel)
            status == HttpStatusCode.PaymentRequired -> AppError.ProviderBalance(Provider.FAL)
            status == HttpStatusCode.TooManyRequests -> AppError.RateLimited(Provider.FAL)
            body.contains("content", ignoreCase = true) &&
                (body.contains("policy", ignoreCase = true) || body.contains("moderation", ignoreCase = true) || body.contains("safety", ignoreCase = true)) ->
                AppError.ContentBlocked(body.take(300))
            else -> AppError.ProviderFailed(Provider.FAL, "HTTP ${status.value}: ${body.take(300)}")
        }

        fun mapTransportError(t: Throwable): AppError = when (t) {
            is SocketTimeoutException, is ConnectTimeoutException,
            is io.ktor.client.plugins.HttpRequestTimeoutException -> AppError.Timeout(t)
            is java.net.UnknownHostException, is java.net.ConnectException -> AppError.Offline
            else -> AppError.Unknown(t.message, t)
        }
    }
}

private fun HttpStatusCode.isSuccess() = value in 200..299
