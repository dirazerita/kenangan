package id.kenang.core.providers.vault

import id.kenang.core.common.maskKey
import io.github.aakira.napier.Napier
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One fal API key. Multi-key is an ORDERED LIST (AD-14): order = failover
 * priority. Labels are user-visible ("Utama", client names, ...).
 */
@Serializable
data class FalKey(val label: String, val key: String) {
    val masked: String get() = maskKey(key, "fal")
}

/**
 * BYOK key vault (AD-11, AD-14).
 *  - fal: ordered list [{label, key}]
 *  - Google Gemini: single optional key
 *  - ElevenLabs: single optional key
 *
 * Backed by an AES-GCM encrypted file in the app's private storage, with a
 * device-derived key. Keys are never logged (only masked forms).
 */
class KeyVault(private val store: SecretStore) {

    private val json = Json { ignoreUnknownKeys = true }

    fun falKeys(): List<FalKey> =
        store.get(NAME_FAL)?.let { runCatching { json.decodeFromString<List<FalKey>>(it) }.getOrNull() }
            ?: emptyList()

    fun saveFalKeys(keys: List<FalKey>) {
        val cleaned = keys.map { FalKey(it.label.trim(), it.key.trim()) }
            .filter { it.key.isNotBlank() }
        store.put(NAME_FAL, json.encodeToString(cleaned))
        Napier.i("fal keys saved: ${cleaned.joinToString { "${it.label}(${it.masked})" }}")
    }

    fun geminiKey(): String? = store.get(NAME_GEMINI)?.takeIf { it.isNotBlank() }
    fun saveGeminiKey(key: String) {
        if (key.isBlank()) store.delete(NAME_GEMINI) else store.put(NAME_GEMINI, key.trim())
    }

    fun elevenLabsKey(): String? = store.get(NAME_ELEVENLABS)?.takeIf { it.isNotBlank() }
    fun saveElevenLabsKey(key: String) {
        if (key.isBlank()) store.delete(NAME_ELEVENLABS) else store.put(NAME_ELEVENLABS, key.trim())
    }

    fun hasFalKey(): Boolean = falKeys().isNotEmpty()

    companion object {
        private const val NAME_FAL = "fal_keys"
        private const val NAME_GEMINI = "gemini_key"
        private const val NAME_ELEVENLABS = "elevenlabs_key"

        /** Encrypted-file vault in app-private storage, keyed to this device. */
        fun createDefault(dir: java.io.File, deviceSecret: String): KeyVault {
            val store = AesGcmFileStore(dir, deviceSecret)
            if (!store.selfTest()) {
                Napier.w("KeyVault: AES-GCM self-test failed — keys may not persist")
            }
            return KeyVault(store)
        }
    }
}
