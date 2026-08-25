package id.kenang.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage
import java.io.File

/** Loads an image file into an ImageBitmap off the UI thread; null while loading/missing. */
@Composable
fun rememberFileBitmap(path: String?): State<ImageBitmap?> =
    produceState<ImageBitmap?>(initialValue = null, path) {
        if (path == null) { value = null; return@produceState }
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = File(path).takeIf { it.isFile }?.readBytes() ?: return@runCatching null
                SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
            }.getOrNull()
        }
    }
