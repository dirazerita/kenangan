package id.kenang.core.providers.story

import id.kenang.core.common.story.CameraMove
import id.kenang.core.common.story.MotionCategory
import id.kenang.core.common.story.MotionTemplateValidator
import id.kenang.core.common.story.MotionTemplates
import id.kenang.core.data.story.ScenePlanItem
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Story-plan fixture (recorded shape): even when the LLM returns a forbidden
 * free-form motion ("dancing wildly") and an unknown camera ("crash_zoom"),
 * the app-code validator repairs it into a template-conforming prompt.
 */
class StoryPlanParsingTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun fixture(): List<ScenePlanItem> {
        val raw = requireNotNull(javaClass.getResourceAsStream("/fixtures/story_plan.json"))
            .bufferedReader().readText()
        return json.decodeFromString(raw)
    }

    @Test
    fun `fixture parses with fusion and single scenes`() {
        val plan = fixture()
        assertEquals(3, plan.size)
        assertEquals("fusion", plan[1].type)
        assertEquals(listOf("p_aaa", "p_bbb"), plan[1].sourcePhotos)
    }

    @Test
    fun `valid categories resolve unchanged`() {
        val spec = MotionTemplateValidator.resolveOrRepair(fixture()[0].motionCategory, fixture()[0].camera, fixture()[0].adjectives)
        assertEquals(MotionCategory.SMILE, spec.category)
        assertEquals(CameraMove.SLOW_PUSH_IN, spec.camera)
    }

    @Test
    fun `forbidden LLM motion is repaired into a valid template prompt`() {
        val bad = fixture()[2]
        assertEquals("dancing wildly", bad.motionCategory)
        val spec = MotionTemplateValidator.resolveOrRepair(bad.motionCategory, bad.camera, bad.adjectives)
        assertEquals(MotionCategory.WALK_SLOWLY, spec.category) // dance → walk_slowly
        assertEquals(CameraMove.SLOW_PUSH_IN, spec.camera)      // crash_zoom → safe default
        assertTrue("spinning" !in spec.adjectives.split(" "))   // forbidden adjective dropped
        val prompt = MotionTemplates.buildPromptEn(spec)
        assertIs<MotionTemplateValidator.Verdict.Valid>(MotionTemplateValidator.validatePromptEn(prompt))
    }
}
