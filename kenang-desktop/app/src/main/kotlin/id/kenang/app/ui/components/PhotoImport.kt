package id.kenang.app.ui.components

import id.kenang.core.data.ffmpeg.FfmpegLocator
import id.kenang.core.data.story.ImageImport
import java.io.File

/**
 * App-side entry to [ImageImport]: resolves the bundled ffmpeg lazily via
 * Koin so non-composable state classes can normalize without constructor
 * churn. HEIC/HEIF/AVIF/WebP/TIFF become JPEG on the way in.
 */
object PhotoImport {
    private val ffmpeg: File? by lazy {
        runCatching {
            org.koin.core.context.GlobalContext.get().get<FfmpegLocator>().locate()
        }.getOrNull()
    }

    fun normalize(file: File): File = ImageImport.normalize(file, ffmpeg)

    fun normalizeAll(files: List<File>): List<File> = files.map(::normalize)
}
