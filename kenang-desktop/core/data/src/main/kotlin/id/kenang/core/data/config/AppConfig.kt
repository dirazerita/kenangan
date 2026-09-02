package id.kenang.core.data.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Schema of app-config.json — IDENTICAL to the future remote config payload
 * (AD-10). Development reads the bundled resource (+ optional %APPDATA%
 * override); Phase 01 switches the source to CONFIG_URL with a one-line change
 * in ConfigRepository.
 *
 * Never hardcode prices or model slugs anywhere else in the app.
 */
@Serializable
data class AppConfig(
    @SerialName("config_version") val configVersion: Int,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("min_version") val minVersion: String,
    @SerialName("latest_version") val latestVersion: String,
    @SerialName("latest_url") val latestUrl: String = "",
    @SerialName("fx_idr") val fxIdr: Double,
    val flags: Map<String, Boolean> = emptyMap(),
    val analysis: AnalysisConfig,
    @SerialName("key_test") val keyTest: KeyTestConfig,
    val tts: TtsConfig,
    @SerialName("voice_clone") val voiceClone: VoiceCloneConfig = VoiceCloneConfig(),
    @SerialName("tts_premium") val ttsPremium: TtsPremiumConfig? = null,
    @SerialName("tier_routing") val tierRouting: TierRouting,
    @SerialName("price_hints") val priceHints: List<PriceHint>,
    val vibes: List<Vibe> = emptyList(),
    @SerialName("bundled_music") val bundledMusic: List<BundledMusic> = emptyList(),
    @SerialName("model_catalog") val modelCatalog: ModelCatalog = ModelCatalog(),
    val limits: Limits,
)

/**
 * User-selectable models per pipeline stage (Settings → Model AI). The first
 * entry of each list is the app default; the user's choice is stored in the
 * settings table and overrides the tier/config routing.
 */
@Serializable
data class ModelCatalog(
    /** I2V models; params merged into the request body like tier i2v_params. */
    val i2v: List<ModelOption> = emptyList(),
    /** Vision model ids for the fal openrouter/router/vision route. */
    val analysis: List<ModelOption> = emptyList(),
    /** TTS slugs (MiniMax family only — same request shape). */
    val tts: List<ModelOption> = emptyList(),
    /** Upscale/restoration models for the standalone photo tool. */
    val upscale: List<ModelOption> = emptyList(),
)

@Serializable
data class ModelOption(
    /** I2V/TTS: fal slug. Analysis: router model id. */
    val id: String,
    @SerialName("label_id") val labelId: String,
    /** Extra request params (e.g. generate_audio=false for Kling). */
    val params: JsonObject? = null,
    /** Honest flag for options not yet blind-tested in Phase 00. */
    val tested: Boolean = true,
    /** Unique selection key when two entries share an id (e.g. Wan flash). */
    val key: String = "",
    /**
     * Request-body shape for the upscale tool: "image_url" (default; body is
     * {image_url, ...params}, result under image.url) or "edit_prompt"
     * (Nano-Banana-style {prompt, image_urls}, result under images[0].url).
     */
    @SerialName("input_mode") val inputMode: String = "image_url",
    /** Indonesian one-liner shown under the option's chip (upscale tool). */
    @SerialName("desc_id") val descId: String = "",
) {
    fun selectionKey(): String = key.ifBlank { id }
}

/**
 * Royalty-free track shipped in app resources (`/music/<file>`), selectable in
 * the wizard's "Musik bawaan" list. [credit] must be shown in About (license
 * attribution, e.g. CC BY).
 */
@Serializable
data class BundledMusic(
    val id: String,
    val file: String,
    @SerialName("label_id") val labelId: String,
    val credit: String = "",
)

@Serializable
data class AnalysisConfig(
    val slug: String,
    val model: String,
    /**
     * Model id for the DIRECT Gemini path (user's own Google key). Kept
     * separate from [model] (the fal-router id) because Google retires model
     * ids for new accounts independently (seen live: 2.5-flash 404s for new
     * users while the fal route still serves it).
     */
    @SerialName("gemini_model") val geminiModel: String = "",
    val note: String = "",
) {
    fun resolvedGeminiModel(): String = geminiModel.ifBlank { model.substringAfter("/") }
}

/** Cheapest possible "Tes koneksi" ping for a fal key (~1 token). */
@Serializable
data class KeyTestConfig(
    @SerialName("fal_slug") val falSlug: String,
    @SerialName("fal_model") val falModel: String,
    @SerialName("fal_max_tokens") val falMaxTokens: Int = 1,
)

@Serializable
data class TtsConfig(
    val slug: String,
    val voice: String,
    @SerialName("language_boost") val languageBoost: String,
    @SerialName("max_chars") val maxChars: Int = 500,
    /** Selectable MiniMax system voices; [voice] stays the locked default. */
    val voices: List<TtsVoice> = emptyList(),
)

/**
 * Voice cloning (owner 2026-09-02): the loved one's voice becomes the
 * narration voice. MiniMax voice-clone — the cloned id plugs straight into
 * the existing Speech-02 HD narration path (best quality, id-ID proven).
 */
@Serializable
data class VoiceCloneConfig(
    val slug: String = "fal-ai/minimax/voice-clone",
    /**
     * TTS model the clone is registered against AND immediately previewed
     * with — the preview "uses" the voice so MiniMax keeps it permanently
     * (unused clones are auto-deleted after 7 days).
     */
    @SerialName("tts_model") val ttsModel: String = "speech-02-hd",
    @SerialName("preview_text") val previewText: String =
        "Halo, ini contoh suara hasil kloning untuk video kenangan keluarga.",
    /** Minimum sample length in seconds (provider requirement). */
    @SerialName("min_seconds") val minSeconds: Int = 10,
)

@Serializable
data class TtsVoice(
    val id: String,
    @SerialName("label_id") val labelId: String,
    val gender: String = "",
)

@Serializable
data class TtsPremiumConfig(
    val provider: String,
    @SerialName("model_id") val modelId: String,
    val voices: List<PremiumVoice> = emptyList(),
    val note: String = "",
)

@Serializable
data class PremiumVoice(val id: String, val name: String, val gender: String = "")

@Serializable
data class TierRouting(
    @SerialName("default_tier") val defaultTier: String,
    val tiers: Map<String, TierConfig>,
    @SerialName("ab_test") val abTest: AbTestConfig? = null,
) {
    /** Resolves a tier, falling back to the default when disabled/unknown. */
    fun resolve(tier: String): TierConfig {
        val requested = tiers[tier]
        if (requested != null && requested.enabled) return requested
        return tiers.getValue(defaultTier)
    }
}

@Serializable
data class TierConfig(
    val enabled: Boolean = true,
    val provisional: Boolean = false,
    val note: String = "",
    val keyframe: String,
    val i2v: String,
    /** Extra params merged into the I2V request body (e.g. generate_audio=false for Kling). */
    @SerialName("i2v_params") val i2vParams: JsonObject? = null,
)

@Serializable
data class AbTestConfig(
    val i2v: String,
    val resolution: String = "480p",
    val enabled: Boolean = false,
)

/** MEMORY §6 PriceHint contract. */
@Serializable
data class PriceHint(
    @SerialName("model_slug") val modelSlug: String,
    val unit: String, // per_second | per_image | per_1k_chars
    val usd: Double,
)

@Serializable
data class Vibe(
    val id: String,
    @SerialName("label_id") val labelId: String,
    @SerialName("desc_id") val descId: String = "",
    /** English setting description injected into keyframe prompts ("" = keep original setting). */
    @SerialName("prompt_en") val promptEn: String = "",
)

/** MEMORY §5 hard limits. */
@Serializable
data class Limits(
    @SerialName("max_photos") val maxPhotos: Int,
    @SerialName("max_scenes") val maxScenes: Int,
    @SerialName("max_total_s") val maxTotalSeconds: Int,
    @SerialName("max_subjects_fusion") val maxSubjectsFusion: Int,
    @SerialName("max_narration_chars") val maxNarrationChars: Int,
)
