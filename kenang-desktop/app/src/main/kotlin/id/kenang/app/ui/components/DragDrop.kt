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
import id.kenang.core.common.i18n.Strings
import io.github.aakira.napier.Napier
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
            // A disabled zone must not light up: it would promise a drop it
            // cannot accept (owner 2026-09-08).
            override fun onEntered(event: DragAndDropEvent) {
                if (enabledState.value) hoverState.value(true)
            }
            override fun onExited(event: DragAndDropEvent) { hoverState.value(false) }
            override fun onEnded(event: DragAndDropEvent) { hoverState.value(false) }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hoverState.value(false)
                // Logged at every outcome (owner 2026-09-08 asked for drag and
                // drop on a control that already had it): when a drop "does
                // nothing", the log says whether it even reached the app.
                if (!enabledState.value) {
                    Napier.i("drop ignored: target disabled")
                    return false
                }
                val transferable = event.awtTransferable
                if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    Napier.w("drop rejected: not a file (drag from a folder, not from a browser)")
                    return false
                }
                val files = runCatching {
                    (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                        .orEmpty().filterIsInstance<File>().filter { it.isFile }
                }.getOrDefault(emptyList())
                if (files.isEmpty()) {
                    Napier.w("drop rejected: no readable file in the transfer")
                    return false
                }
                Napier.i("drop accepted: ${files.size} file(s), first=${files.first().name}")
                filesState.value(files)
                return true
            }
        }
    }
    // Registering interest even when disabled would SWALLOW the drop: Compose
    // hands onDrop to the innermost interested node with no fallback to its
    // parent, so a dead inner zone silently eats a drop the surrounding card
    // would have accepted (owner 2026-09-08).
    return this.dragAndDropTarget(
        shouldStartDragAndDrop = { enabledState.value },
        target = target,
    )
}

/**
 * First dropped file this app can use as a photo. When the drop carried
 * nothing usable the user is TOLD — a silently ignored drop is the failure
 * mode that makes the feature look broken (owner 2026-09-08).
 */
fun List<File>.firstImageOrExplain(onReject: (String) -> Unit): File? {
    val image = firstOrNull { it.extension.lowercase() in IMAGE_DROP_EXTENSIONS }
    if (image == null) {
        onReject(Strings.DROP_UNSUPPORTED + " (" + (firstOrNull()?.name ?: "?") + ")")
    }
    return image
}

/**
 * Image extensions accepted by every photo input — incl. phone formats
 * (HEIC/HEIF/AVIF, owner 2026-09-07) that PhotoImport converts to JPEG.
 */
val IMAGE_DROP_EXTENSIONS = id.kenang.core.data.story.ImageImport.ACCEPTED.toSet()
