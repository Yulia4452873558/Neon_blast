package ru.yulia.neonblast.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import ru.yulia.neonblast.ads.AdBanner
import ru.yulia.neonblast.ads.AdsManager
import ru.yulia.neonblast.ads.findActivity
import ru.yulia.neonblast.audio.Sfx
import ru.yulia.neonblast.audio.Sound
import ru.yulia.neonblast.game.BlastGame
import ru.yulia.neonblast.game.ClearedCell
import ru.yulia.neonblast.game.Difficulty
import ru.yulia.neonblast.game.PowerUp
import ru.yulia.neonblast.game.Profile
import ru.yulia.neonblast.game.Shape
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private const val N = BlastGame.SIZE
private const val FEVER_COMBO = 3

private class GameLayout(val board: Rect, val cell: Float, val slots: List<Rect>, val trayCell: Float) {
    fun cellTopLeft(r: Int, c: Int) = Offset(board.left + c * cell, board.top + r * cell)
    fun cellCenter(r: Int, c: Int) = Offset(board.left + (c + 0.5f) * cell, board.top + (r + 0.5f) * cell)
}

/** Mutable, non-observable visual state; the per-frame clock triggers redraws. */
private class Visuals {
    // cascading entrance wave for cells restored from a saved game
    val placeTime = FloatArray(N * N) { i -> 0.15f + (i / N + i % N) * 0.035f }
    val trayAppear = FloatArray(3) { 0.45f + it * 0.1f }
    var layout: GameLayout? = null

    var dragSlot = -1
    var dragPos = Offset.Zero
    var dragStart = 0f
    var ghostRow = -1
    var ghostCol = -1
    var ghostRows: List<Int> = emptyList()
    var ghostCols: List<Int> = emptyList()

    var returnSlot = -1
    var returnFrom = Offset.Zero
    var returnFromCell = 0f
    var returnStart = 0f
}

private fun computeLayout(size: Size, density: Density): GameLayout = with(density) {
    val pad = 24.dp.toPx()
    val gap = 18.dp.toPx()
    val w = size.width
    val h = size.height
    val boardSize = min(w - 2 * pad, (h - 2 * pad - gap) / 1.5f)
    val cell = boardSize / N
    val trayH = boardSize * 0.5f
    val total = boardSize + gap + trayH
    val top = pad + max(0f, h - 2 * pad - total) * 0.35f
    val board = Rect(Offset((w - boardSize) / 2f, top), Size(boardSize, boardSize))
    val trayTop = board.bottom + gap
    val trayLeft = pad * 0.5f
    val slotW = (w - 2 * trayLeft) / 3f
    val slots = List(3) { i ->
        Rect(trayLeft + i * slotW, trayTop, trayLeft + (i + 1) * slotW, trayTop + trayH)
    }
    val trayCell = minOf(cell * 0.6f, slotW / 5.6f, trayH / 5.6f)
    GameLayout(board, cell, slots, trayCell)
}

@Composable
fun GameScreen(
    game: BlastGame,
    profile: Profile,
    pulse: BackgroundPulse,
    sfx: Sfx,
    ads: AdsManager,
    vibration: Boolean,
    onHome: () -> Unit,
    onRestart: () -> Unit,
) {
    val fx = remember { FxSystem() }
    val vis = remember { Visuals() }
    val clock = remember { mutableFloatStateOf(0f) }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val vibro by rememberUpdatedState(vibration)
    val skin = BlockSkin.of(profile.skin)
    val theme = Theme.of(profile.theme)

    var score by remember { mutableIntStateOf(game.score) }
    var combo by remember { mutableIntStateOf(game.combo) }
    val startBest = remember { profile.best(game.difficulty) }
    var best by remember { mutableIntStateOf(startBest) }
    var newRecord by remember { mutableStateOf(false) }
    var earned by remember { mutableIntStateOf(0) }
    var gameOverAt by remember { mutableFloatStateOf(-1f) }
    var showGameOver by remember { mutableStateOf(false) }
    /** Out of moves but the player chose to try power-ups. */
    var stuck by remember { mutableStateOf(false) }
    val armed = remember { mutableStateOf<PowerUp?>(null) }
    /** Power-up the player tapped without having any: offer an ad or a purchase. */
    var offer by remember { mutableStateOf<PowerUp?>(null) }
    /** An interstitial was already shown for the current loss, so leaving now shows none. */
    var lossAdShown by remember { mutableStateOf(false) }
    val activity = LocalContext.current.findActivity()

    /** Leaving the game screen always goes through an interstitial (unless the loss already showed one). */
    fun exitGame() {
        if (ads.isShowing) return
        if (lossAdShown) onHome() else ads.showInterstitial(activity) { onHome() }
    }
    BackHandler { exitGame() }
    // registered later, so it wins: Back first closes the offer dialog / cancels an armed power-up
    BackHandler(enabled = offer != null || armed.value != null) {
        offer = null
        armed.value = null
    }

    LaunchedEffect(Unit) {
        var emberAcc = 0f
        var last = withFrameNanos { it }
        while (true) withFrameNanos { n ->
            val dt = ((n - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
            last = n
            clock.floatValue += dt
            fx.update(clock.floatValue, dt)
            // fever mode: embers rise from the board while a big combo streak lasts
            val l = vis.layout
            if (l != null && game.combo >= FEVER_COMBO && gameOverAt < 0f) {
                emberAcc += dt * (10f + game.combo * 5f)
                while (emberAcc >= 1f) {
                    emberAcc -= 1f
                    val x = l.board.left + Random.nextFloat() * l.board.width
                    fx.ember(Offset(x, l.board.bottom + l.cell * 0.2f), if (Random.nextBoolean()) NeonColors.Orange else NeonColors.Red, l.cell)
                }
            }
            if (pulse.energy > 0f) pulse.energy = (pulse.energy - dt * 1.1f).coerceAtLeast(0f)
        }
    }
    LaunchedEffect(gameOverAt) {
        if (gameOverAt >= 0f) {
            delay(1500)
            showGameOver = true
            if (!lossAdShown) {
                lossAdShown = true
                ads.showInterstitial(activity) {}
            }
        }
    }

    fun buzz(type: HapticFeedbackType) {
        if (vibro) haptic.performHapticFeedback(type)
    }

    fun popupStyle(color: Color, cellFraction: Float, cell: Float): TextStyle =
        with(density) { glowStyle(color, (cell * cellFraction).toSp(), blur = cell * 0.5f) }

    fun save() = profile.saveGame(if (game.isGameOver) null else game.serialize())

    fun syncScore() {
        score = game.score
        combo = game.combo
        if (game.score > best) {
            val l = vis.layout
            if (best == startBest && startBest > 0 && l != null) {
                fx.popups += Popup(
                    "НОВЫЙ РЕКОРД!", l.board.center - Offset(0f, l.cell * 2.6f),
                    popupStyle(NeonColors.Yellow, 0.8f, l.cell), clock.floatValue + 0.3f, 1.6f, l.cell * 0.5f,
                )
            }
            best = game.score
        }
        newRecord = game.score > startBest
    }

    /** Ends the run: grey wave, results saved, crystals credited, overlay shown shortly after. */
    fun triggerGameOver(at: Float) {
        stuck = false
        armed.value = null
        gameOverAt = at
        earned = profile.onGameOver(game)
        save()
        sfx.play(Sound.GameOver)
    }

    fun blastCells(cells: List<ClearedCell>, origin: Offset, start: Float, l: GameLayout) {
        for (cc in cells) {
            val center = l.cellCenter(cc.row, cc.col)
            val d = (center - origin).getDistance() / l.cell
            fx.dying += DyingCell(cc.row, cc.col, colorOf(cc.color), start + d * 0.04f, center, l.cell)
        }
    }

    fun dragCenter(shape: Shape, l: GameLayout, liftT: Float): Offset {
        val lift = l.cell * 1.3f * liftT
        return Offset(vis.dragPos.x, vis.dragPos.y - lift - shape.rows * l.cell / 2f * liftT)
    }

    fun updateGhost() {
        val l = vis.layout ?: return
        val piece = game.tray.getOrNull(vis.dragSlot) ?: return
        val shape = piece.shape
        val center = dragCenter(shape, l, 1f)
        val left = center.x - shape.cols * l.cell / 2f
        val top = center.y - shape.rows * l.cell / 2f
        val col = ((left - l.board.left) / l.cell).roundToInt()
        val row = ((top - l.board.top) / l.cell).roundToInt()
        if (game.canPlace(shape, row, col)) {
            if (row != vis.ghostRow || col != vis.ghostCol) {
                vis.ghostRow = row
                vis.ghostCol = col
                val (rows, cols) = game.previewLines(shape, row, col)
                vis.ghostRows = rows
                vis.ghostCols = cols
                if (rows.isNotEmpty() || cols.isNotEmpty()) buzz(HapticFeedbackType.TextHandleMove)
            }
        } else {
            vis.ghostRow = -1; vis.ghostCol = -1
            vis.ghostRows = emptyList(); vis.ghostCols = emptyList()
        }
    }

    fun commitPlace(slot: Int, row: Int, col: Int) {
        val l = vis.layout ?: return
        val piece = game.tray[slot] ?: return
        val shape = piece.shape
        val res = game.place(slot, row, col) ?: return
        val t = clock.floatValue
        val cell = l.cell
        val color = NeonColors.palette[piece.color]
        val pieceCenter = Offset(
            l.board.left + (col + shape.cols / 2f) * cell,
            l.board.top + (row + shape.rows / 2f) * cell,
        )

        for ((dr, dc) in shape.cells) {
            vis.placeTime[(row + dr) * N + col + dc] = t
            fx.sparkle(l.cellCenter(row + dr, col + dc), color, 3, cell)
        }
        fx.rings += Ring(pieceCenter, color, t, cell * 2.4f, 0.4f, cell * 0.08f)
        fx.shake = max(fx.shake, 0.18f)
        sfx.play(Sound.Place, 0.8f)

        if (res.lines > 0) {
            val pr = row + shape.rows / 2f - 0.5f
            val pc = col + shape.cols / 2f - 0.5f
            for (cc in res.clearedCells) {
                val d = hypot(cc.row - pr, cc.col - pc)
                fx.dying += DyingCell(cc.row, cc.col, color, t + 0.05f + d * 0.035f, l.cellCenter(cc.row, cc.col), cell)
            }
            for (r in res.clearedRows) {
                fx.beams += Beam(Rect(l.board.left, l.board.top + r * cell, l.board.right, l.board.top + (r + 1) * cell), true, color, t)
            }
            for (c in res.clearedCols) {
                fx.beams += Beam(Rect(l.board.left + c * cell, l.board.top, l.board.left + (c + 1) * cell, l.board.bottom), false, color, t)
            }
            val lines = res.lines
            fx.shake = min(1f, 0.4f + 0.18f * lines + 0.06f * res.combo)
            fx.flash = min(1f, 0.3f * lines)
            fx.flashColor = color
            pulse.energy = min(1.6f, 0.7f + 0.25f * lines)
            pulse.color = color

            fx.popups += Popup("+${res.gained}", pieceCenter, popupStyle(color, 0.6f, cell), t, 1.1f, cell * 1.3f)

            val praise = when {
                lines >= 5 -> "НЕВЕРОЯТНО!" to NeonColors.Magenta
                lines == 4 -> "ПОТРЯСАЮЩЕ!" to NeonColors.Violet
                lines == 3 -> "ОТЛИЧНО!" to NeonColors.Orange
                lines == 2 -> "СУПЕР!" to NeonColors.Lime
                else -> null
            }
            if (res.combo >= 2) {
                fx.popups += Popup(
                    "КОМБО ×${res.combo}", l.board.center - Offset(0f, cell * 1.2f),
                    popupStyle(NeonColors.rainbow(res.combo.toFloat()), 0.75f, cell), t + 0.1f, 1.3f, cell * 0.6f,
                )
            }
            if (praise != null) {
                fx.popups += Popup(
                    praise.first, l.board.center + Offset(0f, cell * 0.3f),
                    popupStyle(praise.second, 0.95f, cell), t + 0.15f, 1.3f, cell * 0.6f,
                )
            }
            sfx.play(Sound.Clear, 0.9f, 2f.pow(min(res.combo - 1, 12) / 12f))
            if (lines >= 2 || res.combo >= FEVER_COMBO) sfx.play(Sound.Blast, min(1f, 0.5f + 0.15f * lines))
            buzz(HapticFeedbackType.LongPress)
        } else {
            buzz(HapticFeedbackType.TextHandleMove)
        }

        if (res.stone >= 0) {
            // a stone crashes down: drop-in pop and a grey shockwave
            vis.placeTime[res.stone] = t + 0.25f
            val sc = l.cellCenter(res.stone / N, res.stone % N)
            fx.rings += Ring(sc, StoneColor, t + 0.3f, cell * 1.8f, 0.5f, cell * 0.1f)
            fx.popups += Popup("КАМЕНЬ!", sc - Offset(0f, cell * 0.6f), popupStyle(StoneColor, 0.4f, cell), t + 0.25f, 0.9f, cell * 0.5f)
            sfx.play(Sound.Drop, 0.9f, 0.6f)
        }

        if (res.perfect) {
            fx.firework(l.board.center, cell, t + 0.3f)
            fx.popups += Popup("ИДЕАЛЬНО!", l.board.center + Offset(0f, cell * 1.8f), popupStyle(Color.White, 1.1f, cell), t + 0.35f, 1.8f, cell)
            fx.flash = 1f
            pulse.energy = 2f
            sfx.play(Sound.Perfect)
        }

        if (res.trayRefilled) for (i in 0..2) vis.trayAppear[i] = t + 0.2f + i * 0.09f

        syncScore()
        profile.onMove(res, game)
        save()
        if (res.gameOver) triggerGameOver(t)
    }

    /** Called after any power-up changed the board or tray. */
    fun afterPowerUp(p: PowerUp) {
        profile.usePowerUp(p)
        armed.value = null
        val t = clock.floatValue
        if (!game.isGameOver) {
            if (stuck) {
                stuck = false
                lossAdShown = false
                vis.layout?.let { l ->
                    fx.popups += Popup("ИГРАЕМ ДАЛЬШЕ!", l.board.center, popupStyle(NeonColors.Lime, 0.7f, l.cell), t + 0.3f, 1.4f, l.cell)
                }
            }
            save()
        } else if (!stuck) {
            triggerGameOver(t + 0.5f)
        }
    }

    fun useBombOrHammer(p: PowerUp, row: Int, col: Int) {
        val l = vis.layout ?: return
        val t = clock.floatValue
        val center = l.cellCenter(row, col)
        val cells = when (p) {
            PowerUp.BOMB -> game.bomb(row, col)
            PowerUp.HAMMER -> game.hammer(row, col)?.let { listOf(it) }
            PowerUp.REROLL -> null
        }
        if (cells == null) {
            fx.popups += Popup("ТУТ ПУСТО", center, popupStyle(Color.White, 0.45f, l.cell), t, 0.8f, l.cell * 0.5f)
            sfx.play(Sound.Drop, 0.6f)
            return
        }
        blastCells(cells, center, t + 0.05f, l)
        if (p == PowerUp.BOMB) {
            fx.rings += Ring(center, NeonColors.Orange, t, l.cell * 4f, 0.6f, l.cell * 0.25f)
            fx.rings += Ring(center, NeonColors.Yellow, t + 0.08f, l.cell * 3f, 0.5f, l.cell * 0.12f)
            fx.burst(center, NeonColors.Orange, 40, l.cell, 1.4f)
            fx.shake = 1f
            fx.flash = 0.8f
            fx.flashColor = NeonColors.Orange
            pulse.energy = 1.5f
            pulse.color = NeonColors.Orange
            sfx.play(Sound.Blast)
        } else {
            fx.rings += Ring(center, NeonColors.Cyan, t, l.cell * 1.8f, 0.4f, l.cell * 0.12f)
            fx.shake = 0.5f
            sfx.play(Sound.Place, 1f, 0.7f)
        }
        buzz(HapticFeedbackType.LongPress)
        afterPowerUp(p)
    }

    fun onPowerTap(p: PowerUp) {
        if (gameOverAt >= 0f) return
        val l = vis.layout ?: return
        if (profile.powerUps(p) <= 0) {
            sfx.play(Sound.Click)
            armed.value = null
            offer = p
            return
        }
        sfx.play(Sound.Click)
        if (p == PowerUp.REROLL) {
            game.reroll()
            val t = clock.floatValue
            for (i in 0..2) vis.trayAppear[i] = t + i * 0.08f
            for (r in l.slots) fx.sparkle(r.center, NeonColors.Cyan, 10, l.cell)
            sfx.play(Sound.Pick)
            afterPowerUp(p)
        } else {
            armed.value = if (armed.value == p) null else p
        }
    }

    fun doRevive() {
        val l = vis.layout ?: return
        val res = game.revive() ?: return
        profile.onRevive()
        val t = clock.floatValue
        val cell = l.cell
        showGameOver = false
        gameOverAt = -1f
        lossAdShown = false
        val c = l.board.center
        blastCells(res.clearedCells, c, t + 0.3f, l)
        for (r in res.rows) fx.beams += Beam(Rect(l.board.left, l.board.top + r * cell, l.board.right, l.board.top + (r + 1) * cell), true, NeonColors.Yellow, t + 0.3f)
        for (col in res.cols) fx.beams += Beam(Rect(l.board.left + col * cell, l.board.top, l.board.left + (col + 1) * cell, l.board.bottom), false, NeonColors.Yellow, t + 0.3f)
        fx.rings += Ring(c, NeonColors.Yellow, t, cell * 7f, 0.9f, cell * 0.2f)
        fx.shake = 1f
        fx.flash = 0.8f
        fx.flashColor = NeonColors.Yellow
        pulse.energy = 1.6f
        pulse.color = NeonColors.Yellow
        fx.popups += Popup("ВТОРОЙ ШАНС!", c, popupStyle(NeonColors.Yellow, 0.7f, cell), t + 0.35f, 1.6f, cell)
        for (i in 0..2) vis.trayAppear[i] = t + 0.7f + i * 0.09f
        combo = game.combo
        sfx.play(Sound.Revive)
        buzz(HapticFeedbackType.LongPress)
        save()
        if (game.isGameOver) triggerGameOver(t + 1f)
    }

    fun watchAdFor(p: PowerUp) {
        offer = null
        val shown = ads.showRewarded(activity, onReward = {
            profile.grantPowerUp(p)
            sfx.play(Sound.Revive, 0.8f)
            vis.layout?.let { l ->
                fx.popups += Popup("+1 ${p.icon}", l.board.center, popupStyle(NeonColors.Lime, 0.9f, l.cell), clock.floatValue + 0.2f, 1.3f, l.cell)
            }
        })
        if (!shown) {
            sfx.play(Sound.Drop, 0.6f)
            vis.layout?.let { l ->
                fx.popups += Popup("РЕКЛАМА ЗАГРУЖАЕТСЯ…", l.board.center, popupStyle(Color.White, 0.5f, l.cell), clock.floatValue, 1.4f, l.cell * 0.5f)
            }
        }
    }

    fun buyFor(p: PowerUp) {
        offer = null
        val l = vis.layout
        if (profile.buyPowerUp(p)) {
            sfx.play(Sound.Revive, 0.7f)
            if (l != null) fx.popups += Popup("+1 ${p.icon}", l.board.center, popupStyle(NeonColors.Cyan, 0.9f, l.cell), clock.floatValue, 1.3f, l.cell)
        } else {
            sfx.play(Sound.Drop, 0.6f)
            if (l != null) fx.popups += Popup("НЕ ХВАТАЕТ 💎", l.board.center, popupStyle(Color.White, 0.6f, l.cell), clock.floatValue, 1.3f, l.cell * 0.5f)
        }
    }

    fun tryPowerUps() {
        sfx.play(Sound.Click)
        showGameOver = false
        gameOverAt = -1f
        stuck = true
    }

    fun surrender() {
        sfx.play(Sound.Click)
        stuck = false
        armed.value = null
        gameOverAt = clock.floatValue
        showGameOver = true
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Hud(score, combo, best, game.difficulty, ::exitGame)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .onSizeChanged { vis.layout = computeLayout(it.toSize(), density) }
                    .pointerInput(game) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            val l = vis.layout ?: return@awaitEachGesture
                            val power = armed.value
                            if (power != null && l.board.contains(down.position)) {
                                down.consume()
                                val col = ((down.position.x - l.board.left) / l.cell).toInt().coerceIn(0, N - 1)
                                val row = ((down.position.y - l.board.top) / l.cell).toInt().coerceIn(0, N - 1)
                                useBombOrHammer(power, row, col)
                                return@awaitEachGesture
                            }
                            if (game.isGameOver) return@awaitEachGesture
                            val slot = l.slots.indexOfFirst { it.contains(down.position) }
                            if (slot < 0 || game.tray[slot] == null) return@awaitEachGesture
                            armed.value = null
                            if (vis.returnSlot == slot) vis.returnSlot = -1
                            down.consume()
                            vis.dragSlot = slot
                            vis.dragPos = down.position
                            vis.dragStart = clock.floatValue
                            sfx.play(Sound.Pick, 0.6f)
                            buzz(HapticFeedbackType.TextHandleMove)
                            updateGhost()
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                vis.dragPos = ch.position
                                ch.consume()
                                updateGhost()
                            }
                            val shape = game.tray[slot]?.shape
                            if (vis.ghostRow >= 0) {
                                commitPlace(slot, vis.ghostRow, vis.ghostCol)
                            } else if (shape != null) {
                                val liftT = easeOutCubic((clock.floatValue - vis.dragStart) / 0.12f)
                                vis.returnSlot = slot
                                vis.returnFrom = dragCenter(shape, l, liftT)
                                vis.returnFromCell = l.trayCell + (l.cell - l.trayCell) * liftT
                                vis.returnStart = clock.floatValue
                                sfx.play(Sound.Drop, 0.6f)
                            }
                            vis.dragSlot = -1
                            vis.ghostRow = -1; vis.ghostCol = -1
                            vis.ghostRows = emptyList(); vis.ghostCols = emptyList()
                        }
                    },
            ) {
                val t = clock.floatValue
                val l = vis.layout ?: return@Canvas
                val amp = fx.shake * fx.shake * l.cell * 0.3f
                val sx = sin(t * 97f) * amp
                val sy = cos(t * 83f) * amp
                translate(sx, sy) {
                    drawBoard(l, t, game, vis, fx, gameOverAt, pulse.energy, combo, skin, theme, armed.value)
                    fx.drawUnder(this, t)
                }
                drawTray(l, t, game, vis, skin)
                drawDragged(l, t, game, vis, skin)
                translate(sx, sy) { fx.drawOver(this, t, measurer) }
            }
            PowerBar(
                profile = profile,
                armed = armed.value,
                enabled = gameOverAt < 0f,
                stuck = stuck,
                onTap = ::onPowerTap,
                onSurrender = ::surrender,
            )
            AdBanner()
        }

        AnimatedVisibility(
            visible = showGameOver,
            enter = fadeIn(tween(400)) + scaleIn(tween(500), initialScale = 0.8f),
            exit = fadeOut() + scaleOut(),
        ) {
            GameOverOverlay(
                score = score,
                best = best,
                newRecord = newRecord,
                earned = earned,
                canRevive = game.canRevive,

                time = { clock.floatValue },
                onRevive = ::doRevive,
                onPowerUps = ::tryPowerUps,
                onRestart = onRestart,
                onHome = ::exitGame,
            )
        }

        val offered = offer
        AnimatedVisibility(offered != null, enter = fadeIn() + scaleIn(initialScale = 0.85f), exit = fadeOut() + scaleOut()) {
            if (offered != null) {
                PowerUpOffer(
                    p = offered,
                    crystals = profile.crystals,
                    adReady = ads.rewardedReady,
                    onWatch = { watchAdFor(offered) },
                    onBuy = { buyFor(offered) },
                    onDismiss = { offer = null },
                )
            }
        }
    }
}

// --- HUD ---------------------------------------------------------------------

@Composable
private fun Hud(score: Int, combo: Int, best: Int, difficulty: Difficulty, onHome: () -> Unit) {
    val shown by animateIntAsState(score, tween(450), label = "score")
    val bump = remember { Animatable(1f) }
    LaunchedEffect(score) {
        if (score > 0) {
            bump.snapTo(1.25f)
            bump.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 400f))
        }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.align(Alignment.CenterStart)) {
            HomeButton(onHome)
            Text(
                difficulty.title.uppercase(),
                style = glowStyle(DifficultyColors.getValue(difficulty), 10.sp, blur = 10f, weight = FontWeight.Bold),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                shown.toString(),
                style = glowStyle(NeonColors.Cyan, 42.sp, blur = 36f),
                modifier = Modifier.graphicsLayer { scaleX = bump.value; scaleY = bump.value },
            )
            AnimatedVisibility(combo >= 2, enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                val fever = combo >= FEVER_COMBO
                Text(
                    if (fever) "🔥 ОГОНЬ ×$combo" else "КОМБО ×$combo",
                    style = glowStyle(if (fever) NeonColors.Red else NeonColors.Orange, 15.sp, blur = 20f),
                )
            }
        }
        Column(Modifier.align(Alignment.CenterEnd), horizontalAlignment = Alignment.End) {
            Text("👑 РЕКОРД", style = glowStyle(NeonColors.Yellow, 12.sp, blur = 14f, weight = FontWeight.Bold))
            Text(best.toString(), style = glowStyle(NeonColors.Yellow, 20.sp, blur = 18f))
        }
    }
}

@Composable
private fun PowerBar(
    profile: Profile,
    armed: PowerUp?,
    enabled: Boolean,
    stuck: Boolean,
    onTap: (PowerUp) -> Unit,
    onSurrender: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val hint = when {
            armed != null -> "${armed.icon} Нажмите на клетку поля"
            stuck -> "Ходов нет! Используйте бонус"
            else -> null
        }
        if (hint != null) {
            Text(hint, style = glowStyle(if (stuck) NeonColors.Red else NeonColors.Yellow, 14.sp, blur = 14f, weight = FontWeight.Bold))
            Spacer(Modifier.height(6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            for (p in PowerUp.entries) {
                PowerButton(p, profile.powerUps(p), armed == p, enabled) { onTap(p) }
            }
            if (stuck) {
                NeonButton("СДАТЬСЯ", NeonColors.Red, onSurrender, fontSize = 13.sp, compact = true)
            }
        }
    }
}

@Composable
private fun PowerButton(p: PowerUp, count: Int, armed: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val clock = rememberClock()
    val color = when (p) {
        PowerUp.BOMB -> NeonColors.Orange
        PowerUp.HAMMER -> NeonColors.Cyan
        PowerUp.REROLL -> NeonColors.Lime
    }
    Box(
        Modifier
            .size(58.dp)
            .graphicsLayer {
                alpha = if (enabled) 1f else 0.4f
                val s = if (armed) 1.08f + 0.06f * sin(clock.value * 8f) else 1f
                scaleX = s; scaleY = s
            }
            .drawBehind {
                val r = CornerRadius(size.minDimension * 0.3f)
                val lit = if (armed) 1f else if (count > 0) 0.55f else 0.2f
                drawRoundRect(Brush.radialGradient(listOf(color.copy(alpha = 0.4f * lit), Color.Transparent), radius = size.maxDimension), cornerRadius = r)
                drawRoundRect(color.copy(alpha = 0.12f), cornerRadius = r)
                drawRoundRect(lerp(NeonColors.Dead, color, lit), cornerRadius = r, style = Stroke(if (armed) 3.dp.toPx() else 2.dp.toPx()))
            }
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(p.icon, fontSize = 26.sp)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .background(if (count > 0) color else NeonColors.Dead, androidx.compose.foundation.shape.CircleShape)
                .padding(horizontal = 6.dp, vertical = 1.dp),
        ) {
            Text(if (count > 0) count.toString() else "▶", style = TextStyle(color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black))
        }
    }
}

@Composable
private fun GameOverOverlay(
    score: Int,
    best: Int,
    newRecord: Boolean,
    earned: Int,
    canRevive: Boolean,
    time: () -> Float,
    onRevive: () -> Unit,
    onPowerUps: () -> Unit,
    onRestart: () -> Unit,
    onHome: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xCC05010F), Color(0xEE12042A), Color(0xCC05010F))))
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            FlickerText("ИГРА ОКОНЧЕНА", NeonColors.Magenta, 32.sp, time)
            Spacer(Modifier.height(24.dp))
            Text("СЧЁТ", style = glowStyle(NeonColors.Cyan, 16.sp, blur = 16f, weight = FontWeight.Bold))
            Text(score.toString(), style = glowStyle(NeonColors.Cyan, 60.sp, blur = 48f))
            if (newRecord) {
                Spacer(Modifier.height(4.dp))
                NeonTitle("НОВЫЙ РЕКОРД!", time, 28.sp)
            } else {
                Text("👑 РЕКОРД $best", style = glowStyle(NeonColors.Yellow, 18.sp, blur = 18f))
            }
            Spacer(Modifier.height(12.dp))
            CrystalChip(earned, prefix = "+")
            Spacer(Modifier.height(32.dp))
            if (canRevive) {
                NeonButton("✦ ВТОРОЙ ШАНС ✦", NeonColors.Yellow, onRevive, fontSize = 20.sp)
                Text(
                    "взрыв 2 строк и 2 столбцов · один раз за игру",
                    style = glowStyle(NeonColors.Yellow, 12.sp, blur = 8f, weight = FontWeight.Normal),
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(18.dp))
            }
            NeonButton("🧰 ИСПОЛЬЗОВАТЬ БОНУС", NeonColors.Orange, onPowerUps, fontSize = 16.sp)
            Spacer(Modifier.height(18.dp))
            NeonButton("ЕЩЁ РАЗ", NeonColors.Lime, onRestart)
            Spacer(Modifier.height(18.dp))
            NeonButton("МЕНЮ", NeonColors.Cyan, onHome, fontSize = 18.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Результат сохранён в таблице рекордов",
                style = TextStyle(color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Center),
            )
        }
    }
}

/** "No power-up left" dialog: watch a rewarded ad for one, or buy it with crystals. */
@Composable
private fun PowerUpOffer(
    p: PowerUp,
    crystals: Int,
    adReady: Boolean,
    onWatch: () -> Unit,
    onBuy: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xB305010F))
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        NeonCard(
            NeonColors.Orange,
            Modifier
                .padding(28.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
            lit = 1f,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(24.dp),
        ) {
            Text(p.icon, fontSize = 56.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            Text(
                p.title.uppercase(), style = glowStyle(NeonColors.Orange, 24.sp, blur = 20f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                p.description, style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, textAlign = TextAlign.Center),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Бонусы закончились", style = glowStyle(NeonColors.Yellow, 15.sp, blur = 10f, weight = FontWeight.Bold),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(20.dp))
            NeonButton(
                if (adReady) "▶ РЕКЛАМА = +1" else "РЕКЛАМА ГРУЗИТСЯ…",
                NeonColors.Lime, onWatch, fontSize = 17.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(14.dp))
            NeonButton(
                "КУПИТЬ ЗА 💎 ${p.price}", if (crystals >= p.price) NeonColors.Cyan else NeonColors.Dead, onBuy, fontSize = 16.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "У вас 💎 $crystals", style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "ОТМЕНА", style = glowStyle(NeonColors.Magenta, 15.sp, blur = 10f, weight = FontWeight.Bold),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                    .padding(8.dp),
            )
        }
    }
}

// --- drawing -----------------------------------------------------------------

private fun DrawScope.drawShape(skin: BlockSkin, shape: Shape, center: Offset, cell: Float, color: Color, alpha: Float = 1f, glow: Float = 1f) {
    val tl = center - Offset(shape.cols * cell / 2f, shape.rows * cell / 2f)
    for ((dr, dc) in shape.cells) {
        drawBlock(skin, tl + Offset(dc * cell, dr * cell), cell, color, alpha = alpha, glow = glow)
    }
}

private fun DrawScope.drawBoard(
    l: GameLayout,
    t: Float,
    game: BlastGame,
    vis: Visuals,
    fx: FxSystem,
    gameOverAt: Float,
    energy: Float,
    combo: Int,
    skin: BlockSkin,
    theme: Theme,
    armed: PowerUp?,
) {
    val cell = l.cell
    val frame = l.board.inflate(cell * 0.2f)
    val corner = CornerRadius(cell * 0.4f)

    // panel + rotating neon rim
    drawRoundRect(Color(0xE60A0620), frame.topLeft, frame.size, corner)
    val ang = t * 0.9f
    val dir = Offset(cos(ang), sin(ang)) * (frame.width / 2f)
    val fever = combo >= FEVER_COMBO && gameOverAt < 0f
    val rimColors = when {
        armed != null -> listOf(NeonColors.Yellow, NeonColors.Orange, NeonColors.Yellow, NeonColors.Orange)
        fever -> listOf(NeonColors.Yellow, NeonColors.Orange, NeonColors.Red, NeonColors.Yellow)
        else -> theme.rim
    }
    val rim = Brush.linearGradient(rimColors, start = frame.center + dir, end = frame.center - dir)
    val boost = 1f + energy * 0.6f + (if (fever || armed != null) 0.8f + 0.6f * pulse(t, 3f) else 0f)
    drawRoundRect(rim, frame.topLeft, frame.size, corner, style = Stroke(cell * 0.55f), alpha = (0.1f * boost).coerceAtMost(1f))
    drawRoundRect(rim, frame.topLeft, frame.size, corner, style = Stroke(cell * 0.22f), alpha = (0.28f * boost).coerceAtMost(1f))
    drawRoundRect(rim, frame.topLeft, frame.size, corner, style = Stroke(cell * 0.05f))

    // empty slots
    val inset = cell * 0.07f
    val slot = Size(cell - inset * 2, cell - inset * 2)
    for (r in 0 until N) for (c in 0 until N) {
        val tl = l.cellTopLeft(r, c) + Offset(inset, inset)
        drawRoundRect(NeonColors.EmptyCell, tl, slot, CornerRadius(cell * 0.16f))
        drawRoundRect(NeonColors.EmptyStroke, tl, slot, CornerRadius(cell * 0.16f), style = Stroke(cell * 0.025f))
    }

    val dragPiece = game.tray.getOrNull(vis.dragSlot)
    val ghostColor = dragPiece?.let { NeonColors.palette[it.color] }
    val hasGhost = dragPiece != null && vis.ghostRow >= 0

    // highlight lines that the ghost would complete
    if (hasGhost && ghostColor != null) {
        val a = 0.12f + 0.14f * pulse(t, 2.2f)
        for (r in vis.ghostRows) {
            drawRect(ghostColor.copy(alpha = a), Offset(l.board.left, l.board.top + r * cell), Size(l.board.width, cell), blendMode = BlendMode.Plus)
        }
        for (c in vis.ghostCols) {
            drawRect(ghostColor.copy(alpha = a), Offset(l.board.left + c * cell, l.board.top), Size(cell, l.board.height), blendMode = BlendMode.Plus)
        }
    }

    // placed blocks and stones
    for (idx in 0 until N * N) {
        val v = game.grid[idx]
        if (v == 0) continue
        val r = idx / N
        val c = idx % N
        val dtp = t - vis.placeTime[idx]
        if (dtp < 0f) continue
        val scale = 0.4f + 0.6f * easeOutBack(dtp / 0.32f)
        val flash = (1f - dtp / 0.25f).coerceAtLeast(0f) * 0.6f
        if (v - 1 == BlastGame.STONE_INDEX) {
            drawStone(l.cellTopLeft(r, c), cell, scale = scale, flash = flash)
            continue
        }
        var color = NeonColors.palette[v - 1]
        var glow = 0.3f + 0.25f * pulse(t, 0.45f, (r + c) * 0.07f)
        if (hasGhost && ghostColor != null && (r in vis.ghostRows || c in vis.ghostCols)) {
            color = ghostColor
            glow = 0.9f + 0.3f * pulse(t, 2.2f)
        }
        if (gameOverAt >= 0f) {
            val g = smoothstep(0f, 1f, (t - gameOverAt - r * 0.08f) / 0.35f)
            color = lerp(color, NeonColors.Dead, g)
            glow *= 1f - g
        }
        drawBlock(skin, l.cellTopLeft(r, c), cell, color, glow = glow, scale = scale, flash = flash)
    }

    // blocks being blasted
    for (d in fx.dying) {
        val tt = (t - d.start) / 0.3f
        val tl = l.cellTopLeft(d.row, d.col)
        if (tt < 0f) {
            drawBlock(skin, tl, cell, d.color, glow = 1.1f, flash = 0.2f)
        } else if (tt < 1f) {
            drawBlock(skin, tl, cell, d.color, alpha = 1f - tt, glow = 1.6f * (1f - tt), scale = 1f + 0.45f * tt, flash = 1f - tt)
        }
    }

    // ghost preview
    if (hasGhost && dragPiece != null && ghostColor != null) {
        val a = 0.3f + 0.15f * pulse(t, 2.2f)
        for ((dr, dc) in dragPiece.shape.cells) {
            drawBlock(skin, l.cellTopLeft(vis.ghostRow + dr, vis.ghostCol + dc), cell, ghostColor, alpha = a, glow = 0f)
        }
    }
}

private fun DrawScope.drawTray(l: GameLayout, t: Float, game: BlastGame, vis: Visuals, skin: BlockSkin) {
    for (slot in 0..2) {
        val piece = game.tray[slot] ?: continue
        if (slot == vis.dragSlot) continue
        val appear = (t - vis.trayAppear[slot]) / 0.45f
        if (appear < 0f) continue
        val shape = piece.shape
        val fits = game.canPlaceAnywhere(shape)
        var color = NeonColors.palette[piece.color]
        if (!fits) color = lerp(color, NeonColors.Dead, 0.75f)

        var center = l.slots[slot].center + Offset(0f, sin(t * 2f + slot * 1.3f) * l.cell * 0.07f)
        var cs = l.trayCell * easeOutBack(appear)
        if (slot == vis.returnSlot) {
            val rt = (t - vis.returnStart) / 0.25f
            if (rt < 1f) {
                val e = easeOutCubic(rt)
                center = vis.returnFrom + (center - vis.returnFrom) * e
                cs = vis.returnFromCell + (l.trayCell - vis.returnFromCell) * e
            } else {
                vis.returnSlot = -1
            }
        }
        drawShape(skin, shape, center, cs, color, alpha = if (fits) 1f else 0.45f, glow = if (fits) 0.7f else 0f)
    }
}

private fun DrawScope.drawDragged(l: GameLayout, t: Float, game: BlastGame, vis: Visuals, skin: BlockSkin) {
    val piece = game.tray.getOrNull(vis.dragSlot) ?: return
    val shape = piece.shape
    val liftT = easeOutCubic((t - vis.dragStart) / 0.12f)
    val cs = l.trayCell + (l.cell - l.trayCell) * liftT
    val lift = l.cell * 1.3f * liftT
    val center = Offset(vis.dragPos.x, vis.dragPos.y - lift - shape.rows * l.cell / 2f * liftT)
    val color = NeonColors.palette[piece.color]
    val wobble = 1f + 0.04f * sin(t * 12f)
    drawShape(skin, shape, center, cs * wobble, color, glow = 1.3f)
}
