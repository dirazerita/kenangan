package id.kenang.core.providers.gen

import id.kenang.core.common.DispatcherProvider
import id.kenang.core.data.GenJobRepository
import id.kenang.core.data.PhotoRepository
import id.kenang.core.data.ProjectRepository
import id.kenang.core.data.SceneRepository
import id.kenang.core.data.SceneStatus
import id.kenang.core.data.SettingsRepository
import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.data.story.RatioCropper
import id.kenang.core.db.DatabaseFactory
import id.kenang.core.db.KenangDb
import id.kenang.core.db.Scene
import id.kenang.core.providers.CostTracker
import id.kenang.core.providers.InMemoryStore
import id.kenang.core.providers.PriceBook
import id.kenang.core.providers.fal.FalKeyPool
import id.kenang.core.providers.fal.FalQueueClient
import id.kenang.core.providers.fal.FalStorage
import id.kenang.core.providers.optional.GeminiClient
import id.kenang.core.providers.story.AnalysisService
import id.kenang.core.providers.story.FaceLock
import id.kenang.core.providers.vault.FalKey
import id.kenang.core.providers.vault.KeyVault
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

private object UnconfinedDispatchers : DispatcherProvider {
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
}

/**
 * Owner 2026-09-15: "Buat Video" on a project whose scene 2 had just been
 * rendered on its own (status keyframe_ready, clip on disk) threw "illegal
 * scene transition keyframe_ready -> generating" and closed the whole app.
 * A run must take every scene as it finds it, and nothing a scene does may
 * reach the window.
 */
class GenerationRunResilienceTest {

    private val dir = File(System.getProperty("java.io.tmpdir"), "kenang-run-${System.nanoTime()}").apply { mkdirs() }
    private var calls = 0

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun orchestrator(db: KenangDb): GenerationOrchestrator {
        // Any network call is a test failure: everything here must resolve locally.
        val http = HttpClient(MockEngine { calls++; respond("{}", HttpStatusCode.InternalServerError) })
        val vault = KeyVault(InMemoryStore()).apply { saveFalKeys(listOf(FalKey("A", "k1"))) }
        val pool = FalKeyPool(vault)
        val falClient = FalQueueClient(http, pool)
        val storage = FalStorage(http, pool)
        val config = ConfigRepository(userConfigFile = File(dir, "does-not-exist.json"))
        val settings = SettingsRepository(db)
        val photos = PhotoRepository(db, UnconfinedDispatchers)
        val scenes = SceneRepository(db, UnconfinedDispatchers)
        val tracker = CostTracker(db, UnconfinedDispatchers)
        val analysis = AnalysisService(
            falClient, storage, config, tracker, GeminiClient(http), vault, photos, scenes, settings,
            object : RatioCropper { override fun cropToRatio(file: File, ratio: String) = Unit },
        )
        return GenerationOrchestrator(
            falClient, storage, ClipDownloader(http), config, PriceBook(config), tracker,
            scenes, GenJobRepository(db, UnconfinedDispatchers), ProjectRepository(db, UnconfinedDispatchers),
            settings, FaceLock(photos, analysis, storage, settings),
        )
    }

    private fun scene(id: String, projectId: String, status: String, index: Long, clip: File?) = Scene(
        scene_id = id, project_id = projectId, source_photos_json = "[]", type = "single",
        vibe = "asli", keyframe_prompt_en = "", keyframe_url = null,
        motion_prompt_en = "they smile", motion_summary_id = "", duration_s = 5,
        regen_count = 0, status = status, order_index = index,
        negative_prompt = null, user_description = null,
        local_keyframe_path = null, local_clip_path = clip?.absolutePath,
    )

    private fun clip(name: String): File = File(dir, name).apply { writeBytes(ByteArray(64) { 1 }) }

    @Test
    fun `a scene left keyframe_ready with its clip is reused, not thrown at`() = runTest {
        val db = DatabaseFactory.createInMemory()
        val projects = ProjectRepository(db, UnconfinedDispatchers)
        val scenes = SceneRepository(db, UnconfinedDispatchers)
        val projectId = projects.create("Rahayu", "16:9", "asli", "standar")
        scenes.upsert(scene("s1", projectId, SceneStatus.CONFIRMED, 0, clip("s1.mp4")))
        scenes.upsert(scene("s2", projectId, SceneStatus.KEYFRAME_READY, 1, clip("s2.mp4")))

        val outcome = orchestrator(db).run(projectId, "standar")

        assertEquals(2, outcome.doneScenes, "both clips are reused for free")
        assertEquals(0, outcome.failedScenes)
        assertEquals(listOf(SceneStatus.DONE, SceneStatus.DONE), scenes.scenes(projectId).sortedBy { it.order_index }.map { it.status })
        assertEquals(0, calls, "nothing was submitted")
    }

    @Test
    fun `a scene without an image is counted as failed and the others still finish`() = runTest {
        val db = DatabaseFactory.createInMemory()
        val projects = ProjectRepository(db, UnconfinedDispatchers)
        val scenes = SceneRepository(db, UnconfinedDispatchers)
        val projectId = projects.create("Rahayu", "16:9", "asli", "standar")
        scenes.upsert(scene("s1", projectId, SceneStatus.DRAFT, 0, null))
        scenes.upsert(scene("s2", projectId, SceneStatus.KEYFRAME_READY, 1, clip("s2.mp4")))

        val o = orchestrator(db)
        val outcome = o.run(projectId, "standar")

        assertEquals(1, outcome.doneScenes)
        assertEquals(1, outcome.failedScenes, "the image-less scene counts as failed, so the screen does not move on")
        assertEquals(SceneStatus.DRAFT, scenes.scenes(projectId).first { it.scene_id == "s1" }.status, "no illegal move was forced")
        assertEquals("gambar adegan belum ada (status draft)", o.errorDetail("s1"))
        assertEquals(0, calls)
    }
}
