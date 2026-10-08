package ru.yulia.neonblast.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.yulia.neonblast.audio.Sfx
import ru.yulia.neonblast.audio.Sound
import ru.yulia.neonblast.game.Achievements
import ru.yulia.neonblast.game.Difficulty
import ru.yulia.neonblast.game.PowerUp
import ru.yulia.neonblast.game.Profile
import ru.yulia.neonblast.game.Stat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dim = TextStyle(color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)

// --- difficulty ---------------------------------------------------------------------

@Composable
fun DifficultyScreen(profile: Profile, onPick: (Difficulty) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenHeader("СЛОЖНОСТЬ", profile.crystals, onBack)
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(Difficulty.entries) { d ->
                val color = DifficultyColors.getValue(d)
                NeonCard(color, Modifier.fillMaxWidth(), lit = 0.8f, onClick = { onPick(d) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DifficultyStars(d, color)
                        HSpace(14)
                        Column(Modifier.weight(1f)) {
                            Text(d.title.uppercase(), style = glowStyle(color, 22.sp, blur = 20f))
                            VSpace(2)
                            Text(d.description, style = dim)
                        }
                    }
                    VSpace(12)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("👑 ${profile.best(d)}", style = glowStyle(NeonColors.Yellow, 15.sp, blur = 10f, weight = FontWeight.Bold))
                        Text("💎 ×${"%.2f".format(d.crystalRate).trimEnd('0').trimEnd('.', ',')}", style = glowStyle(NeonColors.Cyan, 15.sp, blur = 10f, weight = FontWeight.Bold))
                    }
                }
            }
        }
    }
}

@Composable
private fun DifficultyStars(d: Difficulty, color: Color) {
    Canvas(Modifier.size(width = 44.dp, height = 44.dp)) {
        val n = d.ordinal + 1
        val cell = size.width / 2f
        for (i in 0 until 4) {
            val tl = Offset((i % 2) * cell, (i / 2) * cell)
            if (i < n) drawNeonBlock(tl, cell, color, glow = 0.6f) else drawNeonBlock(tl, cell, NeonColors.Dead, alpha = 0.35f, glow = 0f)
        }
    }
}

// --- shop -----------------------------------------------------------------------------

private enum class ShopTab(val title: String) { SKINS("Скины"), THEMES("Темы"), POWER("Бонусы") }

@Composable
fun ShopScreen(profile: Profile, sfx: Sfx, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(ShopTab.SKINS) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        if (message != null) {
            delay(1600)
            message = null
        }
    }
    fun fail() {
        sfx.play(Sound.Drop)
        message = "Не хватает кристаллов 💎"
    }
    fun success(text: String) {
        sfx.play(Sound.Revive, 0.7f)
        message = text
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            ScreenHeader("МАГАЗИН", profile.crystals, onBack)
            Tabs(ShopTab.entries.map { it.title }, tab.ordinal) {
                sfx.play(Sound.Click)
                tab = ShopTab.entries[it]
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (tab) {
                    ShopTab.SKINS -> items(BlockSkin.entries) { s ->
                        val owned = s.price == 0 || s.name in profile.ownedSkins
                        val equipped = BlockSkin.of(profile.skin) == s
                        ShopItem(
                            title = s.title,
                            subtitle = "Внешний вид блоков",
                            color = NeonColors.Magenta,
                            price = s.price, owned = owned, equipped = equipped,
                            preview = { SkinPreview(s) },
                            onBuy = { if (profile.buySkin(s.name, s.price)) { profile.equipSkin(s.name); success("Куплено: ${s.title}") } else fail() },
                            onEquip = { sfx.play(Sound.Click); profile.equipSkin(s.name) },
                        )
                    }
                    ShopTab.THEMES -> items(Theme.entries) { th ->
                        val owned = th.price == 0 || th.name in profile.ownedThemes
                        val equipped = Theme.of(profile.theme) == th
                        ShopItem(
                            title = th.title,
                            subtitle = "Фон и подсветка поля",
                            color = th.rim[0],
                            price = th.price, owned = owned, equipped = equipped,
                            preview = { ThemePreview(th) },
                            onBuy = { if (profile.buyTheme(th.name, th.price)) { profile.equipTheme(th.name); success("Куплено: ${th.title}") } else fail() },
                            onEquip = { sfx.play(Sound.Click); profile.equipTheme(th.name) },
                        )
                    }
                    ShopTab.POWER -> items(PowerUp.entries) { p ->
                        NeonCard(NeonColors.Orange, Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) { Text(p.icon, fontSize = 32.sp) }
                                HSpace(12)
                                Column(Modifier.weight(1f)) {
                                    Text(p.title.uppercase(), style = glowStyle(NeonColors.Orange, 15.sp, blur = 14f).copy(letterSpacing = 0.5.sp))
                                    Text(p.description, style = dim)
                                    Text("В запасе: ${profile.powerUps(p)}", style = dim.copy(color = NeonColors.Yellow))
                                }
                                HSpace(8)
                                NeonButton("💎 ${p.price}", NeonColors.Cyan, {
                                    if (profile.buyPowerUp(p)) success("+1 ${p.title}") else fail()
                                }, fontSize = 14.sp, compact = true)
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            message != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
        ) {
            NeonCard(NeonColors.Cyan, lit = 1f) {
                Text(message ?: "", style = glowStyle(NeonColors.Cyan, 16.sp, blur = 12f, weight = FontWeight.Bold))
            }
        }
    }
}

@Composable
private fun ShopItem(
    title: String,
    subtitle: String,
    color: Color,
    price: Int,
    owned: Boolean,
    equipped: Boolean,
    preview: @Composable () -> Unit,
    onBuy: () -> Unit,
    onEquip: () -> Unit,
) {
    NeonCard(color, Modifier.fillMaxWidth(), lit = if (equipped) 1f else 0.45f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            preview()
            HSpace(14)
            Column(Modifier.weight(1f)) {
                Text(title.uppercase(), style = glowStyle(color, 15.sp, blur = 14f).copy(letterSpacing = 0.5.sp))
                Text(subtitle, style = dim)
            }
            HSpace(8)
            when {
                equipped -> Text("✓ ВЫБРАНО", style = glowStyle(NeonColors.Lime, 12.sp, blur = 10f).copy(letterSpacing = 0.5.sp))
                owned -> NeonButton("ВЫБРАТЬ", NeonColors.Lime, onEquip, fontSize = 13.sp, compact = true)
                else -> NeonButton("💎 $price", NeonColors.Cyan, onBuy, fontSize = 14.sp, compact = true)
            }
        }
    }
}

@Composable
private fun SkinPreview(skin: BlockSkin) {
    val clock = rememberClock()
    Canvas(Modifier.size(52.dp)) {
        val cell = size.width / 2f
        val colors = listOf(NeonColors.Cyan, NeonColors.Magenta, NeonColors.Yellow, NeonColors.Lime)
        for (i in 0 until 4) {
            drawBlock(skin, Offset((i % 2) * cell, (i / 2) * cell), cell, colors[i], glow = 0.4f + 0.3f * pulse(clock.value, 0.6f, i * 0.2f))
        }
    }
}

@Composable
private fun ThemePreview(theme: Theme) {
    Canvas(Modifier.size(52.dp)) {
        val r = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx())
        drawRoundRect(Brush.verticalGradient(theme.sky), cornerRadius = r)
        drawCircle(Brush.radialGradient(listOf(theme.orbs[0].copy(alpha = 0.5f), Color.Transparent), Offset(size.width * 0.3f, size.height * 0.3f), size.width * 0.5f))
        val horizon = size.height * 0.6f
        for (i in 0 until 4) {
            val y = horizon + (size.height - horizon) * (i / 4f) * (i / 4f) * 1.6f
            drawLine(theme.grid.copy(alpha = 0.7f), Offset(4f, y), Offset(size.width - 4f, y), 2f)
        }
        for (k in -3..3) drawLine(theme.grid.copy(alpha = 0.6f), Offset(size.width / 2f, horizon), Offset(size.width / 2f + k * size.width * 0.25f, size.height), 1.5f)
        drawRoundRect(Brush.linearGradient(theme.rim), cornerRadius = r, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    }
}

@Composable
private fun Tabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        titles.forEachIndexed { i, t ->
            NeonToggleTab(t, i == selected, Modifier.weight(1f)) { onSelect(i) }
        }
    }
}

@Composable
private fun NeonToggleTab(title: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    NeonCard(
        if (selected) NeonColors.Magenta else NeonColors.Dead, modifier, lit = if (selected) 1f else 0.3f,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 12.dp), onClick = onClick,
    ) {
        Text(
            title.uppercase(),
            style = glowStyle(if (selected) NeonColors.Magenta else NeonColors.Dead, 13.sp, blur = if (selected) 12f else 1f, weight = FontWeight.Bold)
                .copy(letterSpacing = 1.sp),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

// --- achievements ------------------------------------------------------------------------

@Composable
fun AchievementsScreen(profile: Profile, sfx: Sfx, onBack: () -> Unit) {
    val list = Achievements.all.sortedBy {
        when {
            it.id in profile.unlocked && it.id !in profile.claimed -> 0
            it.id !in profile.unlocked -> 1
            else -> 2
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenHeader("ДОСТИЖЕНИЯ", profile.crystals, onBack)
        Text(
            "Открыто ${profile.unlocked.size} из ${Achievements.all.size}",
            style = glowStyle(NeonColors.Yellow, 14.sp, blur = 10f, weight = FontWeight.Bold),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list, key = { it.id }) { a ->
                val unlocked = a.id in profile.unlocked
                val claimed = a.id in profile.claimed
                val progress = a.progress(profile).coerceAtMost(a.target)
                val color = when {
                    claimed -> NeonColors.Lime
                    unlocked -> NeonColors.Yellow
                    else -> NeonColors.Violet
                }
                NeonCard(color, Modifier.fillMaxWidth().animateItem(), lit = if (unlocked && !claimed) 1f else 0.4f) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(a.icon, fontSize = 32.sp, modifier = Modifier.padding(end = 12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.title, style = glowStyle(color, 16.sp, blur = 12f).copy(letterSpacing = 0.5.sp))
                            Text(a.description, style = dim)
                        }
                        HSpace(8)
                        when {
                            claimed -> Text("✓", style = glowStyle(NeonColors.Lime, 26.sp, blur = 14f))
                            unlocked -> NeonButton("+${a.reward} 💎", NeonColors.Yellow, {
                                if (profile.claim(a)) sfx.play(Sound.Perfect, 0.7f)
                            }, fontSize = 13.sp, compact = true)
                            else -> Text("💎 ${a.reward}", style = dim)
                        }
                    }
                    if (!claimed) {
                        VSpace(10)
                        NeonProgress(progress / a.target.toFloat(), color)
                        VSpace(4)
                        Text("$progress / ${a.target}", style = dim.copy(fontSize = 11.sp))
                    }
                }
            }
        }
    }
}

/** Slide-down banner shown anywhere in the app when an achievement unlocks. */
@Composable
fun AchievementToast(profile: Profile, sfx: Sfx, modifier: Modifier = Modifier) {
    val current = profile.toasts.firstOrNull()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(current) {
        if (current != null) {
            sfx.play(Sound.Perfect, 0.6f)
            visible = true
            delay(2600)
            visible = false
            delay(400)
            if (profile.toasts.isNotEmpty()) profile.toasts.removeAt(0)
        }
    }
    AnimatedVisibility(
        visible && current != null,
        enter = slideInVertically { -it * 2 } + fadeIn(),
        exit = slideOutVertically { -it * 2 } + fadeOut(),
        modifier = modifier.safeDrawingPadding().padding(top = 8.dp),
    ) {
        val a = current ?: return@AnimatedVisibility
        NeonCard(NeonColors.Yellow, lit = 1f) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.icon, fontSize = 30.sp, modifier = Modifier.padding(end = 12.dp))
                Column {
                    Text("🏆 ДОСТИЖЕНИЕ!", style = glowStyle(NeonColors.Yellow, 12.sp, blur = 8f, weight = FontWeight.Bold))
                    Text(a.title, style = glowStyle(NeonColors.Yellow, 18.sp, blur = 14f))
                    Text("Награда ждёт: +${a.reward} 💎", style = dim)
                }
            }
        }
    }
}

// --- records ----------------------------------------------------------------------------

@Composable
fun RecordsScreen(profile: Profile, sfx: Sfx, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Difficulty.NORMAL) }
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.forLanguageTag("ru")) }
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        ScreenHeader("РЕКОРДЫ", profile.crystals, onBack)
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in Difficulty.entries) {
                val c = DifficultyColors.getValue(d)
                NeonCard(
                    if (tab == d) c else NeonColors.Dead, Modifier.weight(1f), lit = if (tab == d) 1f else 0.3f,
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 12.dp), onClick = {
                    sfx.play(Sound.Click)
                    tab = d
                }) {
                    Text(
                        d.title.uppercase(),
                        style = glowStyle(if (tab == d) c else NeonColors.Dead, 11.sp, blur = 8f, weight = FontWeight.Bold).copy(letterSpacing = 0.sp),
                        softWrap = false,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        maxLines = 1,
                    )
                }
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val top = profile.top(tab)
            if (top.isEmpty()) {
                item {
                    NeonCard(NeonColors.Dead, Modifier.fillMaxWidth(), lit = 0.3f) {
                        Text("Пока нет результатов.\nСыграйте на уровне «${tab.title}»!", style = dim, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
            items(top.size) { i ->
                val r = top[i]
                val medal = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." }
                val color = if (i == 0) NeonColors.Yellow else DifficultyColors.getValue(tab)
                NeonCard(color, Modifier.fillMaxWidth(), lit = if (i == 0) 0.9f else 0.35f) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(medal, style = glowStyle(color, 22.sp, blur = 10f), modifier = Modifier.size(width = 44.dp, height = 30.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.score.toString(), style = glowStyle(color, 22.sp, blur = 14f))
                            Text(fmt.format(Date(r.time)), style = dim.copy(fontSize = 11.sp))
                        }
                    }
                }
            }
            item {
                VSpace(8)
                Text("СТАТИСТИКА", style = glowStyle(NeonColors.Cyan, 16.sp, blur = 14f), modifier = Modifier.padding(bottom = 8.dp))
                NeonCard(NeonColors.Cyan, Modifier.fillMaxWidth(), lit = 0.4f) {
                    StatRow("Сыграно игр", profile.stat(Stat.GAMES))
                    StatRow("Поставлено фигур", profile.stat(Stat.PIECES))
                    StatRow("Сожжено линий", profile.stat(Stat.LINES))
                    StatRow("Максимум линий за ход", profile.stat(Stat.MAX_LINES))
                    StatRow("Лучшее комбо", profile.stat(Stat.MAX_COMBO))
                    StatRow("Идеальных очисток", profile.stat(Stat.PERFECTS))
                    StatRow("Использовано бонусов", profile.stat(Stat.POWERUPS))
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = dim.copy(fontSize = 14.sp))
        Text(value.toString(), style = glowStyle(NeonColors.Cyan, 15.sp, blur = 8f, weight = FontWeight.Bold))
    }
}
