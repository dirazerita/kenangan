package id.kenang.core.common

/**
 * Single error taxonomy for the whole app (MASTER_PROMPT_02 §Provider layer).
 * Every provider/network failure MUST be mapped into one of these before
 * reaching the UI; the UI renders them via [ErrorTranslator].
 */
sealed class AppError(open val cause: Throwable? = null) {

    /** Provider refused the content (safety filter). */
    data class ContentBlocked(val detail: String? = null) : AppError()

    /** 401 — the API key is wrong or inactive. [keyLabel] identifies which key. */
    data class InvalidKey(val provider: Provider, val keyLabel: String? = null) : AppError()

    /**
     * Balance exhausted. For fal this means ALL configured keys are
     * exhausted or cooling down (AD-14) — single-key exhaustion fails over
     * silently and never surfaces as an error.
     */
    data class ProviderBalance(val provider: Provider) : AppError()

    /** 429 — rate limited. */
    data class RateLimited(val provider: Provider, val retryAfterSeconds: Long? = null) : AppError()

    /** Provider returned a 5xx / malformed response / job failed server-side. */
    data class ProviderFailed(val provider: Provider, val detail: String? = null, override val cause: Throwable? = null) : AppError(cause)

    /** Network timeout or unreachable host. */
    data class Timeout(override val cause: Throwable? = null) : AppError(cause)

    /** No network at all (offline mode). */
    data object Offline : AppError()

    /** Local FFmpeg assembly failed (no API cost involved). */
    data class AssemblyFailed(val detail: String? = null, override val cause: Throwable? = null) : AppError(cause)

    /** Anything unexpected — must still never surface as a raw exception. */
    data class Unknown(val detail: String? = null, override val cause: Throwable? = null) : AppError(cause)
}

enum class Provider { FAL, GEMINI, ELEVENLABS }

/** Lightweight Result/Either for app flows. */
sealed class AppResult<out T> {
    data class Ok<T>(val value: T) : AppResult<T>()
    data class Err(val error: AppError) : AppResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Ok -> Ok(transform(value))
        is Err -> this
    }

    inline fun onOk(block: (T) -> Unit): AppResult<T> {
        if (this is Ok) block(value)
        return this
    }

    inline fun onErr(block: (AppError) -> Unit): AppResult<T> {
        if (this is Err) block(error)
        return this
    }

    fun getOrNull(): T? = (this as? Ok)?.value
    fun errorOrNull(): AppError? = (this as? Err)?.error
}

fun <T> T.ok(): AppResult<T> = AppResult.Ok(this)
fun AppError.err(): AppResult.Err = AppResult.Err(this)
