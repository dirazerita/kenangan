package id.kenang.core.data.story

import io.github.aakira.napier.Napier
import kotlin.math.abs
import kotlin.math.ln

/**
 * Puts vision-model face boxes into the app's [x0, y0, x1, y1] order.
 *
 * Owner 2026-09-15 (Rahayu RO 1, a family of six): the model answered with
 * its boxes in ITS native order, [y0, x0, y1, x1] - Gemini's box convention -
 * although the prompt asked for x first. Read as x-first, every "face" landed
 * on a wall, a chest or a pair of feet, and those crops were then handed to
 * the image model labelled as the faces it had to reproduce. The same flip
 * put the child's box on her father's chest in Video Berbicara.
 *
 * A prompt cannot make this go away, so the geometry decides. A real face box
 * is about as tall as it is wide (forehead to chin, a little hair), while a
 * swapped box on a non-square photo is stretched by the photo's aspect ratio
 * squared - the two readings are easy to tell apart. Measured on the two
 * real photos in the owner's database: Rahayu's boxes read 0.32 tall-per-wide
 * as stored and 0.96 swapped; the verified two-person photo read 1.34 as
 * stored and 2.39 swapped. A square photo cannot be judged here and is left
 * alone - the crop check in FaceLock catches that case.
 */
object FaceBoxes {

    /** Pixel height / width a face box can plausibly have. */
    private const val MIN_ASPECT = 0.70
    private const val MAX_ASPECT = 2.20

    /** Forehead to chin, a touch of hair: what the analysis prompt asks for. */
    private const val IDEAL_ASPECT = 1.25

    data class Orientation(
        val transposed: Boolean,
        /** Median pixel aspect (height / width) of the boxes as stored; null without usable boxes. */
        val storedAspect: Double?,
        /** The same, with the axes swapped. */
        val swappedAspect: Double?,
    )

    /** [box] read with its axes swapped: [y0, x0, y1, x1] becomes [x0, y0, x1, y1]. */
    fun swap(box: List<Double>): List<Double> = listOf(box[1], box[0], box[3], box[2])

    /**
     * Whether [boxes] (fractions, as stored) are transposed for a photo of
     * [width] x [height] pixels. The median over every usable box decides,
     * and only when the stored reading is not a face while the swapped one
     * is: a legitimate box is never touched on that evidence alone.
     */
    fun orientation(boxes: List<List<Double>?>, width: Int, height: Int): Orientation {
        if (width <= 0 || height <= 0) return Orientation(false, null, null)
        val usable = boxes.filterNotNull().filter { FaceCrops.isPlausible(it) }
        if (usable.isEmpty()) return Orientation(false, null, null)

        val stored = median(usable.map { pixelAspect(it, width, height) })
        val swapped = median(usable.map { pixelAspect(swap(it), width, height) })
        val storedIsFace = stored in MIN_ASPECT..MAX_ASPECT
        val swappedIsFace = swapped in MIN_ASPECT..MAX_ASPECT
        val transposed = !storedIsFace && swappedIsFace && distance(swapped) < distance(stored)
        return Orientation(transposed, stored, swapped)
    }

    /**
     * [boxes] in [x0, y0, x1, y1] order for a [width] x [height] photo:
     * swapped when [orientation] says they were stored transposed, untouched
     * otherwise. Logs the decision so a bad run can be read from the log.
     */
    fun orient(boxes: List<List<Double>?>, width: Int, height: Int, what: String = "face boxes"): List<List<Double>?> {
        val o = orientation(boxes, width, height)
        if (!o.transposed) return boxes
        Napier.w(
            "$what were stored as [y0,x0,y1,x1] (aspect %.2f as stored, %.2f swapped) - axes swapped"
                .format(o.storedAspect, o.swappedAspect),
        )
        return boxes.map { it?.let(::swap) }
    }

    private fun pixelAspect(box: List<Double>, width: Int, height: Int): Double {
        val w = (box[2] - box[0]) * width
        val h = (box[3] - box[1]) * height
        return if (w <= 0.0) Double.MAX_VALUE else h / w
    }

    private fun distance(aspect: Double): Double = abs(ln(aspect / IDEAL_ASPECT))

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }
}
