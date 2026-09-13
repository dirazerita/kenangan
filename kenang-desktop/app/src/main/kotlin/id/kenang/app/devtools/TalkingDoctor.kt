package id.kenang.app.devtools

import id.kenang.app.di.appModule
import id.kenang.core.common.AppResult
import id.kenang.core.common.Logging
import id.kenang.core.data.AppDirs
import id.kenang.core.data.SettingsRepository
import id.kenang.core.providers.fal.FalBilling
import id.kenang.core.providers.fal.FalKeyPool
import id.kenang.core.providers.talking.TalkingVideoService
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import java.io.File
import kotlin.system.exitProcess

/**
 * Diagnostic for Video Berbicara (owner 2026-09-13): one REAL run (paid),
 * bracketed by the fal balance so the per-second price in config can be
 * checked against what the provider actually charged.
 *
 *   -Ddoctor.image=<path>     photo (required)
 *   -Ddoctor.script=<text>    what to say (default: a short Indonesian line)
 *   -Ddoctor.voice=<path>     optional recording to clone
 *   -Ddoctor.model=<key>      catalog selection key (default: selected)
 */
fun main(): Unit = runBlocking {
    Logging.init(AppDirs.logs)
    val koin = startKoin { modules(appModule) }.koin
    koin.get<SettingsRepository>().dataFolder?.takeIf { it.isNotBlank() }?.let { AppDirs.useMediaRoot(File(it)) }

    // -Ddoctor.balanceOnly=true: read the fal balance and stop (free) - used to
    // see a charge that had not posted yet when a run finished.
    if (System.getProperty("doctor.balanceOnly") == "true") {
        val billing = koin.get<FalBilling>()
        val key = koin.get<FalKeyPool>().currentKey()
        val bal = key?.let { (billing.balance(it.key) as? AppResult.Ok)?.value?.currentBalance }
        println("balance now (${key?.label}): ${bal?.let { "$" + "%.3f".format(it) } ?: "(not readable)"}")
        exitProcess(0)
    }

    val image = System.getProperty("doctor.image")?.let(::File)?.takeIf { it.isFile }
        ?: run { println("pass -PdoctorImage=<photo>"); exitProcess(1) }
    val script = System.getProperty("doctor.script")
        ?: "Halo, ini contoh video berbicara dari Kenang. Semoga kenangan ini selalu hangat di hati."
    val voice = System.getProperty("doctor.voice")?.let(::File)?.takeIf { it.isFile }

    val service = koin.get<TalkingVideoService>()
    System.getProperty("doctor.model")?.let { service.select(it) }
    val option = service.selected()
    val est = service.estimate(script.length, option, withNewClone = voice != null && service.existingClone(voice) == null)
    println("== talkingDoctor: ${option.labelId} (${option.id})")
    println("   script ${script.length} chars ≈ ${"%.0f".format(est.seconds)} s · estimate video $${"%.2f".format(est.videoUsd)} + tts $${"%.2f".format(est.ttsUsd)} + clone $${"%.2f".format(est.cloneUsd)}")

    val billing = koin.get<FalBilling>()
    val key = koin.get<FalKeyPool>().currentKey()
    val before = key?.let { (billing.balance(it.key) as? AppResult.Ok)?.value?.currentBalance }
    println("   balance before: ${before?.let { "$" + "%.3f".format(it) } ?: "(not readable with this key)"}")

    val t0 = System.currentTimeMillis()
    when (val r = service.run(image, script, voiceId = null, voiceSample = voice, option = option) { println("   phase: $it") }) {
        is AppResult.Ok -> {
            println("OK in ${(System.currentTimeMillis() - t0) / 1000}s")
            println("   video: ${r.value.video.absolutePath} (${r.value.video.length() / 1024} KB, ${"%.1f".format(r.value.durationS)} s)")
            println("   audio: ${r.value.audio.absolutePath}")
            println("   voice: ${r.value.voiceLabel} · recorded estimate $${"%.3f".format(r.value.usd)}")
        }
        is AppResult.Err -> println("FAILED after ${(System.currentTimeMillis() - t0) / 1000}s: ${r.error}")
    }
    val after = key?.let { (billing.balance(it.key) as? AppResult.Ok)?.value?.currentBalance }
    if (before != null && after != null) {
        println("   balance after: $${"%.3f".format(after)} -> ACTUAL charge $${"%.3f".format(before - after)}")
    }
    exitProcess(0)
}
