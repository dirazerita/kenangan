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

    @Test
    fun `every prompt carries the anti-twin clause (owner 2026-09-01)`() {
        // Composition-freed prompts cloned a single subject into twins; the
        // no-duplication guard must survive on every variant.
        listOf(
            KeyframePrompts.build(taman, "9:16", isFusion = false, subjectCount = 1),
            KeyframePrompts.build(taman, "9:16", isFusion = true, subjectCount = 2),
            KeyframePrompts.build(asli, "16:9", isFusion = false, subjectCount = 1),
            KeyframePrompts.build(
                taman, "9:16", isFusion = false, subjectCount = 1,
                keyframeHint = "They play on a wooden swing together, laughing.",
            ),
        ).forEach { prompt ->
            assertTrue("no twins" in prompt, prompt)
            assertTrue("Never duplicate or clone any person" in prompt, prompt)
        }
    }

    @Test
    fun `non-fusion prompt with known count pins the exact person count`() {
        val one = KeyframePrompts.build(
            taman, "9:16", isFusion = false, subjectCount = 1,
            keyframeHint = "He waves cheerfully near a fountain.", exactSubjects = 1,
        )
        assertTrue("exactly 1 person" in one, one)
        val five = KeyframePrompts.build(
            taman, "9:16", isFusion = false, subjectCount = 4,
            keyframeHint = "The family strolls along a path.", exactSubjects = 5,
        )
        // Uncapped: a 5-person group photo says 5 even though fusion caps at 4.
        assertTrue("exactly 5 people" in five, five)
    }

    /**
     * Owner 2026-09-15 (Rahayu RO 1): a family of six re-staged into new
     * compositions came back as strangers; the one scene the model treated
     * as an edit of the photo kept every face. Three or more people are an
     * EDIT of the photo now — the lock leads and the hint is the surroundings;
     * one or two keep the freed composition (D-055).
     */
    @Test
    fun `a group of three or more is anchored to the photo's composition`() {
        val six = KeyframePrompts.build(
            asli, "16:9", isFusion = false, subjectCount = 4,
            keyframeHint = "Medium shot of the family of six sharing kue on a low wooden table.",
            exactSubjects = 6,
        )
        assertTrue(six.startsWith("GROUP LOCK"), six)
        assertTrue("Keep all 6 people EXACTLY" in six, six)
        assertTrue("sharing kue on a low wooden table" in six, six)
        assertTrue("exactly 6 people" in six, six)
        assertTrue("nobody removed" in six, six)
        assertTrue("do NOT copy the original photo's composition" !in six, six)
        assertTrue("no twins" in six && "16:9 landscape" in six, six)

        val two = KeyframePrompts.build(
            asli, "16:9", isFusion = false, subjectCount = 2,
            keyframeHint = "They share tea on the veranda.", exactSubjects = 2,
        )
        assertTrue("do NOT copy the original photo's composition" in two, two)
        assertTrue("GROUP LOCK" !in two, two)

        // Fusion scenes combine people from different photos: never anchored.
        val fusion = KeyframePrompts.build(
            taman, "9:16", isFusion = true, subjectCount = 3,
            keyframeHint = "They stroll along a flower path.", exactSubjects = 3,
        )
        assertTrue("GROUP LOCK" !in fusion, fusion)

        // A vibe becomes the setting of the surroundings, and restoration still leads.
        val garden = KeyframePrompts.build(
            taman, "9:16", isFusion = false, subjectCount = 3,
            keyframeHint = "The three of them on a bench.", exactSubjects = 3, restore = true,
        )
        assertTrue(garden.startsWith("First fully restore the old photograph"), garden)
        assertTrue("set in a lush tropical garden" in garden, garden)
    }

    @Test
    fun `anchorGroupComposition rebuilds a stored prompt as an edit of the photo`() {
        // The exact text stored for Rahayu RO 1 scene 2 (2026-09-15).
        val stored = "First fully restore the old photograph: repair scratches, tears, stains and creases, " +
            "remove noise and grain, correct color fading and color cast, recover natural skin tones, and " +
            "sharpen softly. Create a new photorealistic scene of the exact same 6 people, keeping the " +
            "original photo's era and setting style: Medium shot of the family of six sharing kue on a low " +
            "wooden table. The scene contains exactly 6 people — count them before finalizing: exactly 6, " +
            "the same individuals as the source photo, nobody added, nobody repeated, no extra " +
            "similar-looking person in the background." + KeyframePrompts.FREE_COMPOSITION_CLAUSE +
            KeyframePrompts.NO_DUPLICATE_CLAUSE + " Photorealistic, warm natural light, 16:9 landscape."

        val anchored = KeyframePrompts.anchorGroupComposition(stored, 6)
        assertTrue(anchored.startsWith("First fully restore the old photograph"), anchored)
        assertTrue("GROUP LOCK — EDIT this photograph (image 1) of exactly 6 people" in anchored, anchored)
        assertTrue("\"Medium shot of the family of six sharing kue on a low wooden table\"" in anchored, anchored)
        assertTrue(KeyframePrompts.FREE_COMPOSITION_CLAUSE !in anchored, anchored)
        assertTrue("Create a new photorealistic scene" !in anchored, anchored)
        assertTrue("no twins" in anchored && anchored.endsWith("16:9 landscape."), anchored)

        // A vibe prompt keeps its setting; the focus clause of a reference scene survives.
        val garden = KeyframePrompts.build(
            taman, "9:16", isFusion = false, subjectCount = 2,
            keyframeHint = "They stroll along a flower path.", exactSubjects = 2, focusMainOnly = true,
        )
        val gardenAnchored = KeyframePrompts.anchorGroupComposition(garden, 4)
        assertTrue("set in a lush tropical garden" in gardenAnchored, gardenAnchored)
        assertTrue("must be OMITTED" in gardenAnchored, gardenAnchored)
        assertTrue("9:16 portrait" in gardenAnchored, gardenAnchored)

        assertTrue(KeyframePrompts.anchorGroupComposition(stored, 2) == stored, "two people stay free")
        assertTrue(KeyframePrompts.anchorGroupComposition(stored, null) == stored, "unknown count changes nothing")
        assertTrue(KeyframePrompts.anchorGroupComposition(anchored, 6) == anchored, "already anchored is left alone")
        assertTrue(KeyframePrompts.anchorGroupComposition("", 6) == "")

        // A prompt of an unknown shape still gets the lock as a clause.
        val odd = "Something hand-written." + KeyframePrompts.FREE_COMPOSITION_CLAUSE
        val oddAnchored = KeyframePrompts.anchorGroupComposition(odd, 3)
        assertTrue("GROUP LOCK: this is an EDIT of image 1" in oddAnchored, oddAnchored)
        assertTrue(oddAnchored.startsWith("Something hand-written."), oddAnchored)
    }

    @Test
    fun `reference-photo scenes omit cut-off faces instead of inventing them`() {
        val prompt = KeyframePrompts.build(
            taman, "16:9", isFusion = false, subjectCount = 1,
            keyframeHint = "Beliau tersenyum tenang di kursi roda.",
            focusMainOnly = true,
        )
        assertTrue("must be OMITTED" in prompt, prompt)
        assertTrue("never invent, reconstruct or guess a face" in prompt, prompt)
        // Owner 2026-09-03: group-worded activities must not conjure companions.
        assertTrue("Do NOT add any companion" in prompt, prompt)
        assertTrue("doing it alone" in prompt, prompt)
        // Normal scenes stay unchanged.
        val normal = KeyframePrompts.build(taman, "16:9", isFusion = false, subjectCount = 1)
        assertTrue("must be OMITTED" !in normal, normal)
    }

    @Test
    fun `negative clause is a strict ban list and empty input adds nothing`() {
        // Owner 2026-09-06: unwanted new people/objects — the user's negative
        // prompt must ride every keyframe submit as an explicit prohibition.
        val clause = KeyframePrompts.negativeClause("orang asing di latar belakang, teks, watermark")
        assertTrue("STRICTLY FORBIDDEN" in clause, clause)
        assertTrue("orang asing di latar belakang" in clause, clause)
        kotlin.test.assertEquals("", KeyframePrompts.negativeClause(null))
        kotlin.test.assertEquals("", KeyframePrompts.negativeClause("   "))
    }

    @Test
    fun `ensureNoDuplicateGuard retrofits old prompts exactly once`() {
        val old = "Create a new photorealistic scene of the exact same people in a garden."
        val patched = KeyframePrompts.ensureNoDuplicateGuard(old)
        assertTrue("no twins" in patched, patched)
        // Idempotent: patching again (or a freshly built prompt) adds nothing.
        kotlin.test.assertEquals(patched, KeyframePrompts.ensureNoDuplicateGuard(patched))
        val fresh = KeyframePrompts.build(taman, "9:16", isFusion = false, subjectCount = 1)
        kotlin.test.assertEquals(fresh, KeyframePrompts.ensureNoDuplicateGuard(fresh))
    }
}
