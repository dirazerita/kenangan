package id.kenang.app.devtools

import id.kenang.app.di.appModule
import id.kenang.core.common.Logging
import id.kenang.core.data.AppDirs
import id.kenang.core.data.PhotoRepository
import id.kenang.core.data.ProjectRepository
import id.kenang.core.db.KenangDb
import id.kenang.core.providers.story.AnalysisService
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import java.io.File
import kotlin.system.exitProcess

/**
 * Diagnostic: re-runs analysis on the newest non-done project (or
 * -Ddoctor.project=<id>) with verbose stage output so the real provider
 * error surfaces (with the new FalQueueClient/FalStorage logging).
 * Costs ≈ $0.01 for moderation+analysis+plan. Run: gradlew :app:analysisDoctor
 */
fun main(): Unit = runBlocking {
    Logging.init(AppDirs.logs)
    startKoin { modules(appModule) }
    val koin = GlobalContext.get()
    val db = koin.get<KenangDb>()
    val projects = koin.get<ProjectRepository>()
    val photos = koin.get<PhotoRepository>()
    val analysis = koin.get<AnalysisService>()

    val explicit = System.getProperty("doctor.project")
    val project = if (explicit != null) {
        projects.get(explicit)
    } else {
        db.kenangQueries.selectAllProjects().executeAsList()
            .firstOrNull { it.status in setOf("draft", "analyzing", "storyboard") }
    }
    if (project == null) { println("no candidate project found"); exitProcess(1) }

    println("== analysisDoctor: '${project.name}' (${project.id}) status=${project.status} ratio=${project.ratio} ==")
    photos.photos(project.id).forEach { p ->
        val f = File(p.local_path)
        println("  photo ${p.id}: ${f.name} ${if (f.isFile) "${f.length() / 1024} KB" else "MISSING"} uploaded=${p.upload_id != null}")
    }

    // -Ddoctor.statusfix: only promote an already-analyzed project (no API calls).
    if (System.getProperty("doctor.statusfix") != null) {
        projects.updateStatus(project.id, "storyboard")
        println("status -> storyboard (no analysis run)")
        exitProcess(0)
    }

    val t0 = System.currentTimeMillis()
    val outcome = analysis.run(
        project.id, project.vibe, project.ratio, project.scene_duration_s, project.narration,
        targetScenes = project.target_scenes,
        restorePhotos = project.restore_photos == 1L,
        sceneGuidance = project.scene_guidance,
        customVibe = project.custom_vibe,
    ) { stage -> println("  [${(System.currentTimeMillis() - t0) / 1000}s] stage: $stage") }
    println("OUTCOME after ${(System.currentTimeMillis() - t0) / 1000}s: $outcome")
    if (outcome is id.kenang.core.providers.story.AnalysisOutcome.Ok) {
        projects.updateStatus(project.id, "storyboard")
        println("status -> storyboard")
        // Variety inspection (dogfood 2026-08-27): every scene should carry a
        // DISTINCT activity in its keyframe prompt.
        koin.get<id.kenang.core.data.SceneRepository>().scenes(project.id)
            .sortedBy { it.order_index }
            .forEach { s ->
                println("  [${s.order_index}] motion='${s.motion_summary_id}'")
                println("      kf: ${s.keyframe_prompt_en?.take(180)}")
            }
    }
    exitProcess(0)
}
