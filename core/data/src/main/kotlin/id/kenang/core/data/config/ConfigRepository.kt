package id.kenang.core.data.config

import id.kenang.core.data.AppDirs
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Loads AppConfig with this precedence (AD-10):
 *   1. %APPDATA%/Kenang/config/app-config.json — user-editable override
 *   2. bundled resource /app-config.json — always present, ships with the app
 *
 * When Phase 01 ships the remote config endpoint, add a CONFIG_URL fetch as
 * precedence 0 here — the schema is identical, so it is a one-line change.
 */
class ConfigRepository(
    private val userConfigFile: File = AppDirs.userConfigFile,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _config = MutableStateFlow(loadBlocking())
    val config: StateFlow<AppConfig> = _config

    fun current(): AppConfig = _config.value

    /** Re-reads the override file (e.g. after the user edits it). */
    fun reload() {
        _config.value = loadBlocking()
    }

    private fun loadBlocking(): AppConfig {
        userConfigFile.takeIf { it.isFile }?.let { file ->
            runCatching { json.decodeFromString<AppConfig>(file.readText()) }
                .onSuccess {
                    Napier.i("Config loaded from user override: ${file.absolutePath}")
                    return it
                }
                .onFailure { e ->
                    Napier.w("User config override invalid, falling back to bundled: ${e.message}")
                }
        }
        return loadBundled()
    }

    private fun loadBundled(): AppConfig {
        val text = requireNotNull(javaClass.getResourceAsStream("/app-config.json")) {
            "Bundled app-config.json missing from resources — broken build"
        }.bufferedReader().readText()
        return json.decodeFromString(text)
    }
}
