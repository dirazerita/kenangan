package id.kenang.core.data

import java.io.File

/**
 * All app data lives under %APPDATA%/Kenang/ (MASTER_PROMPT_02 §App data).
 * NEVER write beside the EXE.
 */
object AppDirs {

    val root: File by lazy {
        val appData = System.getenv("APPDATA")
            ?: File(System.getProperty("user.home"), "AppData/Roaming").absolutePath
        File(appData, "Kenang").apply { mkdirs() }
    }

    val db: File get() = sub("db")
    val logs: File get() = sub("logs")
    val config: File get() = sub("config")
    val tools: File get() = sub("tools")
    val ffmpegDir: File get() = File(tools, "ffmpeg").apply { mkdirs() }
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
