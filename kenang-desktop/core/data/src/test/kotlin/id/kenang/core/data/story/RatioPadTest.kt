package id.kenang.core.data.story

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The canvas the restoration model receives when a ratio is requested
 * (owner 2026-09-17): the photo whole and centred, grey bands where the
 * scene must be extended, nothing cropped or stretched.
 */
class RatioPadTest {

    private val dir = File(System.getProperty("java.io.tmpdir"), "kenang-pad-${System.nanoTime()}").apply { mkdirs() }
    private val cropper = AwtRatioCropper()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun photo(w: Int, h: Int): File {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        img.createGraphics().apply { color = Color.RED; fillRect(0, 0, w, h); dispose() }
        return File(dir, "p_${w}x$h.jpg").also { ImageIO.write(img, "jpg", it) }
    }

    @Test
    fun `a landscape photo gets grey bands above and below on a 9 by 16 canvas`() {
        val out = assertNotNull(cropper.padToRatio(photo(808, 578), "9:16", File(dir, "pad.jpg")))
        val img = ImageIO.read(out)
        assertEquals(808, img.width, "the photo keeps its full width")
        assertEquals(1436, img.height, "808 / (9/16)")
        fun grey(x: Int, y: Int) = Color(img.getRGB(x, y)).let { kotlin.math.abs(it.red - 128) < 12 && kotlin.math.abs(it.green - 128) < 12 }
        fun red(x: Int, y: Int) = Color(img.getRGB(x, y)).let { it.red > 200 && it.green < 60 }
        assertTrue(grey(400, 20), "top band is grey")
        assertTrue(grey(400, 1416), "bottom band is grey")
        assertTrue(red(400, 718), "the photo sits in the middle")
        assertTrue(red(10, 440) && red(797, 716), "the photo is whole, corner to corner")
    }

    @Test
    fun `a portrait photo gets bands left and right on a 16 by 9 canvas`() {
        val out = assertNotNull(cropper.padToRatio(photo(720, 1600), "16:9", File(dir, "pad2.jpg")))
        val img = ImageIO.read(out)
        assertEquals(1600, img.height)
        assertEquals(2844, img.width, "1600 * 16/9")
        assertTrue(Color(img.getRGB(20, 800)).red < 160, "left band is grey")
        assertTrue(Color(img.getRGB(1422, 800)).red > 200, "the photo is in the centre")
    }

    @Test
    fun `a photo already at the ratio needs no canvas`() {
        assertNull(cropper.padToRatio(photo(900, 1600), "9:16", File(dir, "none.jpg")))
        assertNull(cropper.padToRatio(File(dir, "missing.jpg"), "9:16", File(dir, "none2.jpg")))
        assertEquals(808 to 1436, AwtRatioCropper.canvasFor(808, 578, 9.0 / 16.0))
        assertEquals(2844 to 1600, AwtRatioCropper.canvasFor(720, 1600, 16.0 / 9.0))
    }
}
