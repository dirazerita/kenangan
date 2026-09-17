package id.kenang.core.providers.upscale

import kotlin.test.Test
import kotlin.test.assertTrue

/** The stage-1 wording behind both 2026-09-17 reports: a doubled photo and a vanished bench. */
class UpscalePromptTest {

    @Test
    fun `a prepared canvas asks only for the bands to be filled, once`() {
        val p = UpscaleService.restorePrompt("9:16", prepared = true)
        assertTrue("flat grey bands" in p && "exactly ONCE" in p && "never mirrored, repeated or re-staged" in p, p)
        assertTrue("Recompose the result onto" !in p, "the old self-reframe wording must not ride along")
        assertTrue("PRESERVE THE SCENE" in p && "tables, benches" in p, "furniture survives restoration")
    }

    @Test
    fun `without a canvas the old reframe wording still applies, and no ratio means none`() {
        val old = UpscaleService.restorePrompt("16:9", prepared = false)
        assertTrue("Recompose the result onto a 16:9" in old, old)
        val plain = UpscaleService.restorePrompt(null, prepared = false)
        assertTrue("Recompose" !in plain && "grey bands" !in plain && "PRESERVE THE SCENE" in plain, plain)
    }
}
