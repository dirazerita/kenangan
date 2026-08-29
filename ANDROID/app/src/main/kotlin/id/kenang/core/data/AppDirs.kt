package id.kenang.core.data

import java.io.File

/**
 * Android port of the desktop AppDirs. Same folder layout and API, but rooted
 * at the app's private files dir (Context.filesDir/Kenang) instead of
 * %APPDATA% — nothing the app writes needs a storage permission, and
 * uninstalling removes it all.
 *
 * [init] must run once from Application.onCreate before any repository is used.
 */
object AppDirs {

    @Volatile
    private var rootDir: File? = null

    /** Called from KenangApp.onCreate with context.filesDir. */
    fun init(baseDir: File) {
        rootDir = File(baseDir, "Kenang").apply { mkdirs() }
    }

    val root: File
        get() = requireNotNull(rootDir) { "AppDirs.init() not called — see KenangApp" }

    val db: File get() = sub("db")
    val logs: File get() = sub("logs")
    val config: File get() = sub("config")
    val tools: File get() = sub("tools")
    val projects: File get() = sub("projects")

    val dbFile: File get() = File(db, "kenang.db")
    val userConfigFile: File get() = File(config, "app-config.json")

    fun projectDir(projectId: String): File = File(projects, projectId).apply { mkdirs() }
    fun projectPhotos(projectId: String): File = File(projectDir(projectId), "photos").apply { mkdirs() }
    fun projectKeyframes(projectId: String): File = File(projectDir(projectId), "keyframes").apply { mkdirs() }
    fun projectClips(projectId: String): File = File(projectDir(projectId), "clips").apply { mkdirs() }
    fun projectOutput(projectId: String): File = File(projectDir(projectId), "output").apply { mkdirs() }

    /** Deletes a project's whole folder tree (used by Home delete). */
    fun wipeProjectDir(projectId: String) {
        File(projects, projectId).deleteRecursively()
    }

    private fun sub(name: String): File = File(root, name).apply { mkdirs() }
}
