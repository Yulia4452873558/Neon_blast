package ru.yulia.neonblast.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.yulia.neonblast.game.Difficulty

val DifficultyColors = mapOf(
    Difficulty.EASY to NeonColors.Lime,
    Difficulty.NORMAL to NeonColors.Cyan,
    Difficulty.HARD to NeonColors.Orange,
    Difficulty.EXPERT to NeonColors.Red,
)

/** Animated crystal balance pill. */
@Composable
fun CrystalChip(amount: Int, modifier: Modifier = Modifier, prefix: String = "", fontSize: androidx.compose.ui.unit.TextUnit = 17.sp) {
    val shown by animateIntAsState(amount, tween(600), label = "crystals")
    Box(
        modifier
            .drawBehind {
                val r = CornerRadius(size.height / 2f)
                drawRoundRect(NeonColors.Cyan.copy(alpha = 0.14f), cornerRadius = r)
                drawRoundRect(NeonColors.Cyan, cornerRadius = r, style = Stroke(1.5.dp.toPx()))
            }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text("💎 $prefix$shown", style = glowStyle(NeonColors.Cyan, fontSize, blur = 14f).copy(letterSpacing = 0.5.sp), maxLines = 1)
    }
}

/** Glowing back-arrow button. */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.85f else 1f, spring(dampingRatio = 0.4f), label = "press")
    Canvas(
        modifier
            .size(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interaction, indication = null, onClick = onClick),
    ) {
        val c = NeonColors.Magenta
        val w = size.width
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.35f), Color.Transparent)), radius = w / 2f)
        drawCircle(c, radius = w / 2f - 2.dp.toPx(), style = Stroke(2.dp.toPx()))
        val p = Path().apply {
            moveTo(w * 0.56f, w * 0.3f)
            lineTo(w * 0.36f, w * 0.5f)
            lineTo(w * 0.56f, w * 0.7f)
        }
        drawPath(p, c.copy(alpha = 0.4f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(p, Color.White, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Screen title row with back button and crystal balance. */
@Composable
fun ScreenHeader(title: String, crystals: Int, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BackButton(onBack)
        Text(
            title,
            style = glowStyle(NeonColors.Magenta, if (title.length > 8) 21.sp else 24.sp, blur = 24f)
                .copy(letterSpacing = 1.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
            maxLines = 1,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        CrystalChip(crystals, fontSize = 15.sp)
    }
}

/** Dark glass card with a neon outline. */
@Composable
fun NeonCard(
    color: Color,
    modifier: Modifier = Modifier,
    lit: Float = 0.5f,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f), label = "press")
    var m = modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .drawBehind {
            val r = CornerRadius(18.dp.toPx())
            drawRoundRect(Color(0xCC0B0722), cornerRadius = r)
            drawRoundRect(
                Brush.linearGradient(listOf(color.copy(alpha = 0.22f * lit), Color.Transparent), Offset.Zero, Offset(size.width, size.height)),
                cornerRadius = r,
            )
            drawRoundRect(color.copy(alpha = 0.25f * lit), cornerRadius = r, style = Stroke(8.dp.toPx()))
            drawRoundRect(color.copy(alpha = 0.4f + 0.6f * lit), cornerRadius = r, style = Stroke(1.5.dp.toPx()))
        }
    if (onClick != null) m = m.clickable(interaction, indication = null, onClick = onClick)
    Column(m.padding(contentPadding), content = content)
}

/** Thin neon progress bar. */
@Composable
fun NeonProgress(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(700), label = "progress")
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(NeonColors.EmptyCell, cornerRadius = r)
        if (f > 0f) {
            drawRoundRect(color.copy(alpha = 0.3f), size = size.copy(width = size.width * f), cornerRadius = r)
            drawRoundRect(
                Brush.horizontalGradient(listOf(color, androidx.compose.ui.graphics.lerp(color, Color.White, 0.5f))),
                size = size.copy(width = size.width * f), cornerRadius = r,
            )
        }
    }
}

/** Round menu icon with a caption and an optional red badge. */
@Composable
fun MenuIconButton(icon: String, label: String, color: Color, badge: Int = 0, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.4f), label = "press")
    Column(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(interaction, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp)) {
            Box(
                Modifier
                    .size(64.dp)
                    .drawBehind {
                        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.4f), Color.Transparent)), radius = size.width * 0.7f)
                        drawCircle(color.copy(alpha = 0.15f))
                        drawCircle(color, style = Stroke(2.dp.toPx()))
                    },
                contentAlignment = Alignment.Center,
            ) { Text(icon, fontSize = 28.sp) }
            if (badge > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .background(NeonColors.Red, CircleShape)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(badge.toString(), style = TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = glowStyle(color, 13.sp, blur = 10f, weight = FontWeight.Bold).copy(letterSpacing = 1.sp))
    }
}

@Composable
fun HSpace(w: Int) = Spacer(Modifier.width(w.dp))

@Composable
fun VSpace(h: Int) = Spacer(Modifier.height(h.dp))

@Composable
fun RowCentered(content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { content() }
}
