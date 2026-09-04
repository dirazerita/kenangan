package id.kenang.app.ui.storyboard

import id.kenang.core.db.Scene
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO

/**
 * Renders the whole storyboard (every scene keyframe + its Indonesian motion
 * summary) onto ONE shareable PNG contact sheet.
 *
 * Owner request 2026-09-04: customers routinely ask to see the full storyboard
 * as a single image for approval BEFORE the video package is generated. Pure
 * local AWT drawing — zero AI cost, works offline.
 */
object StoryboardSheetRenderer {

    private val BG = Color(0x0B, 0x1B, 0x2B)
    private val CARD_BG = Color(0x14, 0x2A, 0x40)
    private val CARD_BORDER = Color(0x2A, 0x44, 0x5E)
    private val TEXT = Color(0xEC, 0xF2, 0xF8)
    private val TEXT_DIM = Color(0x9F, 0xB2, 0xC4)
    private val CHIP_BG = Color(0x1E, 0x4E, 0x7A)
    private val PLACEHOLDER_BG = Color(0x0F, 0x22, 0x33)

    private const val PAD = 32
    private const val GAP = 24
    private const val CAPTION_H = 190
    private const val HEADER_H = 118
    private const val FOOTER_H = 48

    /** Draws the sheet and writes it as PNG. Returns the written file. */
    fun render(
        projectName: String,
        ratioLabel: String,
        scenes: List<Scene>,
        outFile: File,
    ): File {
        require(scenes.isNotEmpty()) { "storyboard has no scenes" }

        val portrait = ratioLabel.trim() == "9:16"
        val imgW = if (portrait) 340 else 560
        val imgH = if (portrait) 604 else 315
        val cols = when {
            scenes.size == 1 -> 1
            portrait -> minOf(4, scenes.size)
            else -> minOf(3, scenes.size)
        }
        val rows = (scenes.size + cols - 1) / cols
        val cellH = imgH + CAPTION_H

        val sheetW = PAD * 2 + cols * imgW + (cols - 1) * GAP
        val sheetH = HEADER_H + PAD + rows * cellH + (rows - 1) * GAP + FOOTER_H + PAD

        val sheet = BufferedImage(sheetW, sheetH, BufferedImage.TYPE_INT_RGB)
        val g = sheet.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)

            g.color = BG
            g.fillRect(0, 0, sheetW, sheetH)

            drawHeader(g, sheetW, projectName, ratioLabel, scenes)

            scenes.forEachIndexed { i, scene ->
                val col = i % cols
                val row = i / cols
                val x = PAD + col * (imgW + GAP)
                val y = HEADER_H + PAD + row * (cellH + GAP)
                drawCell(g, scene, i + 1, x, y, imgW, imgH)
            }

            // Footer
            g.font = Font("Segoe UI", Font.PLAIN, 16)
            g.color = TEXT_DIM
            val footer = "Dibuat dengan Kenang"
            val fw = g.fontMetrics.stringWidth(footer)
            g.drawString(footer, (sheetW - fw) / 2, sheetH - PAD)
        } finally {
            g.dispose()
        }

        outFile.parentFile?.mkdirs()
        ImageIO.write(sheet, "png", outFile)
        return outFile
    }

    private fun drawHeader(g: Graphics2D, sheetW: Int, projectName: String, ratioLabel: String, scenes: List<Scene>) {
        g.font = Font("Segoe UI", Font.BOLD, 34)
        g.color = TEXT
        g.drawString("STORYBOARD — $projectName", PAD, 58)

        val totalS = scenes.sumOf { it.duration_s }
        val tanggal = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
        g.font = Font("Segoe UI", Font.PLAIN, 18)
        g.color = TEXT_DIM
        g.drawString("${scenes.size} adegan · total ${totalS}s · $ratioLabel · $tanggal", PAD, 90)

        g.color = CARD_BORDER
        g.stroke = BasicStroke(1f)
        g.drawLine(PAD, HEADER_H - 8, sheetW - PAD, HEADER_H - 8)
    }

    private fun drawCell(g: Graphics2D, scene: Scene, nomor: Int, x: Int, y: Int, imgW: Int, imgH: Int) {
        // Card behind image + caption
        g.color = CARD_BG
        g.fillRoundRect(x - 6, y - 6, imgW + 12, imgH + CAPTION_H + 6, 18, 18)
        g.color = CARD_BORDER
        g.drawRoundRect(x - 6, y - 6, imgW + 12, imgH + CAPTION_H + 6, 18, 18)

        drawKeyframe(g, scene, x, y, imgW, imgH)

        // Scene number chip on top of the image
        g.font = Font("Segoe UI", Font.BOLD, 17)
        val chip = "Adegan $nomor · ${scene.duration_s}s"
        val cw = g.fontMetrics.stringWidth(chip) + 22
        g.color = CHIP_BG
        g.fillRoundRect(x + 10, y + 10, cw, 30, 15, 15)
        g.color = TEXT
        g.drawString(chip, x + 21, y + 31)

        // Caption: Indonesian motion summary, wrapped + ellipsized
        val teks = scene.motion_summary_id?.trim().orEmpty()
            .ifBlank { "(belum ada deskripsi gerakan)" }
        g.font = Font("Segoe UI", Font.PLAIN, 16)
        g.color = TEXT
        val lineH = g.fontMetrics.height
        val maxLines = (CAPTION_H - 24) / lineH
        val lines = wrap(g, teks, imgW - 8, maxLines)
        var ty = y + imgH + 14 + g.fontMetrics.ascent
        for (line in lines) {
            g.drawString(line, x + 2, ty)
            ty += lineH
        }
    }

    private fun drawKeyframe(g: Graphics2D, scene: Scene, x: Int, y: Int, w: Int, h: Int) {
        val img = scene.local_keyframe_path
            ?.let(::File)?.takeIf { it.isFile }
            ?.let { runCatching { ImageIO.read(it) }.getOrNull() }

        if (img == null) {
            g.color = PLACEHOLDER_BG
            g.fillRect(x, y, w, h)
            g.color = TEXT_DIM
            g.font = Font("Segoe UI", Font.ITALIC, 16)
            val t = "gambar belum dibuat"
            g.drawString(t, x + (w - g.fontMetrics.stringWidth(t)) / 2, y + h / 2)
            return
        }

        // Center-crop the source so the cell is always fully covered
        val srcRatio = img.width.toDouble() / img.height
        val dstRatio = w.toDouble() / h
        var sx = 0
        var sy = 0
        var sw = img.width
        var sh = img.height
        if (srcRatio > dstRatio) {
            sw = (img.height * dstRatio).toInt()
            sx = (img.width - sw) / 2
        } else {
            sh = (img.width / dstRatio).toInt()
            sy = (img.height - sh) / 2
        }
        g.drawImage(img, x, y, x + w, y + h, sx, sy, sx + sw, sy + sh, null)
    }

    /** Greedy word-wrap to [maxWidth] px, at most [maxLines] lines, "…" on overflow. */
    private fun wrap(g: Graphics2D, text: String, maxWidth: Int, maxLines: Int): List<String> {
        val fm = g.fontMetrics
        val all = mutableListOf<String>()
        var line = ""
        for (word in text.split(Regex("\\s+"))) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (fm.stringWidth(candidate) <= maxWidth) {
                line = candidate
            } else {
                if (line.isNotEmpty()) all.add(line)
                line = word
            }
        }
        if (line.isNotEmpty()) all.add(line)

        if (all.size <= maxLines) return all

        val kept = all.take(maxLines).toMutableList()
        var last = kept.last()
        while (last.isNotEmpty() && fm.stringWidth("$last…") > maxWidth) {
            last = last.dropLast(1).trimEnd()
        }
        kept[kept.size - 1] = "$last…"
        return kept
    }
}
