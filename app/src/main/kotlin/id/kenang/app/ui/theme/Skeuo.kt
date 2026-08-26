package id.kenang.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Skeuomorphic building blocks: layered navy gradients, raised bevels with a
 * light top edge + dark bottom edge, glossy highlight on controls, pressed
 * states that visually "sink". Drop-in signatures for Material's
 * Button/OutlinedButton/Card so screens swap by name only.
 */

/** Full-window brushed-navy backdrop behind every screen. */
@Composable
fun SkeuoBackground(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                listOf(KenangPalette.backgroundTop, KenangPalette.backgroundBottom),
            ),
        ),
    ) { content() }
}

/** Raised panel: soft drop shadow, vertical surface gradient, beveled edge. */
@Composable
fun SkeuoCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier
            .shadow(10.dp, shape, spotColor = Color.Black.copy(alpha = 0.55f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF224162), Color(0xFF14283F))))
            .glossOverlay(visible = true, cornerRadiusDp = 24, strength = 0.16f)
            .border(1.dp, bevelBrush(0.22f), shape),
    ) {
        // Panels sit on the gradient backdrop; force bright content so no
        // default black Text/Icon can slip through (owner report 2026-08-26).
        CompositionLocalProvider(LocalContentColor provides KenangPalette.textBright) {
            content()
        }
    }
}

/** Primary glossy blue button — raised; sinks + darkens when pressed. */
@Composable
fun SkeuoButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = when {
        !enabled -> listOf(Color(0xFF2A3B52), Color(0xFF223148))
        pressed -> listOf(Color(0xFF2D66AC), Color(0xFF1E4B86))
        else -> listOf(Color(0xFF63ACFF), Color(0xFF2E6FCC))
    }
    SkeuoControl(
        interaction = interaction,
        pressed = pressed,
        enabled = enabled,
        fill = fill,
        edgeAlpha = if (enabled) 0.55f else 0.12f,
        gloss = enabled,
        contentColor = if (enabled) Color.White else Color(0xFF7C8FA8),
        onClick = onClick,
        modifier = modifier,
        content = content,
    )
}

/** Secondary "inset" button: darker well with a subtle bevel, bright label. */
@Composable
fun SkeuoOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill = when {
        !enabled -> listOf(Color(0xFF132236), Color(0xFF132236))
        pressed -> listOf(Color(0xFF0E1C2E), Color(0xFF12233B))
        else -> listOf(Color(0xFF172C47), Color(0xFF122439))
    }
    SkeuoControl(
        interaction = interaction,
        pressed = pressed,
        enabled = enabled,
        fill = fill,
        edgeAlpha = 0.16f,
        gloss = false,
        inset = true,
        contentColor = if (enabled) Color(0xFFBBD7F7) else Color(0xFF5F7990),
        onClick = onClick,
        modifier = modifier,
        content = content,
    )
}

// ------------------------------------------------------------------ internals

@Composable
private fun SkeuoControl(
    interaction: MutableInteractionSource,
    pressed: Boolean,
    enabled: Boolean,
    fill: List<Color>,
    edgeAlpha: Float,
    gloss: Boolean,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier,
    inset: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(19.dp)
    Row(
        modifier
            .shadow(
                elevation = if (pressed || !enabled || inset) 1.dp else 6.dp,
                shape = shape,
                spotColor = Color.Black.copy(alpha = 0.5f),
            )
            .clip(shape)
            .background(Brush.verticalGradient(fill))
            .glossOverlay(visible = gloss && !pressed, cornerRadiusDp = 19, strength = 0.45f)
            .border(
                1.dp,
                if (inset) insetBrush(edgeAlpha) else bevelBrush(edgeAlpha),
                shape,
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .defaultMinSize(minWidth = 64.dp, minHeight = 38.dp)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() }
        }
    }
}

/** Light-from-above bevel: bright top edge fading into a dark bottom edge. */
private fun bevelBrush(topAlpha: Float) = Brush.verticalGradient(
    listOf(Color.White.copy(alpha = topAlpha), Color.Black.copy(alpha = 0.40f)),
)

/** Inverted bevel for inset wells: dark top edge, faint light bottom edge. */
private fun insetBrush(alpha: Float) = Brush.verticalGradient(
    listOf(Color.Black.copy(alpha = 0.45f), Color.White.copy(alpha = alpha)),
)

/** Glass highlight across the upper half of a control/panel. */
private fun Modifier.glossOverlay(
    visible: Boolean,
    cornerRadiusDp: Int = 19,
    strength: Float = 0.45f,
): Modifier =
    if (!visible) this else drawWithCache {
        val brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = strength),
            1f to Color.White.copy(alpha = 0.04f),
            endY = size.height * 0.55f,
        )
        onDrawWithContent {
            drawContent()
            drawRoundRect(
                brush = brush,
                size = Size(size.width, size.height * 0.55f),
                cornerRadius = CornerRadius(cornerRadiusDp.dp.toPx(), cornerRadiusDp.dp.toPx()),
            )
        }
    }
