package id.kenang.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import id.kenang.core.common.i18n.Strings

/** Offline banner shown app-wide when the network probe fails. */
@Composable
fun OfflineBanner(visible: Boolean) {
    if (!visible) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF9A6A00))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(Strings.OFFLINE_BANNER, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Small rounded status chip (project status, key status). */
@Composable
fun StatusChip(text: String, color: Color = MaterialTheme.colorScheme.secondary) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f),
        contentColor = color,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/** Confirmation dialog (destructive actions like project delete). */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(Strings.CANCEL) }
        },
    )
}

/** Opens a URL in the system browser (onboarding "buka halaman key" buttons). */
fun openInBrowser(url: String) {
    runCatching {
        val desktop = java.awt.Desktop.getDesktop()
        if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
            desktop.browse(java.net.URI(url))
        }
    }
}
