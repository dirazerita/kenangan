package id.kenang.core.data.config

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Guards the LOCKED routing values from MEMORY §3 (D-005) in the bundled config.
 * If any of these fail, someone changed app-config.json against a locked decision.
 */
class ConfigRepositoryTest {

    private val config = ConfigRepository(userConfigFile = File("does-not-exist.json")).current()

    @Test
    fun `bundled config parses and default tier is standar`() {
        assertEquals("standar", config.tierRouting.defaultTier)
        assertTrue(config.tierRouting.tiers.getValue("standar").enabled)
    }

    @Test
    fun `hemat is locked disabled, resolving to standar`() {
        val hemat = config.tierRouting.tiers.getValue("hemat")
        assertFalse(hemat.enabled, "Hemat locked DISABLED at launch (D-009): Wan-flash spot-check verdict 3")
        assertFalse(hemat.provisional, "no longer provisional — decided by D-009")
        val resolved = config.tierRouting.resolve("hemat")
        assertEquals("fal-ai/kling-video/v3/standard/image-to-video", resolved.i2v)
    }

    @Test
    fun `kling tiers must carry generate_audio false`() {
        for (tier in listOf("standar", "premium")) {
            val cfg = config.tierRouting.tiers.getValue(tier)
            val params = assertNotNull(cfg.i2vParams, "$tier must define i2v_params")
            assertEquals("false", params["generate_audio"].toString(),
                "$tier: Kling audio default is ON upstream — generate_audio must be false (+50% cost otherwise)")
        }
    }

    @Test
    fun `tts is locked to MiniMax Calm_Woman with Indonesian boost`() {
        assertEquals("fal-ai/minimax/speech-02-hd", config.tts.slug)
        assertEquals("Calm_Woman", config.tts.voice)
        assertEquals("Indonesian", config.tts.languageBoost)
        assertEquals(500, config.tts.maxChars)
    }

    @Test
    fun `analysis is locked to openrouter router vision`() {
        assertEquals("openrouter/router/vision", config.analysis.slug)
        assertEquals("google/gemini-2.5-flash", config.analysis.model)
        // Direct-Gemini path id (Google retired 2.5-flash for new accounts).
        assertEquals("gemini-3.6-flash", config.analysis.resolvedGeminiModel())
    }

    @Test
    fun `all routed models have price hints`() {
        val hinted = config.priceHints.map { it.modelSlug }.toSet()
        val routed = config.tierRouting.tiers.values.flatMap { listOf(it.keyframe, it.i2v) } +
            config.tts.slug + config.analysis.slug
        routed.forEach { slug ->
            assertTrue(slug in hinted, "no price hint for routed model: $slug")
        }
    }

    @Test
    fun `limits match MEMORY section 5`() {
        assertEquals(15, config.limits.maxPhotos)
        assertEquals(12, config.limits.maxScenes)
        assertEquals(120, config.limits.maxTotalSeconds)
        assertEquals(4, config.limits.maxSubjectsFusion)
        assertEquals(500, config.limits.maxNarrationChars)
    }
}
