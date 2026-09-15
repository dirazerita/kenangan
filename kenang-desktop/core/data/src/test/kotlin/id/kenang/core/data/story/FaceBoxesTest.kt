package id.kenang.core.data.story

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The vision model sometimes answers boxes y-first (owner 2026-09-15: every
 * "face" of a family of six landed on a wall, a chest or a pair of feet, and
 * those crops were sent to the image model as the faces to reproduce). The
 * numbers below are the real ones from the owner's database.
 */
class FaceBoxesTest {

    /** Rahayu RO 1, 2752x1536, six people, as stored on 2026-09-15. */
    private val rahayu = listOf(
        listOf(0.02, 0.14, 0.22, 0.24), // woman in the brown top
        listOf(0.33, 0.09, 0.49, 0.20), // boy in green
        listOf(0.11, 0.35, 0.27, 0.44), // grandmother
        listOf(0.45, 0.48, 0.61, 0.58), // toddler
        listOf(0.12, 0.57, 0.30, 0.67), // woman in white
        listOf(0.08, 0.76, 0.28, 0.88), // man in the polka-dot shirt
    )

    @Test
    fun `Rahayu's boxes are recognised as transposed and land on the faces once swapped`() {
        val o = FaceBoxes.orientation(rahayu, 2752, 1536)
        assertTrue(o.transposed, "stored aspect ${o.storedAspect}, swapped ${o.swappedAspect}")
        assertTrue(o.storedAspect!! < 0.5, "as stored the boxes are far wider than tall: ${o.storedAspect}")
        assertTrue(o.swappedAspect!! in 0.7..2.2, "swapped they are face-shaped: ${o.swappedAspect}")

        val fixed = FaceBoxes.orient(rahayu, 2752, 1536)
        val man = assertNotNull(fixed[5])
        assertTrue(man[0] > 0.7, "the man sits at the right edge of the photo: $man")
        val grandmother = assertNotNull(fixed[2])
        assertTrue(grandmother[0] in 0.3..0.5 && grandmother[1] < 0.3, "the grandmother is top centre: $grandmother")
        val toddler = assertNotNull(fixed[3])
        assertTrue(toddler[1] > 0.4, "the toddler sits low in the frame: $toddler")
    }

    @Test
    fun `the verified two-person photo is left alone`() {
        // Bunk Jhon 2, 1536x2752: the boxes were confirmed on the faces (D-055).
        val bunk = listOf(listOf(0.48, 0.32, 0.62, 0.43), listOf(0.68, 0.41, 0.96, 0.61))
        val o = FaceBoxes.orientation(bunk, 1536, 2752)
        assertTrue(!o.transposed, "a correct set must not be flipped: $o")
        assertEquals(bunk, FaceBoxes.orient(bunk, 1536, 2752))
    }

    @Test
    fun `the Video Berbicara lap photo was transposed too`() {
        // 1088x1920, father with his daughter on his lap; her box "sat on his chest".
        val lap = listOf(listOf(0.196, 0.185, 0.412, 0.482), listOf(0.299, 0.417, 0.497, 0.703))
        assertTrue(FaceBoxes.orientation(lap, 1088, 1920).transposed)
        val child = FaceBoxes.swap(lap[1])
        assertTrue(child[0] > 0.4 && child[1] < 0.35, "the child's face is right of centre, upper half: $child")
    }

    @Test
    fun `a square photo cannot be judged and is not touched`() {
        val boxes = listOf(listOf(0.1, 0.1, 0.4, 0.2)) // 3:1 wide either way
        assertTrue(!FaceBoxes.orientation(boxes, 2000, 2000).transposed)
    }

    @Test
    fun `a lone tall but legitimate box is kept`() {
        // Pixel aspect 1.9 on a portrait photo: still a face as stored.
        val box = listOf(listOf(0.40, 0.20, 0.60, 0.40))
        assertTrue(!FaceBoxes.orientation(box, 1000, 1900).transposed)
    }

    @Test
    fun `nothing usable means nothing changes`() {
        val o = FaceBoxes.orientation(listOf(null, listOf(0.0, 0.0, 1.0, 1.0)), 2000, 1000)
        assertTrue(!o.transposed)
        assertEquals(listOf(null), FaceBoxes.orient(listOf(null), 2000, 1000))
        assertTrue(!FaceBoxes.orientation(rahayu, 0, 0).transposed)
    }

    @Test
    fun `swap is its own inverse`() {
        val box = listOf(0.1, 0.2, 0.3, 0.4)
        assertEquals(listOf(0.2, 0.1, 0.4, 0.3), FaceBoxes.swap(box))
        assertEquals(box, FaceBoxes.swap(FaceBoxes.swap(box)))
    }
}
