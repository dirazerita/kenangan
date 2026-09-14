package id.kenang.core.providers.fal

import id.kenang.core.providers.vault.FalKey
import id.kenang.core.providers.vault.KeyVault
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** UI status chip for a fal key (Settings/API Key Manager). */
enum class FalKeyStatus { AKTIF, CADANGAN, SALDO_HABIS, PERLU_TOPUP, DITOLAK, JEDA }

/**
 * Why a key is on cooldown (owner 2026-09-14: five keys read "saldo habis"
 * while each still showed 5 USD). The reason decides BOTH how long the key
 * rests and what the user is told — a slow render is not a spent account.
 */
enum class CooldownReason {
    /** fal says the balance is spent. */
    BALANCE,

    /** fal says "User is locked. Reason: TOP_UP" — credit shows, spending is blocked. */
    TOPUP_LOCK,

    /** 401/403 for a reason other than money: wrong, disabled or unauthorised key. */
    REJECTED,

    /** A timeout, a 5xx, a content rejection — the key is fine, the call was not. */
    TROUBLE,
}

/** Emitted when a submit fails over to the next key (AD-14) — UI shows a toast. */
data class KeySwitched(val fromLabel: String, val toLabel: String)

/**
 * Ordered fal key pool with per-reason cooldowns (AD-14).
 *
 * A key the provider refused for money or identity rests for
 * [cooldownMillis] (10 min). A key that merely carried a troubled call rests
 * for [troubleCooldownMillis] (90 s) so the next attempt goes elsewhere
 * without writing the key off — before this split, a single timeout put a
 * funded key out of action for ten minutes under the label "saldo habis",
 * and a handful of them made every key look spent while the owner's accounts
 * still held credit.
 */
class FalKeyPool(
    private val vault: KeyVault,
    private val cooldownMillis: Long = 10 * 60 * 1000,
    private val troubleCooldownMillis: Long = 90 * 1000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private data class Cooldown(val until: Long, val reason: CooldownReason)

    private val cooldowns = mutableMapOf<String, Cooldown>()

    private val _keySwitched = MutableSharedFlow<KeySwitched>(extraBufferCapacity = 8)
    val keySwitched: SharedFlow<KeySwitched> = _keySwitched

    @Synchronized
    fun allKeys(): List<FalKey> = vault.falKeys()

    /** Keys currently usable for a NEW submit, in priority order. */
    @Synchronized
    fun availableKeys(): List<FalKey> {
        val now = clock()
        return vault.falKeys().filter { (cooldowns[it.label]?.until ?: 0L) <= now }
    }

    /** The key a fresh submit would use right now, or null if all are resting. */
    fun currentKey(): FalKey? = availableKeys().firstOrNull()

    /** Key for polling an in-flight job — jobs NEVER migrate keys (AD-14). */
    @Synchronized
    fun keyByLabel(label: String): FalKey? = vault.falKeys().firstOrNull { it.label == label }

    /**
     * Rests [label] for the duration its [reason] deserves. A longer rest
     * already in place is never shortened, so one timeout cannot mask a key
     * the provider has actually refused.
     */
    @Synchronized
    fun markExhausted(label: String, reason: CooldownReason = CooldownReason.BALANCE) {
        val until = clock() + if (reason == CooldownReason.TROUBLE) troubleCooldownMillis else cooldownMillis
        val existing = cooldowns[label]
        if (existing == null || until > existing.until) {
            cooldowns[label] = Cooldown(until, reason)
        }
    }

    /** Seconds until [label] may be used again, or 0 when it is ready now. */
    @Synchronized
    fun restSeconds(label: String): Long {
        val until = cooldowns[label]?.until ?: return 0
        return ((until - clock()).coerceAtLeast(0) + 999) / 1000
    }

    /** Why [label] is resting, or null when it is ready. */
    @Synchronized
    fun reason(label: String): CooldownReason? =
        cooldowns[label]?.takeIf { it.until > clock() }?.reason

    /** Reasons every resting key is resting for — empty when at least one key is ready. */
    @Synchronized
    fun restingReasons(): List<CooldownReason> {
        val now = clock()
        val keys = vault.falKeys()
        if (keys.isEmpty()) return emptyList()
        val resting = keys.mapNotNull { cooldowns[it.label]?.takeIf { c -> c.until > now }?.reason }
        return if (resting.size == keys.size) resting else emptyList()
    }

    suspend fun emitSwitch(from: String, to: String) {
        _keySwitched.emit(KeySwitched(from, to))
    }

    /** Status chips for the Settings UI. */
    @Synchronized
    fun statuses(): Map<String, FalKeyStatus> {
        val now = clock()
        val keys = vault.falKeys()
        val firstAvailable = keys.firstOrNull { (cooldowns[it.label]?.until ?: 0L) <= now }?.label
        return keys.associate { k ->
            val resting = cooldowns[k.label]?.takeIf { it.until > now }
            val status = when {
                resting != null -> when (resting.reason) {
                    CooldownReason.BALANCE -> FalKeyStatus.SALDO_HABIS
                    CooldownReason.TOPUP_LOCK -> FalKeyStatus.PERLU_TOPUP
                    CooldownReason.REJECTED -> FalKeyStatus.DITOLAK
                    CooldownReason.TROUBLE -> FalKeyStatus.JEDA
                }
                k.label == firstAvailable -> FalKeyStatus.AKTIF
                else -> FalKeyStatus.CADANGAN
            }
            k.label to status
        }
    }
}
