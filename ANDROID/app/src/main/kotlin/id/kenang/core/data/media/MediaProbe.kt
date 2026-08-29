package id.kenang.core.data.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import io.github.aakira.napier.Napier
import java.io.File

/**
 * Android replacement for the desktop ffprobe calls: clip duration and poster
 * frames come from MediaMetadataRetriever.
 */
object MediaProbe {

    /** Duration in milliseconds, or null when the file is unreadable. */
    fun durationMs(file: File): Long? {
        if (!file.isFile) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (e: Exception) {
            Napier.w("probe failed for ${file.name}: ${e.message}")
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** Writes a JPEG poster frame (1s in) next to the video; false on failure. */
    fun extractThumbnail(video: File, target: File): Boolean {
        if (!video.isFile) return false
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(video.absolutePath)
            val frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
                ?: return false
            target.parentFile?.mkdirs()
            target.outputStream().use { out -> frame.compress(Bitmap.CompressFormat.JPEG, 88, out) }
            frame.recycle()
            true
        } catch (e: Exception) {
            Napier.w("thumbnail failed for ${video.name}: ${e.message}")
            false
        } finally {
            runCatching { retriever.release() }
        }
    }
}
