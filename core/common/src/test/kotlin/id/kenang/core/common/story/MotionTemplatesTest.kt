package id.kenang.core.common.story

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MotionTemplatesTest {

    @Test
    fun `template prompt builds EN and ID consistently`() {
        val spec = MotionSpec(
            MotionCategory.SLIGHT_HEAD_TURN, CameraMove.SLOW_PUSH_IN,
            adjectives = "warm and gentle",
            subjectEn = "the elderly woman", subjectId = "Beliau",
        )
        assertEquals(
            "The elderly woman turns the head slightly, warm and gentle; gentle slow push-in.",
            MotionTemplates.buildPromptEn(spec),
        )
        assertEquals("Beliau menoleh pelan; kamera mendekat perlahan.", MotionTemplates.buildSummaryId(spec))
    }

    @Test
    fun `valid template prompt passes the validator`() {
        val prompt = MotionTemplates.buildPromptEn(MotionSpec(MotionCategory.SMILE, CameraMove.STATIC))
        assertIs<MotionTemplateValidator.Verdict.Valid>(MotionTemplateValidator.validatePromptEn(prompt))
    }

    @Test
    fun `forbidden free-form verb is rejected`() {
        // DoD: attempt one forbidden free-form verb and see it rejected.
        val verdict = MotionTemplateValidator.validatePromptEn("She starts dancing wildly across the room; fast zoom.")
        val invalid = assertIs<MotionTemplateValidator.Verdict.Invalid>(verdict)
        assertEquals("dancing", invalid.forbiddenWord)
    }

    @Test
    fun `free-form text without any template phrase is rejected`() {
        assertIs<MotionTemplateValidator.Verdict.Invalid>(
            MotionTemplateValidator.validatePromptEn("The scene shifts dramatically into chaos."),
        )
    }

    @Test
    fun `unknown LLM category is repaired to nearest allowed category`() {
        val spec = MotionTemplateValidator.resolveOrRepair("dancing", "fast_zoom")
        assertEquals(MotionCategory.WALK_SLOWLY, spec.category)
        assertEquals(CameraMove.SLOW_PUSH_IN, spec.camera) // unknown camera → safe default
        assertIs<MotionTemplateValidator.Verdict.Valid>(
            MotionTemplateValidator.validatePromptEn(MotionTemplates.buildPromptEn(spec)),
        )
    }

    @Test
    fun `completely unknown category falls back to smile`() {
        assertEquals(MotionCategory.SMILE, MotionTemplateValidator.resolveOrRepair("teleport", "static").category)
    }

    @Test
    fun `adjectives are sanitized - forbidden words dropped, capped at 8`() {
        val cleaned = MotionTemplateValidator.sanitizeAdjectives(
            "warm running gentle one two three four five six seven",
        )
        val words = cleaned.split(" ")
        assertTrue("running" !in words)
        assertTrue(words.size <= 8)
    }

    @Test
    fun `every category and camera produces a validator-passing prompt`() {
        MotionCategory.entries.forEach { cat ->
            CameraMove.entries.forEach { cam ->
                val prompt = MotionTemplates.buildPromptEn(MotionSpec(cat, cam))
                assertIs<MotionTemplateValidator.Verdict.Valid>(
                    MotionTemplateValidator.validatePromptEn(prompt),
                    "category=${cat.key} camera=${cam.key} → $prompt",
                )
            }
        }
    }
}
