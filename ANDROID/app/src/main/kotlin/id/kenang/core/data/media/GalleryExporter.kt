package id.kenang.core.data.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Publishes finished videos into the phone's gallery under
 * `Movies/Kenang/<nama proyek>/`, which is the Android equivalent of the
 * desktop "Folder Output" setting. MediaStore needs no storage permission on
 * API 29+, which is why minSdk is 29.
 */
class GalleryExporter(private val context: Context) {

    /** Copies an IMAGE into Pictures/Kenang/[folderName]/; returns its content Uri. */
    suspend fun exportImage(
        file: File,
        folderName: String,
        displayName: String = file.name,
    ): Uri? = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        val safeFolder = folderName.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().ifBlank { "Kenang" }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, if (displayName.endsWith(".png")) "image/png" else "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Kenang/$safeFolder")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = runCatching {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: run {
            Napier.w("gallery image export: insert failed for $displayName")
            return@withContext null
        }
        runCatching {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrElse { e ->
            Napier.w("gallery image export failed for $displayName: ${e.message}")
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    /** Copies [file] into Movies/Kenang/[folderName]/[subFolder]; returns its content Uri. */
    suspend fun export(
        file: File,
        folderName: String,
        displayName: String = file.name,
        subFolder: String? = null,
    ): Uri? = withContext(Dispatchers.IO) {
        if (!file.isFile) return@withContext null
        val safeFolder = folderName.replace(Regex("[\\\\/:*?\"<>|]"), "").trim().ifBlank { "Kenang" }
        val relative = buildString {
            append(Environment.DIRECTORY_MOVIES)
            append("/Kenang/")
            append(safeFolder)
            if (!subFolder.isNullOrBlank()) append("/").append(subFolder)
        }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, relative)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = runCatching {
            resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: run {
            Napier.w("gallery export: insert failed for $displayName")
            return@withContext null
        }
        runCatching {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        }.getOrElse { e ->
            Napier.w("gallery export failed for $displayName: ${e.message}")
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }
}
