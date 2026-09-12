package id.kenang.core.data.story

import io.github.aakira.napier.Napier
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Cuts a face reference out of the ORIGINAL photo (owner 2026-09-12: customers
 * returned videos because the faces drifted). The whole photo is downscaled
 * to 2048px before upload, which leaves each face of a family group a few
 * hundred pixels wide - too little for the image model to hold an identity.
 * A crop from the full-resolution file hands it the face at native detail.
 *
 * The box comes from a vision model and is only approximately right, so the
 * crop is a generous square around it: a box that is off by a third still
 * lands the whole face - plus hair and shoulders, which are identity too.
 */
object FaceCrops {

    /**
     * Square side = the box's longer side times this. Verified 2026-09-12 on
     * a real two-person photo: the model's boxes were tight and accurate, and
     * 2.2 pulled the neighbour's whole face into the crop - one reference
     * showing two faces undermines the "this is THIS person" contract.
     */
    private const val PADDING = 1.7

    /** Crops smaller than this (in the source) carry no usable detail. */
    private const val MIN_SOURCE_SIDE = 96

    /** Longest side of the written JPEG; the source is never upscaled. */
    private const val MAX_OUTPUT_SIDE = 1024

    /**
     * Writes the crop for [box] ([x0, y0, x1, y1] as fractions) to [out] and
     * returns it; null when the box is unusable or the image cannot be read.
     * An existing [out] is returned as-is, so re-runs cost nothing.
     */
    fun crop(source: File, box: List<Double>, out: File): File? {
        if (out.isFile && out.length() > 0) return out
        if (!isPlausible(box)) return null
        val img = runCatching { ImageIO.read(source) }.getOrNull() ?: return null

        val (x0, y0, x1, y1) = box
        val bw = (x1 - x0) * img.width
        val bh = (y1 - y0) * img.height
        val side = (maxOf(bw, bh) * PADDING).toInt()
        if (maxOf(bw, bh) < MIN_SOURCE_SIDE) {
            Napier.i("face crop skipped: face too small (${bw.toInt()}x${bh.toInt()}) in ${source.name}")
            return null
        }
        // Stay square: a face near an edge slides the window inward instead
        // of being cut off (a clipped, non-square reference reads as a crop
        // of a crop to the model).
        val boundedSide = minOf(side, img.width, img.height)
        val cx = ((x0 + x1) / 2 * img.width).toInt()
        val cy = ((y0 + y1) / 2 * img.height).toInt()
        val left = (cx - boundedSide / 2).coerceIn(0, img.width - boundedSide)
        val top = (cy - boundedSide / 2).coerceIn(0, img.height - boundedSide)
        val w = boundedSide
        val h = boundedSide
        if (w < MIN_SOURCE_SIDE || h < MIN_SOURCE_SIDE) return null

        val region = img.getSubimage(left, top, w, h)
        val scale = minOf(1.0, MAX_OUTPUT_SIDE.toDouble() / maxOf(w, h))
        val ow = (w * scale).toInt().coerceAtLeast(1)
        val oh = (h * scale).toInt().coerceAtLeast(1)
        val rgb = BufferedImage(ow, oh, BufferedImage.TYPE_INT_RGB).also { target ->
            val g = target.createGraphics()
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g.drawImage(region, 0, 0, ow, oh, null)
            g.dispose()
        }

        return runCatching {
            out.parentFile?.mkdirs()
            val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
            val params = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = 0.92f
            }
            ImageIO.createImageOutputStream(out).use { stream ->
                writer.output = stream
                writer.write(null, IIOImage(rgb, null, null), params)
            }
            writer.dispose()
            out
        }.onFailure { Napier.w("face crop write failed: ${it.message}") }.getOrNull()
    }

    /**
     * A usable box: inside the frame, positive area, and not the whole photo
     * (a model that cannot find the face sometimes answers with the frame).
     */
    fun isPlausible(box: List<Double>?): Boolean {
        if (box == null || box.size != 4) return false
        val (x0, y0, x1, y1) = box
        if (listOf(x0, y0, x1, y1).any { it.isNaN() || it < 0.0 || it > 1.0 }) return false
        val w = x1 - x0
        val h = y1 - y0
        if (w <= 0.01 || h <= 0.01) return false
        return w < 0.9 || h < 0.9
    }

    private operator fun List<Double>.component4(): Double = this[3]
}
