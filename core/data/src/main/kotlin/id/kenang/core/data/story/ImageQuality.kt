package id.kenang.core.data.story

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Wizard Step-1 photo validation (MASTER_PROMPT_03). */
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

    fun check(file: File): PhotoCheck {
        if (file.extension.lowercase() !in ALLOWED_EXT) {
            return reject("Format tidak didukung. Gunakan JPG, PNG, atau WebP.")
        }
        if (file.length() > MAX_BYTES) {
            return reject("Ukuran file melebihi 20MB. Kecilkan dulu fotonya.")
        }
        val img = runCatching { ImageIO.read(file) }.getOrNull()
            ?: return reject("File tidak bisa dibaca sebagai gambar.")
        if (minOf(img.width, img.height) < MIN_SIDE) {
            return reject("Resolusi terlalu kecil (min sisi terpendek ${MIN_SIDE}px).")
        }

        val blur = varianceOfLaplacian(img)
        val (badge, tip) = when {
            blur < BLUR_KURANG -> QualityBadge.KURANG to
                "Foto ini tampak buram. Hasil video mungkin kurang tajam — kalau ada, pilih foto yang lebih jelas."
            blur < BLUR_BAGUS -> QualityBadge.CUKUP to
                "Kualitas cukup. Foto yang lebih tajam akan memberi hasil lebih baik."
            else -> QualityBadge.BAGUS to null
        }
        return PhotoCheck(true, badge, tip, img.width, img.height, blur)
    }

    private fun reject(reason: String) =
        PhotoCheck(false, QualityBadge.KURANG, null, 0, 0, 0.0, rejectReasonId = reason)

    /**
     * Blur heuristic: variance of the 4-neighbour Laplacian over grayscale,
     * on an image downscaled so max side ≤ 512 (keeps it fast and makes the
     * thresholds resolution-independent).
     */
    fun varianceOfLaplacian(src: BufferedImage): Double {
        val scale = 512.0 / maxOf(src.width, src.height)
        val w: Int
        val h: Int
        val img: BufferedImage
        if (scale < 1.0) {
            w = maxOf(3, (src.width * scale).toInt())
            h = maxOf(3, (src.height * scale).toInt())
            img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            val g = img.createGraphics()
            g.drawImage(src, 0, 0, w, h, null)
            g.dispose()
        } else {
            w = src.width; h = src.height; img = src
        }

        val gray = DoubleArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val rgb = img.getRGB(x, y)
            val r = (rgb shr 16) and 0xFF
            val g2 = (rgb shr 8) and 0xFF
            val b = rgb and 0xFF
            gray[y * w + x] = 0.299 * r + 0.587 * g2 + 0.114 * b
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
