package id.kenang.core.data.story

import io.github.aakira.napier.Napier
import java.io.File

/** Android [RatioCropper]: BitmapFactory center-crop (same math as desktop AWT). */
class AndroidRatioCropper : RatioCropper {
    override fun cropToRatio(file: File, ratio: String) {
        val target = if (ratio == "16:9") 16.0 / 9.0 else 9.0 / 16.0
        runCatching {
            val bmp = android.graphics.BitmapFactory.decodeFile(file.absolutePath) ?: return
            val current = bmp.width.toDouble() / bmp.height
            if (kotlin.math.abs(current - target) <= 0.01) return
            val w: Int
            val h: Int
            if (current > target) {
                h = bmp.height; w = (bmp.height * target).toInt().coerceAtLeast(1)
            } else {
                w = bmp.width; h = (bmp.width / target).toInt().coerceAtLeast(1)
            }
            val cropped = android.graphics.Bitmap.createBitmap(
                bmp, (bmp.width - w) / 2, (bmp.height - h) / 2, w, h,
            )
            file.outputStream().use {
                cropped.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it)
            }
        }.onFailure { Napier.w("ratio crop skipped: ${it.message}") }
    }

    override fun padToRatio(source: File, ratio: String, out: File): File? {
        val target = if (ratio == "16:9") 16.0 / 9.0 else 9.0 / 16.0
        return runCatching {
            val bmp = android.graphics.BitmapFactory.decodeFile(source.absolutePath) ?: return null
            val current = bmp.width.toDouble() / bmp.height
            if (kotlin.math.abs(current - target) <= 0.01) return null
            val (w, h) = canvasFor(bmp.width, bmp.height, target)
            val canvas = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(canvas).apply {
                drawColor(android.graphics.Color.rgb(PAD_GREY, PAD_GREY, PAD_GREY))
                drawBitmap(bmp, ((w - bmp.width) / 2).toFloat(), ((h - bmp.height) / 2).toFloat(), null)
            }
            out.parentFile?.mkdirs()
            out.outputStream().use { canvas.compress(android.graphics.Bitmap.CompressFormat.JPEG, 92, it) }
            canvas.recycle()
            bmp.recycle()
            out
        }.onFailure { Napier.w("ratio pad skipped: ${it.message}") }.getOrNull()
    }

    companion object {
        /** Flat grey the prompt names as "the bands" - neither black (reads as vignette) nor white (reads as paper). */
        const val PAD_GREY = 128

        /** The smallest canvas of [target] ratio that holds a [width]x[height] image whole. */
        fun canvasFor(width: Int, height: Int, target: Double): Pair<Int, Int> {
            val current = width.toDouble() / height
            return if (current > target) width to (width / target).toInt().coerceAtLeast(height)
            else (height * target).toInt().coerceAtLeast(width) to height
        }
    }
}
