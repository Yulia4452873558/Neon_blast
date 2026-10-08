package ru.yulia.neonblast

import ru.yulia.neonblast.game.BlastGame
import ru.yulia.neonblast.game.Difficulty
import ru.yulia.neonblast.game.Piece
import ru.yulia.neonblast.game.Shapes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BlastGameTest {
    private val single = Shapes.all.indexOfFirst { it.cells.size == 1 }

    private fun game(): BlastGame = BlastGame(Random(1)).apply { reset() }

    @Test
    fun clearsFullRowAndScores() {
        val g = game()
        for (c in 0 until 7) g.grid[c] = 1
        g.tray[0] = Piece(single, 2)
        val res = g.place(0, 0, 7)
        assertNotNull(res)
        assertEquals(listOf(0), res!!.clearedRows)
        assertEquals(8, res.clearedCells.size)
        assertTrue((0 until 8).all { g.cell(0, it) == 0 })
        assertEquals(1 + BlastGame.lineScore(1) + BlastGame.PERFECT_BONUS, res.gained)
        assertTrue(res.perfect)
    }

    @Test
    fun clearsRowAndColumnTogether() {
        val g = game()
        for (i in 1 until 8) { g.grid[i] = 1; g.grid[i * 8] = 1 }
        g.grid[63] = 1
        g.tray[0] = Piece(single, 0)
        val res = g.place(0, 0, 0)!!
        assertEquals(2, res.lines)
        assertEquals(15, res.clearedCells.size)
        assertFalse(res.perfect)
    }

    @Test
    fun rejectsOverlap() {
        val g = game()
        g.grid[0] = 1
        g.tray[0] = Piece(single, 0)
        assertNull(g.place(0, 0, 0))
    }

    @Test
    fun comboGrowsAndExpires() {
        val g = game()
        repeat(2) {
            for (c in 0 until 7) g.grid[8 * 7 + c] = 1
            g.tray[0] = Piece(single, 0); g.tray[1] = Piece(single, 0)
            g.place(0, 7, 7)
        }
        assertEquals(2, g.combo)
        repeat(BlastGame.COMBO_GRACE) { i ->
            g.tray[0] = Piece(single, 0); g.tray[1] = Piece(single, 0)
            g.place(0, 0, i)
        }
        assertEquals(0, g.combo)
    }

    @Test
    fun serializeRoundTrip() {
        val g = game()
        g.grid[5] = 3
        val copy = BlastGame()
        assertTrue(copy.restore(g.serialize()))
        assertEquals(g.grid.toList(), copy.grid.toList())
        assertEquals(g.tray.toList(), copy.tray.toList())
        assertFalse(copy.restore("garbage"))
    }

    @Test
    fun freshTrayIsAlwaysPlayable() {
        repeat(50) { seed ->
            val g = BlastGame(Random(seed)).apply { reset() }
            assertTrue(g.hasAnyMove())
        }
    }

    @Test
    fun reviveWorksOncePerGame() {
        val g = game()
        // checkerboard leaves no room for the big square
        for (i in 0 until 64) g.grid[i] = if ((i / 8 + i % 8) % 2 == 0) 1 else 0
        val square = Shapes.all.indexOfFirst { it.cells.size == 9 }
        g.tray[0] = Piece(square, 0); g.tray[1] = null; g.tray[2] = null
        assertTrue(g.restore(g.serialize()))
        assertTrue(g.isGameOver)
        val res = g.revive()
        assertNotNull(res)
        assertFalse(g.isGameOver)
        assertTrue(g.revived)
        assertNull(g.revive())
        val copy = BlastGame()
        assertTrue(copy.restore(g.serialize()))
        assertTrue(copy.revived)
    }

    @Test
    fun expertStartsWithStonesAndHasNoRevive() {
        val g = BlastGame(Random(3)).apply { reset(Difficulty.EXPERT) }
        assertEquals(Difficulty.EXPERT.startStones, g.grid.count { it == BlastGame.STONE_INDEX + 1 })
        assertFalse(g.canRevive)
        val copy = BlastGame()
        assertTrue(copy.restore(g.serialize()))
        assertEquals(Difficulty.EXPERT, copy.difficulty)
        assertEquals(g.id, copy.id)
    }

    @Test
    fun stonesAreClearedByLines() {
        val g = game()
        for (c in 0 until 7) g.grid[c] = BlastGame.STONE_INDEX + 1
        g.tray[0] = Piece(single, 0)
        val res = g.place(0, 0, 7)!!
        assertEquals(1, res.lines)
        assertEquals(7, res.clearedCells.count { it.color == BlastGame.STONE_INDEX })
    }

    @Test
    fun bombClearsArea() {
        val g = game()
        for (i in 0 until 64) g.grid[i] = 1
        g.grid[0] = 0 // keep the board from being full rows
        val cleared = g.bomb(4, 4)!!
        assertEquals(9, cleared.size)
        assertNull(g.bomb(4, 4))
        assertNotNull(g.hammer(0, 1))
        assertNull(g.hammer(0, 1))
    }

    @Test
    fun easyFavorsSmallPieces() {
        fun avg(d: Difficulty): Double {
            val g = BlastGame(Random(11)).apply { reset(d) }
            var sum = 0
            repeat(300) { g.refillTray(); sum += g.tray.sumOf { it!!.shape.cells.size } }
            return sum / 900.0
        }
        assertTrue(avg(Difficulty.EASY) < avg(Difficulty.EXPERT))
    }
}
