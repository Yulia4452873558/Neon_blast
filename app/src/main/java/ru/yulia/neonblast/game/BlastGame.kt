package ru.yulia.neonblast.game

import kotlin.math.pow
import kotlin.random.Random

data class ClearedCell(val row: Int, val col: Int, val color: Int)

data class MoveResult(
    val piece: Piece,
    val row: Int,
    val col: Int,
    val clearedRows: List<Int>,
    val clearedCols: List<Int>,
    val clearedCells: List<ClearedCell>,
    val gained: Int,
    val combo: Int,
    val perfect: Boolean,
    val trayRefilled: Boolean,
    val gameOver: Boolean,
    /** Board index of a stone that fell after this move, or -1. */
    val stone: Int,
) {
    val lines: Int get() = clearedRows.size + clearedCols.size
}

data class ReviveResult(val rows: List<Int>, val cols: List<Int>, val clearedCells: List<ClearedCell>)

/** Pure game logic: an 8x8 board, a tray of three pieces, scoring, combos, obstacles and power-ups. */
class BlastGame(private val random: Random = Random.Default) {

    companion object {
        const val SIZE = 8
        const val PERFECT_BONUS = 300
        /** Placements without a clear after which the combo streak is lost. */
        const val COMBO_GRACE = 3
        /** Color index used for stone obstacles (grid value STONE_INDEX + 1). */
        const val STONE_INDEX = Shapes.COLOR_COUNT

        private val PERMUTATIONS = listOf(
            intArrayOf(0, 1, 2), intArrayOf(0, 2, 1), intArrayOf(1, 0, 2),
            intArrayOf(1, 2, 0), intArrayOf(2, 0, 1), intArrayOf(2, 1, 0),
        )

        fun lineScore(lines: Int): Int = when (lines) {
            0 -> 0
            1 -> 10
            2 -> 30
            3 -> 60
            4 -> 100
            else -> 100 + (lines - 4) * 60
        }
    }

    /** 0 = empty, otherwise color index + 1 (STONE_INDEX + 1 for stones). */
    val grid = IntArray(SIZE * SIZE)
    val tray = arrayOfNulls<Piece>(3)

    var difficulty = Difficulty.NORMAL; private set
    /** Unique id of the current run, used to de-duplicate saved results. */
    var id = 0L; private set
    var score = 0; private set
    var combo = 0; private set
    var movesSinceClear = 0; private set
    var placements = 0; private set
    var isGameOver = false; private set
    /** The one-per-game second chance has been used. */
    var revived = false; private set
    /** Crystals already credited for this run (results can be finalized more than once after a revive). */
    var crystalsAwarded = 0

    val canRevive: Boolean get() = difficulty.reviveAllowed && !revived

    fun cell(row: Int, col: Int): Int = grid[row * SIZE + col]

    fun reset(difficulty: Difficulty = this.difficulty) {
        this.difficulty = difficulty
        id = System.currentTimeMillis()
        grid.fill(0)
        score = 0
        combo = 0
        movesSinceClear = 0
        placements = 0
        revived = false
        crystalsAwarded = 0
        repeat(difficulty.startStones) { addStone() }
        refillTray()
        isGameOver = !hasAnyMove()
    }

    fun canPlace(shape: Shape, row: Int, col: Int): Boolean = canPlace(grid, shape, row, col)

    fun canPlaceAnywhere(shape: Shape): Boolean = canPlaceAnywhere(grid, shape)

    fun hasAnyMove(): Boolean = tray.any { it != null && canPlaceAnywhere(it.shape) }

    /** Rows and columns that would become full if [shape] were placed at (row, col). */
    fun previewLines(shape: Shape, row: Int, col: Int): Pair<List<Int>, List<Int>> {
        val copy = grid.copyOf()
        for ((dr, dc) in shape.cells) copy[(row + dr) * SIZE + col + dc] = 1
        return fullRows(copy) to fullCols(copy)
    }

    fun place(slot: Int, row: Int, col: Int): MoveResult? {
        val piece = tray[slot] ?: return null
        val shape = piece.shape
        if (isGameOver || !canPlace(shape, row, col)) return null

        for ((dr, dc) in shape.cells) grid[(row + dr) * SIZE + col + dc] = piece.color + 1

        val rows = fullRows(grid)
        val cols = fullCols(grid)
        val cleared = LinkedHashMap<Int, ClearedCell>()
        for (r in rows) for (c in 0 until SIZE) cleared[r * SIZE + c] = ClearedCell(r, c, grid[r * SIZE + c] - 1)
        for (c in cols) for (r in 0 until SIZE) cleared[r * SIZE + c] = ClearedCell(r, c, grid[r * SIZE + c] - 1)
        for (idx in cleared.keys) grid[idx] = 0

        val lines = rows.size + cols.size
        if (lines > 0) {
            combo++
            movesSinceClear = 0
        } else {
            movesSinceClear++
            if (movesSinceClear >= COMBO_GRACE) combo = 0
        }

        val perfect = lines > 0 && grid.all { it == 0 }
        val gained = shape.cells.size +
            lineScore(lines) * maxOf(1, combo) +
            (if (perfect) PERFECT_BONUS else 0)
        score += gained
        placements++

        val stone = if (difficulty.stoneEvery > 0 && lines == 0 && placements % difficulty.stoneEvery == 0) addStone() else -1

        tray[slot] = null
        val refilled = tray.all { it == null }
        if (refilled) refillTray()
        isGameOver = !hasAnyMove()

        return MoveResult(
            piece, row, col, rows, cols, cleared.values.toList(),
            gained, combo, perfect, refilled, isGameOver, stone,
        )
    }

    /** Second chance: blasts the two fullest rows and columns and deals a fresh, playable tray. */
    fun revive(): ReviveResult? {
        if (!isGameOver || !canRevive) return null
        revived = true
        val rows = (0 until SIZE).sortedByDescending { r -> (0 until SIZE).count { c -> cell(r, c) != 0 } }.take(2)
        val cols = (0 until SIZE).sortedByDescending { c -> (0 until SIZE).count { r -> cell(r, c) != 0 } }.take(2)
        val cleared = LinkedHashMap<Int, ClearedCell>()
        for (r in rows) for (c in 0 until SIZE) if (cell(r, c) != 0) cleared[r * SIZE + c] = ClearedCell(r, c, cell(r, c) - 1)
        for (c in cols) for (r in 0 until SIZE) if (cell(r, c) != 0) cleared[r * SIZE + c] = ClearedCell(r, c, cell(r, c) - 1)
        for (idx in cleared.keys) grid[idx] = 0
        combo = 0
        movesSinceClear = 0
        var tries = 0
        do refillTray() while (!hasAnyMove() && ++tries < 50)
        isGameOver = !hasAnyMove()
        return ReviveResult(rows, cols, cleared.values.toList())
    }

    // --- power-ups ------------------------------------------------------------

    /** Clears the 3×3 area around (row, col). Returns null if there was nothing to blast. */
    fun bomb(row: Int, col: Int): List<ClearedCell>? {
        val cleared = mutableListOf<ClearedCell>()
        for (r in row - 1..row + 1) for (c in col - 1..col + 1) {
            if (r !in 0 until SIZE || c !in 0 until SIZE) continue
            val v = cell(r, c)
            if (v != 0) {
                cleared += ClearedCell(r, c, v - 1)
                grid[r * SIZE + c] = 0
            }
        }
        if (cleared.isEmpty()) return null
        isGameOver = !hasAnyMove()
        return cleared
    }

    /** Smashes a single occupied cell (stones included). */
    fun hammer(row: Int, col: Int): ClearedCell? {
        if (row !in 0 until SIZE || col !in 0 until SIZE) return null
        val v = cell(row, col)
        if (v == 0) return null
        grid[row * SIZE + col] = 0
        isGameOver = !hasAnyMove()
        return ClearedCell(row, col, v - 1)
    }

    fun reroll() {
        var tries = 0
        do refillTray() while (!hasAnyMove() && ++tries < 30)
        isGameOver = !hasAnyMove()
    }

    // --- generation -------------------------------------------------------------

    /** Drops a stone on a random empty cell that doesn't complete a line. Returns its index or -1. */
    private fun addStone(): Int {
        val candidates = (0 until SIZE * SIZE).filter { idx ->
            if (grid[idx] != 0) return@filter false
            val r = idx / SIZE
            val c = idx % SIZE
            (0 until SIZE).count { grid[r * SIZE + it] != 0 } < SIZE - 1 &&
                (0 until SIZE).count { grid[it * SIZE + c] != 0 } < SIZE - 1
        }
        if (candidates.isEmpty()) return -1
        val idx = candidates[random.nextInt(candidates.size)]
        grid[idx] = STONE_INDEX + 1
        return idx
    }

    /**
     * Deals three new pieces. Depending on the difficulty, tries to find a set that can be
     * fully placed in some order (or at least partially), so the hand is rarely hopeless.
     */
    fun refillTray() {
        val weights = Shapes.all.map { it.weight * (it.cells.size / 4f).pow(difficulty.sizeBias) }
        val total = weights.sum()
        fun randomPiece(): Piece {
            var pick = random.nextFloat() * total
            var index = weights.lastIndex
            for ((i, w) in weights.withIndex()) {
                pick -= w
                if (pick < 0f) { index = i; break }
            }
            return Piece(index, 0)
        }
        fun randomSet(): Array<Piece> {
            val colors = (0 until Shapes.COLOR_COUNT).shuffled(random)
            return Array(3) { i -> randomPiece().copy(color = colors[i]) }
        }

        var fallback: Array<Piece>? = null
        repeat(30) {
            val set = randomSet()
            val ok = when (difficulty.fairness) {
                2 -> allPlaceable(set)
                else -> set.any { p -> canPlaceAnywhere(p.shape) }
            }
            if (ok) {
                set.copyInto(tray)
                return
            }
            if (fallback == null && set.any { p -> canPlaceAnywhere(p.shape) }) fallback = set
        }
        (fallback ?: randomSet()).copyInto(tray)
    }

    private fun allPlaceable(set: Array<Piece>): Boolean = PERMUTATIONS.any { order ->
        val sim = grid.copyOf()
        order.all { i -> greedyPlace(sim, set[i].shape) }
    }

    /** Places [shape] on [board] where it clears the most lines; false if it doesn't fit. */
    private fun greedyPlace(board: IntArray, shape: Shape): Boolean {
        var bestR = -1; var bestC = -1; var bestLines = -1
        for (r in 0..SIZE - shape.rows) for (c in 0..SIZE - shape.cols) {
            if (!canPlace(board, shape, r, c)) continue
            val copy = board.copyOf()
            for ((dr, dc) in shape.cells) copy[(r + dr) * SIZE + c + dc] = 1
            val lines = fullRows(copy).size + fullCols(copy).size
            if (lines > bestLines) { bestLines = lines; bestR = r; bestC = c }
        }
        if (bestR < 0) return false
        for ((dr, dc) in shape.cells) board[(bestR + dr) * SIZE + bestC + dc] = 1
        val rows = fullRows(board); val cols = fullCols(board)
        for (r in rows) for (c in 0 until SIZE) board[r * SIZE + c] = 0
        for (c in cols) for (r in 0 until SIZE) board[r * SIZE + c] = 0
        return true
    }

    // --- persistence -------------------------------------------------------

    fun serialize(): String = listOf(
        grid.joinToString(","),
        tray.joinToString(",") { p -> p?.let { "${it.shapeIndex}:${it.color}" } ?: "-" },
        score, combo, movesSinceClear, if (revived) 1 else 0,
        difficulty.name, id, crystalsAwarded, placements,
    ).joinToString("|")

    fun restore(data: String): Boolean = try {
        val parts = data.split("|")
        val cells = parts[0].split(",").map { it.toInt() }
        require(cells.size == SIZE * SIZE && cells.all { it in 0..STONE_INDEX + 1 })
        val pieces = parts[1].split(",").map { token ->
            if (token == "-") null else token.split(":").let { (s, c) ->
                require(s.toInt() in Shapes.all.indices && c.toInt() in 0 until Shapes.COLOR_COUNT)
                Piece(s.toInt(), c.toInt())
            }
        }
        require(pieces.size == 3)
        cells.forEachIndexed { i, v -> grid[i] = v }
        pieces.forEachIndexed { i, p -> tray[i] = p }
        score = parts[2].toInt()
        combo = parts[3].toInt()
        movesSinceClear = parts[4].toInt()
        revived = parts.getOrNull(5) == "1"
        difficulty = parts.getOrNull(6)?.let { n -> Difficulty.entries.firstOrNull { it.name == n } } ?: Difficulty.NORMAL
        id = parts.getOrNull(7)?.toLongOrNull() ?: System.currentTimeMillis()
        crystalsAwarded = parts.getOrNull(8)?.toIntOrNull() ?: 0
        placements = parts.getOrNull(9)?.toIntOrNull() ?: 0
        if (tray.all { it == null }) refillTray()
        isGameOver = !hasAnyMove()
        true
    } catch (e: Exception) {
        false
    }
}

private fun canPlace(board: IntArray, shape: Shape, row: Int, col: Int): Boolean =
    shape.cells.all { (dr, dc) ->
        val r = row + dr
        val c = col + dc
        r in 0 until BlastGame.SIZE && c in 0 until BlastGame.SIZE && board[r * BlastGame.SIZE + c] == 0
    }

private fun canPlaceAnywhere(board: IntArray, shape: Shape): Boolean {
    for (r in 0..BlastGame.SIZE - shape.rows) for (c in 0..BlastGame.SIZE - shape.cols) {
        if (canPlace(board, shape, r, c)) return true
    }
    return false
}

private fun fullRows(board: IntArray): List<Int> = (0 until BlastGame.SIZE).filter { r ->
    (0 until BlastGame.SIZE).all { c -> board[r * BlastGame.SIZE + c] != 0 }
}

private fun fullCols(board: IntArray): List<Int> = (0 until BlastGame.SIZE).filter { c ->
    (0 until BlastGame.SIZE).all { r -> board[r * BlastGame.SIZE + c] != 0 }
}
