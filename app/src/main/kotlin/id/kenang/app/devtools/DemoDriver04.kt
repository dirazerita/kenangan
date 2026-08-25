package id.kenang.app.devtools

import id.kenang.app.di.appModule
import id.kenang.core.common.AppResult
import id.kenang.core.common.Logging
import id.kenang.core.data.AppDirs
import id.kenang.core.data.GenJobRepository
import id.kenang.core.data.MusicLibrary
import id.kenang.core.data.OutputRepository
import id.kenang.core.data.PhotoRepository
import id.kenang.core.data.ProjectRepository
import id.kenang.core.data.SceneRepository
import id.kenang.core.data.SceneStatus
import id.kenang.core.providers.CostTracker
import id.kenang.core.providers.gen.AssemblyService
import id.kenang.core.providers.gen.GenerationOrchestrator
import id.kenang.core.providers.story.AnalysisOutcome
import id.kenang.core.providers.story.AnalysisService
import id.kenang.core.providers.story.KeyframeService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import java.io.File
import kotlin.system.exitProcess

/**
 * Scripted Phase-04 E2E (docs/demo-04.md) against REAL fal APIs — small but
 * real money (Standar tier). Own-family photos (MEMORY: owner-approved).
 *
 * Stages (`-PdemoStage=`):
 *  a            — create 9:16 project → analysis → keyframes → confirm →
 *                 submit all scenes, then HARD-KILL once every scene has a fal
 *                 request id persisted (simulates force-kill mid-generation).
 *  b:<project>  — resume that project: polling continues on the SAME fal
 *                 request ids (no resubmission = no double spend), then
 *                 TTS → subtitles → assembly → output row.
 *  full[:ratio] — straight through, default 16:9 (the second DoD export).
 */
private val NARRATION =
    "Di taman kecil itu, kita pernah tertawa bersama. Senyum kakek dan hangatnya " +
        "keluarga tetap tinggal di hati kami. Kenangan ini akan kami jaga selamanya."

fun main(): Unit = runBlocking {
    Logging.init(AppDirs.logs)
    startKoin { modules(appModule) }
    val koin = GlobalContext.get()

    val projects = koin.get<ProjectRepository>()
    val photos = koin.get<PhotoRepository>()
    val scenes = koin.get<SceneRepository>()
    val analysis = koin.get<AnalysisService>()
    val keyframes = koin.get<KeyframeService>()
    val orchestrator = koin.get<GenerationOrchestrator>()
    val assembly = koin.get<AssemblyService>()
    val jobs = koin.get<GenJobRepository>()
    val outputs = koin.get<OutputRepository>()
    val costs = koin.get<CostTracker>()
    val music = koin.get<MusicLibrary>()

    val assetDir = File(System.getProperty("demo.photos") ?: "e:/PROJECT SULTAN/KENANG/POC/ASSETS")
    val stage = System.getProperty("demo.stage") ?: "full"

    suspend fun buildProject(ratio: String): String {
        println("== [1] Project ($ratio, standar) from own-family photos ==")
        val projectId = projects.create("Kenangan Kakek", ratio, "taman", "standar")
        listOf("kakek1.jpg", "kakek2.jpg", "keluarga.jpg").forEach { name ->
            val f = File(assetDir, name)
            require(f.isFile) { "missing asset ${f.absolutePath}" }
            photos.addPhoto(projectId, f)
            println("  + $name")
        }
        val track = music.tracks().firstOrNull()
        requireNotNull(track) { "bundled music missing" }
        val musicDir = File(AppDirs.projectDir(projectId), "music").apply { mkdirs() }
        val musicFile = track.file.copyTo(File(musicDir, track.file.name), overwrite = true)
        println("  ♪ bundled: ${track.meta.labelId}")
        projects.updateMeta(projectId, "Kenangan Kakek", ratio, "taman", "standar",
            NARRATION, musicFile.absolutePath, 5L)

        println("== [2] Analysis ==")
        val outcome = analysis.run(projectId, "taman", ratio, 5L, NARRATION) { println("  stage: $it") }
        if (outcome !is AnalysisOutcome.Ok) { println("ABORT analysis: $outcome"); exitProcess(1) }

        println("== [3] Keyframes ==")
        scenes.scenes(projectId).forEach { s ->
            when (val r = keyframes.generate(s.scene_id, "standar", isRegen = false)) {
                is AppResult.Ok -> println("  ${s.scene_id}: OK")
                is AppResult.Err -> { println("  ${s.scene_id}: FAILED ${r.error}"); exitProcess(1) }
            }
        }
        scenes.confirmAll(projectId)
        projects.updateStatus(projectId, "generating")
        println("  confirmed ${scenes.scenes(projectId).size} scenes -> $projectId")
        return projectId
    }

    suspend fun finish(projectId: String) {
        println("== [5] Audio (TTS + subtitles) + FFmpeg assembly ==")
        when (val prep = assembly.prepareAudio(projectId)) {
            is AssemblyService.AudioPrep.Ready -> {
                val r = assembly.assemble(projectId, prep.narration) { p -> print("\r  assembling $p%") }
                println()
                when (r) {
                    is AppResult.Ok -> println("  OUTPUT: ${r.value.absolutePath} (${r.value.length() / 1_000_000} MB)")
                    is AppResult.Err -> { println("  ASSEMBLY FAILED: ${r.error}"); exitProcess(1) }
                }
            }
            is AssemblyService.AudioPrep.DurationMismatch -> {
                println("  duration rule fired (narasi ${prep.narration.durationMs}ms > video ${prep.videoDurationMs}ms) -> tempo 1.1")
                val r = assembly.assemble(projectId, prep.narration, narrationTempo = 1.1) { }
                when (r) {
                    is AppResult.Ok -> println("  OUTPUT: ${r.value.absolutePath}")
                    is AppResult.Err -> { println("  ASSEMBLY FAILED: ${r.error}"); exitProcess(1) }
                }
            }
            is AssemblyService.AudioPrep.Failed -> { println("  TTS FAILED: ${prep.error}"); exitProcess(1) }
        }
        println("== [6] Ledger ==")
        println("  project est total: $${"%.4f".format(costs.projectTotalUsd(projectId))}")
        jobs.jobsByProject(projectId).forEach {
            println("  job ${it.id.take(10)} scene=${it.scene_id} status=${it.status} key=${it.key_label} req=${it.backend_job_id?.take(12)}")
        }
        outputs.byProject(projectId).forEach { println("  output ${it.ratio} -> ${it.path}") }
    }

    when {
        stage == "a" || stage.startsWith("a:") -> {
            // a:<projectId> reuses an existing confirmed project (keyframes already paid).
            val projectId = if (stage.startsWith("a:")) stage.substringAfter("a:") else buildProject("9:16")
            println("== [4a] Submit scenes, then HARD-KILL (force-kill simulation) ==")
            val gen = launch { orchestrator.run(projectId, "standar") }
            // Wait until every scene's job row carries a fal request id.
            val total = scenes.scenes(projectId).size
            while (true) {
                val submitted = jobs.jobsByProject(projectId).count { it.backend_job_id != null }
                println("  submitted $submitted/$total")
                if (submitted >= total) break
                delay(2000)
            }
            println("  KILLING PROCESS NOW — resume with: -PdemoStage=b:$projectId")
            Runtime.getRuntime().halt(42) // no shutdown hooks: as brutal as a crash
        }
        stage.startsWith("b:") -> {
            val projectId = stage.substringAfter("b:")
            println("== [4b] RESUME after kill — polling existing fal jobs (projectId=$projectId) ==")
            val before = jobs.jobsByProject(projectId).mapNotNull { it.backend_job_id }.toSet()
            val outcome = orchestrator.run(projectId, "standar")
            val after = jobs.jobsByProject(projectId).mapNotNull { it.backend_job_id }.toSet()
            println("  outcome: done=${outcome.doneScenes} failed=${outcome.failedScenes} fatal=${outcome.fatal}")
            println("  fal request ids before=$${before.size} after=${after.size} " +
                "resubmitted=${(after - before).size} (must be 0)")
            if ((after - before).isNotEmpty()) { println("  !! RESUBMISSION DETECTED"); exitProcess(1) }
            if (!outcome.allDone) exitProcess(1)
            finish(projectId)
            println("\nDEMO STAGE B PASSED — projectId=$projectId")
            exitProcess(0)
        }
        else -> {
            val ratio = stage.substringAfter(":", "16:9")
            val projectId = buildProject(ratio)
            println("== [4] Generation (fal, Standar/Kling audio-off) ==")
            val outcome = orchestrator.run(projectId, "standar")
            println("  outcome: done=${outcome.doneScenes} failed=${outcome.failedScenes} fatal=${outcome.fatal}")
            if (!outcome.allDone) exitProcess(1)
            finish(projectId)
            println("\nDEMO FULL ($ratio) PASSED — projectId=$projectId")
            exitProcess(0)
        }
    }
}
