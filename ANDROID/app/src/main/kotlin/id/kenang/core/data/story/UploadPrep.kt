package id.kenang.core.data.story

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Prepares a photo for provider upload: downscale to [maxSide] and re-encode
 * as JPEG (q≈85). VLM analysis and keyframe editing don't benefit from
 * full-resolution scans, and mobile uplinks time out on multi-MB originals.
 * The ORIGINAL file stays untouched in the project folder.
 */
object UploadPrep {

    fun prepareJpeg(file: File, maxSide: Int = 2048): ByteArray {
        val bitmap = ImageQuality.decodeDownsampled(file, maxSide) ?: return file.readBytes()
        return try {
            val longest = maxOf(bitmap.width, bitmap.height)
            // inSampleSize only halves, so scale the rest of the way exactly.
            val scaled = if (longest > maxSide) {
                val ratio = maxSide.toDouble() / longest
                val w = (bitmap.width * ratio).toInt().coerceAtLeast(1)
                val h = (bitmap.height * ratio).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(bitmap, w, h, true)
            } else {
                bitmap
            }
            val buffer = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, buffer)
            if (scaled !== bitmap) scaled.recycle()
            buffer.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }
}
