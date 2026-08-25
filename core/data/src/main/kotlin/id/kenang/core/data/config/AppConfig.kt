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
    @SerialName("tts_premium") val ttsPremium: TtsPremiumConfig? = null,
    @SerialName("tier_routing") val tierRouting: TierRouting,
    @SerialName("price_hints") val priceHints: List<PriceHint>,
    val vibes: List<Vibe> = emptyList(),
    val limits: Limits,
)

@Serializable
data class AnalysisConfig(
    val slug: String,
    val model: String,
    val note: String = "",
)

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
