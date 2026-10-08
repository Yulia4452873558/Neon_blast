package ru.yulia.neonblast.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Seconds since this composable entered composition, updated every frame. */
@Composable
fun rememberClock(): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time.floatValue = (it - start) / 1_000_000_000f }
    }
    return time
}

/** Shared "energy" the game pumps into the background on big moments. */
class BackgroundPulse {
    var energy by mutableFloatStateOf(0f)
    var color by mutableStateOf(NeonColors.Magenta)
}

private class Star(val x: Float, val y: Float, val size: Float, val phase: Float, val speed: Float)

@Composable
fun NeonBackground(pulse: BackgroundPulse, theme: Theme, modifier: Modifier = Modifier) {
    val clock = rememberClock()
    val stars = remember {
        val r = Random(7)
        List(90) { Star(r.nextFloat(), r.nextFloat() * 0.7f, 0.6f + r.nextFloat() * 1.8f, r.nextFloat(), 0.3f + r.nextFloat()) }
    }
    Canvas(modifier.fillMaxSize()) {
        val t = clock.value
        val energy = pulse.energy
        val w = size.width
        val h = size.height

        drawRect(
            Brush.verticalGradient(
                0f to theme.sky[0],
                0.45f to theme.sky[1],
                0.62f to theme.sky[2],
                1f to theme.sky[3],
            ),
        )

        // drifting nebula orbs
        val orbs = listOf(
            Triple(theme.orbs[0], 0.2f, 0.25f),
            Triple(theme.orbs[1], 0.8f, 0.18f),
            Triple(theme.orbs[2], 0.5f, 0.45f),
            Triple(theme.orbs[3], 0.15f, 0.7f),
        )
        orbs.forEachIndexed { i, (c, ox, oy) ->
            val cx = w * (ox + 0.08f * sin(t * 0.13f + i * 1.7f))
            val cy = h * (oy + 0.05f * sin(t * 0.11f + i * 2.3f))
            val rad = w * (0.55f + 0.1f * sin(t * 0.2f + i))
            val col = lerp(c, pulse.color, energy * 0.6f)
            drawCircle(
                Brush.radialGradient(listOf(col.copy(alpha = 0.16f + energy * 0.12f), Color.Transparent), Offset(cx, cy), rad),
                radius = rad, center = Offset(cx, cy),
            )
        }

        // twinkling stars
        for (s in stars) {
            val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin((t * s.speed + s.phase) * 6.28f))
            drawCircle(Color.White.copy(alpha = tw * 0.8f), radius = s.size, center = Offset(s.x * w, s.y * h))
        }

        // synthwave grid
        val horizon = h * 0.6f
        val gridColor = lerp(theme.grid, pulse.color, energy)
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, gridColor.copy(alpha = 0.35f + energy * 0.3f), Color.Transparent),
                startY = horizon - h * 0.03f, endY = horizon + h * 0.03f,
            ),
            topLeft = Offset(0f, horizon - h * 0.03f),
            size = androidx.compose.ui.geometry.Size(w, h * 0.06f),
            blendMode = BlendMode.Plus,
        )
        val lines = 14
        val scroll = (t * 0.35f) % 1f
        for (i in 0 until lines) {
            val p = (i + scroll) / lines
            val y = horizon + (h - horizon) * p.pow(2.2f)
            val a = (p * 0.55f + energy * 0.3f).coerceIn(0f, 1f)
            drawLine(gridColor.copy(alpha = a), Offset(0f, y), Offset(w, y), strokeWidth = 1f + p * 3f)
        }
        val vp = Offset(w / 2f, horizon)
        for (k in -12..12) {
            val bottom = Offset(w / 2f + k * w * 0.16f, h)
            drawLine(
                Brush.verticalGradient(listOf(Color.Transparent, gridColor.copy(alpha = 0.5f + energy * 0.3f)), startY = horizon, endY = h),
                vp, bottom, strokeWidth = 2f,
            )
        }
    }
}
