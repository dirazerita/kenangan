package id.kenang.app.devtools

import id.kenang.app.di.appModule
import id.kenang.core.common.AppResult
import id.kenang.core.common.Logging
import id.kenang.core.common.events.GenerationEvents
import id.kenang.core.data.AppDirs
import id.kenang.core.data.PhotoRepository
import id.kenang.core.data.ProjectRepository
import id.kenang.core.data.SceneRepository
import id.kenang.core.data.SceneStatus
import id.kenang.core.providers.CostTracker
import id.kenang.core.providers.story.AnalysisOutcome
import id.kenang.core.providers.story.AnalysisService
import id.kenang.core.providers.story.CostEstimator
import id.kenang.core.providers.story.KeyframeService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.context.GlobalContext
import java.io.File
import kotlin.system.exitProcess

/**
 * Scripted Phase-03 demo (docs/demo-03.md) — drives the REAL services end to
 * end against the user's fal key: wizard-equivalent setup with PoC photos →
 * analysis → keyframes → edit/regen/reorder/delete → estimator → confirm
 * event validated against the MEMORY §6 Scene contract.
 *
 * Run: gradlew :app:demoDriver "-PdemoPhotos=<dir>" — costs real (small) money.
 */
fun main(): Unit = runBlocking {
    Logging.init(AppDirs.logs)
    startKoin { modules(appModule) }
    val koin = GlobalContext.get()

    val projects = koin.get<ProjectRepository>()
    val photos = koin.get<PhotoRepository>()
    val scenes = koin.get<SceneRepository>()
    val analysis = koin.get<AnalysisService>()
    val keyframes = koin.get<KeyframeService>()
    val estimator = koin.get<CostEstimator>()
    val costs = koin.get<CostTracker>()
    val events = koin.get<GenerationEvents>()

    val assetDir = File(System.getProperty("demo.photos") ?: "e:/PROJECT SULTAN/POC/ASSETS")
    // 4 real photos incl. the fusion-worthy pair (foto09 + foto14).
    val demoPhotos = listOf("foto07_lansia_solo.jpg", "foto05_keluarga_grup.jpg",
        "foto09_dewasa_fusion.jpg", "foto14_fusion_b.jpg")

    println("== [1] Create project + photos ==")
    val projectId = projects.create("Demo Fase 03", "9:16", "taman", "standar")
    demoPhotos.forEach { name ->
        val f = File(assetDir, name)
        require(f.isFile) { "missing asset ${f.absolutePath}" }
        photos.addPhoto(projectId, f)
        println("  + $name")
    }
    projects.updateMeta(projectId, "Demo Fase 03", "9:16", "taman", "standar",
        "Di taman kecil itu, kita pernah tertawa bersama.", null, 5L)

    println("== [2] Analysis (moderation -> PhotoAnalysis -> story plan) ==")
    val t0 = System.currentTimeMillis()
    val outcome = analysis.run(projectId, "taman", "9:16", 5L,
        "Di taman kecil itu, kita pernah tertawa bersama.") { stage ->
        println("  stage: $stage (t=${(System.currentTimeMillis() - t0) / 1000}s)")
    }
    println("  analysis took ${(System.currentTimeMillis() - t0) / 1000}s -> $outcome")
    if (outcome !is AnalysisOutcome.Ok) { println("ABORT: $outcome"); exitProcess(1) }

    scenes.scenes(projectId).forEach {
        println("  scene ${it.scene_id} [${it.type}] motion='${it.motion_summary_id}' :: ${it.keyframe_prompt_en?.take(110)}…")
    }

    println("== [3] Keyframes (Nano Banana, tier=standar) ==")
    scenes.scenes(projectId).forEach { s ->
        val r = keyframes.generate(s.scene_id, "standar", isRegen = false)
        println("  ${s.scene_id}: " + when (r) {
            is AppResult.Ok -> "OK -> ${r.value.local_keyframe_path}"
            is AppResult.Err -> "FAILED ${r.error}"
        })
    }

    println("== [4] Storyboard mutations ==")
    val list = scenes.scenes(projectId)
    // Edit one prompt via the template system (like the editor dialog does).
    val spec = id.kenang.core.common.story.MotionSpec(
        id.kenang.core.common.story.MotionCategory.LOOK_AT_CAMERA,
        id.kenang.core.common.story.CameraMove.STATIC,
        subjectEn = "the elderly woman", subjectId = "Beliau",
    )
    scenes.updateMotion(list[0].scene_id,
        id.kenang.core.common.story.MotionTemplates.buildPromptEn(spec),
        id.kenang.core.common.story.MotionTemplates.buildSummaryId(spec))
    println("  edited motion of ${list[0].scene_id}")
    // Regenerate one keyframe (counts as regen in the estimator).
    val regen = keyframes.generate(list[1].scene_id, "standar", isRegen = true)
    println("  regen ${list[1].scene_id}: ${if (regen is AppResult.Ok) "OK" else regen}")
    // Reorder: move last scene to front.
    val ordered = scenes.scenes(projectId).sortedBy { it.order_index }.map { it.scene_id }
    scenes.reorder(listOf(ordered.last()) + ordered.dropLast(1))
    println("  reordered: last -> first")
    // Delete one scene (min-1 guard stays intact).
    val afterReorder = scenes.scenes(projectId).sortedBy { it.order_index }
    val deleted = scenes.delete(afterReorder.last().scene_id, projectId)
    println("  deleted ${afterReorder.last().scene_id}: $deleted")

    println("== [5] Estimator vs hand computation ==")
    val current = scenes.scenes(projectId)
    val est = estimator.estimate(current, "standar")
    val handI2v = current.sumOf { it.duration_s } * 0.084
    val handKf = current.sumOf { it.regen_count } * 0.039
    println("  estimator: $${"%.4f".format(est.usd)} (i2v $${"%.4f".format(est.i2vUsd)} + regen $${"%.4f".format(est.keyframeUsd)}) ≈ Rp${"%,.0f".format(est.idr)}")
    println("  hand:      $${"%.4f".format(handI2v + handKf)}  -> match=${Math.abs(est.usd - (handI2v + handKf)) < 1e-9}")

    println("== [6] Confirm -> StartGenerationRequest + §6 contract validation ==")
    var received: id.kenang.core.common.events.StartGenerationRequest? = null
    val sub = launch { received = events.start.first() }
    scenes.confirmAll(projectId)
    events.requestStart(id.kenang.core.common.events.StartGenerationRequest(projectId, "standar"))
    kotlinx.coroutines.delay(200)
    sub.cancel()
    println("  event received: $received")

    val json = Json { ignoreUnknownKeys = true }
    var contractOk = true
    scenes.scenes(projectId).forEach { s ->
        val problems = buildList {
            if (s.status != SceneStatus.CONFIRMED) add("status=${s.status}")
            if (s.type !in setOf("single", "fusion")) add("type")
            if (s.keyframe_prompt_en.isNullOrBlank()) add("keyframe_prompt_en")
            if (s.keyframe_url.isNullOrBlank()) add("keyframe_url")
            if (s.motion_prompt_en.isNullOrBlank()) add("motion_prompt_en")
            if (s.motion_summary_id.isNullOrBlank()) add("motion_summary_id")
            if (s.duration_s !in setOf(5L, 10L)) add("duration_s")
            if (runCatching { json.decodeFromString<List<String>>(s.source_photos_json) }.getOrNull().isNullOrEmpty()) add("source_photos")
            val verdict = id.kenang.core.common.story.MotionTemplateValidator.validatePromptEn(s.motion_prompt_en ?: "")
            if (verdict !is id.kenang.core.common.story.MotionTemplateValidator.Verdict.Valid) add("motion not template-valid")
        }
        if (problems.isNotEmpty()) { contractOk = false; println("  ✗ ${s.scene_id}: $problems") }
        else println("  ✓ ${s.scene_id} conforms to MEMORY §6 Scene contract")
    }

    println("== [7] Project cost (CostTracker) ==")
    println("  project total est: $${"%.4f".format(costs.projectTotalUsd(projectId))}")
    costs.thisMonthPerKey().forEach { println("  key '${it.keyLabel}': $${"%.4f".format(it.usd)}") }

    println("\nDEMO ${if (contractOk) "PASSED" else "FAILED"} — projectId=$projectId")
    exitProcess(if (contractOk) 0 else 1)
}
