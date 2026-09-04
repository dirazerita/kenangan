package id.kenang.app.devtools

import id.kenang.app.di.appModule
import id.kenang.app.ui.storyboard.StoryboardSheetRenderer
import id.kenang.core.common.Logging
import id.kenang.core.data.AppDirs
import id.kenang.core.data.SceneRepository
import id.kenang.core.data.SettingsRepository
import id.kenang.core.db.KenangDb
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import java.io.File
import kotlin.system.exitProcess

/**
 * Diagnostic: renders the storyboard contact sheet for the newest project
 * (or -PdoctorProject=<id>) headlessly — verifies the D-030 renderer without
 * clicking through the UI. Free, offline. Run: gradlew :app:sheetDoctor
 */
fun main(): Unit = runBlocking {
    Logging.init(AppDirs.logs)
    startKoin { modules(appModule) }
    val koin = GlobalContext.get()
    val db = koin.get<KenangDb>()
    val scenes = koin.get<SceneRepository>()
    val settings = koin.get<SettingsRepository>()

    val explicit = System.getProperty("doctor.project")
    val project = if (explicit != null) {
        db.kenangQueries.selectAllProjects().executeAsList().firstOrNull { it.id == explicit }
    } else {
        db.kenangQueries.selectAllProjects().executeAsList().maxByOrNull { it.updated_at }
    }
    if (project == null) { println("no project found"); exitProcess(1) }

    val list = scenes.scenes(project.id).sortedBy { it.order_index }
    println("== sheetDoctor: '${project.name}' — ${list.size} scenes, ratio ${project.ratio}")

    val folderName = project.name.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().ifBlank { "Kenang" }
    val safeName = project.name.replace(Regex("[^A-Za-z0-9_\\- ]"), "").trim()
        .replace(' ', '_').ifBlank { "kenang" }
    val dir = settings.outputFolder?.trim()?.takeIf { it.isNotBlank() }
        ?.let { File(it, folderName) }
        ?.takeIf { d -> runCatching { d.mkdirs(); d.isDirectory }.getOrDefault(false) }
        ?: AppDirs.projectOutput(project.id)
    val out = StoryboardSheetRenderer.render(
        project.name, project.ratio, list, File(dir, "Storyboard_$safeName.png"),
    )
    println("SHEET OK -> ${out.absolutePath} (${out.length() / 1024} KB)")
    exitProcess(0)
}
