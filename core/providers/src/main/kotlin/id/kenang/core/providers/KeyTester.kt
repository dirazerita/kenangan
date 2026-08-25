package id.kenang.core.providers

import id.kenang.core.common.AppResult
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.providers.fal.FalKeyPool
import id.kenang.core.providers.fal.FalQueueClient
import id.kenang.core.providers.fal.SubmittedFalJob
import id.kenang.core.providers.optional.ElevenLabsClient
import id.kenang.core.providers.optional.GeminiClient
import id.kenang.core.providers.vault.FalKey
import id.kenang.core.providers.vault.KeyVault
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * "Tes koneksi" per provider (MASTER_PROMPT_02 §API Key Manager):
 *  - fal: cheapest 1-token LLM ping (config key_test; UI shows "biaya tes < $0.001")
 *  - Gemini: models.list (free)
 *  - ElevenLabs: GET /voices (free)
 */
class KeyTester(
    private val configRepository: ConfigRepository,
    private val vault: KeyVault,
    private val falClient: FalQueueClient,
    private val geminiClient: GeminiClient,
    private val elevenLabsClient: ElevenLabsClient,
) {
    /**
     * Tests ONE specific fal key by submitting the 1-token ping through a
     * single-key pool, so failover cannot silently mask a dead key.
     */
    suspend fun testFalKey(key: FalKey): AppResult<Unit> {
        val cfg = configRepository.current().keyTest
        val singleKeyVault = KeyVault(InMemoryStore())
        singleKeyVault.saveFalKeys(listOf(key))
        val pool = FalKeyPool(singleKeyVault)
        val client = falClient.withPool(pool)
        val body = buildJsonObject {
            put("model", cfg.falModel)
            put("prompt", "ping")
            put("max_tokens", cfg.falMaxTokens)
        }
        return when (val submitted = client.submit(cfg.falSlug, body)) {
            is AppResult.Err -> submitted
            is AppResult.Ok -> client.awaitResult(
                SubmittedFalJob(submitted.value.requestId, cfg.falSlug, key.label),
                timeoutMillis = 60_000,
            ).map { }
        }
    }

    suspend fun testGemini(): AppResult<Unit>? =
        vault.geminiKey()?.let { geminiClient.testKey(it) }

    suspend fun testElevenLabs(): AppResult<Unit>? =
        vault.elevenLabsKey()?.let { elevenLabsClient.testKey(it) }
}

/** Tiny in-memory store used to build throwaway single-key pools for testing a key. */
class InMemoryStore : id.kenang.core.providers.vault.SecretStore {
    private val map = mutableMapOf<String, String>()
    override fun put(name: String, secret: String) { map[name] = secret }
    override fun get(name: String): String? = map[name]
    override fun delete(name: String) { map.remove(name) }
    override fun selfTest(): Boolean = true
}
