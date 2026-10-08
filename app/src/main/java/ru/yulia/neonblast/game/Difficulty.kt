package ru.yulia.neonblast.game

/**
 * @param crystalRate multiplier for crystals earned from the score.
 * @param sizeBias >0 favors big pieces, <0 favors small ones.
 * @param fairness 2 = the whole tray is always placeable, 1 = at least one piece fits.
 * @param startStones stone obstacles placed on a new board.
 * @param stoneEvery a stone falls on the board every N placements without a clear (0 = never).
 */
enum class Difficulty(
    val title: String,
    val description: String,
    val crystalRate: Float,
    val sizeBias: Float,
    val fairness: Int,
    val startStones: Int,
    val stoneEvery: Int,
    val reviveAllowed: Boolean,
) {
    EASY("Лёгкий", "Небольшие фигуры, ход есть всегда", 1f, -0.9f, 2, 0, 0, true),
    NORMAL("Обычный", "Классический баланс фигур", 1.25f, 0f, 2, 0, 0, true),
    HARD("Сложный", "Крупные фигуры, удача не гарантирована", 1.6f, 0.6f, 1, 0, 0, true),
    EXPERT("Эксперт", "Камни на поле и каменный дождь. Без второго шанса", 2.2f, 0.8f, 1, 5, 6, false),
}

enum class PowerUp(val title: String, val icon: String, val description: String, val price: Int) {
    BOMB("Бомба", "💣", "Взрывает область 3×3", 60),
    HAMMER("Молот", "🔨", "Разбивает одну клетку, даже камень", 25),
    REROLL("Обновить", "🔄", "Заменяет фигуры в лотке", 40),
}
