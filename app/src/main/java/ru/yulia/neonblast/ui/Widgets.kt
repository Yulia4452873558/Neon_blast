package ru.yulia.neonblast.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.sin

@Composable
fun NeonButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
    compact: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.4f), label = "press")
    val glow by rememberInfiniteTransition(label = "btn").animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "glow",
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(
                    Brush.radialGradient(
                        listOf(color.copy(alpha = 0.45f * glow), Color.Transparent),
                        center = center, radius = size.width * 0.75f,
                    ),
                    topLeft = Offset(-size.width * 0.2f, -size.height * 0.6f),
                    size = androidx.compose.ui.geometry.Size(size.width * 1.4f, size.height * 2.2f),
                    cornerRadius = r,
                )
                drawRoundRect(color.copy(alpha = 0.18f), cornerRadius = r)
                drawRoundRect(color.copy(alpha = 0.35f * glow), cornerRadius = r, style = Stroke(10.dp.toPx()))
                drawRoundRect(Color.White.copy(alpha = 0.9f), cornerRadius = r, style = Stroke(2.5.dp.toPx()))
            }
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = if (compact) 16.dp else 40.dp, vertical = if (compact) 10.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = glowStyle(color, fontSize, blur = 24f).let { if (compact) it.copy(letterSpacing = 0.5.sp) else it }, maxLines = 1)
    }
}

/** Big bouncing title where every letter glows in its own hue. */
@Composable
fun NeonTitle(text: String, time: () -> Float, fontSize: TextUnit, hueOffset: Float = 0f) {
    Row {
        text.forEachIndexed { i, ch ->
            Box(
                Modifier.graphicsLayer {
                    val t = time()
                    translationY = sin(t * 3f + i * 0.6f) * 6.dp.toPx()
                    val s = 1f + 0.06f * sin(t * 2.2f + i * 0.9f)
                    scaleX = s; scaleY = s
                },
            ) {
                val color = NeonColors.rainbow(time() * 0.6f + i * 0.7f + hueOffset)
                Text(ch.toString(), style = glowStyle(color, fontSize, blur = 40f))
            }
        }
    }
}

/** Small round neon icon button that draws a home glyph. */
@Composable
fun HomeButton(onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, spring(dampingRatio = 0.4f), label = "press")
    Canvas(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interaction, indication = null, onClick = onClick),
    ) {
        val c = NeonColors.Cyan
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.35f), Color.Transparent)), radius = this.size.minDimension / 2f)
        drawCircle(c, radius = this.size.minDimension / 2f - 2.dp.toPx(), style = Stroke(2.dp.toPx()))
        val w = this.size.width
        val p = Path().apply {
            moveTo(w * 0.28f, w * 0.5f)
            lineTo(w * 0.5f, w * 0.29f)
            lineTo(w * 0.72f, w * 0.5f)
            moveTo(w * 0.35f, w * 0.45f)
            lineTo(w * 0.35f, w * 0.7f)
            lineTo(w * 0.65f, w * 0.7f)
            lineTo(w * 0.65f, w * 0.45f)
        }
        drawPath(p, c.copy(alpha = 0.4f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(p, Color.White, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Flickering text like a neon sign that is warming up. */
@Composable
fun FlickerText(text: String, color: Color, fontSize: TextUnit, time: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .graphicsLayer {
                val t = time()
                val f = sin(t * 37f) * sin(t * 13f) * sin(t * 5.3f)
                alpha = if (f > 0.55f) 0.35f else 1f
            },
    ) {
        Text(text, style = glowStyle(color, fontSize, blur = 44f))
    }
}

/** Pill-shaped on/off switch; lit up with neon when enabled. */
@Composable
fun NeonToggle(label: String, on: Boolean, color: Color, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.4f), label = "press")
    val lit by animateFloatAsState(if (on) 1f else 0f, tween(250), label = "lit")
    val tint = androidx.compose.ui.graphics.lerp(NeonColors.Dead, color, lit)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBehind {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(tint.copy(alpha = 0.12f + 0.12f * lit), cornerRadius = r)
                drawRoundRect(tint.copy(alpha = 0.3f * lit), cornerRadius = r, style = Stroke(8.dp.toPx()))
                drawRoundRect(tint, cornerRadius = r, style = Stroke(2.dp.toPx()))
            }
            .clickable(interaction, indication = null, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$label  ${if (on) "ВКЛ" else "ВЫКЛ"}",
            style = glowStyle(tint, 13.sp, blur = 16f * lit + 1f, weight = FontWeight.Bold).copy(letterSpacing = 1.sp),
        )
    }
}
