package id.kenang.core.providers.voice

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.Provider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import id.kenang.core.data.SettingsRepository
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.providers.CostTracker
import id.kenang.core.providers.PriceBook
import id.kenang.core.providers.fal.FalQueueClient
import id.kenang.core.providers.fal.FalStorage
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * One cloned narration voice. [voiceId] is MiniMax's custom_voice_id — it
 * plugs straight into the existing Speech-02 HD `voice_setting.voice_id`
 * path, so cloned voices work everywhere a preset voice does (previews,
 * final narration). [keyLabel] records which fal key created the clone:
 * the voice lives in THAT provider account, so TTS served by another key
 * cannot find it (KI note in Settings).
 */
@Serializable
data class ClonedVoice(
    val label: String,
    val voiceId: String,
    val keyLabel: String? = null,
    val createdAt: Long = 0L,
)

/**
 * "Kloning Suara" (owner 2026-09-02): clone a loved one's voice from an
 * audio sample (≥ 10s) via MiniMax voice-clone — the best cloning stack fal
 * offers that feeds the app's locked HD narration model. The clone call
 * includes a short preview text so MiniMax registers the voice as "used"
 * immediately (unused clones are auto-deleted after 7 days).
 */
class VoiceCloneService(
    private val falClient: FalQueueClient,
    private val storage: FalStorage,
    private val configRepository: ConfigRepository,
    private val priceBook: PriceBook,
    private val costTracker: CostTracker,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val KEY_CLONED_VOICES = "cloned_voices"
        /** Pseudo project id for gen_cost rows (clones belong to no project). */
        private const val COST_PROJECT = "voice-clone"
        private val AUDIO_MIME = mapOf(
            "wav" to "audio/wav", "mp3" to "audio/mpeg", "m4a" to "audio/mp4",
            "aac" to "audio/aac", "ogg" to "audio/ogg", "flac" to "audio/flac",
        )
        val AUDIO_EXTENSIONS = AUDIO_MIME.keys.toList()
    }

    fun cloned(): List<ClonedVoice> =
        settings.get(KEY_CLONED_VOICES)
            ?.let { runCatching { json.decodeFromString<List<ClonedVoice>>(it) }.getOrNull() }
            ?: emptyList()

    fun estimateUsd(): Double =
        priceBook.estimate(configRepository.current().voiceClone.slug, 1.0)?.usd ?: 0.0

    /** Removes a clone from the app's list (the provider copy expires on its own). */
    fun remove(voiceId: String) {
        save(cloned().filterNot { it.voiceId == voiceId })
    }

    suspend fun clone(sample: File, label: String): AppResult<ClonedVoice> {
        val cfg = configRepository.current().voiceClone
        val cleanLabel = label.trim().ifBlank { sample.nameWithoutExtension }
        val mime = AUDIO_MIME[sample.extension.lowercase()]
            ?: return AppError.Unknown("Format audio tidak didukung: .${sample.extension}").err()

        val audioUrl = when (val up = storage.uploadBytes(sample.readBytes(), sample.name, mime)) {
            is AppResult.Ok -> up.value
            is AppResult.Err -> return up
        }

        val body = buildJsonObject {
            put("audio_url", audioUrl)
            put("noise_reduction", true)
            put("need_volume_normalization", true)
            // Preview TTS with the fresh clone — hearing it AND marking the
            // voice as used so MiniMax keeps it past the 7-day window.
            put("text", cfg.previewText)
            put("model", cfg.ttsModel)
        }
        val submitted = when (val s = falClient.submit(cfg.slug, body)) {
            is AppResult.Ok -> s.value
            is AppResult.Err -> return s
        }
        val payload = when (val r = falClient.awaitResult(submitted, timeoutMillis = 4 * 60_000)) {
            is AppResult.Ok -> r.value.payload
            is AppResult.Err -> return r
        }
        val voiceId = runCatching { payload["custom_voice_id"]!!.jsonPrimitive.content }.getOrNull()
            ?: return AppError.ProviderFailed(Provider.FAL, "no custom_voice_id in clone result").err()

        val est = estimateUsd()
        costTracker.record(
            COST_PROJECT, submitted.requestId, cfg.slug, submitted.keyLabel, 1.0, "per_call", est,
        )
        val voice = ClonedVoice(cleanLabel, voiceId, submitted.keyLabel, System.currentTimeMillis())
        save(cloned().filterNot { it.voiceId == voiceId } + voice)
        Napier.i("voice cloned: '$cleanLabel' via key=${submitted.keyLabel}") // id never logged
        return voice.ok()
    }

    private fun save(list: List<ClonedVoice>) {
        settings.set(KEY_CLONED_VOICES, json.encodeToString(list))
    }
}
