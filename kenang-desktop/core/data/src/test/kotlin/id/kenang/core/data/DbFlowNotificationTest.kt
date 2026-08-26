package id.kenang.core.data

import id.kenang.core.common.DefaultDispatcherProvider
import id.kenang.core.db.DatabaseFactory
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.test.Test
import kotlin.test.fail

/**
 * Regression for the "storyboard/generation screens never update" dogfood bug:
 * every screen is driven by SQLDelight asFlow() listeners, so a driver that
 * stops delivering change notifications freezes the whole UI while background
 * work completes fine (owner waited ~1h for keyframes that finished in 70s).
 */
class DbFlowNotificationTest {

    @Test
    fun `scene flow emits after a write from another coroutine`() = runBlocking {
        val dbFile = File(System.getProperty("java.io.tmpdir"), "kenang-flow-${System.nanoTime()}.db")
        try {
            val db = DatabaseFactory.create(dbFile)
            val dispatchers = DefaultDispatcherProvider()
            val scenes = SceneRepository(db, dispatchers)
            val now = System.currentTimeMillis()
            db.kenangQueries.insertProject(
                "p1", "t", "9:16", "taman", "standar", null, null, "storyboard", 5L, now, now,
            )
            db.kenangQueries.upsertScene(
                "sc0", "p1", "[]", "single", "taman", null, null, null, null,
                5L, 0L, SceneStatus.DRAFT, 0L, null, null,
            )

            // Collector waiting for the scene to become ready (what the UI does).
            val waiter = launch(dispatchers.io) {
                scenes.observeScenes("p1").first { list ->
                    list.any { it.status == SceneStatus.KEYFRAME_READY }
                }
            }
            // Give the collector time to register its listener, then write.
            kotlinx.coroutines.delay(500)
            db.kenangQueries.transaction {
                db.kenangQueries.updateSceneKeyframe(
                    SceneStatus.KEYFRAME_READY, "https://x/kf.jpg", "C:/kf.jpg", 0L, "sc0",
                )
            }

            try {
                withTimeout(5_000) { waiter.join() }
            } catch (e: TimeoutCancellationException) {
                waiter.cancel()
                fail("asFlow() never saw the write — DB change notifications are broken")
            }
        } finally {
            dbFile.delete()
            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()
        }
    }
}
