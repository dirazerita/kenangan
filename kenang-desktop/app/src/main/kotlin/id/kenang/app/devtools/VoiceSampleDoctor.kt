package id.kenang.app.devtools

import id.kenang.app.di.appModule
import id.kenang.core.common.AppResult
import id.kenang.core.common.Logging
import id.kenang.core.data.AppDirs
import id.kenang.core.data.SettingsRepository
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.providers.story.TtsPreviewService
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import java.io.File
import kotlin.system.exitProcess

/**
 * Generates the bundled voice samples (owner 2026-09-16): one short MiniMax
 * sentence per preset voice of the config, written to every directory in
 * -Ddoctor.samplesOut (';'-separated) as voices/<id>.mp3 so the app ships
 * them and a click on "Dengar" costs nothing. Paid once, about 0.1 USD per
 * 1000 characters over all voices; a voice the provider rejects is reported
 * and skipped.
 *
 *   -Ddoctor.samplesOut=<dir>[;<dir>]   required
 *   -Ddoctor.only=<id>[,<id>]           regenerate just these voices
 */
fun main(): Unit = runBlocking {
    Logging.init(AppDirs.logs)
    val koin = startKoin { modules(appModule) }.koin
    koin.get<SettingsRepository>().dataFolder?.takeIf { it.isNotBlank() }?.let { AppDirs.useMediaRoot(File(it)) }

    val outDirs = System.getProperty("doctor.samplesOut")?.split(";")?.map { File(it.trim()) }?.filter { it.path.isNotBlank() }
        ?: run { println("pass -PsamplesOut=<dir>[;<dir>]"); exitProcess(1) }
    val only = System.getProperty("doctor.only")?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet()
    val tts = koin.get<ConfigRepository>().current().tts
    val preview = koin.get<TtsPreviewService>()
    println("== voiceSamples: ${tts.voices.size} voice(s), text \"${tts.previewText}\" -> ${outDirs.joinToString { it.absolutePath }}")

    var failed = 0
    for (voice in tts.voices) {
        if (only != null && voice.id !in only) continue
        val t0 = System.currentTimeMillis()
        when (val r = preview.preview(tts.previewText, voice.id)) {
            is AppResult.Ok -> {
                outDirs.forEach { dir ->
                    dir.mkdirs()
                    r.value.copyTo(File(dir, TtsPreviewService.sampleName(voice.id) + ".mp3"), overwrite = true)
                }
                println("OK   ${voice.id.padEnd(20)} ${voice.labelId.padEnd(16)} ${r.value.length() / 1024} KB in ${(System.currentTimeMillis() - t0) / 1000}s")
            }
            is AppResult.Err -> {
                failed++
                println("FAIL ${voice.id.padEnd(20)} ${voice.labelId.padEnd(16)} ${r.error}")
            }
        }
    }
    println("== done, $failed failure(s)")
    exitProcess(if (failed == 0) 0 else 2)
}
