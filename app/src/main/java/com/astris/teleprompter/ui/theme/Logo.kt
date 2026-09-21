package com.astris.teleprompter.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's brand mark: three script lines on a graphite badge, the middle one lit up in amber
 * with a play/cursor notch — "this is the line being read right now." Used for both the in-app
 * header and (as a hand-translated vector) the launcher icon, so the two stay visually in sync.
 */
@Composable
fun TeleprompterLogo(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val corner = CornerRadius(w * 0.26f, h * 0.26f)

        drawRoundRect(
            brush = Brush.verticalGradient(listOf(GraphiteSurfaceVariant, GraphiteBackground)),
            cornerRadius = corner
        )
        drawRoundRect(
            color = Amber.copy(alpha = 0.20f),
            cornerRadius = corner,
            style = Stroke(width = w * 0.022f)
        )

        val barHeight = h * 0.09f

        drawLine(
            color = IvoryText.copy(alpha = 0.85f),
            start = Offset(w * 0.30f, h * 0.34f),
            end = Offset(w * 0.74f, h * 0.34f),
            strokeWidth = barHeight,
            cap = StrokeCap.Round
        )
        drawLine(
            brush = Brush.horizontalGradient(
                listOf(AmberBright, Amber),
                startX = w * 0.34f,
                endX = w * 0.82f
            ),
            start = Offset(w * 0.34f, h * 0.5f),
            end = Offset(w * 0.82f, h * 0.5f),
            strokeWidth = barHeight * 1.2f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = IvoryText.copy(alpha = 0.5f),
            start = Offset(w * 0.30f, h * 0.66f),
            end = Offset(w * 0.62f, h * 0.66f),
            strokeWidth = barHeight,
            cap = StrokeCap.Round
        )

        val cursor = Path().apply {
            moveTo(w * 0.185f, h * 0.5f - barHeight * 0.95f)
            lineTo(w * 0.185f, h * 0.5f + barHeight * 0.95f)
            lineTo(w * 0.30f, h * 0.5f)
            close()
        }
        drawPath(cursor, color = AmberBright)
    }
}
