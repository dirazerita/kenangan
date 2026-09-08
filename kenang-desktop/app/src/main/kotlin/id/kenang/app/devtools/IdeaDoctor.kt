package id.kenang.app.devtools

import id.kenang.app.di.appModule
import id.kenang.core.common.AppResult
import id.kenang.core.common.Logging
import id.kenang.core.data.AppDirs
import id.kenang.core.data.SceneRepository
import id.kenang.core.db.KenangDb
import id.kenang.core.providers.story.AnalysisService
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import java.io.File
import kotlin.system.exitProcess

/**
 * Diagnostic for D-050: asks for a reference-photo scene idea and prints it,
 * so the suggestion can be judged against the photo it was read from without
 * clicking through the dialog. One small vision call (~$0.002).
 *
 * Run: gradlew :app:ideaDoctor -PdoctorImage=<path> [-PdoctorProject=<id>]
 */
fun main(): Unit = runBlocking {
    Logging.init(AppDirs.logs)
    startKoin { modules(appModule) }
    val koin = GlobalContext.get()
    val db = koin.get<KenangDb>()
    val scenes = koin.get<SceneRepository>()
    val analysis = koin.get<AnalysisService>()

    val explicit = System.getProperty("doctor.project")
    val all = db.kenangQueries.selectAllProjects().executeAsList()
    val project = if (explicit != null) all.firstOrNull { it.id == explicit }
    else all.maxByOrNull { it.updated_at }
    if (project == null) { println("no project found"); exitProcess(1) }

    val image = System.getProperty("doctor.image")?.let { File(it) }
        ?: run { println("pass -PdoctorImage=<path>"); exitProcess(1) }
    if (!image.isFile) { println("image not found: ${image.absolutePath}"); exitProcess(1) }

    val onBoard = scenes.scenes(project.id).sortedBy { it.order_index }
        .mapNotNull { it.user_description ?: it.motion_summary_id }
    println("== ideaDoctor: '${project.name}' — ${onBoard.size} scenes on the board")
    println("   photo: ${image.name}")

    val t0 = System.currentTimeMillis()
    // -PdoctorAvoid="<text>" reproduces pressing "Ganti usulan".
    val avoid = System.getProperty("doctor.avoid")?.let { listOf(it) } ?: emptyList()
    if (avoid.isNotEmpty()) println("   rejecting: ${avoid.first().take(70)}…")
    when (val r = analysis.suggestSceneIdea(project.id, image, onBoard, avoid)) {
        is AppResult.Ok -> {
            val idea = r.value
            println("OK in ${(System.currentTimeMillis() - t0) / 1000}s")
            println("   description_id (${idea.descriptionId.split(" ").size} kata):")
            println("      ${idea.descriptionId}")
            println("   activity_en: ${idea.activityEn}")
            println("   keyword=${idea.keyword}  category=${idea.category.key}  camera=${idea.camera.key}")
        }
        is AppResult.Err -> println("FAILED after ${(System.currentTimeMillis() - t0) / 1000}s: ${r.error}")
    }
    exitProcess(0)
}
