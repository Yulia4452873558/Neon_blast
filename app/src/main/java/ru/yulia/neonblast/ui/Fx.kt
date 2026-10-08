package ru.yulia.neonblast.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Particle(
    var x: Float, var y: Float,
    var vx: Float, var vy: Float,
    val life: Float,
    val color: Color,
    val size: Float,
    val kind: Int, // 0 = glowing spark streak, 1 = spinning shard, 2 = soft orb, 3 = rising ember
    var rot: Float,
    val spin: Float,
) {
    var age = 0f
}

class Ring(val center: Offset, val color: Color, val start: Float, val maxRadius: Float, val duration: Float, val width: Float)

class Beam(val rect: Rect, val horizontal: Boolean, val color: Color, val start: Float)

class Popup(
    val text: String,
    val center: Offset,
    val style: TextStyle,
    val start: Float,
    val duration: Float,
    val rise: Float,
)

/** A board cell that is being blasted; it explodes into particles once [start] is reached. */
class DyingCell(val row: Int, val col: Int, val color: Color, val start: Float, val center: Offset, val cellSize: Float) {
    var exploded = false
}

class FxSystem {
    val particles = ArrayList<Particle>()
    val rings = ArrayList<Ring>()
    val beams = ArrayList<Beam>()
    val popups = ArrayList<Popup>()
    val dying = ArrayList<DyingCell>()

    var shake = 0f
    var flash = 0f
    var flashColor = Color.White
    private val rnd = Random(System.nanoTime())

    fun update(now: Float, dt: Float) {
        for (d in dying) {
            if (!d.exploded && now >= d.start) {
                d.exploded = true
                burst(d.center, d.color, 14, d.cellSize)
            }
        }
        dying.removeAll { now - it.start > 0.45f }

        val it = particles.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.age += dt
            if (p.age >= p.life) { it.remove(); continue }
            val drag = when (p.kind) { 2 -> 0.9f; 3 -> 0.99f; else -> 0.96f }
            val gravity = when (p.kind) { 2 -> -120f; 3 -> -260f; else -> 1400f }
            p.vx *= drag
            p.vy = p.vy * drag + gravity * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            p.rot += p.spin * dt
        }
        rings.removeAll { now - it.start > it.duration }
        beams.removeAll { now - it.start > 0.6f }
        popups.removeAll { now - it.start > it.duration }

        shake = (shake - dt * 2.2f).coerceAtLeast(0f)
        flash = (flash - dt * 2.5f).coerceAtLeast(0f)
    }

    fun burst(center: Offset, color: Color, count: Int, cell: Float, power: Float = 1f) {
        repeat(count) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val speed = cell * (2f + rnd.nextFloat() * 9f) * power
            val kind = when (rnd.nextInt(10)) { in 0..4 -> 0; in 5..7 -> 1; else -> 2 }
            particles += Particle(
                x = center.x + (rnd.nextFloat() - 0.5f) * cell * 0.6f,
                y = center.y + (rnd.nextFloat() - 0.5f) * cell * 0.6f,
                vx = cos(a) * speed,
                vy = sin(a) * speed - cell * 4f,
                life = 0.45f + rnd.nextFloat() * 0.6f,
                color = if (rnd.nextInt(5) == 0) Color.White else color,
                size = cell * (0.08f + rnd.nextFloat() * 0.14f),
                kind = kind,
                rot = rnd.nextFloat() * 360f,
                spin = (rnd.nextFloat() - 0.5f) * 900f,
            )
        }
        if (particles.size > 900) particles.subList(0, particles.size - 900).clear()
    }

    fun sparkle(center: Offset, color: Color, count: Int, cell: Float) {
        repeat(count) {
            val a = rnd.nextFloat() * 2f * PI.toFloat()
            val speed = cell * (1f + rnd.nextFloat() * 2.5f)
            particles += Particle(
                center.x, center.y, cos(a) * speed, sin(a) * speed - cell,
                life = 0.35f + rnd.nextFloat() * 0.3f, color = color,
                size = cell * (0.05f + rnd.nextFloat() * 0.06f), kind = 2, rot = 0f, spin = 0f,
            )
        }
    }

    fun ember(at: Offset, color: Color, cell: Float) {
        particles += Particle(
            at.x, at.y,
            vx = (rnd.nextFloat() - 0.5f) * cell * 1.2f,
            vy = -cell * (1.5f + rnd.nextFloat() * 3f),
            life = 0.9f + rnd.nextFloat() * 1.1f,
            color = if (rnd.nextInt(4) == 0) NeonColors.Yellow else color,
            size = cell * (0.05f + rnd.nextFloat() * 0.08f), kind = 3, rot = 0f, spin = 0f,
        )
    }

    fun firework(center: Offset, cell: Float, now: Float) {
        NeonColors.palette.forEachIndexed { i, c ->
            burst(center, c, 22, cell, 1.6f)
            rings += Ring(center, c, now + i * 0.05f, cell * (4f + i * 0.6f), 0.8f, cell * 0.12f)
        }
    }

    fun clear() {
        particles.clear(); rings.clear(); beams.clear(); popups.clear(); dying.clear()
        shake = 0f; flash = 0f
    }

    // --- drawing ----------------------------------------------------------

    fun drawUnder(scope: DrawScope, now: Float) = with(scope) {
        for (b in beams) {
            val t = ((now - b.start) / 0.6f).coerceIn(0f, 1f)
            val a = (1f - t) * (1f - t)
            val grow = 0.35f + 2.2f * easeOutCubic(t)
            val r = b.rect
            if (b.horizontal) {
                val h = r.height * grow
                val top = r.center.y - h / 2f
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, b.color.copy(alpha = 0.8f * a), Color.White.copy(alpha = a), b.color.copy(alpha = 0.8f * a), Color.Transparent),
                        startY = top, endY = top + h,
                    ),
                    topLeft = Offset(r.left - r.width * 0.1f * t, top),
                    size = androidx.compose.ui.geometry.Size(r.width * (1f + 0.2f * t), h),
                    blendMode = BlendMode.Plus,
                )
            } else {
                val w = r.width * grow
                val left = r.center.x - w / 2f
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, b.color.copy(alpha = 0.8f * a), Color.White.copy(alpha = a), b.color.copy(alpha = 0.8f * a), Color.Transparent),
                        startX = left, endX = left + w,
                    ),
                    topLeft = Offset(left, r.top - r.height * 0.1f * t),
                    size = androidx.compose.ui.geometry.Size(w, r.height * (1f + 0.2f * t)),
                    blendMode = BlendMode.Plus,
                )
            }
        }
    }

    fun drawOver(scope: DrawScope, now: Float, measurer: TextMeasurer) = with(scope) {
        for (r in rings) {
            val t = (now - r.start) / r.duration
            if (t < 0f) continue
            val e = easeOutCubic(t)
            drawCircle(
                color = r.color.copy(alpha = (1f - t).coerceIn(0f, 1f)),
                radius = r.maxRadius * e,
                center = r.center,
                style = Stroke(width = r.width * (1f - t * 0.7f)),
                blendMode = BlendMode.Plus,
            )
        }

        for (p in particles) {
            val t = p.age / p.life
            val a = (1f - t).coerceIn(0f, 1f)
            when (p.kind) {
                0 -> {
                    val tail = Offset(p.vx, p.vy) * 0.035f
                    drawLine(
                        color = p.color.copy(alpha = a * 0.5f), start = Offset(p.x, p.y) - tail * 1.4f,
                        end = Offset(p.x, p.y), strokeWidth = p.size * 2.2f, blendMode = BlendMode.Plus,
                    )
                    drawLine(
                        color = lerp(p.color, Color.White, 0.6f).copy(alpha = a), start = Offset(p.x, p.y) - tail,
                        end = Offset(p.x, p.y), strokeWidth = p.size * 0.8f,
                    )
                }
                1 -> rotate(p.rot, Offset(p.x, p.y)) {
                    val s = p.size * 1.6f * (1f - t * 0.5f)
                    drawRect(
                        color = p.color.copy(alpha = a), topLeft = Offset(p.x - s / 2f, p.y - s / 2f),
                        size = androidx.compose.ui.geometry.Size(s, s),
                    )
                }
                else -> {
                    val rad = p.size * 3f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(p.color.copy(alpha = a), p.color.copy(alpha = 0f)),
                            center = Offset(p.x, p.y), radius = rad,
                        ),
                        radius = rad, center = Offset(p.x, p.y), blendMode = BlendMode.Plus,
                    )
                }
            }
        }

        for (pp in popups) {
            val t = (now - pp.start) / pp.duration
            if (t < 0f) continue
            val scale = easeOutBack(t / 0.22f) * (1f + 0.08f * t)
            val alpha = 1f - smoothstep(0.7f, 1f, t)
            drawGlowText(measurer, pp.text, pp.center - Offset(0f, pp.rise * easeOutCubic(t)), pp.style, scale, alpha)
        }

        if (flash > 0f) drawRect(flashColor.copy(alpha = flash * 0.35f), blendMode = BlendMode.Plus)
    }
}
