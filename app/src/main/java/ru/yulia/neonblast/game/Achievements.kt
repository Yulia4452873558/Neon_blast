package ru.yulia.neonblast.game

class Achievement(
    val id: String,
    val icon: String,
    val title: String,
    val description: String,
    val target: Int,
    val reward: Int,
    val progress: (Profile) -> Int,
)

object Achievements {
    val all: List<Achievement> = listOf(
        Achievement("games_1", "🎮", "Первый запуск", "Сыграйте первую игру", 1, 10) { it.stat(Stat.GAMES) },
        Achievement("games_10", "🕹️", "Завсегдатай", "Сыграйте 10 игр", 10, 30) { it.stat(Stat.GAMES) },
        Achievement("games_50", "🌃", "Житель неона", "Сыграйте 50 игр", 50, 100) { it.stat(Stat.GAMES) },
        Achievement("pieces_1000", "🧱", "Строитель", "Поставьте 1000 фигур", 1000, 80) { it.stat(Stat.PIECES) },
        Achievement("lines_50", "🧹", "Чистильщик", "Сожгите 50 линий", 50, 25) { it.stat(Stat.LINES) },
        Achievement("lines_500", "💥", "Разрушитель", "Сожгите 500 линий", 500, 150) { it.stat(Stat.LINES) },
        Achievement("multi_3", "⚡", "Тройной удар", "Сожгите 3 линии за один ход", 3, 30) { it.stat(Stat.MAX_LINES) },
        Achievement("multi_5", "☄️", "Мегавзрыв", "Сожгите 5 линий за один ход", 5, 120) { it.stat(Stat.MAX_LINES) },
        Achievement("combo_3", "🔥", "В ударе", "Наберите комбо ×3", 3, 20) { it.stat(Stat.MAX_COMBO) },
        Achievement("combo_6", "🌋", "Огненный поток", "Наберите комбо ×6", 6, 60) { it.stat(Stat.MAX_COMBO) },
        Achievement("combo_10", "🚀", "Неудержимый", "Наберите комбо ×10", 10, 150) { it.stat(Stat.MAX_COMBO) },
        Achievement("perfect_1", "✨", "Идеально", "Полностью очистите поле", 1, 50) { it.stat(Stat.PERFECTS) },
        Achievement("perfect_10", "💎", "Перфекционист", "Очистите поле 10 раз", 10, 200) { it.stat(Stat.PERFECTS) },
        Achievement("score_1000", "🥉", "Тысячник", "Наберите 1000 очков за игру", 1000, 30) { it.stat(Stat.BEST_ANY) },
        Achievement("score_5000", "🥈", "Мастер блоков", "Наберите 5000 очков за игру", 5000, 100) { it.stat(Stat.BEST_ANY) },
        Achievement("score_10000", "🥇", "Легенда неона", "Наберите 10 000 очков за игру", 10000, 250) { it.stat(Stat.BEST_ANY) },
        Achievement("hard_2000", "🛡️", "Закалённый", "2000 очков на «Сложном» или «Эксперте»", 2000, 120) { it.stat(Stat.BEST_HARD) },
        Achievement("expert_3000", "👑", "Эксперт", "3000 очков на «Эксперте»", 3000, 250) { it.stat(Stat.BEST_EXPERT) },
        Achievement("shop_1", "🛍️", "Модник", "Купите что-нибудь в магазине", 1, 20) { it.stat(Stat.PURCHASES) },
        Achievement("powerups_10", "🧰", "Тактик", "Используйте 10 бонусов", 10, 40) { it.stat(Stat.POWERUPS) },
    )
}
