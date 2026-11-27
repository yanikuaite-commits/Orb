package dev.orb.app.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.orb.app.ui.theme.OrbRed
import dev.orb.app.ui.theme.OrbRedDeep

@Composable
fun OrbMark(modifier: Modifier = Modifier, size: Dp = 96.dp) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(9000, easing = LinearEasing),
        ),
        label = "spin",
    )
    Canvas(modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(OrbRed.copy(alpha = 0.42f), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
        )
        drawCircle(
            color = OrbRed.copy(alpha = 0.30f),
            radius = radius * 0.86f,
            style = Stroke(width = 1.dp.toPx()),
        )
        drawCircle(
            color = OrbRed.copy(alpha = 0.18f),
            radius = radius * 0.97f,
            style = Stroke(width = 1.dp.toPx()),
        )
        rotate(spin) {
            drawCircle(
                color = Color.White.copy(alpha = 0.75f),
                radius = 1.8.dp.toPx(),
                center = Offset(center.x, center.y - radius * 0.86f),
            )
            drawCircle(
                color = OrbRed.copy(alpha = 0.9f),
                radius = 1.2.dp.toPx(),
                center = Offset(center.x, center.y + radius * 0.86f),
            )
        }
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, Color(0xFFFF6B8A), OrbRed, OrbRedDeep),
                center = center,
                radius = radius * 0.52f,
            ),
            radius = radius * 0.52f * pulse,
        )
    }
}
