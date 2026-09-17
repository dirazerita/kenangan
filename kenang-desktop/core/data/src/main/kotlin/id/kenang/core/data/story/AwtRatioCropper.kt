package id.kenang.core.data.story

import io.github.aakira.napier.Napier
import java.io.File

/** Desktop [RatioCropper]: ImageIO center-crop (same math as the storyboard's). */
class AwtRatioCropper : RatioCropper {
    override fun cropToRatio(file: File, ratio: String) {
        val target = if (ratio == "16:9") 16.0 / 9.0 else 9.0 / 16.0
        runCatching {
            val img = javax.imageio.ImageIO.read(file) ?: return
            val current = img.width.toDouble() / img.height
            if (kotlin.math.abs(current - target) <= 0.01) return
            val w: Int
            val h: Int
            if (current > target) {
                h = img.height; w = (img.height * target).toInt().coerceAtLeast(1)
            } else {
                w = img.width; h = (img.width / target).toInt().coerceAtLeast(1)
            }
            val sub = img.getSubimage((img.width - w) / 2, (img.height - h) / 2, w, h)
            val copy = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
            copy.createGraphics().apply { drawImage(sub, 0, 0, null); dispose() }
            val fmt = if (file.extension.lowercase() == "png") "png" else "jpg"
            javax.imageio.ImageIO.write(copy, fmt, file)
        }.onFailure { Napier.w("ratio crop skipped: ${it.message}") }
    }

    override fun padToRatio(source: File, ratio: String, out: File): File? {
        val target = if (ratio == "16:9") 16.0 / 9.0 else 9.0 / 16.0
        return runCatching {
            val img = javax.imageio.ImageIO.read(source) ?: return null
            val current = img.width.toDouble() / img.height
            if (kotlin.math.abs(current - target) <= 0.01) return null
            val (w, h) = canvasFor(img.width, img.height, target)
            val canvas = java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB)
            canvas.createGraphics().apply {
                color = java.awt.Color(PAD_GREY, PAD_GREY, PAD_GREY)
                fillRect(0, 0, w, h)
                drawImage(img, (w - img.width) / 2, (h - img.height) / 2, null)
                dispose()
            }
            out.parentFile?.mkdirs()
            javax.imageio.ImageIO.write(canvas, "jpg", out)
            out
        }.onFailure { Napier.w("ratio pad skipped: ${it.message}") }.getOrNull()
    }

    companion object {
        /** Flat grey the prompt names as "the bands" - neither black (reads as vignette) nor white (reads as paper). */
        const val PAD_GREY = 128

        /** The smallest canvas of [target] ratio that holds a [width]x[height] image whole. */
        fun canvasFor(width: Int, height: Int, target: Double): Pair<Int, Int> {
            val current = width.toDouble() / height
            return if (current > target) width to (width / target).toInt().coerceAtLeast(height)
            else (height * target).toInt().coerceAtLeast(width) to height
        }
    }
}
