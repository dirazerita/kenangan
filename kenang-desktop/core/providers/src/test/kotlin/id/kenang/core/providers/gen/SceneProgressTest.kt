package id.kenang.core.providers.gen

import id.kenang.core.data.SettingsRepository
import id.kenang.core.db.DatabaseFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The generation screen's bars (owner 2026-09-16): fal reports no
 * percentage for a render, so a scene's fraction is time against the
 * running average of earlier clips, and it never claims done early.
 */
class SceneProgressTest {

    @Test
    fun `a render creeps with time and stops short of done`() {
        val p = GenerationOrchestrator.SceneProgress(GenerationOrchestrator.Phase.RENDERING, startedAt = 0L, estimateS = 200)
        assertEquals(0.05f, p.at(0L), 0.001f)
        assertTrue(p.at(100_000L) in 0.45f..0.55f, "half the estimate is about half the bar: ${p.at(100_000L)}")
        assertEquals(0.95f, p.at(400_000L), 0.001f, "twice the estimate still is not done")
        assertEquals(100, p.remainingS(100_000L))
        assertNull(p.remainingS(300_000L), "past the estimate there is no promise left")

        assertEquals(0f, p.copy(phase = GenerationOrchestrator.Phase.WAITING).at(999L))
        assertEquals(0.03f, p.copy(phase = GenerationOrchestrator.Phase.QUEUED).at(999L))
        assertEquals(0.97f, p.copy(phase = GenerationOrchestrator.Phase.DOWNLOADING).at(999L))
        assertEquals(1f, p.copy(phase = GenerationOrchestrator.Phase.DONE).at(999L))
    }

    @Test
    fun `estimates start at forty seconds per clip second and learn from real clips`() {
        val stats = RenderTimeStats(SettingsRepository(DatabaseFactory.createInMemory()))
        assertEquals(200, stats.estimateSeconds("fal-ai/kling-video/v3/pro/image-to-video", 5))
        assertEquals(400, stats.estimateSeconds("fal-ai/kling-video/v3/pro/image-to-video", 10))
        assertEquals(60, stats.estimateSeconds("x", 1), "never below a minute")

        stats.record("fal-ai/kling-video/v3/pro/image-to-video", 5, 300)
        assertEquals(300, stats.estimateSeconds("fal-ai/kling-video/v3/pro/image-to-video", 5))
        stats.record("fal-ai/kling-video/v3/pro/image-to-video", 5, 100)
        assertEquals(200, stats.estimateSeconds("fal-ai/kling-video/v3/pro/image-to-video", 5), "a running average")
        assertEquals(400, stats.estimateSeconds("fal-ai/kling-video/v3/pro/image-to-video", 10), "other lengths untouched")
        stats.record("fal-ai/kling-video/v3/pro/image-to-video", 5, 0)
        assertEquals(200, stats.estimateSeconds("fal-ai/kling-video/v3/pro/image-to-video", 5), "a zero is ignored")
    }
}
