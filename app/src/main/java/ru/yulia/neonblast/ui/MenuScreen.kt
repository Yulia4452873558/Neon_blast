package ru.yulia.neonblast.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.yulia.neonblast.game.Difficulty
import ru.yulia.neonblast.game.Shapes
import kotlin.random.Random

private class DecorPiece(val shape: Int, val color: Int, val x: Float, val phase: Float, val speed: Float, val spin: Float, val size: Float)

@Composable
fun MenuScreen(
    crystals: Int,
    skin: BlockSkin,
    continueDifficulty: Difficulty?,
    achievementsBadge: Int,
    sound: Boolean,
    vibration: Boolean,
    onToggleSound: () -> Unit,
    onToggleVibration: () -> Unit,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onShop: () -> Unit,
    onAchievements: () -> Unit,
    onRecords: () -> Unit,
) {
    val clock = rememberClock()
    val decor = remember {
        val r = Random(42)
        List(14) {
            DecorPiece(
                r.nextInt(Shapes.all.size), r.nextInt(Shapes.COLOR_COUNT), r.nextFloat(), r.nextFloat(),
                0.03f + r.nextFloat() * 0.05f, (r.nextFloat() - 0.5f) * 40f, 0.035f + r.nextFloat() * 0.03f,
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.value
            for (p in decor) {
                val cell = size.width * p.size
                val shape = Shapes.all[p.shape]
                val span = size.height + cell * 12
                val y = ((p.phase + t * p.speed) % 1f) * span - cell * 6
                val center = Offset(p.x * size.width, y)
                rotate(p.spin * t + p.phase * 360f, center) {
                    val tl = center - Offset(shape.cols * cell / 2f, shape.rows * cell / 2f)
                    for ((dr, dc) in shape.cells) {
                        drawBlock(skin, tl + Offset(dc * cell, dr * cell), cell, NeonColors.palette[p.color], alpha = 0.45f, glow = 0.6f)
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxSize().safeDrawingPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                CrystalChip(crystals)
            }
            Spacer(Modifier.weight(1f))
            NeonTitle("NEON", { clock.value }, 64.sp)
            NeonTitle("BLAST", { clock.value }, 72.sp, hueOffset = 3f)
            Spacer(Modifier.height(8.dp))
            Text("БЛОКИ И НЕОН", style = glowStyle(NeonColors.Cyan, 18.sp, blur = 18f).copy(letterSpacing = 6.sp))
            Spacer(Modifier.height(44.dp))
            if (continueDifficulty != null) {
                NeonButton("ПРОДОЛЖИТЬ", NeonColors.Lime, onContinue)
                Text(
                    continueDifficulty.title,
                    style = glowStyle(DifficultyColors.getValue(continueDifficulty), 12.sp, blur = 8f, weight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 6.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
            NeonButton(if (continueDifficulty != null) "НОВАЯ ИГРА" else "ИГРАТЬ", NeonColors.Magenta, onNewGame)
            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                MenuIconButton("🛒", "МАГАЗИН", NeonColors.Cyan, onClick = onShop)
                MenuIconButton("🏆", "НАГРАДЫ", NeonColors.Yellow, badge = achievementsBadge, onClick = onAchievements)
                MenuIconButton("📊", "РЕКОРДЫ", NeonColors.Violet, onClick = onRecords)
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                NeonToggle("♪ ЗВУК", sound, NeonColors.Cyan, onToggleSound)
                NeonToggle("≋ ВИБРО", vibration, NeonColors.Violet, onToggleVibration)
            }
        }
    }
}
