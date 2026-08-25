package id.kenang.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WarmPrimary = Color(0xFF8D5B4C)      // warm sepia — memory/nostalgia tone
private val WarmSecondary = Color(0xFFB08968)
private val WarmBackground = Color(0xFFFDF8F4)

private val LightColors = lightColorScheme(
    primary = WarmPrimary,
    secondary = WarmSecondary,
    background = WarmBackground,
    surface = Color(0xFFFFFDFB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE3B8A5),
    secondary = WarmSecondary,
)

@Composable
fun KenangTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
