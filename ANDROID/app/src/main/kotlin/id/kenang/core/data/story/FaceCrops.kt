package id.kenang.core.data.story

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import io.github.aakira.napier.Napier
import java.io.File
import java.io.FileOutputStream

/**
 * Cuts a face reference out of the ORIGINAL photo (owner 2026-09-12: customers
 * returned videos because the faces drifted). The whole photo is downscaled
 * to 2048px before upload, which leaves each face of a family group a few
 * hundred pixels wide - too little for the image model to hold an identity.
 * A crop from the full-resolution file hands it the face at native detail.
 *
 * Android twin of the desktop Java2D version: [BitmapRegionDecoder] decodes
 * ONLY the face window, so a 12-megapixel phone photo never has to fit in
 * memory whole. Boxes and crops share one coordinate space - both the
 * analysis upload and this decoder read the stored pixels as they are.
 */
object FaceCrops {

    /** Square side = the box's longer side times this (tight boxes verified live; 2.2 pulled neighbours in). */
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

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null

        val (x0, y0, x1, y1) = box
        val bw = (x1 - x0) * width
        val bh = (y1 - y0) * height
        if (maxOf(bw, bh) < MIN_SOURCE_SIDE) {
            Napier.i("face crop skipped: face too small (${bw.toInt()}x${bh.toInt()}) in ${source.name}")
            return null
        }
        // Stay square: a face near an edge slides the window inward instead
        // of being cut off.
        val side = minOf((maxOf(bw, bh) * PADDING).toInt(), width, height)
        if (side < MIN_SOURCE_SIDE) return null
        val cx = ((x0 + x1) / 2 * width).toInt()
        val cy = ((y0 + y1) / 2 * height).toInt()
        val left = (cx - side / 2).coerceIn(0, width - side)
        val top = (cy - side / 2).coerceIn(0, height - side)

        // Decode at the coarsest power-of-two that still leaves >= MAX_OUTPUT_SIDE.
        var sample = 1
        while (side / (sample * 2) >= MAX_OUTPUT_SIDE) sample *= 2
        val region = runCatching {
            @Suppress("DEPRECATION")
            val decoder = BitmapRegionDecoder.newInstance(source.absolutePath, false) ?: return null
            try {
                decoder.decodeRegion(
                    Rect(left, top, left + side, top + side),
                    BitmapFactory.Options().apply { inSampleSize = sample },
                )
            } finally {
                decoder.recycle()
            }
        }.onFailure { Napier.w("face crop decode failed: ${it.message}") }.getOrNull() ?: return null

        val longest = maxOf(region.width, region.height)
        val scaled = if (longest > MAX_OUTPUT_SIDE) {
            val f = MAX_OUTPUT_SIDE.toFloat() / longest
            Bitmap.createScaledBitmap(region, (region.width * f).toInt().coerceAtLeast(1), (region.height * f).toInt().coerceAtLeast(1), true)
        } else region

        return runCatching {
            out.parentFile?.mkdirs()
            FileOutputStream(out).use { scaled.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            out
        }.onFailure { Napier.w("face crop write failed: ${it.message}") }.getOrNull().also {
            if (scaled !== region) scaled.recycle()
            region.recycle()
        }
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
