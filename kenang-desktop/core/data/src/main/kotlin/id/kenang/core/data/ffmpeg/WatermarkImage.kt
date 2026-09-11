package id.kenang.core.data.ffmpeg

import io.github.aakira.napier.Napier
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.font.FontRenderContext
import java.awt.font.TextLayout
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Full-frame watermark for the preview copy (owner 2026-09-11): a video sent
 * to a customer BEFORE payment must be unmistakably marked, so the text is
 * huge and centred rather than a discreet corner badge.
 *
 * Drawn with Java2D instead of ffmpeg's drawtext: that filter needs a font
 * file path inside filter_complex, which on Windows means escaping a drive
 * colon and spaces - a trap this project has been bitten by before. A PNG the
 * exact size of the frame overlays at 0:0 with nothing to escape.
 */
object WatermarkImage {

    /** What the watermark says. Kept here so both platforms read one source. */
    const val TEXT = "VIDEO KENANGAN"

    /** Opaque enough to deter reuse, sheer enough to still show the memory. */
    private const val FILL_ALPHA = 0.62f
    private const val OUTLINE_ALPHA = 0.75f

    /**
     * Returns a cached PNG of [width]x[height] carrying [text]. Regenerated
     * only when missing, so repeated exports pay nothing.
     */
    fun render(width: Int, height: Int, dir: File, text: String = TEXT): File? = runCatching {
        dir.mkdirs()
        val target = File(dir, "wm_${width}x${height}.png")
        if (target.isFile && target.length() > 0) return target

        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.composite = AlphaComposite.Src

        val lines = layoutLines(text, width, height)
        val frc = g.fontRenderContext
        val laidOut = lines.map { (line, font) -> TextLayout(line, font, frc) }
        val lineHeights = laidOut.map { it.bounds.height }
        val gap = height * 0.02
        val totalHeight = lineHeights.sum() + gap * (laidOut.size - 1)

        var y = (height - totalHeight) / 2
        laidOut.forEachIndexed { i, layout ->
            val bounds = layout.bounds
            y += lineHeights[i]
            val x = (width - bounds.width) / 2 - bounds.x
            val outline = layout.getOutline(
                java.awt.geom.AffineTransform.getTranslateInstance(x, y - bounds.y - bounds.height),
            )
            // Dark edge first so the text survives a bright frame underneath.
            g.color = Color(0f, 0f, 0f, OUTLINE_ALPHA)
            g.stroke = BasicStroke((width * 0.006f).coerceAtLeast(3f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            g.draw(outline)
            g.color = Color(1f, 1f, 1f, FILL_ALPHA)
            g.fill(outline)
            y += gap
        }
        g.dispose()

        ImageIO.write(image, "png", target)
        Napier.i("watermark rendered: ${target.name}")
        target
    }.onFailure { Napier.e("watermark render failed: ${it.message}") }.getOrNull()

    /**
     * Splits [text] so it fills the frame: one line in landscape, stacked
     * words in portrait, each sized to ~88% of the width.
     */
    private fun layoutLines(text: String, width: Int, height: Int): List<Pair<String, Font>> {
        val words = text.split(" ").filter { it.isNotBlank() }
        val portrait = height > width
        val lines = if (portrait && words.size > 1) words else listOf(text)
        val maxWidth = width * 0.88
        // Also cap by height so many lines never overflow the frame.
        val maxLineHeight = height * 0.72 / lines.size
        return lines.map { line -> line to fitFont(line, maxWidth, maxLineHeight) }
    }

    /** Largest font whose rendered [line] fits the given box. */
    private fun fitFont(line: String, maxWidth: Double, maxHeight: Double): Font {
        val frc = FontRenderContext(null, true, true)
        var size = 10
        var best = baseFont(size)
        while (size < 1000) {
            val candidate = baseFont(size)
            val bounds = TextLayout(line, candidate, frc).bounds
            if (bounds.width > maxWidth || bounds.height > maxHeight) break
            best = candidate
            size += 2
        }
        return best
    }

    /** Arial Black when Windows has it (it usually does), else a bold sans. */
    private fun baseFont(size: Int): Font {
        val black = Font("Arial Black", Font.BOLD, size)
        return if (black.family.equals("Arial Black", ignoreCase = true)) black
        else Font(Font.SANS_SERIF, Font.BOLD, size)
    }
}
