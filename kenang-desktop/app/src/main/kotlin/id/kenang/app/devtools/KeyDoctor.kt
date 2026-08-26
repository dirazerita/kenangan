package id.kenang.app.devtools

import id.kenang.app.di.appModule
import id.kenang.core.common.AppResult
import id.kenang.core.common.Logging
import id.kenang.core.data.AppDirs
import id.kenang.core.providers.KeyTester
import id.kenang.core.providers.vault.KeyVault
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import kotlin.system.exitProcess

/**
 * Diagnostic: runs the app's own "Tes koneksi" against EVERY stored fal key
 * in priority order and prints label + result (keys themselves never printed
 * — AD-11). ~$0.001 per key. Run: gradlew :app:keyDoctor
 */
fun main(): Unit = runBlocking {
    Logging.init(AppDirs.logs)
    startKoin { modules(appModule) }
    val koin = GlobalContext.get()
    val vault = koin.get<KeyVault>()
    val tester = koin.get<KeyTester>()

    val keys = vault.falKeys()
    println("== keyDoctor: ${keys.size} fal key(s) in priority order ==")
    var firstOk: String? = null
    keys.forEachIndexed { i, key ->
        val verdict = when (val r = tester.testFalKey(key)) {
            is AppResult.Ok -> { if (firstOk == null) firstOk = key.label; "OK" }
            is AppResult.Err -> "FAIL ${r.error}"
        }
        println("  #${i + 1} ${key.label.padEnd(12)} (${key.masked}) -> $verdict")
    }
    println(
        when {
            keys.isEmpty() -> "NO KEYS STORED"
            firstOk == keys.firstOrNull()?.label -> "VERDICT: priority key '${firstOk}' works — analysis failures are NOT key-related."
            firstOk != null -> "VERDICT: priority key '${keys.first().label}' is BROKEN; first working key is '$firstOk'. " +
                "Submits fail over only on balance-exhausted, NOT on other errors — fix or demote the broken key."
            else -> "VERDICT: NO working fal key — every call will fail."
        },
    )
    exitProcess(0)
}
