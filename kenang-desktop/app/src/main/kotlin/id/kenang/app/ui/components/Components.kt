package id.kenang.app.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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

/**
 * Owner brand badge: bright cyan→gold gradient text inside a glass pill with a
 * softly pulsing glow, so it reads as a premium signature rather than a
 * footnote (owner request 2026-08-28).
 */
@Composable
fun BrandBadge(modifier: Modifier = Modifier) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition()
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(1800),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
    )
    val cyan = Color(0xFF7DF9FF)
    val gold = Color(0xFFFFD37A)
    Box(
        modifier
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(cyan.copy(alpha = 0.12f * glow), gold.copy(alpha = 0.12f * glow)),
                ),
                RoundedCornerShape(50),
            )
            .border(
                1.dp,
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(cyan.copy(alpha = 0.55f * glow), gold.copy(alpha = 0.55f * glow)),
                ),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 16.dp, vertical = 7.dp),
    ) {
        Text(
            Strings.BRAND_TEXT,
            style = MaterialTheme.typography.labelLarge.copy(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(cyan, gold)),
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = cyan.copy(alpha = 0.85f * glow),
                    blurRadius = 18f * glow,
                ),
                letterSpacing = androidx.compose.ui.unit.TextUnit(0.4f, androidx.compose.ui.unit.TextUnitType.Sp),
            ),
        )
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

/**
 * Plain URL shown under a "Buka …" button so users can also just copy it:
 * selectable text + a one-click "Salin" that flips to "Disalin ✓" briefly.
 */
@Composable
fun CopyableUrl(url: String) {
    var copied by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(copied) {
        if (copied) {
            kotlinx.coroutines.delay(2500)
            copied = false
        }
    }
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.text.selection.SelectionContainer {
            Text(
                url,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        TextButton(onClick = {
            runCatching {
                java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    .setContents(java.awt.datatransfer.StringSelection(url), null)
            }
            copied = true
        }) {
            Text(
                if (copied) Strings.KEYS_LINK_COPIED else Strings.KEYS_COPY_LINK,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
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
