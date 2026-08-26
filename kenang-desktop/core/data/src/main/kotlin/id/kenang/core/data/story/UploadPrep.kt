package id.kenang.core.data.story

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Prepares a photo for provider upload: downscale to [maxSide] and re-encode
 * as JPEG (q≈0.85). VLM analysis and keyframe editing don't benefit from
 * full-resolution scans, and slow uplinks time out on multi-MB originals.
 * The ORIGINAL file stays untouched in the project folder.
 */
object UploadPrep {

    fun prepareJpeg(file: File, maxSide: Int = 2048): ByteArray {
        val src = ImageIO.read(file) ?: return file.readBytes()
        val scale = maxSide.toDouble() / maxOf(src.width, src.height)
        val img = if (scale >= 1.0 && file.extension.lowercase() in setOf("jpg", "jpeg")) {
            return file.readBytes() // already small JPEG — send as-is
        } else if (scale >= 1.0) {
            toRgb(src)
        } else {
            val w = (src.width * scale).toInt().coerceAtLeast(1)
            val h = (src.height * scale).toInt().coerceAtLeast(1)
            BufferedImage(w, h, BufferedImage.TYPE_INT_RGB).also { out ->
                val g = out.createGraphics()
                g.setRenderingHint(
                    java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                )
                g.drawImage(src, 0, 0, w, h, null)
                g.dispose()
            }
        }
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val params = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = 0.85f
        }
        val buffer = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(buffer).use { out ->
            writer.output = out
            writer.write(null, IIOImage(img, null, null), params)
        }
        writer.dispose()
        return buffer.toByteArray()
    }

    private fun toRgb(src: BufferedImage): BufferedImage =
        if (src.type == BufferedImage.TYPE_INT_RGB) src
        else BufferedImage(src.width, src.height, BufferedImage.TYPE_INT_RGB).also { out ->
            val g = out.createGraphics(); g.drawImage(src, 0, 0, null); g.dispose()
        }
}
