package ru.yulia.neonblast.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import ru.yulia.neonblast.game.BlastGame

enum class BlockSkin(val title: String, val price: Int) {
    GLASS("Неон-стекло", 0),
    TUBE("Неон-контур", 150),
    PIXEL("Пиксель", 200),
    CANDY("Леденцы", 250),
    CRYSTAL("Кристалл", 400),
    ;

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: GLASS
    }
}

enum class Theme(
    val title: String,
    val price: Int,
    val sky: List<Color>,
    val grid: Color,
    val orbs: List<Color>,
    val rim: List<Color>,
) {
    SYNTHWAVE(
        "Синтвейв", 0,
        listOf(Color(0xFF05010F), Color(0xFF140530), Color(0xFF2A0745), Color(0xFF07021A)),
        NeonColors.Magenta,
        listOf(NeonColors.Magenta, NeonColors.Cyan, NeonColors.Violet, NeonColors.Blue),
        listOf(NeonColors.Cyan, NeonColors.Magenta, NeonColors.Violet, NeonColors.Cyan),
    ),
    CYBER(
        "Киберпанк", 200,
        listOf(Color(0xFF010A0D), Color(0xFF032328), Color(0xFF0B3A3A), Color(0xFF010A0D)),
        NeonColors.Cyan,
        listOf(NeonColors.Yellow, NeonColors.Cyan, Color(0xFF00FFA3), NeonColors.Cyan),
        listOf(NeonColors.Yellow, NeonColors.Cyan, Color(0xFF00FFA3), NeonColors.Yellow),
    ),
    OCEAN(
        "Глубина", 250,
        listOf(Color(0xFF00040F), Color(0xFF021A3D), Color(0xFF053A6B), Color(0xFF000814)),
        Color(0xFF2BD9FF),
        listOf(NeonColors.Blue, Color(0xFF2BD9FF), Color(0xFF00FFC8), NeonColors.Violet),
        listOf(Color(0xFF2BD9FF), NeonColors.Blue, Color(0xFF00FFC8), Color(0xFF2BD9FF)),
    ),
    LAVA(
        "Лава", 300,
        listOf(Color(0xFF0D0100), Color(0xFF2B0500), Color(0xFF4D0F00), Color(0xFF0D0100)),
        NeonColors.Orange,
        listOf(NeonColors.Red, NeonColors.Orange, NeonColors.Yellow, NeonColors.Red),
        listOf(NeonColors.Yellow, NeonColors.Orange, NeonColors.Red, NeonColors.Yellow),
    ),
    AURORA(
        "Северное сияние", 350,
        listOf(Color(0xFF00060A), Color(0xFF041F24), Color(0xFF1A0D3D), Color(0xFF02040F)),
        NeonColors.Lime,
        listOf(NeonColors.Lime, NeonColors.Violet, Color(0xFF00FFC8), NeonColors.Magenta),
        listOf(NeonColors.Lime, Color(0xFF00FFC8), NeonColors.Violet, NeonColors.Lime),
    ),
    ;

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: SYNTHWAVE
    }
}

val StoneColor = Color(0xFF8C86A8)

/** Palette color for a grid color index, stones included. */
fun colorOf(index: Int): Color =
    if (index == BlastGame.STONE_INDEX) StoneColor else NeonColors.palette[index]

/** Draws one block cell in the given [skin]. Parameters match [drawNeonBlock]. */
fun DrawScope.drawBlock(
    skin: BlockSkin,
    topLeft: Offset,
    size: Float,
    color: Color,
    alpha: Float = 1f,
    glow: Float = 1f,
    scale: Float = 1f,
    flash: Float = 0f,
) {
    if (skin == BlockSkin.GLASS) {
        drawNeonBlock(topLeft, size, color, alpha, glow, scale, flash)
        return
    }
    if (scale <= 0.01f || alpha <= 0.01f) return
    val s = size * scale
    val tl = topLeft + Offset((size - s) / 2f, (size - s) / 2f)
    val center = tl + Offset(s / 2f, s / 2f)
    if (glow > 0f) {
        val r = s * 0.95f
        val boost = if (skin == BlockSkin.TUBE) 1.4f else 1f
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = (0.55f * glow * alpha * boost).coerceAtMost(1f)), color.copy(alpha = 0f)), center, r),
            radius = r, center = center,
        )
    }
    val inset = s * 0.05f
    val bTl = tl + Offset(inset, inset)
    val bs = s - inset * 2
    when (skin) {
        BlockSkin.TUBE -> {
            val corner = CornerRadius(s * 0.22f)
            drawRoundRect(lerp(color, Color.Black, 0.82f), bTl, Size(bs, bs), corner, alpha = alpha)
            drawRoundRect(color, bTl + Offset(s * 0.04f, s * 0.04f), Size(bs - s * 0.08f, bs - s * 0.08f), corner, style = Stroke(s * 0.11f), alpha = alpha)
            drawRoundRect(lerp(color, Color.White, 0.7f), bTl + Offset(s * 0.04f, s * 0.04f), Size(bs - s * 0.08f, bs - s * 0.08f), corner, style = Stroke(s * 0.035f), alpha = alpha)
            drawCircle(color.copy(alpha = 0.5f * alpha), radius = s * 0.09f, center = center)
        }
        BlockSkin.PIXEL -> {
            val e = bs * 0.16f
            drawRect(color, bTl, Size(bs, bs), alpha = alpha)
            drawRect(lerp(color, Color.White, 0.45f), bTl, Size(bs, e), alpha = alpha)
            drawRect(lerp(color, Color.White, 0.25f), bTl, Size(e, bs), alpha = alpha)
            drawRect(lerp(color, Color.Black, 0.45f), bTl + Offset(0f, bs - e), Size(bs, e), alpha = alpha)
            drawRect(lerp(color, Color.Black, 0.3f), bTl + Offset(bs - e, 0f), Size(e, bs), alpha = alpha)
            drawRect(Color.White, bTl + Offset(e * 1.3f, e * 1.3f), Size(e, e), alpha = 0.8f * alpha)
        }
        BlockSkin.CANDY -> {
            val corner = CornerRadius(s * 0.42f)
            drawRoundRect(
                Brush.radialGradient(
                    listOf(lerp(color, Color.White, 0.55f), color, lerp(color, Color.Black, 0.35f)),
                    center = bTl + Offset(bs * 0.35f, bs * 0.3f), radius = bs * 0.9f,
                ),
                bTl, Size(bs, bs), corner, alpha = alpha,
            )
            drawOval(Color.White, bTl + Offset(bs * 0.18f, bs * 0.12f), Size(bs * 0.38f, bs * 0.22f), alpha = 0.65f * alpha)
            drawCircle(Color.White.copy(alpha = 0.5f * alpha), radius = bs * 0.05f, center = bTl + Offset(bs * 0.72f, bs * 0.72f))
        }
        BlockSkin.CRYSTAL -> {
            val x0 = bTl.x; val y0 = bTl.y; val x1 = x0 + bs; val y1 = y0 + bs
            val k = bs * 0.28f
            val ix0 = x0 + k; val iy0 = y0 + k; val ix1 = x1 - k; val iy1 = y1 - k
            fun facet(c: Color, pts: List<Offset>) {
                val p = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                    close()
                }
                drawPath(p, c, alpha = alpha)
            }
            facet(lerp(color, Color.White, 0.5f), listOf(Offset(x0, y0), Offset(x1, y0), Offset(ix1, iy0), Offset(ix0, iy0)))
            facet(lerp(color, Color.White, 0.2f), listOf(Offset(x0, y0), Offset(ix0, iy0), Offset(ix0, iy1), Offset(x0, y1)))
            facet(lerp(color, Color.Black, 0.25f), listOf(Offset(x1, y0), Offset(x1, y1), Offset(ix1, iy1), Offset(ix1, iy0)))
            facet(lerp(color, Color.Black, 0.5f), listOf(Offset(x0, y1), Offset(ix0, iy1), Offset(ix1, iy1), Offset(x1, y1)))
            facet(lerp(color, Color.White, 0.15f), listOf(Offset(ix0, iy0), Offset(ix1, iy0), Offset(ix1, iy1), Offset(ix0, iy1)))
            drawLine(Color.White.copy(alpha = 0.8f * alpha), Offset(ix0, iy0), Offset(ix0 + (ix1 - ix0) * 0.45f, iy0), strokeWidth = s * 0.04f, cap = StrokeCap.Round)
            drawRect(lerp(color, Color.White, 0.6f), bTl, Size(bs, bs), style = Stroke(s * 0.03f), alpha = alpha)
        }
        BlockSkin.GLASS -> Unit
    }
    if (flash > 0f) drawRect(Color.White, bTl, Size(bs, bs), alpha = (flash * alpha).coerceIn(0f, 1f))
}

/** A cracked stone obstacle. */
fun DrawScope.drawStone(topLeft: Offset, size: Float, alpha: Float = 1f, scale: Float = 1f, flash: Float = 0f) {
    if (scale <= 0.01f || alpha <= 0.01f) return
    val s = size * scale
    val tl = topLeft + Offset((size - s) / 2f, (size - s) / 2f)
    val inset = s * 0.06f
    val bTl = tl + Offset(inset, inset)
    val bs = s - inset * 2
    val corner = CornerRadius(s * 0.14f)
    drawRoundRect(
        Brush.linearGradient(listOf(Color(0xFF8E89A6), Color(0xFF55506B), Color(0xFF34304A)), bTl, bTl + Offset(bs, bs)),
        bTl, Size(bs, bs), corner, alpha = alpha,
    )
    drawRoundRect(Color(0xFFB9B4D0), bTl, Size(bs, bs), corner, style = Stroke(s * 0.04f), alpha = alpha)
    val crack = Path().apply {
        moveTo(bTl.x + bs * 0.2f, bTl.y + bs * 0.15f)
        lineTo(bTl.x + bs * 0.45f, bTl.y + bs * 0.45f)
        lineTo(bTl.x + bs * 0.35f, bTl.y + bs * 0.62f)
        lineTo(bTl.x + bs * 0.6f, bTl.y + bs * 0.88f)
        moveTo(bTl.x + bs * 0.45f, bTl.y + bs * 0.45f)
        lineTo(bTl.x + bs * 0.8f, bTl.y + bs * 0.35f)
    }
    drawPath(crack, Color(0xFF221E33), style = Stroke(s * 0.05f, cap = StrokeCap.Round), alpha = alpha)
    if (flash > 0f) drawRoundRect(Color.White, bTl, Size(bs, bs), corner, alpha = (flash * alpha).coerceIn(0f, 1f))
}
