package com.qilian.tv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import com.qilian.tv.data.OnlineAi
import com.qilian.tv.ui.ConfirmDialog
import com.qilian.tv.ui.LocalDeps
import com.qilian.tv.ui.Palette
import com.qilian.tv.ui.Panel
import com.qilian.tv.ui.Pill
import com.qilian.tv.ui.Routes
import com.qilian.tv.ui.ScreenTitle
import com.qilian.tv.ui.Seal
import com.qilian.tv.ui.Segmented
import com.qilian.tv.ui.TvButton
import com.qilian.tv.ui.Type

@Composable
fun SettingsScreen(nav: NavHostController) {
    val store = LocalDeps.current.store
    val s = store.settings
    var confirmClear by remember { mutableStateOf(false) }
    val online by produceState<Boolean?>(initialValue = null) { value = OnlineAi.health() }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val deps = LocalDeps.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Row(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 34.dp)) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { ScreenTitle("设置", "把这台电视调成你的棋桌", subtitle = "所有偏好只保存在本机；没有账号，也没有云端同步。") }
            item { Spacer(Modifier.height(6.dp)) }
            item {
                SettingRow("默认执子", "快速开局时使用") {
                    Segmented(listOf(1 to "执红", -1 to "执黑"), s.userColor, { store.updateSettings(s.copy(userColor = it)) }, Modifier.focusRequester(first))
                }
            }
            item {
                SettingRow("默认难度", "入门更短思考，挑战使用完整强度") {
                    Segmented(listOf(1 to "入门", 2 to "进阶", 3 to "挑战"), s.level, { store.updateSettings(s.copy(level = it)) })
                }
            }
            item {
                SettingRow("在线 AI", "联网时由在线 Pikafish 走子；关闭后始终使用本地 AI") {
                    Segmented(listOf(true to "开", false to "关"), s.onlineAi, { store.updateSettings(s.copy(onlineAi = it)) })
                }
            }
            item {
                SettingRow("允许悔棋", "人机对战：机器最后一步退 2 手，自己最后一步退 1 手") {
                    Segmented(listOf(true to "开", false to "关"), s.allowUndo, { store.updateSettings(s.copy(allowUndo = it)) })
                }
            }
            item {
                SettingRow("显示可走位置", "选中棋子后用绿点标出能走的位置") {
                    Segmented(listOf(true to "显示", false to "隐藏"), s.showLegalMoves, { store.updateSettings(s.copy(showLegalMoves = it)) })
                }
            }
            item {
                SettingRow("落子音效", "木质落子声，跟随电视音量") {
                    Segmented(listOf(true to "开", false to "关"), s.sound, { store.updateSettings(s.copy(sound = it)) })
                }
            }
            item {
                SettingRow("本机数据", "清除棋谱、训练进度、收藏和设置，不可恢复") {
                    TvButton("清除所有数据", { confirmClear = true }, seal = "删", danger = true, compact = true)
                }
            }
        }
        Spacer(Modifier.width(30.dp))
        Column(Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            com.qilian.tv.ui.VersionPanel(deps.updates, scope)
            Panel(Modifier.fillMaxWidth()) {
                Text("AI 引擎", style = Type.Label.copy(color = Palette.Gold))
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("在线 AI", style = Type.BodyStrong)
                    Spacer(Modifier.weight(1f))
                    when (online) {
                        null -> Pill("检测中", color = Palette.Muted)
                        true -> Pill("可用", color = Palette.Jade)
                        false -> Pill("未连接", color = Palette.Cinnabar)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("本地 AI", style = Type.BodyStrong)
                    Spacer(Modifier.weight(1f))
                    Pill("已就绪", color = Palette.Jade)
                }
                Spacer(Modifier.height(10.dp))
                Text("在线 AI 不可用时会自动改用本机 AI，不需要联网也能下。", style = Type.Small)
            }
            Panel(Modifier.fillMaxWidth()) {
                Text("关于南坡象棋", style = Type.Label.copy(color = Palette.Gold))
                Spacer(Modifier.height(8.dp))
                Text("专注中国象棋：对弈、杀法、残局、开局。不做账号、不做匹配、不做排行榜。", style = Type.Small.copy(color = Palette.IvoryMuted))
                Spacer(Modifier.height(12.dp))
                TvButton("关于与许可", { nav.navigate(Routes.ABOUT) }, seal = "关", compact = true)
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "清除所有本机数据？",
            message = "棋谱、训练进度、收藏和设置都会被删除，此操作不可恢复。",
            confirmText = "清除",
            onConfirm = {
                confirmClear = false
                store.clearAll()
            },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, control: @Composable () -> Unit) {
    Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.CardTitle.copy(fontSize = 17.sp))
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = Type.Small)
            }
            Spacer(Modifier.width(16.dp))
            control()
        }
    }
}

@Composable
fun AboutScreen(nav: NavHostController) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Row(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 40.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Seal("南", size = 56.dp)
                Spacer(Modifier.width(18.dp))
                Column {
                    Text("南坡象棋", style = Type.Headline.copy(fontSize = 32.sp))
                    Text("中国象棋 · 电视与平板 · v${LocalDeps.current.updates.installed.versionName}", style = Type.Body)
                }
            }
            Spacer(Modifier.height(24.dp))
            AboutBlock(
                "从哪里来",
                "南坡象棋基于开源项目“棋练”（github.com/chuccp/chess-train）制作：规则核、训练题库（一步杀法、残棋定式、开局）与在线 AI 接口均来自该项目，界面为电视和平板重新设计。",
            )
            AboutBlock(
                "内容来源",
                "残棋与杀法主要整理自《适情雅趣》《橘中秘》《梅花谱》《自出洞来无敌手》《竹香斋》《百局象棋谱》等古谱。古谱原文属于公有领域；讲解由原项目重新整理，原创题目以 CC0 发布。",
            )
            AboutBlock(
                "AI 与隐私",
                "在线 AI 由 auto-chess 服务计算（象棋为 Pikafish + NNUE），每次只发送当前局面；离线时使用本机 AI。没有账号与统计，棋谱和进度只保存在这台设备里，卸载或清除应用数据会一并删除。",
            )
            AboutBlock(
                "许可",
                "南坡象棋与原项目一样以 GPL-3.0 发布源代码；Pikafish 代码 GPL-3.0，NNUE 权重遵循 Pikafish NNUE-License（免费、非商业）。本版本免费、非商业，不含广告或付费内容。",
            )
        }
        Spacer(Modifier.width(40.dp))
        Column {
            TvButton("返回", { nav.popBackStack() }, Modifier.focusRequester(focus), seal = "返", primary = true)
        }
    }
}

@Composable
private fun AboutBlock(title: String, body: String) {
    Text(title, style = Type.Title)
    Spacer(Modifier.height(6.dp))
    Text(body, style = Type.Body.copy(fontSize = 15.sp, lineHeight = 25.sp))
    Spacer(Modifier.height(18.dp))
}
