package id.kenang.core.data

import id.kenang.core.common.DefaultDispatcherProvider
import id.kenang.core.db.DatabaseFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression for the Phase-04 orchestrator crash: 3+ coroutines writing scene
 * transitions on IO threads against the FILE-based driver (ThreadLocal JDBC
 * connections) while another coroutine polls reads. Without busy_timeout this
 * dies with SQLITE_BUSY.
 */
class DbConcurrencyTest {

    @Test
    fun `parallel scene transitions with a concurrent reader survive`() = runBlocking {
        val dbFile = File(System.getProperty("java.io.tmpdir"), "kenang-conc-${System.nanoTime()}.db")
        try {
            val db = DatabaseFactory.create(dbFile)
            val dispatchers = DefaultDispatcherProvider()
            val scenes = SceneRepository(db, dispatchers)
            val jobs = GenJobRepository(db, dispatchers)
            val now = System.currentTimeMillis()
            db.kenangQueries.insertProject(
                "p1", "t", "9:16", "taman", "standar", null, null, "storyboard", 5L, now, now,
            )
            repeat(6) { i ->
                db.kenangQueries.upsertScene(
                    "sc$i", "p1", "[]", "single", "taman", null, null, null, null,
                    5L, 0L, SceneStatus.CONFIRMED, i.toLong(), null, null,
                )
            }

            coroutineScope {
                val reader = launch(dispatchers.io) {
                    repeat(200) { jobs.jobsByProject("p1"); scenes.scenes("p1") }
                }
                (0 until 6).map { i ->
                    async(dispatchers.io) {
                        scenes.transition("sc$i", SceneStatus.GENERATING)
                        repeat(20) { n ->
                            jobs.create("sc$i", "model", 0.42)
                        }
                        scenes.transition("sc$i", SceneStatus.DONE)
                    }
                }.awaitAll()
                reader.join()
            }
            assertEquals(6, scenes.scenes("p1").count { it.status == SceneStatus.DONE })
        } finally {
            dbFile.delete()
            File(dbFile.absolutePath + "-wal").delete()
            File(dbFile.absolutePath + "-shm").delete()
        }
    }
}
