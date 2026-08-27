package id.kenang.core.providers.story

import id.kenang.core.data.config.Vibe
import kotlin.test.Test
import kotlin.test.assertTrue

class KeyframePromptsTest {

    private val taman = Vibe("taman", "Taman", "Suasana taman", "a lush tropical garden with soft greenery and blooming flowers")
    private val asli = Vibe("asli", "Suasana asli", "", "")

    @Test
    fun `fusion prompt MUST carry the exactly-N-people clause (D-003)`() {
        val prompt = KeyframePrompts.build(taman, "9:16", isFusion = true, subjectCount = 2)
        assertTrue("Exactly 2 people, no additional people" in prompt, prompt)
    }

    @Test
    fun `single prompt carries preservation and ratio clauses`() {
        val prompt = KeyframePrompts.build(taman, "16:9", isFusion = false, subjectCount = 1)
        assertTrue("Preserve faces, age, body, and clothing exactly" in prompt)
        assertTrue("16:9 landscape" in prompt)
        assertTrue("lush tropical garden" in prompt)
    }

    @Test
    fun `activity hint LEADS the prompt and frees the composition`() {
        val prompt = KeyframePrompts.build(
            taman, "9:16", isFusion = false, subjectCount = 1,
            keyframeHint = "They play on a wooden swing together, laughing.",
        )
        // Activity-first: the scene description comes before the preservation
        // clause, and composition copying is explicitly forbidden (dogfood
        // 2026-08-27: trailing hints produced 12 near-identical keyframes).
        assertTrue(prompt.indexOf("wooden swing") < prompt.indexOf("Preserve each person"), prompt)
        assertTrue("do NOT copy the original photo's composition" in prompt, prompt)
        assertTrue("face, age, and clothing exactly" in prompt, prompt)
    }

    @Test
    fun `fusion with activity hint keeps the exactly-N clause`() {
        val prompt = KeyframePrompts.build(
            taman, "9:16", isFusion = true, subjectCount = 3,
            keyframeHint = "They stroll along a flower path.",
        )
        assertTrue("Exactly 3 people, no additional people" in prompt, prompt)
    }

    @Test
    fun `asli vibe keeps the original setting (restoration-lite)`() {
        val prompt = KeyframePrompts.build(asli, "9:16", isFusion = false, subjectCount = 1)
        assertTrue("keeping the original setting" in prompt)
    }
}
