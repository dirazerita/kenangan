package id.kenang.core.providers.gen

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.Provider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import id.kenang.core.data.AppDirs
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.providers.CostTracker
import id.kenang.core.providers.PriceBook
import id.kenang.core.providers.fal.FalQueueClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.File

/**
 * F5.1: full-narration TTS via the locked MiniMax id-ID voice (config-routed).
 * One call per project → `projects/<id>/audio/narration.mp3`.
 */
class TtsService(
    private val falClient: FalQueueClient,
    private val http: HttpClient,
    private val configRepository: ConfigRepository,
    private val priceBook: PriceBook,
    private val costTracker: CostTracker,
) {
    data class Narration(val file: File, val durationMs: Long)

    /** Synthesizes [text] (≤ config max chars) and returns the local MP3 + duration. */
    suspend fun synthesize(projectId: String, text: String): AppResult<Narration> {
        val tts = configRepository.current().tts
        val trimmed = text.trim().take(tts.maxChars)
        if (trimmed.isBlank()) return AppError.Unknown("empty narration").err()

        val outFile = File(File(AppDirs.projectDir(projectId), "audio").apply { mkdirs() }, "narration.mp3")

        val body = buildJsonObject {
            put("text", trimmed)
            putJsonObject("voice_setting") {
                put("voice_id", tts.voice)
                put("speed", 0.95)
            }
            put("output_format", "url")
            put("language_boost", tts.languageBoost)
        }
        val submitted = when (val s = falClient.submit(tts.slug, body)) {
            is AppResult.Ok -> s.value
            is AppResult.Err -> return s
        }
        val payload = when (val r = falClient.awaitResult(submitted, timeoutMillis = 3 * 60_000)) {
            is AppResult.Ok -> r.value.payload
            is AppResult.Err -> return r
        }
        val audioUrl = runCatching {
            payload["audio"]!!.jsonObject["url"]!!.jsonPrimitive.content
        }.getOrNull() ?: return AppError.ProviderFailed(Provider.FAL, "no audio in TTS result").err()
        val durationMs = payload["duration_ms"]?.jsonPrimitive?.longOrNull ?: 0L

        return runCatching {
            outFile.writeBytes(http.get(audioUrl).readRawBytes())
            val est = priceBook.estimate(tts.slug, trimmed.length.toDouble())?.usd ?: 0.0
            costTracker.record(
                projectId, submitted.requestId, tts.slug, submitted.keyLabel,
                trimmed.length.toDouble(), "per_1k_chars", est,
            )
            Narration(outFile, durationMs)
        }.fold(
            onSuccess = { it.ok() },
            onFailure = { AppError.Unknown("narration save failed", it).err() },
        )
    }
}
