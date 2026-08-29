package id.kenang.app.ui.about
import id.kenang.app.ui.theme.SkeuoButton
import id.kenang.app.ui.theme.SkeuoCard
import id.kenang.app.ui.theme.SkeuoOutlinedButton

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
        // CC-BY attribution for the bundled music collection (config-driven).
        val bundledMusic = org.koin.compose.koinInject<id.kenang.core.data.config.ConfigRepository>()
            .current().bundledMusic
        bundledMusic.map { it.credit }.distinct().forEach { credit ->
            Spacer(Modifier.height(4.dp))
            Text(
                Strings.ABOUT_MUSIC_PREFIX + credit +
                    " (${bundledMusic.size} lagu: ${bundledMusic.joinToString(", ") { it.labelId.substringBefore(" —") }})",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(20.dp))
        id.kenang.app.ui.components.BrandBadge()

        Spacer(Modifier.height(24.dp))
        SkeuoOutlinedButton(onClick = onBack) { Text(Strings.BACK) }
    }
}
