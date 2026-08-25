package id.kenang.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Skeuomorphic navy-blue theme (owner request, Stabilization 2026-08-25):
 * one committed look — deep blue layered surfaces, bright near-white text,
 * glossy raised controls (see Skeuo.kt). No system light/dark switch.
 */
object KenangPalette {
    val primary = Color(0xFF56A5FF)
    val backgroundTop = Color(0xFF16304F)     // app gradient start
    val backgroundBottom = Color(0xFF081220)  // app gradient end
    val surface = Color(0xFF152A44)
    val surfaceHigh = Color(0xFF1C3556)       // dialogs, raised panels
    val textBright = Color(0xFFEAF3FF)
    val textDim = Color(0xFFA9C4E4)
}

private val BlueColors = darkColorScheme(
    primary = KenangPalette.primary,
    onPrimary = Color(0xFFF6FAFF),
    primaryContainer = Color(0xFF1E4976),
    onPrimaryContainer = Color(0xFFD7E9FF),
    secondary = Color(0xFF8FC1F5),
    onSecondary = Color(0xFF0A1B2C),
    secondaryContainer = Color(0xFF1B3A5D),
    onSecondaryContainer = Color(0xFFCBE2FA),
    tertiary = Color(0xFF9AD8FF),
    background = Color(0xFF0C1A2C),
    onBackground = KenangPalette.textBright,
    surface = KenangPalette.surface,
    onSurface = KenangPalette.textBright,
    surfaceVariant = Color(0xFF1B3049),
    onSurfaceVariant = KenangPalette.textDim,
    surfaceContainer = Color(0xFF152A44),
    surfaceContainerLow = Color(0xFF122439),
    surfaceContainerHigh = KenangPalette.surfaceHigh,
    surfaceContainerHighest = Color(0xFF224066),
    inverseSurface = Color(0xFFEAF3FF),
    inverseOnSurface = Color(0xFF12233B),
    inversePrimary = Color(0xFF1E4976),
    outline = Color(0xFF3D5B80),
    outlineVariant = Color(0xFF27415F),
    error = Color(0xFFFF8E8E),
    onError = Color(0xFF33090C),
    errorContainer = Color(0xFF5C1E24),
    onErrorContainer = Color(0xFFFFDADA),
    scrim = Color(0xCC050B14),
)

private val KenangShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun KenangTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BlueColors,
        shapes = KenangShapes,
        content = content,
    )
}
