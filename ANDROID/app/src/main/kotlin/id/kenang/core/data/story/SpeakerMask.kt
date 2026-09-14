package id.kenang.core.data.story

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import io.github.aakira.napier.Napier
import java.io.File
import java.io.FileOutputStream

/**
 * Black-and-white mask that tells OmniHuman which person in a group photo
 * should speak (owner 2026-09-15: "yang berbicara yang laki-laki saja").
 * fal's contract is literal — "only the person in the white area of the mask
 * will speak" — so the mask is pure black with one white region over the
 * chosen person.
 *
 * The mask MUST match the uploaded image pixel for pixel, so callers build it
 * from the dimensions of the bytes they actually uploaded, not from the
 * original file (which [UploadPrep] may have downscaled).
 */
object SpeakerMask {

    /** Grown slightly so hair, shoulders and gesturing hands stay inside. */
    private const val MARGIN = 0.03

    /**
     * Writes a mask of [width]x[height] whose white area is [box]
     * ([x0, y0, x1, y1] as fractions of the frame). Returns null when the box
     * is unusable — the caller then sends no mask and lets the model choose.
     */
    fun render(width: Int, height: Int, box: List<Double>, out: File): File? {
        if (width <= 0 || height <= 0) return null
        val area = usableBox(box) ?: return null

        return runCatching {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.BLACK)

            val left = (area[0] * width).toFloat().coerceIn(0f, (width - 1).toFloat())
            val top = (area[1] * height).toFloat().coerceIn(0f, (height - 1).toFloat())
            val right = (area[2] * width).toFloat().coerceIn(left + 1f, width.toFloat())
            val bottom = (area[3] * height).toFloat().coerceIn(top + 1f, height.toFloat())
            canvas.drawRect(left, top, right, bottom, Paint().apply { color = Color.WHITE })

            out.parentFile?.mkdirs()
            FileOutputStream(out).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            Napier.i("speaker mask: ${out.name} white=${left.toInt()},${top.toInt()}-${right.toInt()},${bottom.toInt()} of ${width}x$height")
            out
        }.onFailure { Napier.w("speaker mask failed: ${it.message}") }.getOrNull()
    }

    /**
     * Clamps and grows [box]; null when it is missing, inverted, or covers so
     * much of the frame that masking would be pointless.
     */
    fun usableBox(box: List<Double>?): List<Double>? {
        if (box == null || box.size != 4) return null
        if (box.any { it.isNaN() || it < -0.5 || it > 1.5 }) return null
        // Reject a degenerate box BEFORE the margin grows it: a detection
        // failure must yield no mask, not a small white blob somewhere that
        // makes a random patch of the photo speak.
        if (maxOf(box[0], box[2]) - minOf(box[0], box[2]) < 0.01) return null
        if (maxOf(box[1], box[3]) - minOf(box[1], box[3]) < 0.01) return null
        val x0 = (minOf(box[0], box[2]) - MARGIN).coerceIn(0.0, 1.0)
        val y0 = (minOf(box[1], box[3]) - MARGIN).coerceIn(0.0, 1.0)
        val x1 = (maxOf(box[0], box[2]) + MARGIN).coerceIn(0.0, 1.0)
        val y1 = (maxOf(box[1], box[3]) + MARGIN).coerceIn(0.0, 1.0)
        if (x1 - x0 < 0.03 || y1 - y0 < 0.03) return null
        // Covering nearly everything is the same as no mask at all.
        if ((x1 - x0) > 0.97 && (y1 - y0) > 0.97) return null
        return listOf(x0, y0, x1, y1)
    }

    /**
     * A body region derived from a face box, for photos where the model gave
     * a face but no person outline: the face widened to shoulder span and
     * carried down to the bottom of the frame.
     */
    fun bodyFromFace(face: List<Double>): List<Double>? {
        if (face.size != 4) return null
        val fw = face[2] - face[0]
        val fh = face[3] - face[1]
        if (fw <= 0 || fh <= 0) return null
        val cx = (face[0] + face[2]) / 2
        val halfWidth = (fw * 1.6).coerceAtMost(0.45)
        return listOf(
            (cx - halfWidth).coerceIn(0.0, 1.0),
            (face[1] - fh * 0.5).coerceIn(0.0, 1.0),
            (cx + halfWidth).coerceIn(0.0, 1.0),
            1.0,
        )
    }
}
