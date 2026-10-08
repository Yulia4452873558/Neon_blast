package ru.yulia.neonblast.game

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

enum class Stat {
    GAMES, PIECES, LINES, MAX_LINES, MAX_COMBO, PERFECTS, REVIVES, POWERUPS, PURCHASES,
    BEST_ANY, BEST_HARD, BEST_EXPERT,
}

data class GameRecord(val id: Long, val difficulty: Difficulty, val score: Int, val time: Long)

/**
 * Everything that persists between runs: settings, the saved game, crystals, stats,
 * achievements, purchases and the score table. Observable fields are Compose state.
 */
class Profile(context: Context) {
    private val sp = context.getSharedPreferences("neon_blast", Context.MODE_PRIVATE)

    // --- settings -------------------------------------------------------------

    var sound: Boolean
        get() = sp.getBoolean("sound", true)
        set(value) = sp.edit().putBoolean("sound", value).apply()

    var vibration: Boolean
        get() = sp.getBoolean("vibration", true)
        set(value) = sp.edit().putBoolean("vibration", value).apply()

    fun saveGame(data: String?) {
        sp.edit().apply { if (data == null) remove("game") else putString("game", data) }.apply()
    }

    fun loadGame(): String? = sp.getString("game", null)

    // --- currency ---------------------------------------------------------------

    private val crystalState = mutableIntStateOf(sp.getInt("crystals", 0))
    val crystals: Int get() = crystalState.intValue

    fun addCrystals(amount: Int) {
        crystalState.intValue += amount
        sp.edit().putInt("crystals", crystalState.intValue).apply()
    }

    /** Spends crystals if there are enough. */
    fun spend(amount: Int): Boolean {
        if (crystals < amount) return false
        addCrystals(-amount)
        return true
    }

    // --- stats ------------------------------------------------------------------

    fun stat(s: Stat): Int = sp.getInt("stat_${s.name}", 0)

    private fun addStat(s: Stat, delta: Int) {
        if (delta != 0) sp.edit().putInt("stat_${s.name}", stat(s) + delta).apply()
    }

    private fun maxStat(s: Stat, value: Int) {
        if (value > stat(s)) sp.edit().putInt("stat_${s.name}", value).apply()
    }

    // --- achievements -------------------------------------------------------------

    var unlocked by mutableStateOf(sp.getStringSet("ach_unlocked", emptySet())!!.toSet())
        private set
    var claimed by mutableStateOf(sp.getStringSet("ach_claimed", emptySet())!!.toSet())
        private set

    /** Freshly unlocked achievements waiting to be shown as a toast. */
    val toasts = mutableStateListOf<Achievement>()

    val claimable: Int get() = unlocked.count { it !in claimed }

    fun checkAchievements() {
        val fresh = Achievements.all.filter { it.id !in unlocked && it.progress(this) >= it.target }
        if (fresh.isEmpty()) return
        unlocked = unlocked + fresh.map { it.id }
        sp.edit().putStringSet("ach_unlocked", unlocked).apply()
        toasts += fresh
    }

    fun claim(a: Achievement): Boolean {
        if (a.id !in unlocked || a.id in claimed) return false
        claimed = claimed + a.id
        sp.edit().putStringSet("ach_claimed", claimed).apply()
        addCrystals(a.reward)
        return true
    }

    // --- shop -------------------------------------------------------------------------

    var ownedSkins by mutableStateOf(sp.getStringSet("owned_skins", emptySet())!!.toSet())
        private set
    var ownedThemes by mutableStateOf(sp.getStringSet("owned_themes", emptySet())!!.toSet())
        private set
    var skin by mutableStateOf(sp.getString("skin", "")!!)
        private set
    var theme by mutableStateOf(sp.getString("theme", "")!!)
        private set

    // everyone starts with one of each power-up as a gift
    private val powerStates = PowerUp.entries.associateWith { mutableIntStateOf(sp.getInt("pu_${it.name}", 1)) }

    fun powerUps(p: PowerUp): Int = powerStates.getValue(p).intValue

    val totalPowerUps: Int get() = PowerUp.entries.sumOf { powerUps(it) }

    private fun setPowerUps(p: PowerUp, n: Int) {
        powerStates.getValue(p).intValue = n
        sp.edit().putInt("pu_${p.name}", n).apply()
    }

    fun buySkin(name: String, price: Int): Boolean {
        if (!spend(price)) return false
        ownedSkins = ownedSkins + name
        sp.edit().putStringSet("owned_skins", ownedSkins).apply()
        onPurchase()
        return true
    }

    fun buyTheme(name: String, price: Int): Boolean {
        if (!spend(price)) return false
        ownedThemes = ownedThemes + name
        sp.edit().putStringSet("owned_themes", ownedThemes).apply()
        onPurchase()
        return true
    }

    fun buyPowerUp(p: PowerUp): Boolean {
        if (!spend(p.price)) return false
        setPowerUps(p, powerUps(p) + 1)
        onPurchase()
        return true
    }

    /** A power-up earned by watching a rewarded ad. */
    fun grantPowerUp(p: PowerUp) {
        setPowerUps(p, powerUps(p) + 1)
    }

    fun equipSkin(name: String) {
        skin = name
        sp.edit().putString("skin", name).apply()
    }

    fun equipTheme(name: String) {
        theme = name
        sp.edit().putString("theme", name).apply()
    }

    private fun onPurchase() {
        addStat(Stat.PURCHASES, 1)
        checkAchievements()
    }

    // --- game events ---------------------------------------------------------------

    fun onMove(res: MoveResult, game: BlastGame) {
        addStat(Stat.PIECES, 1)
        addStat(Stat.LINES, res.lines)
        maxStat(Stat.MAX_LINES, res.lines)
        maxStat(Stat.MAX_COMBO, res.combo)
        if (res.perfect) addStat(Stat.PERFECTS, 1)
        updateBest(game)
        checkAchievements()
    }

    fun usePowerUp(p: PowerUp): Boolean {
        val n = powerUps(p)
        if (n <= 0) return false
        setPowerUps(p, n - 1)
        addStat(Stat.POWERUPS, 1)
        checkAchievements()
        return true
    }

    fun onRevive() {
        addStat(Stat.REVIVES, 1)
    }

    /**
     * Called whenever a run ends (possibly again after a revive): counts the game once,
     * credits the crystals not yet paid out and stores the result. Returns crystals for the run.
     */
    fun onGameOver(game: BlastGame): Int {
        if (sp.getLong("last_counted_game", 0L) != game.id) {
            sp.edit().putLong("last_counted_game", game.id).apply()
            addStat(Stat.GAMES, 1)
        }
        val total = crystalsFor(game)
        val delta = total - game.crystalsAwarded
        if (delta > 0) addCrystals(delta)
        game.crystalsAwarded = total
        saveResult(game)
        updateBest(game)
        checkAchievements()
        return total
    }

    fun crystalsFor(game: BlastGame): Int = (game.score * game.difficulty.crystalRate / 20f).toInt()

    private fun updateBest(game: BlastGame) {
        maxStat(Stat.BEST_ANY, game.score)
        if (game.difficulty >= Difficulty.HARD) maxStat(Stat.BEST_HARD, game.score)
        if (game.difficulty == Difficulty.EXPERT) maxStat(Stat.BEST_EXPERT, game.score)
    }

    // --- score table ----------------------------------------------------------------

    var records by mutableStateOf(loadRecords())
        private set

    private fun loadRecords(): List<GameRecord> = try {
        val arr = JSONArray(sp.getString("records", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            GameRecord(o.getLong("id"), Difficulty.valueOf(o.getString("d")), o.getInt("s"), o.getLong("t"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun saveResult(game: BlastGame) {
        if (game.score <= 0) return
        val rec = GameRecord(game.id, game.difficulty, game.score, System.currentTimeMillis())
        val merged = records.filter { it.id != game.id } + rec
        // keep the top 10 per difficulty
        records = merged.groupBy { it.difficulty }.values.flatMap { list -> list.sortedByDescending { it.score }.take(10) }
        val arr = JSONArray()
        for (r in records) arr.put(JSONObject().put("id", r.id).put("d", r.difficulty.name).put("s", r.score).put("t", r.time))
        sp.edit().putString("records", arr.toString()).apply()
    }

    fun top(d: Difficulty): List<GameRecord> = records.filter { it.difficulty == d }.sortedByDescending { it.score }

    /** Best score on [d]; the pre-difficulty best counts towards "Normal". */
    fun best(d: Difficulty): Int = maxOf(
        top(d).firstOrNull()?.score ?: 0,
        if (d == Difficulty.NORMAL) sp.getInt("best", 0) else 0,
    )
}
