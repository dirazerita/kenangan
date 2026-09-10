package id.kenang.core.data

import id.kenang.core.common.AppResult
import id.kenang.core.common.DefaultDispatcherProvider
import id.kenang.core.db.DatabaseFactory
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The data folder can live on another drive (owner 2026-09-10: 7 GB on C:).
 * Every stored media path is absolute, so the move is only correct if the
 * database is rewritten with it — otherwise every project opens to missing
 * photos and clips.
 */
class DataFolderMoverTest {

    private val dispatchers = DefaultDispatcherProvider()
    private val temp = File(System.getProperty("java.io.tmpdir"), "kenang-move-${System.nanoTime()}")

    @AfterTest
    fun cleanUp() {
        AppDirs.useMediaRoot(null)
        temp.deleteRecursively()
    }

    private fun scenario(): Triple<DataFolderMover, id.kenang.core.db.KenangDb, File> {
        val oldRoot = File(temp, "old").apply { mkdirs() }
        AppDirs.useMediaRoot(oldRoot)

        val clip = File(AppDirs.projectClips("p1"), "sc0.mp4").apply { writeBytes(ByteArray(2048) { 7 }) }
        val photo = File(AppDirs.projectPhotos("p1"), "foto.jpg").apply { writeBytes(ByteArray(1024) { 3 }) }
        File(AppDirs.music, "lagu.mp3").writeBytes(ByteArray(512) { 9 })

        val db = DatabaseFactory.create(File(temp, "kenang.db"))
        val settings = SettingsRepository(db)
        val now = System.currentTimeMillis()
        db.kenangQueries.insertProject(
            "p1", "t", "9:16", "taman", "standar", null, null, "storyboard", 5L, now, now,
        )
        db.kenangQueries.upsertScene(
            "sc0", "p1", "[]", "single", "taman", null, null, null, null,
            5L, 0L, SceneStatus.DONE, 0L, null, null, null, clip.absolutePath,
        )
        db.kenangQueries.insertPhoto("ph1", "p1", photo.absolutePath, null, null)
        return Triple(DataFolderMover(db, settings, dispatchers), db, oldRoot)
    }

    @Test
    fun `moves the files and rewrites every stored path`() = runBlocking {
        val (mover, db, oldRoot) = scenario()
        val target = File(temp, "newdrive").apply { mkdirs() }

        val plan = mover.plan(target)
        assertTrue(plan is AppResult.Ok, "plan failed: $plan")
        val p = (plan as AppResult.Ok).value
        assertEquals(File(target, "Kenang").absolutePath, p.to.absolutePath, "picked folder gains a Kenang subfolder")
        assertEquals(3, p.files)

        val moved = mover.move(p)
        assertTrue(moved is AppResult.Ok, "move failed: $moved")
        val newRoot = (moved as AppResult.Ok).value

        // Files arrived, with their content.
        val newClip = File(newRoot, "projects/p1/clips/sc0.mp4")
        assertTrue(newClip.isFile, "clip missing at the new location")
        assertEquals(2048, newClip.length())
        assertTrue(File(newRoot, "projects/p1/photos/foto.jpg").isFile)
        assertTrue(File(newRoot, "music/lagu.mp3").isFile)

        // The database points at them — the part that makes the move usable.
        val scene = db.kenangQueries.selectSceneById("sc0").executeAsOne()
        assertEquals(newClip.absolutePath, scene.local_clip_path)
        val storedPhoto = db.kenangQueries.selectPhotosByProject("p1").executeAsOne().local_path
        assertTrue(storedPhoto.startsWith(newRoot.absolutePath), "photo path not rebased: $storedPhoto")

        // And the app now resolves everything under the new root.
        assertEquals(newRoot.absolutePath, AppDirs.mediaRoot.absolutePath)
        assertTrue(!File(oldRoot, "projects").exists(), "old folder left behind")
    }

    /** A target inside the current data folder would copy into itself forever. */
    @Test
    fun `refuses a target inside the current data folder`() = runBlocking {
        val (mover, _, oldRoot) = scenario()
        val result = mover.plan(File(oldRoot, "projects"))
        assertTrue(result is AppResult.Err, "a nested target must be rejected")
    }

    /** Nothing may be deleted when the copy cannot complete. */
    @Test
    fun `keeps the old data when there is not enough room`() = runBlocking {
        val (mover, db, oldRoot) = scenario()
        val plan = (mover.plan(File(temp, "small")) as AppResult.Ok).value
        val impossible = plan.copy(bytes = Long.MAX_VALUE / 2)

        val result = mover.move(impossible)

        assertTrue(result is AppResult.Err, "a move that cannot fit must fail")
        assertTrue(File(oldRoot, "projects/p1/clips/sc0.mp4").isFile, "old clip was lost")
        val scene = db.kenangQueries.selectSceneById("sc0").executeAsOne()
        assertTrue(scene.local_clip_path!!.startsWith(oldRoot.absolutePath), "paths were rewritten on a failed move")
    }
}
