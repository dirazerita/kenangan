package id.kenang.core.data.story

import java.awt.Color
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The mask decides who speaks in a group photo (owner 2026-09-15). fal reads
 * it literally — "only the person in the white area of the mask will speak" —
 * so a wrong box means the wrong person talks in a video the customer paid
 * for. These pin the geometry and the refusals.
 */
class SpeakerMaskTest {

    private val dir = File(System.getProperty("java.io.tmpdir"), "kenang-mask-${System.nanoTime()}")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `white covers the chosen person and nothing else`() {
        // The man on the right of a 1000x2000 portrait.
        val out = SpeakerMask.render(1000, 2000, listOf(0.55, 0.30, 1.0, 1.0), File(dir, "m.png"))
        assertNotNull(out)
        val image = ImageIO.read(out)
        assertEquals(1000, image.width)
        assertEquals(2000, image.height)

        fun isWhite(x: Int, y: Int) = Color(image.getRGB(x, y)).let { it.red > 200 && it.green > 200 && it.blue > 200 }
        assertTrue(isWhite(900, 1500), "the chosen person is not inside the white area")
        assertTrue(!isWhite(100, 400), "the other person's area is not black")
        assertTrue(!isWhite(100, 1900), "the other person's lower area is not black")
    }

    @Test
    fun `the box is grown a little so shoulders and hands stay inside`() {
        val out = SpeakerMask.render(1000, 1000, listOf(0.40, 0.40, 0.60, 0.60), File(dir, "g.png"))
        assertNotNull(out)
        val image = ImageIO.read(out)
        fun isWhite(x: Int, y: Int) = Color(image.getRGB(x, y)).red > 200
        // 3% margin each side: 0.37..0.63 of the frame.
        assertTrue(isWhite(380, 500), "left margin missing")
        assertTrue(isWhite(620, 500), "right margin missing")
        assertTrue(!isWhite(340, 500), "the margin is far larger than intended")
    }

    @Test
    fun `an unusable box yields no mask rather than a wrong one`() {
        assertNull(SpeakerMask.usableBox(null))
        assertNull(SpeakerMask.usableBox(listOf(0.1, 0.1, 0.1, 0.1)), "zero area")
        assertNull(SpeakerMask.usableBox(listOf(0.0, 0.0, 1.0, 1.0)), "masking the whole frame is pointless")
        assertNull(SpeakerMask.render(1000, 1000, listOf(0.0, 0.0, 1.0, 1.0), File(dir, "n.png")))
        assertNotNull(SpeakerMask.usableBox(listOf(0.6, 0.2, 0.95, 0.9)))
    }

    @Test
    fun `a face box falls back to a plausible body region`() {
        val body = SpeakerMask.bodyFromFace(listOf(0.45, 0.10, 0.55, 0.25))
        assertNotNull(body)
        assertTrue(body[0] < 0.45 && body[2] > 0.55, "the body is not wider than the face")
        assertTrue(body[1] < 0.10, "the body does not start above the face")
        assertEquals(1.0, body[3], "the body must reach the bottom of the frame")
        assertNotNull(SpeakerMask.usableBox(body))
    }
}
