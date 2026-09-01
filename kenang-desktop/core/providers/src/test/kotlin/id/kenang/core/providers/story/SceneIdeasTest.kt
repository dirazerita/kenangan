package id.kenang.core.providers.story

import id.kenang.core.common.story.MotionTemplateValidator
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneIdeasTest {

    @Test
    fun `pool is varied and keywords are self-matching`() {
        assertTrue(SceneIdeas.ALL.size >= 12, "pool too small: ${SceneIdeas.ALL.size}")
        val keywords = SceneIdeas.ALL.map { it.keyword }
        assertEquals(keywords.size, keywords.distinct().size, "duplicate keywords")
        // The dedup filter matches the keyword against prompts BUILT FROM the
        // idea itself — so each keyword must occur in its own activity text.
        SceneIdeas.ALL.forEach { idea ->
            assertTrue(
                idea.keyword in (idea.activityEn + " " + idea.descriptionId).lowercase(),
                "keyword '${idea.keyword}' not present in its own texts",
            )
        }
    }

    @Test
    fun `activities survive the motion detail sanitizer`() {
        SceneIdeas.ALL.forEach { idea ->
            val clean = MotionTemplateValidator.sanitizeDetail(idea.activityEn + ".")
            assertTrue(clean.isNotBlank(), "activity dropped by sanitizer: ${idea.activityEn}")
        }
    }

    @Test
    fun `pick avoids ideas already present in the storyboard`() {
        val used = SceneIdeas.ALL.first().let { (it.activityEn + " " + it.descriptionId).lowercase() }
        repeat(50) { seed ->
            val picked = SceneIdeas.pick(used, Random(seed))
            assertTrue(picked.keyword !in used, "picked an already-used idea: ${picked.keyword}")
        }
    }

    @Test
    fun `pick still returns an idea when everything is used`() {
        val allUsed = SceneIdeas.ALL.joinToString(" ") { it.activityEn + " " + it.descriptionId }.lowercase()
        // Better a repeated activity than a dead button.
        SceneIdeas.pick(allUsed, Random(1))
    }
}
