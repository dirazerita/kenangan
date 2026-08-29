package id.kenang.core.data.story

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Wizard Step-1 photo validation (MASTER_PROMPT_03) — Android/Bitmap port. */
enum class QualityBadge { BAGUS, CUKUP, KURANG }

data class PhotoCheck(
    val ok: Boolean,
    val badge: QualityBadge,
    /** Plain-language Indonesian tip when not BAGUS. */
    val tipId: String?,
    val width: Int,
    val height: Int,
    val blurScore: Double,
    /** Hard-reject reason (mime/size/dimensions), null when acceptable. */
    val rejectReasonId: String? = null,
)

object ImageQuality {

    const val MAX_BYTES = 20L * 1024 * 1024
    const val MIN_SIDE = 512
    private val ALLOWED_EXT = setOf("jpg", "jpeg", "png", "webp", "bmp")

    // Variance-of-Laplacian thresholds, tuned on downscaled (max 512px) grayscale.
    private const val BLUR_KURANG = 60.0
    private const val BLUR_BAGUS = 250.0

    /** Analysis size for the blur heuristic — keeps thresholds resolution-independent. */
    private const val ANALYSIS_SIDE = 512

    fun check(file: File): PhotoCheck {
        if (file.extension.lowercase() !in ALLOWED_EXT) {
            return reject("Format tidak didukung. Gunakan JPG, PNG, atau WebP.")
        }
        if (file.length() > MAX_BYTES) {
            return reject("Ukuran file melebihi 20MB. Kecilkan dulu fotonya.")
        }

        // Bounds pass first: never decode a 50MP photo into a phone's heap.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) {
            return reject("File tidak bisa dibaca sebagai gambar.")
        }
        if (minOf(width, height) < MIN_SIDE) {
            return reject("Resolusi terlalu kecil (min sisi terpendek ${MIN_SIDE}px).")
        }

        val small = decodeDownsampled(file, ANALYSIS_SIDE)
            ?: return reject("File tidak bisa dibaca sebagai gambar.")
        val blur = try {
            varianceOfLaplacian(small)
        } finally {
            small.recycle()
        }

        val (badge, tip) = when {
            blur < BLUR_KURANG -> QualityBadge.KURANG to
                "Foto ini tampak buram. Hasil video mungkin kurang tajam — kalau ada, pilih foto yang lebih jelas."
            blur < BLUR_BAGUS -> QualityBadge.CUKUP to
                "Kualitas cukup. Foto yang lebih tajam akan memberi hasil lebih baik."
            else -> QualityBadge.BAGUS to null
        }
        return PhotoCheck(true, badge, tip, width, height, blur)
    }

    private fun reject(reason: String) =
        PhotoCheck(false, QualityBadge.KURANG, null, 0, 0, 0.0, rejectReasonId = reason)

    /** Decodes with inSampleSize so the result's longest side is >= [maxSide] but small. */
    fun decodeDownsampled(file: File, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }.getOrNull()
    }

    /**
     * Blur heuristic: variance of the 4-neighbour Laplacian over grayscale.
     * Same maths as the desktop build, reading pixels out of a Bitmap.
     */
    fun varianceOfLaplacian(src: Bitmap): Double {
        val w = src.width
        val h = src.height
        if (w < 3 || h < 3) return 0.0
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = DoubleArray(w * h)
        for (i in pixels.indices) {
            val rgb = pixels[i]
            val r = (rgb shr 16) and 0xFF
            val g = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            gray[i] = 0.299 * r + 0.587 * g + 0.114 * b
        }

        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val lap = -4 * gray[y * w + x] + gray[y * w + x - 1] + gray[y * w + x + 1] +
                gray[(y - 1) * w + x] + gray[(y + 1) * w + x]
            sum += lap; sumSq += lap * lap; n++
        }
        if (n == 0) return 0.0
        val mean = sum / n
        return sumSq / n - mean * mean
    }
}
