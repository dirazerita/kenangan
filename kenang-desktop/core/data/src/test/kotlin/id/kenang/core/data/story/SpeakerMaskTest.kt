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

    /**
     * The real numbers from the owner's failing run (2026-09-15): the man's
     * body box was 86% x 90% of the frame with the child inside it. Building
     * the white area from his FACE is what keeps her out of it.
     */
    @Test
    fun `the white area comes from the face, not the body`() {
        val manFace = listOf(0.196, 0.185, 0.412, 0.482)
        val manBody = listOf(0.196, 0.168, 1.0, 1.0)
        val childFace = listOf(0.299, 0.417, 0.497, 0.703)

        val box = SpeakerMask.speakerBox(manFace, manBody)
        assertNotNull(box)
        assertTrue(box[2] < 0.60, "the white area still spans the frame: $box")
        assertTrue(box[3] < 0.70, "the white area still reaches the bottom: $box")

        val out = SpeakerMask.render(
            1088, 1920, box, File(dir, "lap2.png"),
            others = listOf(childFace), speakerFace = manFace,
        )
        assertNotNull(out)
        val image = ImageIO.read(out)
        fun white(fx: Double, fy: Double) =
            Color(image.getRGB((fx * 1088).toInt(), (fy * 1920).toInt())).red > 200

        assertTrue(white(0.30, 0.33), "the father's face must be white")
        assertTrue(!white(0.60, 0.45), "the child's face is inside the white area - both would speak")
        assertTrue(!white(0.50, 0.90), "the lower body is white; only the speaker's head should be")
    }

    /** With no face box at all we still fall back to the body. */
    @Test
    fun `a missing face falls back to the body box`() {
        val box = SpeakerMask.speakerBox(null, listOf(0.5, 0.2, 0.9, 0.95))
        assertNotNull(box)
        assertTrue(box[0] < 0.5 && box[2] > 0.9, "the fallback should be the grown body box")
    }

    /**
     * The father-and-child case (owner 2026-09-15): the man's box covers
     * nearly the whole frame with the child inside it, so a plain rectangle
     * made BOTH of them speak.
     */
    @Test
    fun `a person sitting inside the speaker's box is cut back out`() {
        val man = listOf(0.10, 0.10, 1.0, 1.0)
        val manFace = listOf(0.20, 0.20, 0.42, 0.40)
        val child = listOf(0.35, 0.45, 0.80, 1.0)

        val out = SpeakerMask.render(
            1000, 1000, man, File(dir, "lap.png"),
            others = listOf(child), speakerFace = manFace,
        )
        assertNotNull(out)
        val image = ImageIO.read(out)
        fun isWhite(x: Int, y: Int) = Color(image.getRGB(x, y)).red > 200

        assertTrue(isWhite(300, 300), "the speaker's face must stay white")
        assertTrue(isWhite(150, 800), "the speaker's own body should still be white")
        assertTrue(!isWhite(600, 700), "the child inside the box is still white - both would speak")
        assertTrue(!isWhite(500, 980), "the child's lower area is still white")
    }

    /** An overlap must never cost the speaker their own face. */
    @Test
    fun `the speaker's face survives an overlapping cut-out`() {
        // A large but legal speaker box, and another person's box that would
        // swallow it whole if the face were not restored afterwards.
        val speaker = listOf(0.05, 0.05, 0.98, 1.0)
        val speakerFace = listOf(0.40, 0.10, 0.60, 0.30)
        val otherCoveringEverything = listOf(0.0, 0.0, 1.0, 1.0)

        val out = SpeakerMask.render(
            800, 800, speaker, File(dir, "ov.png"),
            others = listOf(otherCoveringEverything), speakerFace = speakerFace,
        )
        assertNotNull(out)
        val image = ImageIO.read(out)
        fun isWhite(x: Int, y: Int) = Color(image.getRGB(x, y)).red > 200
        assertTrue(isWhite(400, 160), "the speaker's face was erased by the cut-out")
        assertTrue(!isWhite(100, 700), "everything else should have been cut out")
    }

    @Test
    fun `an unusable box yields no mask rather than a wrong one`() {
        assertNull(SpeakerMask.usableBox(null))
        assertNull(SpeakerMask.usableBox(listOf(0.1, 0.1, 0.1, 0.1)), "zero area")
        // A frame-filling box is only pointless when there is nobody to cut out
        // of it; with a second person present it is exactly what we want.
        assertNull(SpeakerMask.render(1000, 1000, listOf(0.0, 0.0, 1.0, 1.0), File(dir, "n.png")))
        assertNotNull(
            SpeakerMask.render(
                1000, 1000, listOf(0.0, 0.0, 1.0, 1.0), File(dir, "n2.png"),
                others = listOf(listOf(0.5, 0.5, 0.9, 0.9)), speakerFace = listOf(0.1, 0.1, 0.3, 0.3),
            ),
        )
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
