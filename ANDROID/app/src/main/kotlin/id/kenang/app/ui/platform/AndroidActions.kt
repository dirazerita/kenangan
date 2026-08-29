package id.kenang.app.ui.platform

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import id.kenang.core.common.i18n.Strings
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The desktop build reaches for java.awt (Desktop.open, FileDialog, Toolkit
 * clipboard). Those don't exist on Android, so every such action funnels
 * through here: share sheets, the system viewer, and the clipboard.
 */
object AndroidActions {

    /** Content Uri for an app-private file, via the manifest's FileProvider. */
    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun copyToClipboard(context: Context, label: String, text: String) {
        runCatching {
            val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText(label, text))
        }.onFailure { Napier.w("clipboard copy failed: ${it.message}") }
    }

    fun openUrl(context: Context, url: String) {
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { Napier.w("open url failed: ${it.message}") }
    }

    /** Opens a finished video in whatever player the user has. */
    fun playVideo(context: Context, file: File) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uriFor(context, file), "video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure { Napier.w("play video failed: ${it.message}") }
    }

    /** Share sheet (WhatsApp, Drive, ...) for the finished video. */
    fun shareVideo(context: Context, file: File) {
        runCatching {
            val intent = Intent(Intent.ACTION_SEND)
                .setType("video/mp4")
                .putExtra(Intent.EXTRA_STREAM, uriFor(context, file))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(
                Intent.createChooser(intent, Strings.RESULT_SHARE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { Napier.w("share video failed: ${it.message}") }
    }

    /** Opens the gallery so the user can find Movies/Kenang/<project>. */
    fun openGallery(context: Context) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW)
                .setType("video/*")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure { Napier.w("open gallery failed: ${it.message}") }
    }

    /**
     * Copies picked content Uris into app storage — the whole pipeline works
     * on real Files (quality check, upload, keyframe replacement), and a
     * picker Uri permission does not survive a restart.
     */
    suspend fun importPicked(context: Context, uris: List<Uri>, targetDir: File): List<File> =
        withContext(Dispatchers.IO) {
            targetDir.mkdirs()
            uris.mapNotNull { uri ->
                runCatching {
                    val ext = extensionOf(context, uri)
                    val target = File(targetDir, "pick_${System.nanoTime()}.$ext")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        target.outputStream().use { input.copyTo(it) }
                    } ?: return@runCatching null
                    target.takeIf { it.length() > 0 }
                }.getOrElse {
                    Napier.w("import failed for $uri: ${it.message}")
                    null
                }
            }
        }

    private fun extensionOf(context: Context, uri: Uri): String {
        val mime = context.contentResolver.getType(uri).orEmpty()
        return when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("bmp") -> "bmp"
            mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
            mime.contains("wav") -> "wav"
            mime.contains("aac") || mime.contains("m4a") || mime.contains("mp4") -> "m4a"
            mime.contains("ogg") -> "ogg"
            else -> "jpg"
        }
    }
}
