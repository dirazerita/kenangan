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
}
