package id.kenang.core.data.story

import io.github.aakira.napier.Napier
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Black-and-white mask that tells OmniHuman which person in a group photo
 * should speak (owner 2026-09-15: "yang berbicara yang laki-laki saja").
 * fal's contract is literal - "only the person in the white area of the mask
 * will speak" - so the mask is pure black with one white region over the
 * chosen person.
 *
 * A rectangle alone is not enough. When a father holds a child on his lap his
 * box covers almost the whole frame and the child sits inside it, so both
 * spoke (owner 2026-09-15, second report: white was 86% x 90% of the frame).
 * The mask therefore CUTS the other people back out of the white area, and
 * finally re-paints the chosen face white so an overlap can never erase the
 * speaker.
 *
 * The mask MUST match the uploaded image pixel for pixel, so callers build it
 * from the dimensions of the bytes they actually uploaded, not from the
 * original file (which [UploadPrep] may have downscaled).
 */
object SpeakerMask {

    /** Grown slightly so hair, shoulders and gesturing hands stay inside. */
    private const val MARGIN = 0.03

    /** Other people are cut out generously - a sliver of their face is enough to make them talk. */
    private const val OTHER_MARGIN = 0.02

    /** The speaker's own face is restored a little wider than detected. */
    private const val FACE_MARGIN = 0.04

    /**
     * Writes a mask of [width]x[height] whose white area is [box]
     * ([x0, y0, x1, y1] as fractions of the frame), with [others] cut back
     * out of it and [speakerFace] guaranteed to stay white. Returns null when
     * the box is unusable - the caller then sends no mask and lets the model
     * choose.
     */
    fun render(
        width: Int,
        height: Int,
        box: List<Double>,
        out: File,
        others: List<List<Double>> = emptyList(),
        speakerFace: List<Double>? = null,
    ): File? {
        if (width <= 0 || height <= 0) return null
        val area = usableBox(box) ?: return null
        // A white area that fills the frame only says something when somebody
        // is being cut back out of it; on its own it is the same as no mask.
        if (others.isEmpty() && (area[2] - area[0]) > 0.97 && (area[3] - area[1]) > 0.97) return null

        return runCatching {
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics()
            g.color = Color.BLACK
            g.fillRect(0, 0, width, height)

            fun paint(region: List<Double>, color: Color) {
                val left = (region[0] * width).toInt().coerceIn(0, width - 1)
                val top = (region[1] * height).toInt().coerceIn(0, height - 1)
                val right = (region[2] * width).toInt().coerceIn(left + 1, width)
                val bottom = (region[3] * height).toInt().coerceIn(top + 1, height)
                g.color = color
                g.fillRect(left, top, right - left, bottom - top)
            }

            paint(area, Color.WHITE)
            // Everyone else is cut back out, or they speak too.
            others.mapNotNull { grow(it, OTHER_MARGIN) }.forEach { paint(it, Color.BLACK) }
            // ...but never at the cost of the speaker's own face.
            speakerFace?.let { grow(it, FACE_MARGIN) }?.let { paint(it, Color.WHITE) }
            g.dispose()

            out.parentFile?.mkdirs()
            ImageIO.write(image, "png", out)
            Napier.i(
                "speaker mask: ${out.name} ${width}x$height white=${fmt(area)}" +
                    (if (others.isEmpty()) "" else " minus ${others.size} other(s)"),
            )
            out
        }.onFailure { Napier.w("speaker mask failed: ${it.message}") }.getOrNull()
    }

    private fun fmt(box: List<Double>): String =
        box.joinToString(",") { "%.2f".format(it) }

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
        return listOf(x0, y0, x1, y1)
    }

    /** Grows a box by [margin] on every side, clamped to the frame. */
    fun grow(box: List<Double>?, margin: Double): List<Double>? {
        if (box == null || box.size != 4) return null
        if (box.any { it.isNaN() }) return null
        val x0 = (minOf(box[0], box[2]) - margin).coerceIn(0.0, 1.0)
        val y0 = (minOf(box[1], box[3]) - margin).coerceIn(0.0, 1.0)
        val x1 = (maxOf(box[0], box[2]) + margin).coerceIn(0.0, 1.0)
        val y1 = (maxOf(box[1], box[3]) + margin).coerceIn(0.0, 1.0)
        if (x1 - x0 <= 0.0 || y1 - y0 <= 0.0) return null
        return listOf(x0, y0, x1, y1)
    }

    /**
     * The white region for a chosen speaker: their face when we have it, the
     * body box only as a fallback. One function so the app and the doctor can
     * never disagree about the geometry (they did, 2026-09-15).
     */
    fun speakerBox(faceBox: List<Double>?, personBox: List<Double>?): List<Double>? =
        faceRegion(faceBox) ?: usableBox(personBox)

    /**
     * The white area for a chosen speaker, built from their FACE rather than
     * their whole body (owner 2026-09-15, second report). A body box in a
     * photo where a father holds a child covers nearly the frame and the
     * child with it, and the detector's boxes for the OTHER person proved
     * unreliable — a face-sized region depends on one box only, and everyone
     * else is black by construction.
     */
    fun faceRegion(face: List<Double>?): List<Double>? {
        if (face == null || face.size != 4) return null
        if (face.any { it.isNaN() }) return null
        val x0 = minOf(face[0], face[2])
        val x1 = maxOf(face[0], face[2])
        val y0 = minOf(face[1], face[3])
        val y1 = maxOf(face[1], face[3])
        val w = x1 - x0
        val h = y1 - y0
        if (w < 0.02 || h < 0.02) return null
        // Head plus a little hair and shoulder, not the whole torso.
        val padX = w * 0.30
        val padY = h * 0.30
        return listOf(
            (x0 - padX).coerceIn(0.0, 1.0),
            (y0 - padY).coerceIn(0.0, 1.0),
            (x1 + padX).coerceIn(0.0, 1.0),
            (y1 + padY).coerceIn(0.0, 1.0),
        )
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
