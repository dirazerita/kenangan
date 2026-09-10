package id.kenang.core.data

import id.kenang.core.common.AppError
import id.kenang.core.common.AppResult
import id.kenang.core.common.DispatcherProvider
import id.kenang.core.common.err
import id.kenang.core.common.ok
import id.kenang.core.db.KenangDb
import io.github.aakira.napier.Napier
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Moves the heavy app data (projects, staged tools, music, caches) to a folder
 * the user picks - usually on another drive (owner 2026-09-10: Kenang had
 * grown to 7 GB on C:). The database, logs and config stay in %APPDATA%.
 *
 * Safety over speed: everything is COPIED and verified first, the stored
 * absolute paths are rewritten in one transaction, and only then are the
 * originals deleted. A failure at any point leaves the old folder complete and
 * untouched - the user simply keeps working where they were.
 */
class DataFolderMover(
    private val db: KenangDb,
    private val settings: SettingsRepository,
    private val dispatchers: DispatcherProvider,
) {

    /** What a move would involve, for the confirmation dialog. */
    data class Plan(
        val from: File,
        val to: File,
        val bytes: Long,
        val files: Int,
        val freeAtTarget: Long,
    ) {
        val fits: Boolean get() = freeAtTarget > bytes + SAFETY_MARGIN
    }

    /** Progress of a running move: 0-100 and the folder being copied. */
    fun interface Progress {
        fun report(percent: Int, detail: String)
    }

    /**
     * Resolves what [chosen] means and measures the work. A folder that is not
     * already a Kenang data folder gets a "Kenang" subfolder, so picking a
     * drive root does not scatter our subfolders across it.
     */
    suspend fun plan(chosen: File): AppResult<Plan> = withContext(dispatchers.io) {
        val from = AppDirs.mediaRoot
        val to = resolveTarget(chosen)

        when {
            !runCatching { to.mkdirs(); to.isDirectory }.getOrDefault(false) ->
                return@withContext AppError.Unknown("folder tujuan tidak bisa dibuat").err()
            !to.canWrite() ->
                return@withContext AppError.Unknown("folder tujuan tidak bisa ditulisi").err()
            to.canonicalFile == from.canonicalFile ->
                return@withContext AppError.Unknown("folder tujuan sama dengan folder sekarang").err()
            to.canonicalPath.startsWith(from.canonicalPath + File.separator) ->
                return@withContext AppError.Unknown("folder tujuan ada di dalam folder data sekarang").err()
        }

        var bytes = 0L
        var files = 0
        movableDirs(from).forEach { dir ->
            dir.walkTopDown().forEach { f -> if (f.isFile) { bytes += f.length(); files++ } }
        }
        Plan(from, to, bytes, files, to.usableSpace).ok()
    }

    /**
     * Executes [plan] and returns the new data folder. The setting and the live
     * [AppDirs] override are updated here, so the running app keeps working
     * without a restart.
     */
    suspend fun move(plan: Plan, onProgress: Progress = Progress { _, _ -> }): AppResult<File> =
        withContext(dispatchers.io) {
            if (busyGenerating()) {
                return@withContext AppError.Unknown("ada proses pembuatan video yang sedang berjalan").err()
            }
            if (!plan.fits) {
                return@withContext AppError.Unknown("ruang di drive tujuan tidak cukup").err()
            }

            val sources = movableDirs(plan.from)
            val copied = mutableListOf<File>()
            var doneBytes = 0L

            // ---- 1. copy everything, verifying each file ----
            for (dir in sources) {
                val targetDir = File(plan.to, dir.name)
                onProgress.report(percent(doneBytes, plan.bytes), dir.name)
                val result = runCatching {
                    dir.walkTopDown().forEach { src ->
                        val relative = src.toRelativeString(dir)
                        val dest = if (relative.isEmpty()) targetDir else File(targetDir, relative)
                        if (src.isDirectory) {
                            dest.mkdirs()
                        } else {
                            dest.parentFile?.mkdirs()
                            Files.copy(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
                            check(dest.length() == src.length()) { "ukuran tidak cocok: " + src.name }
                            doneBytes += src.length()
                            onProgress.report(percent(doneBytes, plan.bytes), dir.name)
                        }
                    }
                }
                if (result.isFailure) {
                    val why = result.exceptionOrNull()
                    Napier.e("data move failed while copying ${dir.name}: ${why?.message}")
                    copied.forEach { it.deleteRecursively() }
                    targetDir.deleteRecursively()
                    return@withContext AppError.Unknown(
                        "penyalinan gagal (${why?.message}) - data lama tidak diubah", why,
                    ).err()
                }
                copied += targetDir
            }

            // ---- 2. rewrite the stored absolute paths ----
            val oldPrefix = plan.from.absolutePath
            val newPrefix = plan.to.absolutePath
            val rebased = runCatching {
                db.kenangQueries.transaction {
                    db.kenangQueries.rebaseScenePaths(oldPrefix, newPrefix, oldPrefix, newPrefix)
                    db.kenangQueries.rebasePhotoPaths(oldPrefix, newPrefix)
                    db.kenangQueries.rebaseProjectMusicPaths(oldPrefix, newPrefix)
                }
            }
            if (rebased.isFailure) {
                val why = rebased.exceptionOrNull()
                Napier.e("data move failed while rewriting paths: ${why?.message}")
                copied.forEach { it.deleteRecursively() }
                return@withContext AppError.Unknown(
                    "pembaruan lokasi file di database gagal - data lama tidak diubah", why,
                ).err()
            }

            // ---- 3. only now let go of the originals ----
            settings.dataFolder = newPrefix
            AppDirs.useMediaRoot(plan.to)
            onProgress.report(100, "")
            sources.forEach { dir ->
                if (!dir.deleteRecursively()) {
                    // Files held open by another program stay behind; the app is
                    // already running from the new folder, so this is cosmetic.
                    Napier.w("could not delete old folder ${dir.absolutePath}")
                }
            }
            Napier.i("data folder moved: $oldPrefix -> $newPrefix")
            plan.to.ok()
        }

    /** Total size of the current data folder, for the settings screen. */
    suspend fun currentSizeBytes(): Long = withContext(dispatchers.io) {
        movableDirs(AppDirs.mediaRoot).sumOf { dir ->
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }
    }

    private fun movableDirs(root: File): List<File> =
        AppDirs.MOVABLE.map { File(root, it) }.filter { it.isDirectory }

    private fun busyGenerating(): Boolean = runCatching {
        db.kenangQueries.countScenesByStatus(SceneStatus.GENERATING).executeAsOne() > 0
    }.getOrDefault(false)

    private fun percent(done: Long, total: Long): Int =
        if (total <= 0) 0 else ((done * 100) / total).toInt().coerceIn(0, 99)

    companion object {
        /** Leave the target drive room to breathe after the move. */
        const val SAFETY_MARGIN = 500L * 1024 * 1024

        /**
         * A plain folder gains a "Kenang" subfolder; a folder already used as a
         * Kenang data folder is taken as-is.
         */
        fun resolveTarget(chosen: File): File = when {
            chosen.name.equals("Kenang", ignoreCase = true) -> chosen
            AppDirs.MOVABLE.any { File(chosen, it).isDirectory } -> chosen
            else -> File(chosen, "Kenang")
        }
    }
}
