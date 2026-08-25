package id.kenang.app.ui.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import id.kenang.app.ui.components.openInBrowser
import id.kenang.core.common.i18n.Strings

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp)) {
        Text(Strings.ABOUT_TITLE, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            Strings.ABOUT_PRIVACY_HEADLINE,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
        Spacer(Modifier.height(24.dp))

        // TODO: real URLs once the license website pages are published (Phase 01/05).
        TextButton(onClick = { openInBrowser("https://kenang.id/privacy") }) { Text(Strings.ABOUT_PRIVACY) }
        TextButton(onClick = { openInBrowser("https://kenang.id/terms") }) { Text(Strings.ABOUT_TERMS) }
        TextButton(onClick = { openInBrowser("https://kenang.id/licenses") }) { Text(Strings.ABOUT_LICENSES) }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        Text(Strings.ABOUT_FFMPEG, style = MaterialTheme.typography.bodySmall)
        // CC-BY attribution for every bundled track (config-driven, Phase 04).
        val bundledMusic = org.koin.compose.koinInject<id.kenang.core.data.config.ConfigRepository>()
            .current().bundledMusic
        bundledMusic.forEach { track ->
            Spacer(Modifier.height(4.dp))
            Text(Strings.ABOUT_MUSIC_PREFIX + track.credit, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onBack) { Text(Strings.BACK) }
    }
}
