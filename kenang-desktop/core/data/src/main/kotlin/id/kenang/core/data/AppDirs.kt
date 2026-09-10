package id.kenang.core.data

import java.io.File

/**
 * App data lives under %APPDATA%/Kenang/ (MASTER_PROMPT_02 §App data).
 * NEVER write beside the EXE.
 *
 * The HEAVY part of that — projects, staged tools, music and caches — can be
 * relocated to another drive (owner 2026-09-10: 7 GB piling up on C:). The
 * database, logs and config always stay in %APPDATA%: they are tiny, and the
 * app must be able to start and read its own settings before it can know
 * where the user put everything else.
 */
object AppDirs {

    val root: File by lazy {
        val appData = System.getenv("APPDATA")
            ?: File(System.getProperty("user.home"), "AppData/Roaming").absolutePath
        File(appData, "Kenang").apply { mkdirs() }
    }

    @Volatile
    private var mediaOverride: File? = null

    /** Where the heavy folders live; [root] until a data folder is chosen. */
    val mediaRoot: File get() = mediaOverride ?: root

    /**
     * Points the heavy folders at [dir] (null/unusable → back to [root]).
     * Called once at startup from the saved setting, and again right after a
     * successful move.
     */
    fun useMediaRoot(dir: File?) {
        mediaOverride = dir?.takeIf { runCatching { it.mkdirs(); it.isDirectory }.getOrDefault(false) }
    }

    /** Subfolders that a data-folder move relocates, in [mediaRoot]. */
    val MOVABLE = listOf("projects", "tools", "music", "cache", "motion", "upscale")

    val db: File get() = sub("db")
    val logs: File get() = sub("logs")
    val config: File get() = sub("config")
    val tools: File get() = mediaSub("tools")
    val ffmpegDir: File get() = File(tools, "ffmpeg").apply { mkdirs() }
    val projects: File get() = mediaSub("projects")
    val music: File get() = mediaSub("music")
    val cache: File get() = mediaSub("cache")
    val motion: File get() = mediaSub("motion")
    val upscale: File get() = mediaSub("upscale")

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

    private fun mediaSub(name: String): File = File(mediaRoot, name).apply { mkdirs() }
}
