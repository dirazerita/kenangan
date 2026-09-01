package id.kenang.core.data

import id.kenang.core.db.KenangDb

/**
 * Key-value settings backed by the `settings` table.
 * Reserved keys (unused until Phase 05, D-002): "license_state", "trial_exports_used".
 */
class SettingsRepository(private val db: KenangDb) {

    companion object {
        const val KEY_OUTPUT_FOLDER = "output_folder"
        const val KEY_LANGUAGE = "language"
        const val KEY_TELEMETRY_OPT_OUT = "telemetry_opt_out"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
        /** Rights/consent attestation timestamp (MEMORY §7) — set once, first project. */
        const val KEY_CONSENT_ACCEPTED_AT = "consent_accepted_at"
        // Model overrides (Settings → Model AI); blank/absent = follow config.
        const val KEY_MODEL_I2V = "model_i2v"
        const val KEY_MODEL_ANALYSIS = "model_analysis"
        const val KEY_MODEL_TTS = "model_tts"
        const val KEY_DEFAULT_VOICE = "default_voice"
        /** Index of the last narration suggestion used, so the next project differs. */
        const val KEY_LAST_NARRATION_TEMPLATE = "last_narration_template"
        /** Upscale tool: user-chosen results folder (blank = <output_folder>/Upscale). */
        const val KEY_UPSCALE_OUTPUT_FOLDER = "upscale_output_folder"
        // TODO(D-002): "license_state" + "trial_exports_used" reserved for Phase 05.
    }

    fun get(key: String): String? =
        db.kenangQueries.selectSetting(key).executeAsOneOrNull()

    fun set(key: String, value: String) {
        db.kenangQueries.upsertSetting(key, value)
    }

    fun getBool(key: String, default: Boolean = false): Boolean =
        get(key)?.toBooleanStrictOrNull() ?: default

    fun setBool(key: String, value: Boolean) = set(key, value.toString())

    var onboardingDone: Boolean
        get() = getBool(KEY_ONBOARDING_DONE)
        set(v) = setBool(KEY_ONBOARDING_DONE, v)

    var outputFolder: String?
        get() = get(KEY_OUTPUT_FOLDER)
        set(v) { if (v != null) set(KEY_OUTPUT_FOLDER, v) }

    /** Blank is stored to mean "back to default"; reads normalize it to null. */
    private fun getOverride(key: String): String? = get(key)?.takeIf { it.isNotBlank() }

    var modelI2v: String?
        get() = getOverride(KEY_MODEL_I2V)
        set(v) = set(KEY_MODEL_I2V, v ?: "")

    var modelAnalysis: String?
        get() = getOverride(KEY_MODEL_ANALYSIS)
        set(v) = set(KEY_MODEL_ANALYSIS, v ?: "")

    var modelTts: String?
        get() = getOverride(KEY_MODEL_TTS)
        set(v) = set(KEY_MODEL_TTS, v ?: "")

    var defaultVoice: String?
        get() = getOverride(KEY_DEFAULT_VOICE)
        set(v) = set(KEY_DEFAULT_VOICE, v ?: "")

    var upscaleOutputFolder: String?
        get() = getOverride(KEY_UPSCALE_OUTPUT_FOLDER)
        set(v) = set(KEY_UPSCALE_OUTPUT_FOLDER, v ?: "")

    var lastNarrationTemplate: Int
        get() = get(KEY_LAST_NARRATION_TEMPLATE)?.toIntOrNull() ?: -1
        set(v) = set(KEY_LAST_NARRATION_TEMPLATE, v.toString())

    val consentAccepted: Boolean
        get() = get(KEY_CONSENT_ACCEPTED_AT) != null

    fun recordConsent() = set(KEY_CONSENT_ACCEPTED_AT, System.currentTimeMillis().toString())
}
