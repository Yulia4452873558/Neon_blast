package ru.yulia.neonblast.game

/** A block figure described by its occupied cells as (row, col) offsets from the top-left corner. */
class Shape(val cells: List<Pair<Int, Int>>, val weight: Int) {
    val rows: Int = cells.maxOf { it.first } + 1
    val cols: Int = cells.maxOf { it.second } + 1
}

/** A concrete piece in the tray: a shape plus a palette color index. */
data class Piece(val shapeIndex: Int, val color: Int) {
    val shape: Shape get() = Shapes.all[shapeIndex]
}

private fun shape(weight: Int, vararg rows: String): Shape {
    val cells = mutableListOf<Pair<Int, Int>>()
    rows.forEachIndexed { r, line ->
        line.forEachIndexed { c, ch -> if (ch == '#') cells += r to c }
    }
    return Shape(cells, weight)
}

object Shapes {
    const val COLOR_COUNT = 8

    val all: List<Shape> = listOf(
        // dots & lines
        shape(3, "#"),
        shape(6, "##"), shape(6, "#", "#"),
        shape(6, "###"), shape(6, "#", "#", "#"),
        shape(5, "####"), shape(5, "#", "#", "#", "#"),
        shape(3, "#####"), shape(3, "#", "#", "#", "#", "#"),
        // squares & rectangles
        shape(8, "##", "##"),
        shape(3, "###", "###", "###"),
        shape(4, "##", "##", "##"), shape(4, "###", "###"),
        // small corners
        shape(5, "##", "#."), shape(5, "##", ".#"), shape(5, "#.", "##"), shape(5, ".#", "##"),
        // L
        shape(4, "#.", "#.", "##"), shape(4, ".#", ".#", "##"), shape(4, "##", "#.", "#."), shape(4, "##", ".#", ".#"),
        shape(4, "###", "#.."), shape(4, "###", "..#"), shape(4, "#..", "###"), shape(4, "..#", "###"),
        // big corners
        shape(2, "#..", "#..", "###"), shape(2, "..#", "..#", "###"),
        shape(2, "###", "#..", "#.."), shape(2, "###", "..#", "..#"),
        // T
        shape(4, "###", ".#."), shape(4, ".#.", "###"), shape(4, "#.", "##", "#."), shape(4, ".#", "##", ".#"),
        // S / Z
        shape(4, ".##", "##."), shape(4, "##.", ".##"), shape(4, "#.", "##", ".#"), shape(4, ".#", "##", "#."),
        // diagonals
        shape(2, "#.", ".#"), shape(2, ".#", "#."),
    )

    val totalWeight: Int = all.sumOf { it.weight }
}
