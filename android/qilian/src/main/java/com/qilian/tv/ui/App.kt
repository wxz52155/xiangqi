package com.qilian.tv.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.qilian.tv.data.Store
import com.qilian.tv.data.TrainingKind
import com.qilian.tv.game.Sounds
import com.qilian.tv.ui.screens.AboutScreen
import com.qilian.tv.ui.screens.GameScreen
import com.qilian.tv.ui.screens.HistoryScreen
import com.qilian.tv.ui.screens.HomeScreen
import com.qilian.tv.ui.screens.ReviewScreen
import com.qilian.tv.ui.screens.SettingsScreen
import com.qilian.tv.ui.screens.SetupScreen
import com.qilian.tv.ui.screens.TrainingHubScreen
import com.qilian.tv.ui.screens.TrainingListScreen
import com.qilian.tv.ui.screens.TrainingPlayScreen

class AppDeps(val context: Context, val store: Store, val sounds: Sounds?) {
    val updates = UpdateController(context)
}

val LocalDeps = staticCompositionLocalOf<AppDeps> { error("AppDeps missing") }

object Routes {
    const val HOME = "home"
    const val SETUP = "setup"
    const val GAME = "game"
    const val TRAIN = "train"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun game(mode: String, color: Int, level: Int, resume: Boolean = false) =
        "game?mode=$mode&color=$color&level=$level&resume=$resume"

    fun gameFromFen(mode: String, color: Int, level: Int, fen: String) =
        "game?mode=$mode&color=$color&level=$level&resume=false&fen=" + android.net.Uri.encode(fen)

    fun trainList(kind: TrainingKind) = "train/${kind.id}"
    fun trainPlay(kind: TrainingKind, id: String) = "train/${kind.id}/$id"
    fun review(id: String) = "review/$id"
}

private data class NavItem(val route: String, val seal: String, val label: String)

private val NAV_ITEMS = listOf(
    NavItem(Routes.HOME, "首", "首页"),
    NavItem(Routes.SETUP, "弈", "人机对弈"),
    NavItem(Routes.TRAIN, "练", "训练营"),
    NavItem(Routes.HISTORY, "谱", "我的棋谱"),
    NavItem(Routes.SETTINGS, "设", "设置"),
)

@Composable
fun QilianApp(deps: AppDeps) {
    QilianTheme {
        CompositionLocalProvider(LocalDeps provides deps) {
            val nav = rememberNavController()
            val appScope = androidx.compose.runtime.rememberCoroutineScope()
            androidx.compose.runtime.LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(2500)
                deps.updates.autoCheck(appScope)
            }
            AppBackground {
                AppNavHost(nav)
                UpdateDialog(deps.updates, appScope)
            }
        }
    }
}

/** 顶层页面套一层左侧导航抽屉；对局、训练等全屏页面不显示。 */
@Composable
internal fun WithDrawer(nav: NavHostController, current: String, content: @Composable () -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    NavigationDrawer(
        drawerState = drawerState,
        drawerContent = { value ->
            Column(
                Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Seal("南", size = 44.dp, modifier = Modifier.padding(start = 6.dp, bottom = 18.dp))
                NAV_ITEMS.forEach { item ->
                    val selected = item.route == current
                    NavigationDrawerItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                nav.navigate(item.route) {
                                    popUpTo(Routes.HOME) { saveState = false }
                                    launchSingleTop = true
                                }
                            }
                        },
                        leadingContent = {
                            Seal(
                                item.seal,
                                size = 30.dp,
                                background = if (selected) Palette.Cinnabar else Palette.InkCard,
                                foreground = if (selected) Palette.Ivory else Palette.GoldSoft,
                            )
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            focusedContainerColor = Palette.Gold,
                            focusedContentColor = Palette.Night,
                            selectedContainerColor = Palette.InkCard,
                            selectedContentColor = Palette.GoldSoft,
                            contentColor = Palette.IvoryMuted,
                        ),
                        modifier = Modifier.padding(vertical = 3.dp),
                    ) {
                        Text(item.label, style = Type.BodyStrong.copy(fontSize = 16.sp, color = androidx.compose.ui.graphics.Color.Unspecified))
                    }
                }
                Spacer(Modifier.weight(1f))
                if (value == DrawerValue.Open) {
                    Text("南坡象棋", style = Type.Small, modifier = Modifier.padding(start = 10.dp))
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize()) { content() }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController) {
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) { WithDrawer(nav, Routes.HOME) { HomeScreen(nav) } }
        composable(Routes.SETUP) { WithDrawer(nav, Routes.SETUP) { SetupScreen(nav) } }
        composable(Routes.TRAIN) { WithDrawer(nav, Routes.TRAIN) { TrainingHubScreen(nav) } }
        composable(Routes.HISTORY) { WithDrawer(nav, Routes.HISTORY) { HistoryScreen(nav) } }
        composable(Routes.SETTINGS) { WithDrawer(nav, Routes.SETTINGS) { SettingsScreen(nav) } }
        composable(Routes.ABOUT) { AboutScreen(nav) }

        composable(
            "game?mode={mode}&color={color}&level={level}&resume={resume}&fen={fen}",
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType; defaultValue = "ai" },
                navArgument("color") { type = NavType.IntType; defaultValue = 1 },
                navArgument("level") { type = NavType.IntType; defaultValue = 2 },
                navArgument("resume") { type = NavType.BoolType; defaultValue = false },
                navArgument("fen") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { e ->
            val args = e.arguments
            GameScreen(
                nav = nav,
                mode = args?.getString("mode") ?: "ai",
                color = args?.getInt("color") ?: 1,
                level = args?.getInt("level") ?: 2,
                resume = args?.getBoolean("resume") ?: false,
                fen = args?.getString("fen"),
            )
        }
        composable("train/{kind}") { e ->
            TrainingListScreen(nav, TrainingKind.of(e.arguments?.getString("kind") ?: "tactics"))
        }
        composable("train/{kind}/{id}") { e ->
            TrainingPlayScreen(
                nav,
                TrainingKind.of(e.arguments?.getString("kind") ?: "tactics"),
                e.arguments?.getString("id") ?: "",
            )
        }
        composable("review/{id}") { e -> ReviewScreen(nav, e.arguments?.getString("id") ?: "") }
    }
}

@Composable
fun rememberDeps(context: Context): AppDeps {
    val store = remember { Store.get(context) }
    val sounds = remember { runCatching { Sounds(context, store) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { sounds?.release() } }
    return remember { AppDeps(context, store, sounds) }
}

@Composable
fun ScreenTitle(eyebrow: String, title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier) {
        Text(eyebrow, style = Type.Label.copy(color = Palette.Gold))
        Spacer(Modifier.height(6.dp))
        Text(title, style = Type.Headline.copy(fontSize = 30.sp))
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = Type.Body, modifier = Modifier.width(620.dp))
        }
    }
}
