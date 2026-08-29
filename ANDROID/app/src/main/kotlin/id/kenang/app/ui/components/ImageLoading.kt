package id.kenang.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import id.kenang.core.data.story.ImageQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Loads an image file into an ImageBitmap off the UI thread; null while
 * loading/missing. Always downsampled — a storyboard grid of 12 full-size
 * photos would blow a phone's heap.
 */
@Composable
fun rememberFileBitmap(path: String?, maxSide: Int = 1080): State<ImageBitmap?> =
    produceState<ImageBitmap?>(initialValue = null, path, maxSide) {
        if (path == null) { value = null; return@produceState }
        value = withContext(Dispatchers.IO) {
            runCatching {
                val file = File(path).takeIf { it.isFile } ?: return@runCatching null
                ImageQuality.decodeDownsampled(file, maxSide)?.asImageBitmap()
            }.getOrNull()
        }
    }
