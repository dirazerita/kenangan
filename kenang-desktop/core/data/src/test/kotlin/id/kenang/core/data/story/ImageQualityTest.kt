package id.kenang.core.data.story

import java.awt.image.BufferedImage
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class ImageQualityTest {

    private fun noisyImage(size: Int): BufferedImage {
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        val rnd = Random(42)
        for (y in 0 until size) for (x in 0 until size) {
            val v = rnd.nextInt(256)
            img.setRGB(x, y, (v shl 16) or (v shl 8) or v)
        }
        return img
    }

    private fun flatImage(size: Int): BufferedImage {
        val img = BufferedImage(size, size, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until size) for (x in 0 until size) img.setRGB(x, y, 0x808080)
        return img
    }

    @Test
    fun `sharp (noisy) image scores far higher than flat (blurred) image`() {
        val sharp = ImageQuality.varianceOfLaplacian(noisyImage(600))
        val flat = ImageQuality.varianceOfLaplacian(flatImage(600))
        assertTrue(sharp > flat * 100, "sharp=$sharp flat=$flat")
        assertTrue(flat < 1.0)
    }
}
