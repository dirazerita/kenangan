@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)

package id.kenang.app.ui.components

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.io.File

/**
 * Explorer drag-and-drop onto a composable (owner 2026-09-02: EVERY image/
 * video input should accept a dropped file). Same AWT-transferable dance the
 * wizard and upscale screens proved out, packaged once. [onHover] toggles the
 * caller's highlight; [onFiles] receives the dropped files (never empty).
 */
@Composable
fun Modifier.filesDropTarget(
    enabled: Boolean = true,
    onHover: (Boolean) -> Unit = {},
    onFiles: (List<File>) -> Unit,
): Modifier {
    val enabledState = rememberUpdatedState(enabled)
    val hoverState = rememberUpdatedState(onHover)
    val filesState = rememberUpdatedState(onFiles)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { hoverState.value(true) }
            override fun onExited(event: DragAndDropEvent) { hoverState.value(false) }
            override fun onEnded(event: DragAndDropEvent) { hoverState.value(false) }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hoverState.value(false)
                if (!enabledState.value) return false
                val transferable = event.awtTransferable
                if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
                val files = runCatching {
                    (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                        .orEmpty().filterIsInstance<File>().filter { it.isFile }
                }.getOrDefault(emptyList())
                if (files.isEmpty()) return false
                filesState.value(files)
                return true
            }
        }
    }
    return this.dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)
}

/** Common image extensions accepted by the photo inputs. */
val IMAGE_DROP_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp")
