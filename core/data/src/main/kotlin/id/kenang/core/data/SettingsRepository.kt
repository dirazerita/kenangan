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
}
