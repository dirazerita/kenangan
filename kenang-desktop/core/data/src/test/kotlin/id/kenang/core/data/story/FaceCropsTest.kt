package id.kenang.core.data.story

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Face references for the face lock (owner 2026-09-12) are cut from the
 * ORIGINAL photo around a vision-model box. The box is only roughly right,
 * so the crop must be forgiving; a bad box must yield nothing rather than a
 * meaningless reference.
 */
class FaceCropsTest {

    private val dir = File(System.getProperty("java.io.tmpdir"), "kenang-facecrops-${System.nanoTime()}")

    private fun photo(width: Int, height: Int): File {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = Color.DARK_GRAY
        g.fillRect(0, 0, width, height)
        g.dispose()
        dir.mkdirs()
        return File(dir, "photo.jpg").also { ImageIO.write(img, "jpg", it) }
    }

    @Test
    fun `crops a square around the box, padded, at native detail`() {
        val src = photo(3000, 2000)
        // A face 300px wide near the centre.
        val box = listOf(0.45, 0.35, 0.55, 0.50)
        val out = FaceCrops.crop(src, box, File(dir, "face.jpg"))
        assertNotNull(out)
        val img = ImageIO.read(out)
        assertEquals(img.width, img.height, "reference must be square")
        assertTrue(img.width > 300, "crop should be padded beyond the face box")
        assertTrue(img.width <= 1024, "crop is downscaled only to the output cap")
        dir.deleteRecursively()
    }

    @Test
    fun `a face at the edge slides the window inward instead of clipping`() {
        val src = photo(2000, 2000)
        val box = listOf(0.90, 0.05, 0.99, 0.18) // top-right corner
        val out = FaceCrops.crop(src, box, File(dir, "edge.jpg"))
        assertNotNull(out)
        val img = ImageIO.read(out)
        assertEquals(img.width, img.height, "edge faces must still produce a square")
        dir.deleteRecursively()
    }

    @Test
    fun `rejects boxes that cannot be a face`() {
        assertTrue(!FaceCrops.isPlausible(null))
        assertTrue(!FaceCrops.isPlausible(listOf(0.1, 0.1, 0.1, 0.1)), "zero area")
        assertTrue(!FaceCrops.isPlausible(listOf(0.0, 0.0, 1.0, 1.0)), "the whole frame is not a face")
        assertTrue(!FaceCrops.isPlausible(listOf(-0.1, 0.2, 0.3, 0.4)), "outside the frame")
        assertTrue(FaceCrops.isPlausible(listOf(0.4, 0.3, 0.6, 0.55)))
    }

    @Test
    fun `a face too small to carry detail yields no reference`() {
        val src = photo(1200, 800)
        val tiny = listOf(0.50, 0.50, 0.53, 0.55) // ~36px wide
        assertNull(FaceCrops.crop(src, tiny, File(dir, "tiny.jpg")))
        dir.deleteRecursively()
    }

    /** Owner 2026-09-16: Kling refused a 250 px face crop ("minimum 300x300"). */
    @Test
    fun `a small crop is enlarged to the minimum, a large one is returned as is`() {
        val small = photo(250, 250)
        val enlarged = FaceCrops.ensureMinSide(small, 320, File(dir, "small_k320.jpg"))
        assertNotNull(enlarged)
        assertTrue(enlarged != small, "a small crop must be replaced by an enlarged copy")
        val img = ImageIO.read(enlarged)
        assertTrue(img.width >= 320 && img.height >= 320, "enlarged to at least 320: ${img.width}x${img.height}")

        val big = photo(800, 800)
        assertEquals(big, FaceCrops.ensureMinSide(big, 320, File(dir, "big_k320.jpg")), "a large crop needs no copy")
        assertNull(FaceCrops.ensureMinSide(File(dir, "missing.jpg"), 320, File(dir, "x.jpg")))
        dir.deleteRecursively()
    }

    @Test
    fun `an existing crop is reused, not redrawn`() {
        val src = photo(2000, 2000)
        val box = listOf(0.4, 0.4, 0.6, 0.6)
        val first = FaceCrops.crop(src, box, File(dir, "cached.jpg"))!!
        val stamp = first.lastModified()
        Thread.sleep(20)
        val second = FaceCrops.crop(src, box, File(dir, "cached.jpg"))!!
        assertEquals(stamp, second.lastModified())
        dir.deleteRecursively()
    }
}
