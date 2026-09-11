package id.kenang.core.data.ffmpeg

import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The preview copy goes to a paying customer's inbox before they pay, so the
 * mark has to be unmissable (owner 2026-09-11: "sangat kelihatan banget").
 * The renders are left in the temp folder on purpose — they are what a human
 * looks at when judging that.
 */
class WatermarkImageTest {

    private val dir = File(System.getProperty("java.io.tmpdir"), "kenang-wm-preview")

    @Test
    fun `renders a full-frame mark for both ratios`() {
        listOf(1080 to 1920, 1920 to 1080).forEach { (w, h) ->
            File(dir, "wm_${w}x${h}.png").delete()
            val png = WatermarkImage.render(w, h, dir)
            assertNotNull(png, "no watermark produced for ${w}x$h")

            val image = ImageIO.read(png)
            assertEquals(w, image.width)
            assertEquals(h, image.height)

            // The mark must cover a serious share of the middle band, or it is
            // a discreet badge rather than a deterrent.
            var inked = 0
            val band = (h * 0.3).toInt()..(h * 0.7).toInt()
            for (y in band step 4) {
                for (x in 0 until w step 4) {
                    if ((image.getRGB(x, y) ushr 24) > 40) inked++
                }
            }
            val sampled = (band.count() / 4 + 1) * (w / 4 + 1)
            val coverage = inked.toDouble() / sampled
            assertTrue(coverage > 0.06, "watermark too faint for ${w}x$h: coverage $coverage")

            // Corners stay clear — the memory itself must remain visible.
            assertEquals(0, image.getRGB(2, 2) ushr 24, "corner is not transparent")
        }
    }

    @Test
    fun `reuses the cached png instead of redrawing`() {
        val first = WatermarkImage.render(1080, 1920, dir)
        assertNotNull(first)
        val stamp = first.lastModified()
        val second = WatermarkImage.render(1080, 1920, dir)
        assertEquals(first.absolutePath, second?.absolutePath)
        assertEquals(stamp, second!!.lastModified(), "watermark was redrawn on every export")
    }
}
