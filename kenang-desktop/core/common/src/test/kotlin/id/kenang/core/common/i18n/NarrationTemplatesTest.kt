package id.kenang.core.common.i18n

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The wizard prefills the narration box from this pool. Owner report
 * 2026-09-01: with a single template every project opened with identical
 * text, so the pool has to stay genuinely varied and within the input limit.
 */
class NarrationTemplatesTest {

    /** Mirrors limits.max_narration_chars in app-config.json. */
    private val maxChars = 1000

    @Test
    fun `pool is large enough to feel varied`() {
        assertTrue(
            Strings.WIZARD_NARRATION_TEMPLATES.size >= 10,
            "expected at least 10 suggestions, got ${Strings.WIZARD_NARRATION_TEMPLATES.size}",
        )
    }

    @Test
    fun `suggestions are distinct`() {
        val all = Strings.WIZARD_NARRATION_TEMPLATES
        assertEquals(all.size, all.distinct().size, "duplicate narration suggestions")
        // Openings differ too — two texts that start alike still read as "the
        // same narration again" to a user skimming the box.
        val openings = all.map { it.take(40) }
        assertEquals(openings.size, openings.distinct().size, "two suggestions start identically")
    }

    @Test
    fun `every suggestion fits the narration limit and is substantial`() {
        Strings.WIZARD_NARRATION_TEMPLATES.forEachIndexed { index, text ->
            assertTrue(text.length in 150..maxChars, "suggestion $index has length ${text.length}")
            assertTrue(text.trim() == text, "suggestion $index has stray whitespace")
        }
    }

    @Test
    fun `legacy single template still resolves to the first suggestion`() {
        assertEquals(Strings.WIZARD_NARRATION_TEMPLATES.first(), Strings.WIZARD_NARRATION_TEMPLATE)
    }
}
