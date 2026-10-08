package ru.yulia.neonblast

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ru.yulia.neonblast.ads.AdsManager
import ru.yulia.neonblast.audio.Sfx
import ru.yulia.neonblast.audio.Sound
import ru.yulia.neonblast.game.BlastGame
import ru.yulia.neonblast.game.Difficulty
import ru.yulia.neonblast.game.Profile
import ru.yulia.neonblast.ui.AchievementToast
import ru.yulia.neonblast.ui.AchievementsScreen
import ru.yulia.neonblast.ui.BackgroundPulse
import ru.yulia.neonblast.ui.BlockSkin
import ru.yulia.neonblast.ui.DifficultyScreen
import ru.yulia.neonblast.ui.GameScreen
import ru.yulia.neonblast.ui.MenuScreen
import ru.yulia.neonblast.ui.NeonBackground
import ru.yulia.neonblast.ui.NeonColors
import ru.yulia.neonblast.ui.RecordsScreen
import ru.yulia.neonblast.ui.ShopScreen
import ru.yulia.neonblast.ui.Theme
import ru.yulia.neonblast.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private lateinit var sfx: Sfx

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val profile = Profile(this)
        sfx = Sfx(applicationContext).apply { enabled = profile.sound }
        setContent {
            MyApplicationTheme(darkTheme = true, dynamicColor = false) {
                NeonBlastApp(profile, sfx, (application as NeonBlastApplication).ads)
            }
        }
    }

    override fun onDestroy() {
        sfx.release()
        super.onDestroy()
    }
}

private enum class Screen { Menu, Difficulty, Game, Shop, Achievements, Records }

@Composable
private fun NeonBlastApp(profile: Profile, sfx: Sfx, ads: AdsManager) {
    val pulse = remember { BackgroundPulse() }
    val game = remember {
        BlastGame().apply {
            val saved = profile.loadGame()
            if (saved == null || !restore(saved) || isGameOver) reset(Difficulty.NORMAL)
        }
    }
    var screen by rememberSaveable { mutableStateOf(Screen.Menu) }
    var gameKey by remember { mutableIntStateOf(0) }
    var sound by remember { mutableStateOf(profile.sound) }
    var vibration by remember { mutableStateOf(profile.vibration) }

    fun go(s: Screen) {
        sfx.play(Sound.Click)
        screen = s
    }

    fun newGame(d: Difficulty) {
        sfx.play(Sound.Click)
        game.reset(d)
        profile.saveGame(game.serialize())
        gameKey++
        screen = Screen.Game
    }

    // the game screen handles Back itself (it shows an interstitial before leaving)
    BackHandler(enabled = screen != Screen.Menu && screen != Screen.Game) { screen = Screen.Menu }

    Box(Modifier.fillMaxSize().background(NeonColors.Night)) {
        NeonBackground(pulse, Theme.of(profile.theme))
        Crossfade(screen, animationSpec = tween(400), label = "screen") { s ->
            when (s) {
                Screen.Menu -> MenuScreen(
                    crystals = profile.crystals,
                    skin = BlockSkin.of(profile.skin),
                    continueDifficulty = game.difficulty.takeIf {
                        profile.loadGame() != null && game.score > 0 && !game.isGameOver
                    },
                    achievementsBadge = profile.claimable,
                    sound = sound,
                    vibration = vibration,
                    onToggleSound = {
                        sound = !sound
                        profile.sound = sound
                        sfx.enabled = sound
                        sfx.play(Sound.Click)
                    },
                    onToggleVibration = {
                        vibration = !vibration
                        profile.vibration = vibration
                        sfx.play(Sound.Click)
                    },
                    onContinue = { go(Screen.Game) },
                    onNewGame = { go(Screen.Difficulty) },
                    onShop = { go(Screen.Shop) },
                    onAchievements = { go(Screen.Achievements) },
                    onRecords = { go(Screen.Records) },
                )
                Screen.Difficulty -> DifficultyScreen(profile, onPick = ::newGame, onBack = { go(Screen.Menu) })
                Screen.Shop -> ShopScreen(profile, sfx, onBack = { go(Screen.Menu) })
                Screen.Achievements -> AchievementsScreen(profile, sfx, onBack = { go(Screen.Menu) })
                Screen.Records -> RecordsScreen(profile, sfx, onBack = { go(Screen.Menu) })
                Screen.Game -> key(gameKey) {
                    GameScreen(
                        game = game,
                        profile = profile,
                        pulse = pulse,
                        sfx = sfx,
                        ads = ads,
                        vibration = vibration,
                        onHome = { screen = Screen.Menu },
                        onRestart = { newGame(game.difficulty) },
                    )
                }
            }
        }
        AchievementToast(profile, sfx, Modifier.align(Alignment.TopCenter))
    }
}
