package id.kenang.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import id.kenang.core.common.AppResult
import id.kenang.core.common.ErrorTranslator
import id.kenang.core.providers.KeyTester
import id.kenang.core.providers.fal.FalKeyPool
import id.kenang.core.providers.fal.FalKeyStatus
import id.kenang.core.providers.vault.FalKey
import id.kenang.core.providers.vault.KeyVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Per-key connection-test UI state. */
sealed class TestState {
    data object Idle : TestState()
    data object Testing : TestState()
    data object Ok : TestState()
    data class Fail(val message: String) : TestState()
}

/** Remaining credit of one fal key (best effort — see [KeyManagerState.testFalKey]). */
sealed class BalanceState {
    data object Loading : BalanceState()
    data class Value(val amount: Double, val currency: String) : BalanceState()
    /** fal answered 401/403: the key works, but reading credit needs an admin key. */
    data object NeedsAdminKey : BalanceState()
    data object Unavailable : BalanceState()
}

/**
 * Shared state holder for the BYOK key manager — used by both Settings and
 * the onboarding wizard. All mutations go straight to the vault (ordered fal
 * list, AD-14); UI state mirrors it.
 */
class KeyManagerState(
    private val vault: KeyVault,
    private val pool: FalKeyPool,
    private val tester: KeyTester,
    private val billing: id.kenang.core.providers.fal.FalBilling,
    private val scope: CoroutineScope,
) {
    var falKeys by mutableStateOf(vault.falKeys())
        private set

    /** label -> chip status (aktif / cadangan / saldo habis). */
    var falStatuses by mutableStateOf(pool.statuses())
        private set

    /** label -> test result; "gemini"/"elevenlabs" pseudo-labels for the single keys. */
    val testResults = mutableStateMapOf<String, TestState>()

    /** label -> remaining fal credit, filled after a connection test. */
    val balances = mutableStateMapOf<String, BalanceState>()

    var geminiKey by mutableStateOf(vault.geminiKey() ?: "")
    var elevenLabsKey by mutableStateOf(vault.elevenLabsKey() ?: "")

    private fun persist(keys: List<FalKey>) {
        vault.saveFalKeys(keys)
        falKeys = vault.falKeys()
        falStatuses = pool.statuses()
    }

    fun addFalKey(label: String, key: String): Boolean {
        val cleanLabel = label.trim().ifBlank { "Key ${falKeys.size + 1}" }
        if (key.isBlank() || falKeys.any { it.label == cleanLabel }) return false
        persist(falKeys + FalKey(cleanLabel, key.trim()))
        return true
    }

    fun removeFalKey(label: String) {
        persist(falKeys.filterNot { it.label == label })
        testResults.remove(label)
    }

    fun moveFalKey(label: String, delta: Int) {
        val idx = falKeys.indexOfFirst { it.label == label }
        moveFalKeyTo(label, idx + delta)
    }

    /** Sets a key's priority position directly (0-based) and persists the order (AD-14). */
    fun moveFalKeyTo(label: String, targetIndex: Int) {
        val idx = falKeys.indexOfFirst { it.label == label }
        if (idx < 0 || targetIndex !in falKeys.indices || targetIndex == idx) return
        val list = falKeys.toMutableList()
        val item = list.removeAt(idx)
        list.add(targetIndex, item)
        persist(list)
    }

    fun testFalKey(key: FalKey) {
        testResults[key.label] = TestState.Testing
        balances[key.label] = BalanceState.Loading
        scope.launch {
            testResults[key.label] = when (val result = tester.testFalKey(key)) {
                is AppResult.Ok -> TestState.Ok
                is AppResult.Err -> TestState.Fail(ErrorTranslator.translate(result.error).message)
            }
            falStatuses = pool.statuses()
            // Remaining credit is a best-effort extra: fal serves it only to
            // ADMIN-scoped keys (403 for plain generation keys), so it must
            // never turn a working key into a failed test.
            balances[key.label] = when (val b = billing.balance(key.key)) {
                is AppResult.Ok -> b.value.currentBalance
                    ?.let { BalanceState.Value(it, b.value.currency) }
                    ?: BalanceState.Unavailable
                is AppResult.Err ->
                    if (b.error is id.kenang.core.common.AppError.InvalidKey) BalanceState.NeedsAdminKey
                    else BalanceState.Unavailable
            }
        }
    }

    fun saveGemini() = vault.saveGeminiKey(geminiKey)

    fun testGemini() {
        saveGemini()
        testResults["gemini"] = TestState.Testing
        scope.launch {
            testResults["gemini"] = when (val result = tester.testGemini()) {
                is AppResult.Ok -> TestState.Ok
                is AppResult.Err -> TestState.Fail(ErrorTranslator.translate(result.error).message)
                null -> TestState.Idle
            }
        }
    }

    fun saveElevenLabs() = vault.saveElevenLabsKey(elevenLabsKey)

    fun testElevenLabs() {
        saveElevenLabs()
        testResults["elevenlabs"] = TestState.Testing
        scope.launch {
            testResults["elevenlabs"] = when (val result = tester.testElevenLabs()) {
                is AppResult.Ok -> TestState.Ok
                is AppResult.Err -> TestState.Fail(ErrorTranslator.translate(result.error).message)
                null -> TestState.Idle
            }
        }
    }

    fun statusLabel(status: FalKeyStatus): String = when (status) {
        FalKeyStatus.AKTIF -> id.kenang.core.common.i18n.Strings.KEYS_STATUS_ACTIVE
        FalKeyStatus.CADANGAN -> id.kenang.core.common.i18n.Strings.KEYS_STATUS_BACKUP
        FalKeyStatus.SALDO_HABIS -> id.kenang.core.common.i18n.Strings.KEYS_STATUS_EXHAUSTED
    }
}
