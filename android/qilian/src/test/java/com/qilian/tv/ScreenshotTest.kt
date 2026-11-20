package com.qilian.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.rememberNavController
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.qilian.tv.data.Content
import com.qilian.tv.data.Store
import com.qilian.tv.data.TrainingKind
import com.qilian.tv.game.Mode
import com.qilian.tv.game.TrainingSession
import com.qilian.tv.game.XiangqiGame
import com.qilian.tv.ui.AppBackground
import com.qilian.tv.ui.AppDeps
import com.qilian.tv.ui.LocalDeps
import com.qilian.tv.ui.QilianTheme
import com.qilian.tv.ui.Routes
import com.qilian.tv.ui.WithDrawer
import com.qilian.tv.ui.screens.GameLayout
import com.qilian.tv.ui.screens.GamesHubScreen
import com.qilian.tv.ui.screens.HomeScreen
import com.qilian.tv.ui.screens.SettingsScreen
import com.qilian.tv.ui.screens.SetupScreen
import com.qilian.tv.ui.screens.TrainingHubScreen
import com.qilian.tv.ui.screens.TrainingListScreen
import com.qilian.tv.ui.screens.TrainingPlayContent
import com.qilian.tv.ui.screens.GomokuScreen
import com.qilian.tv.ui.screens.JungleScreen
import org.junit.Rule
import org.junit.Test

/** 1080p 电视（xhdpi → 960×540dp）上渲染主要界面，用于检查布局。 */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1920,
            screenHeight = 1080,
            xdpi = 320,
            ydpi = 320,
            density = Density.XHIGH,
            orientation = ScreenOrientation.LANDSCAPE,
            softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    private fun deps() = AppDeps(paparazzi.context, Store.get(paparazzi.context), null)

    private fun shot(content: @Composable () -> Unit) {
        val d = deps()
        paparazzi.snapshot {
            QilianTheme {
                CompositionLocalProvider(LocalDeps provides d) {
                    AppBackground { content() }
                }
            }
        }
    }

    private fun seedContent() {
        val text = javaClass.classLoader!!.getResource("sample-tactics.json")!!.readText()
        Content.seed(TrainingKind.TACTICS, Content.parse(TrainingKind.TACTICS, text))
        val lessons = javaClass.classLoader!!.getResource("sample-endgames.json")!!.readText()
        Content.seed(TrainingKind.ENDGAME, Content.parse(TrainingKind.ENDGAME, lessons))
        Content.seed(TrainingKind.OPENING, Content.parse(TrainingKind.OPENING, lessons))
    }

    @Test
    fun home() {
        seedContent()
        shot {
            val nav = rememberNavController()
            WithDrawer(nav, Routes.HOME) { HomeScreen(nav) }
        }
    }

    @Test
    fun setup() = shot {
        val nav = rememberNavController()
        WithDrawer(nav, Routes.SETUP) { SetupScreen(nav) }
    }

    @Test
    fun game() = shot {
        val scope = rememberCoroutineScope()
        val d = LocalDeps.current
        val game = androidx.compose.runtime.remember {
            XiangqiGame(scope, d.store, null).apply {
                start(Mode.HUMAN, 1, 2)
                for (ucci in listOf("h2e2", "h9g7", "h0g2", "i9h9", "i0h0", "b9c7")) {
                    val (f, t) = com.qilian.tv.xiangqi.Xq.parseUcci(ucci)!!
                    onPoint(f); onPoint(t)
                }
                onPoint(com.qilian.tv.xiangqi.Xq.parseUcci("e2e6")!!.first)
            }
        }
        GameLayout(game, onExit = {}, onReview = {})
    }

    @Test
    fun trainingHub() {
        seedContent()
        shot {
            val nav = rememberNavController()
            WithDrawer(nav, Routes.TRAIN) { TrainingHubScreen(nav) }
        }
    }

    @Test
    fun trainingList() {
        seedContent()
        shot { TrainingListScreen(rememberNavController(), TrainingKind.TACTICS) }
    }

    @Test
    fun trainingPlay() {
        seedContent()
        shot {
            val scope = rememberCoroutineScope()
            val d = LocalDeps.current
            val item = Content.cached(TrainingKind.TACTICS)!!.first()
            val session = androidx.compose.runtime.remember { TrainingSession(item, scope, d.store, null).apply { hint() } }
            TrainingPlayContent(rememberNavController(), TrainingKind.TACTICS, item, session)
        }
    }

    @Test
    fun lessonPlay() {
        seedContent()
        shot {
            val scope = rememberCoroutineScope()
            val d = LocalDeps.current
            val item = Content.cached(TrainingKind.ENDGAME)!!.first()
            val session = androidx.compose.runtime.remember { TrainingSession(item, scope, d.store, null) }
            TrainingPlayContent(rememberNavController(), TrainingKind.ENDGAME, item, session)
        }
    }

    @Test
    fun games() = shot {
        val nav = rememberNavController()
        WithDrawer(nav, Routes.GAMES) { GamesHubScreen(nav) }
    }

    @Test
    fun gomoku() = shot { GomokuScreen(rememberNavController()) }

    @Test
    fun jungle() = shot { JungleScreen(rememberNavController()) }

    @Test
    fun settings() = shot {
        val nav = rememberNavController()
        WithDrawer(nav, Routes.SETTINGS) { SettingsScreen(nav) }
    }
}
