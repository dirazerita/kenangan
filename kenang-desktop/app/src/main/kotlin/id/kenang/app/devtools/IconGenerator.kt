package id.kenang.app.devtools

import java.awt.BasicStroke
import java.awt.Color
import java.awt.LinearGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import javax.imageio.ImageIO

/**
 * Draws the Kenang app icon and writes it as a multi-size Windows .ico plus a
 * PNG for the running window/taskbar. Source-of-truth for the icon so it can
 * be regenerated or tweaked instead of living as an opaque binary.
 *
 * Design: skeuomorphic navy tile (same palette as the app theme) with a glossy
 * top highlight, a warm golden heart (kenangan) glowing softly, and a play
 * triangle knocked out of it (memory VIDEO).
 *
 * Run: gradlew :app:generateIcon
 */
fun main() {
    val appDir = File(System.getProperty("icon.appDir") ?: "app")
    val icoFile = File(appDir, "icons/kenang.ico").apply { parentFile.mkdirs() }
    val pngFile = File(appDir, "src/main/resources/icon/kenang_512.png").apply { parentFile.mkdirs() }

    val sizes = listOf(16, 24, 32, 48, 64, 128, 256)
    val images = sizes.associateWith { drawIcon(it) }

    ImageIO.write(drawIcon(512), "png", pngFile)
    writeIco(images.values.toList(), icoFile)

    println("icon written:")
    println("  ${icoFile.absolutePath} (${icoFile.length() / 1024} KB, sizes ${sizes.joinToString()})")
    println("  ${pngFile.absolutePath} (${pngFile.length() / 1024} KB, 512px)")
}

private fun drawIcon(size: Int): BufferedImage {
    val img = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    val s = size.toFloat()
    val pad = s * 0.045f
    val tile = RoundRectangle2D.Float(pad, pad, s - 2 * pad, s - 2 * pad, s * 0.24f, s * 0.24f)

    // --- navy tile with vertical gradient (app palette) ---
    g.paint = LinearGradientPaint(
        Point2D.Float(0f, pad), Point2D.Float(0f, s - pad),
        floatArrayOf(0f, 0.55f, 1f),
        arrayOf(Color(0x3D8BFF), Color(0x1B4E8E), Color(0x0B1E36)),
    )
    g.fill(tile)

    // --- glossy highlight across the upper half (skeuomorphic) ---
    val oldClip = g.clip
    g.clip(tile)
    g.paint = LinearGradientPaint(
        Point2D.Float(0f, pad), Point2D.Float(0f, s * 0.58f),
        floatArrayOf(0f, 1f),
        arrayOf(Color(255, 255, 255, 90), Color(255, 255, 255, 0)),
    )
    g.fill(RoundRectangle2D.Float(pad, pad, s - 2 * pad, s * 0.52f, s * 0.24f, s * 0.24f))
    g.clip = oldClip

    // --- warm glow behind the heart ---
    val cx = s / 2f
    val cy = s * 0.52f
    val r = s * 0.30f
    if (size >= 32) {
        g.paint = RadialGradientPaint(
            Point2D.Float(cx, cy), r * 1.9f,
            floatArrayOf(0f, 1f),
            arrayOf(Color(255, 190, 120, 110), Color(255, 190, 120, 0)),
        )
        g.fill(Ellipse2D.Float(cx - r * 1.9f, cy - r * 1.9f, r * 3.8f, r * 3.8f))
    }

    // --- heart (kenangan) ---
    val heart = heartPath(cx, cy, r)
    g.paint = LinearGradientPaint(
        Point2D.Float(0f, cy - r), Point2D.Float(0f, cy + r),
        floatArrayOf(0f, 1f),
        arrayOf(Color(0xFFE49A.toInt()), Color(0xFF8B57.toInt())),
    )
    g.fill(heart)
    if (size >= 48) {
        g.paint = Color(255, 255, 255, 70)
        g.stroke = BasicStroke(s * 0.012f)
        g.draw(heart)
    }

    // --- play triangle knocked out of the heart (memory VIDEO) ---
    val t = s * 0.115f
    val tri = Path2D.Float().apply {
        moveTo(cx - t * 0.62f, cy - t)
        lineTo(cx + t * 0.95f, cy)
        lineTo(cx - t * 0.62f, cy + t)
        closePath()
    }
    g.paint = Color(0x0B1E36)
    g.fill(tri)

    // --- top bevel edge ---
    if (size >= 32) {
        g.paint = Color(255, 255, 255, 60)
        g.stroke = BasicStroke(s * 0.012f)
        g.draw(tile)
    }

    g.dispose()
    return img
}

/** Symmetric heart built from four cubic segments; [r] is the half-height. */
private fun heartPath(cx: Float, cy: Float, r: Float): Path2D.Float = Path2D.Float().apply {
    moveTo(cx, cy + r * 0.95f)
    curveTo(cx - r * 1.35f, cy + r * 0.05f, cx - r * 1.05f, cy - r * 0.98f, cx - r * 0.42f, cy - r * 0.72f)
    curveTo(cx - r * 0.16f, cy - r * 0.60f, cx - r * 0.05f, cy - r * 0.44f, cx, cy - r * 0.30f)
    curveTo(cx + r * 0.05f, cy - r * 0.44f, cx + r * 0.16f, cy - r * 0.60f, cx + r * 0.42f, cy - r * 0.72f)
    curveTo(cx + r * 1.05f, cy - r * 0.98f, cx + r * 1.35f, cy + r * 0.05f, cx, cy + r * 0.95f)
    closePath()
}

/**
 * Minimal ICO writer: PNG-compressed entries (supported by Windows Vista+),
 * so no BMP/AND-mask handling is needed.
 */
private fun writeIco(images: List<BufferedImage>, target: File) {
    val payloads = images.map { img ->
        ByteArrayOutputStream().also { ImageIO.write(img, "png", it) }.toByteArray()
    }
    DataOutputStream(target.outputStream().buffered()).use { out ->
        fun le16(v: Int) { out.write(v and 0xFF); out.write((v ushr 8) and 0xFF) }
        fun le32(v: Int) {
            out.write(v and 0xFF); out.write((v ushr 8) and 0xFF)
            out.write((v ushr 16) and 0xFF); out.write((v ushr 24) and 0xFF)
        }
        le16(0); le16(1); le16(images.size)          // ICONDIR
        var offset = 6 + images.size * 16
        images.forEachIndexed { i, img ->
            out.write(if (img.width >= 256) 0 else img.width)
            out.write(if (img.height >= 256) 0 else img.height)
            out.write(0); out.write(0)               // palette, reserved
            le16(1); le16(32)                        // planes, bpp
            le32(payloads[i].size); le32(offset)
            offset += payloads[i].size
        }
        payloads.forEach { out.write(it) }
    }
}
