package id.kenang.core.data.ffmpeg

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssBuilderTest {

    private val narration =
        "Waktu boleh berlalu, tapi senyummu tetap tinggal di hati kami. " +
            "Di rumah ini, tawamu masih terdengar hangat, menemani setiap doa yang kami kirimkan. " +
            "Terima kasih untuk kasih sayang yang tak pernah pudar."

    @Test
    fun `lines never exceed 42 chars and keep all words`() {
        val lines = AssBuilder.splitLines(narration)
        assertTrue(lines.all { it.length <= AssBuilder.MAX_LINE_CHARS }, "long line in $lines")
        assertEquals(
            narration.split(Regex("\\s+")).filter { it.isNotBlank() },
            lines.joinToString(" ").split(" "),
        )
    }

    @Test
    fun `cues cover the full duration proportionally and in order`() {
        val totalMs = 18_000L
        val cues = AssBuilder.buildCues(narration, totalMs)
        assertTrue(cues.isNotEmpty())
        assertEquals(0L, cues.first().startMs)
        assertEquals(totalMs, cues.last().endMs)
        cues.zipWithNext().forEach { (a, b) ->
            assertTrue(a.endMs <= b.startMs + 1, "overlapping cues: $a -> $b")
        }
        // Proportionality: the longest sentence gets the longest share.
        val longest = cues.maxBy { it.endMs - it.startMs }
        assertTrue(longest.endMs - longest.startMs >= totalMs / cues.size)
    }

    @Test
    fun `portrait style uses higher safe-area margin`() {
        val portrait = AssBuilder.build(narration, 10_000, "9:16")
        val landscape = AssBuilder.build(narration, 10_000, "16:9")
        assertContains(portrait, "PlayResX: 1080")
        assertContains(portrait, "PlayResY: 1920")
        assertContains(portrait, ",260,1") // MarginV 260
        assertContains(landscape, "PlayResX: 1920")
        assertContains(landscape, ",90,1")
    }

    @Test
    fun `style is boxless white with soft shadow, bottom-center`() {
        val doc = AssBuilder.build(narration, 10_000, "9:16")
        val style = doc.lineSequence().first { it.startsWith("Style: Kenang") }
        // BorderStyle=1, Outline=0, Shadow=2, Alignment=2 (bottom-center)
        assertContains(style, "&H00FFFFFF")
        assertContains(style, ",1,0,2,2,")
    }

    @Test
    fun `timestamps are ass formatted`() {
        assertEquals("0:00:00.00", AssBuilder.ts(0))
        assertEquals("0:01:05.50", AssBuilder.ts(65_500))
        assertEquals("1:00:00.99", AssBuilder.ts(3_600_990))
    }

    @Test
    fun `empty narration yields no cues`() {
        assertTrue(AssBuilder.buildCues("   ", 5000).isEmpty())
    }
}
