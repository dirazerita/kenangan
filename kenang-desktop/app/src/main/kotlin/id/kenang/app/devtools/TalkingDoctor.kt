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

    // -Ddoctor.audioOnly=<file>: measure and prepare a recording the way the
    // MP3 mode does (probe, copy/transcode/trim) and stop - no cost.
    System.getProperty("doctor.audioOnly")?.takeIf { it.isNotBlank() }?.let { path ->
        val probe = koin.get<id.kenang.core.data.media.AudioProbe>()
        val src = File(path)
        val ms = probe.durationMs(src)
        println("audio only: ${src.name} duration=${ms?.let { "%.1f s".format(it / 1000.0) } ?: "unreadable"}")
        val out = probe.prepare(src, File(File(id.kenang.core.data.AppDirs.cache, "talking"), "audiotest_${System.nanoTime()}.mp3"), TalkingVideoService.MAX_AUDIO_S)
        println("prepared: ${out?.absolutePath ?: "(rejected)"} ${out?.let { "${it.length() / 1024} KB, ${probe.durationMs(it)?.let { d -> "%.1f s".format(d / 1000.0) }}" } ?: ""}")
        System.out.flush()
        Runtime.getRuntime().halt(0)
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

    // -Ddoctor.speaker=<1-based index or part of the label>: who should speak.
    val speakerPick = System.getProperty("doctor.speaker")
    var speaker: id.kenang.core.data.story.SpeakerCandidate? = null
    if (speakerPick != null) {
        when (val people = service.speakers(image)) {
            is AppResult.Ok -> {
                println("   people found: ${people.value.size}")
                people.value.forEachIndexed { i, p ->
                    println("     ${i + 1}. ${p.label}  face=${p.faceBox}  person=${p.personBox}")
                }
                speaker = people.value.getOrNull(speakerPick.toIntOrNull()?.minus(1) ?: -1)
                    ?: people.value.firstOrNull { it.label.contains(speakerPick, ignoreCase = true) }
                println("   speaking: ${speaker?.label ?: "(not matched - no mask)"}")
            }
            is AppResult.Err -> println("   speaker detection failed: ${people.error}")
        }
    }

    // -Ddoctor.maskOnly=true: detect + render the mask and stop, so the
    // geometry can be judged without paying for a video.
    if (System.getProperty("doctor.maskOnly") == "true") {
        if (speaker == null) {
            println("pass -PdoctorSpeaker=<n> together with -PdoctorMaskOnly=true")
            exitProcess(1)
        }
        val prepared = id.kenang.core.data.story.UploadPrep.prepareJpeg(image)
        val decoded = javax.imageio.ImageIO.read(java.io.ByteArrayInputStream(prepared))
        val others = (service.speakers(image) as? AppResult.Ok)?.value.orEmpty()
            .filter { it.id != speaker.id }
            .mapNotNull { o ->
                val body = o.personBox?.takeIf { b ->
                    speaker.faceBox?.let { f -> !(b[0] < f[2] && b[2] > f[0] && b[1] < f[3] && b[3] > f[1]) } ?: true
                }
                body ?: o.faceBox
            }
        val box = id.kenang.core.data.story.SpeakerMask.speakerBox(speaker.faceBox, speaker.personBox)
        if (box == null) {
            println("no usable box for ${speaker.label}")
            exitProcess(1)
        }
        val out = File(AppDirs.cache, "talking/masktest_${System.nanoTime()}.png")
        val mask = id.kenang.core.data.story.SpeakerMask.render(
            decoded.width, decoded.height, box, out, others = others, speakerFace = speaker.faceBox,
        )
        println("   mask: ${mask?.absolutePath ?: "(none)"}  (${decoded.width}x${decoded.height}, cut out ${others.size})")
        exitProcess(0)
    }

    val t0 = System.currentTimeMillis()
    when (val r = service.run(image, script, voiceId = null, voiceSample = voice, option = option, speaker = speaker) { println("   phase: $it") }) {
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
