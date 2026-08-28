package id.kenang.app.devtools

import id.kenang.app.di.appModule
import id.kenang.core.common.AppResult
import id.kenang.core.common.Logging
import id.kenang.core.data.AppDirs
import id.kenang.core.providers.fal.FalQueueClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import kotlin.system.exitProcess

/**
 * Diagnostic: pings candidate model ids through fal's openrouter router with
 * max_tokens=1 (~$0.001 each) to verify which ids actually resolve before
 * they are offered in the Settings → Model AI catalog.
 * Run: gradlew :app:modelDoctor
 */
private val CANDIDATES = listOf(
    "google/gemini-2.5-flash",
    "google/gemini-3.6-flash",
    "anthropic/claude-haiku-4.5",
    "anthropic/claude-sonnet-5",
    "anthropic/claude-opus-5",
    "anthropic/claude-fable-5",
    "openai/gpt-5-mini",
)

fun main(): Unit = runBlocking {
    Logging.init(AppDirs.logs)
    startKoin { modules(appModule) }
    val falClient = GlobalContext.get().get<FalQueueClient>()

    println("== modelDoctor: probing ${CANDIDATES.size} router model ids (1 token each) ==")
    for (model in CANDIDATES) {
        val body = buildJsonObject {
            put("model", model)
            put("prompt", "ping")
            put("max_tokens", 300)
            // Reasoning-mandatory models (Fable 5, GPT-5 Mini, Gemini 3.6)
            // refuse requests without an explicit reasoning setting.
            put("reasoning", true)
        }
        val verdict = when (val s = falClient.submit("openrouter/router", body)) {
            is AppResult.Err -> "SUBMIT FAIL ${s.error}"
            is AppResult.Ok -> when (val r = falClient.awaitResult(s.value, timeoutMillis = 60_000)) {
                is AppResult.Ok -> "OK"
                is AppResult.Err -> "FAIL ${r.error.toString().take(120)}"
            }
        }
        println("  ${model.padEnd(32)} -> $verdict")
    }
    exitProcess(0)
}
