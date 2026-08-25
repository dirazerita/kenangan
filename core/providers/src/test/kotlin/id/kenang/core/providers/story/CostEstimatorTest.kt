package id.kenang.core.providers.story

import id.kenang.core.data.config.ConfigRepository
import id.kenang.core.db.Scene
import id.kenang.core.providers.PriceBook
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CostEstimatorTest {

    private val configRepository = ConfigRepository(userConfigFile = File("does-not-exist.json"))
    private val estimator = CostEstimator(configRepository, PriceBook(configRepository))

    private fun scene(id: String, durationS: Long, regens: Long) = Scene(
        scene_id = id, project_id = "p", source_photos_json = "[\"p1\"]", type = "single",
        vibe = "taman", keyframe_prompt_en = "", keyframe_url = null,
        motion_prompt_en = "", motion_summary_id = "", duration_s = durationS,
        regen_count = regens, status = "keyframe_ready", order_index = 0,
        local_keyframe_path = null, local_clip_path = null,
    )

    @Test
    fun `standar estimate matches hand-computed PriceBook values`() {
        // Hand-computed (MASTER_PROMPT_03 formula, config rates):
        //   i2v: (5+5+10)s × $0.084 = $1.68
        //   keyframe regens: 2 × $0.039 = $0.078
        //   total = $1.758 · Rp = × 16500 = Rp29,007
        val scenes = listOf(scene("a", 5, 0), scene("b", 5, 2), scene("c", 10, 0))
        val est = estimator.estimate(scenes, "standar")
        assertEquals(1.68, est.i2vUsd, 1e-9)
        assertEquals(0.078, est.keyframeUsd, 1e-9)
        assertEquals(1.758, est.usd, 1e-9)
        assertEquals(1.758 * 16500, est.idr, 1e-6)
        assertEquals(20L, est.totalDurationS)
        assertTrue(est.complete)
    }

    @Test
    fun `premium uses pro rates`() {
        // 10s × $0.112 + 1 regen × $0.15 = 1.12 + 0.15 = 1.27
        val est = estimator.estimate(listOf(scene("a", 10, 1)), "premium")
        assertEquals(1.27, est.usd, 1e-9)
    }

    @Test
    fun `disabled hemat resolves to standar rates (D-005 provisional)`() {
        val est = estimator.estimate(listOf(scene("a", 10, 0)), "hemat")
        assertEquals(10 * 0.084, est.usd, 1e-9)
    }
}
