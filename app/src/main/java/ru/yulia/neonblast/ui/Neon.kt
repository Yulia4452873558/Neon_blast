package ru.yulia.neonblast.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

object NeonColors {
    val Cyan = Color(0xFF00F0FF)
    val Magenta = Color(0xFFFF2BD6)
    val Lime = Color(0xFF7CFF4F)
    val Orange = Color(0xFFFF8A00)
    val Yellow = Color(0xFFFFE600)
    val Violet = Color(0xFFA259FF)
    val Red = Color(0xFFFF3B5C)
    val Blue = Color(0xFF2B8CFF)

    val palette = listOf(Cyan, Magenta, Lime, Orange, Yellow, Violet, Red, Blue)

    val Night = Color(0xFF07021A)
    val Panel = Color(0xFF0D0826)
    val EmptyCell = Color(0xFF161033)
    val EmptyStroke = Color(0xFF2A2152)
    val Dead = Color(0xFF4A4560)

    /** Smoothly cycles through the palette; [t] in palette steps. */
    fun rainbow(t: Float): Color {
        val n = palette.size
        val f = ((t % n) + n) % n
        val i = f.toInt()
        return lerp(palette[i], palette[(i + 1) % n], f - i)
    }
}

// --- easing --------------------------------------------------------------

fun easeOutBack(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    val c1 = 1.9f
    val c3 = c1 + 1f
    return 1f + c3 * (x - 1f).pow(3) + c1 * (x - 1f).pow(2)
}

fun easeOutCubic(t: Float): Float = 1f - (1f - t.coerceIn(0f, 1f)).pow(3)

fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

fun glowStyle(color: Color, size: TextUnit, blur: Float = 28f, weight: FontWeight = FontWeight.Black) = TextStyle(
    color = Color.White,
    fontSize = size,
    fontWeight = weight,
    letterSpacing = 2.sp,
    shadow = Shadow(color = color, offset = Offset.Zero, blurRadius = blur),
)

// --- drawing -------------------------------------------------------------

/**
 * A glossy, beveled neon block with an outer glow.
 * [topLeft]/[size] describe the cell slot; [scale] scales the block around the slot center.
 */
fun DrawScope.drawNeonBlock(
    topLeft: Offset,
    size: Float,
    color: Color,
    alpha: Float = 1f,
    glow: Float = 1f,
    scale: Float = 1f,
    flash: Float = 0f,
) {
    if (scale <= 0.01f || alpha <= 0.01f) return
    val s = size * scale
    val tl = topLeft + Offset((size - s) / 2f, (size - s) / 2f)
    val center = tl + Offset(s / 2f, s / 2f)

    if (glow > 0f) {
        val r = s * 0.95f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(color.copy(alpha = 0.55f * glow * alpha), color.copy(alpha = 0f)),
                center = center, radius = r,
            ),
            radius = r, center = center,
        )
    }

    val inset = s * 0.05f
    val bodyTl = tl + Offset(inset, inset)
    val bodySize = Size(s - inset * 2, s - inset * 2)
    val corner = CornerRadius(s * 0.2f)

    // body
    drawRoundRect(
        brush = Brush.verticalGradient(
            listOf(lerp(color, Color.White, 0.35f), color, lerp(color, Color.Black, 0.45f)),
            startY = bodyTl.y, endY = bodyTl.y + bodySize.height,
        ),
        topLeft = bodyTl, size = bodySize, cornerRadius = corner, alpha = alpha,
    )
    // inner bevel
    val bevel = s * 0.17f
    drawRoundRect(
        brush = Brush.linearGradient(
            listOf(lerp(color, Color.Black, 0.15f), lerp(color, Color.Black, 0.4f)),
            start = bodyTl, end = bodyTl + Offset(bodySize.width, bodySize.height),
        ),
        topLeft = tl + Offset(bevel, bevel),
        size = Size(s - bevel * 2, s - bevel * 2),
        cornerRadius = CornerRadius(s * 0.12f),
        alpha = alpha,
    )
    // gloss
    drawRoundRect(
        color = Color.White,
        topLeft = tl + Offset(s * 0.2f, s * 0.1f),
        size = Size(s * 0.42f, s * 0.07f),
        cornerRadius = CornerRadius(s * 0.04f),
        alpha = 0.55f * alpha,
    )
    // neon rim
    drawRoundRect(
        color = lerp(color, Color.White, 0.55f),
        topLeft = bodyTl, size = bodySize, cornerRadius = corner,
        style = Stroke(width = s * 0.045f), alpha = alpha,
    )
    if (flash > 0f) {
        drawRoundRect(
            color = Color.White, topLeft = bodyTl, size = bodySize, cornerRadius = corner,
            alpha = (flash * alpha).coerceIn(0f, 1f),
        )
    }
}

fun DrawScope.drawGlowText(
    measurer: TextMeasurer,
    text: String,
    center: Offset,
    style: TextStyle,
    scale: Float = 1f,
    alpha: Float = 1f,
) {
    if (alpha <= 0.01f || scale <= 0.01f) return
    val layout = measurer.measure(text, style)
    val tl = center - Offset(layout.size.width / 2f, layout.size.height / 2f)
    withTransform({ scale(scale, scale, center) }) {
        // second pass doubles the glow intensity
        drawText(layout, topLeft = tl, alpha = alpha)
        drawText(layout, topLeft = tl, alpha = alpha)
    }
}

/** 0..1 triangle-ish pulse, handy for idle shimmer. */
fun pulse(time: Float, speed: Float = 1f, phase: Float = 0f): Float =
    0.5f + 0.5f * sin((time * speed + phase) * 2f * PI.toFloat())
