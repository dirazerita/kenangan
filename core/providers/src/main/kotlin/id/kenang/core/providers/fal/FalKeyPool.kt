package id.kenang.core.providers.fal

import id.kenang.core.providers.vault.FalKey
import id.kenang.core.providers.vault.KeyVault
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** UI status chip for a fal key (Settings/API Key Manager). */
enum class FalKeyStatus { AKTIF, CADANGAN, SALDO_HABIS }

/** Emitted when a submit fails over to the next key (AD-14) — UI shows a toast. */
data class KeySwitched(val fromLabel: String, val toLabel: String)

/**
 * Ordered fal key pool with exhaustion cooldowns (AD-14).
 * A key marked exhausted is skipped for [cooldownMillis] (10 min) before it
 * may be tried again.
 */
class FalKeyPool(
    private val vault: KeyVault,
    private val cooldownMillis: Long = 10 * 60 * 1000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val exhaustedUntil = mutableMapOf<String, Long>()

    private val _keySwitched = MutableSharedFlow<KeySwitched>(extraBufferCapacity = 8)
    val keySwitched: SharedFlow<KeySwitched> = _keySwitched

    @Synchronized
    fun allKeys(): List<FalKey> = vault.falKeys()

    /** Keys currently usable for a NEW submit, in priority order. */
    @Synchronized
    fun availableKeys(): List<FalKey> {
        val now = clock()
        return vault.falKeys().filter { (exhaustedUntil[it.label] ?: 0L) <= now }
    }

    /** The key a fresh submit would use right now, or null if all are exhausted/cooling. */
    fun currentKey(): FalKey? = availableKeys().firstOrNull()

    /** Key for polling an in-flight job — jobs NEVER migrate keys (AD-14). */
    @Synchronized
    fun keyByLabel(label: String): FalKey? = vault.falKeys().firstOrNull { it.label == label }

    @Synchronized
    fun markExhausted(label: String) {
        exhaustedUntil[label] = clock() + cooldownMillis
    }

    suspend fun emitSwitch(from: String, to: String) {
        _keySwitched.emit(KeySwitched(from, to))
    }

    /** Status chips for the Settings UI. */
    @Synchronized
    fun statuses(): Map<String, FalKeyStatus> {
        val now = clock()
        val keys = vault.falKeys()
        val firstAvailable = keys.firstOrNull { (exhaustedUntil[it.label] ?: 0L) <= now }?.label
        return keys.associate { k ->
            val status = when {
                (exhaustedUntil[k.label] ?: 0L) > now -> FalKeyStatus.SALDO_HABIS
                k.label == firstAvailable -> FalKeyStatus.AKTIF
                else -> FalKeyStatus.CADANGAN
            }
            k.label to status
        }
    }
}
