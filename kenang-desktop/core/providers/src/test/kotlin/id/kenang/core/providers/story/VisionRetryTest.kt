package id.kenang.core.providers.story

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Owner 2026-09-17: a fourteen-person photo overflowed the analysis budget
 * on every attempt, and every retry asked for the same thing. A cut-off
 * answer must be recognised as such and asked for again compactly, with
 * more room, under the router's ceiling.
 */
class VisionRetryTest {

    @Test
    fun `the parser messages seen live are recognised as truncation`() {
        listOf(
            "Expected end of the object '}', but had 'EOF' instead at path: \$.subjects[9]",
            "Unexpected JSON token at offset 1847: Expected quotation mark '\"', but had 'EOF'",
            "Unexpected JSON token at offset 1998: EOF at path: \$.subjects[13].desc",
            "Expected end of the array ']', but had 'EOF' instead at path: \$.subjects",
        ).forEach { assertTrue(VisionRetry.looksTruncated(it), it) }

        assertFalse(VisionRetry.looksTruncated("Unexpected JSON token at offset 0: Expected start of the object '{', but had '['"))
        assertFalse(VisionRetry.looksTruncated("Field 'photo_id' is required"))
        assertFalse(VisionRetry.looksTruncated(null))
    }

    @Test
    fun `the budget doubles up to the ceiling and the retry asks for a compact answer`() {
        assertEquals(1600, VisionRetry.nextTokens(800))
        assertEquals(2000, VisionRetry.nextTokens(1600))
        assertEquals(2000, VisionRetry.nextTokens(2000))
        assertEquals(VisionRetry.TOKEN_CEILING, AnalysisService.ANALYSIS_TOKENS)

        val retry = VisionRetry.compactRetryPrompt("Analyze this photo. Return JSON.")
        assertTrue(retry.startsWith("Analyze this photo. Return JSON."), "the original request stays intact")
        assertTrue("CUT OFF" in retry && "SAME number of entries" in retry, retry)
    }
}
