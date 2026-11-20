package com.qilian.tv.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.qilian.tv.ui.Avatar
import com.qilian.tv.ui.LocalDeps
import com.qilian.tv.ui.Palette
import com.qilian.tv.ui.Panel
import com.qilian.tv.ui.Routes
import com.qilian.tv.ui.ScreenTitle
import com.qilian.tv.ui.Seal
import com.qilian.tv.ui.TvButton
import com.qilian.tv.ui.Type
import com.qilian.tv.ui.board.MiniBoard
import com.qilian.tv.xiangqi.Xq

@Composable
fun SetupScreen(nav: NavHostController) {
    val store = LocalDeps.current.store
    var mode by remember { mutableStateOf("ai") }
    var color by remember { mutableIntStateOf(store.settings.userColor) }
    var level by remember { mutableIntStateOf(store.settings.level) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Row(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 34.dp)) {
        Column(Modifier.weight(1f)) {
            ScreenTitle("人机对弈 · 双人同屏", "今天想执哪一边？", subtitle = "选好对手、执子和难度，按“开始对局”。棋局每一步都会自动保存在本机。")
            Spacer(Modifier.height(24.dp))

            Text("对手", style = Type.Label)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ChoiceCard("人机对弈", "有提示与悔棋", "机", mode == "ai", Modifier.focusRequester(first), width = 166.dp) { mode = "ai" }
                ChoiceCard("双人同屏", "两人轮流走子", "双", mode == "human", width = 166.dp) { mode = "human" }
                ChoiceCard("面对面", "平板横放对坐", "对", mode == "face", width = 166.dp, sealColor = Palette.Jade) { mode = "face" }
            }
            Spacer(Modifier.height(20.dp))
            Text(if (mode == "ai") "执子" else "先手", style = Type.Label)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ChoiceCard("执红先行", "你先走，掌握主动", "帅", color == 1, enabled = mode == "ai", sealColor = Palette.PieceRed) { color = 1 }
                ChoiceCard("执黑后行", "先看 AI 如何开局", "将", color == -1, enabled = mode == "ai", sealColor = Palette.PieceBlack) { color = -1 }
            }
            Spacer(Modifier.height(20.dp))
            Text("难度", style = Type.Label)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ChoiceCard("入门", "思考更短", "初", level == 1, width = 150.dp, enabled = mode == "ai", sealColor = Palette.Jade) { level = 1 }
                ChoiceCard("进阶", "兼顾棋力与速度", "中", level == 2, width = 150.dp, enabled = mode == "ai", sealColor = Palette.GoldDeep) { level = 2 }
                ChoiceCard("挑战", "完整强度", "高", level == 3, width = 150.dp, enabled = mode == "ai", sealColor = Palette.Cinnabar) { level = 3 }
            }
            Spacer(Modifier.height(26.dp))
            TvButton(
                "开始对局",
                onClick = {
                    store.updateSettings(store.settings.copy(userColor = color, level = level))
                    nav.navigate(Routes.game(mode, if (mode == "ai") color else 1, level))
                },
                seal = "弈",
                primary = true,
            )
        }
        Spacer(Modifier.width(32.dp))
        Panel(Modifier.width(300.dp).fillMaxHeight()) {
            Text("这一局", style = Type.Label.copy(color = Palette.Gold))
            Spacer(Modifier.height(14.dp))
            MiniBoard(Xq.initial().board, Modifier.fillMaxWidth(), flipped = mode == "ai" && color == -1)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(if (color == 1 || mode != "ai") "帅" else "将", red = color == 1 || mode != "ai", size = 38.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(if (mode != "ai") "红方 · 先走" else "你 · ${if (color == 1) "执红" else "执黑"}", style = Type.BodyStrong)
                    Text(
                        if (mode == "face") "红方坐左侧、黑方坐右侧，棋子字各朝自己" else if (mode == "human") "黑方由另一位玩家操作" else "AI · ${com.qilian.tv.data.levelLabel(level)}",
                        style = Type.Small,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            listOf("每步自动保存到本机", "结束后可在“我的棋谱”回放", "联网用在线 AI，断网自动切本地 AI").forEach {
                Text("· $it", style = Type.Small.copy(color = Palette.IvoryMuted, fontSize = 13.sp))
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/** 可选择的大卡片：选中时金色描边 + 对勾。 */
@Composable
fun ChoiceCard(
    title: String,
    subtitle: String,
    seal: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    width: Dp = 230.dp,
    enabled: Boolean = true,
    sealColor: Color = Palette.Cinnabar,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.touchClick(enabled, onClick).width(width).height(78.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Palette.InkCard else Palette.InkRaised.copy(alpha = 0.7f),
            contentColor = Palette.Ivory,
            focusedContainerColor = Palette.InkCard,
            focusedContentColor = Palette.Ivory,
            disabledContainerColor = Palette.InkRaised.copy(alpha = 0.35f),
            disabledContentColor = Palette.Muted,
        ),
        border = ClickableSurfaceDefaults.border(
            border = androidx.tv.material3.Border(
                BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Palette.Gold.copy(alpha = 0.85f) else Palette.InkLine),
                shape = shape,
            ),
            focusedBorder = Border(BorderStroke(3.dp, Palette.Gold), shape = shape),
            disabledBorder = Border(BorderStroke(1.dp, Palette.InkLine.copy(alpha = 0.4f)), shape = shape),
        ),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(Palette.Gold.copy(alpha = 0.4f), 14.dp)),
    ) {
        Row(Modifier.padding(horizontal = 14.dp).fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Seal(seal, size = 38.dp, background = if (enabled) sealColor else Palette.Muted.copy(alpha = 0.4f))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = Type.CardTitle.copy(color = if (enabled) Palette.Ivory else Palette.Muted))
                Text(subtitle, style = Type.Small, maxLines = 1)
            }
            if (selected) {
                Text("✓", style = Type.Title.copy(color = Palette.Gold, fontSize = 20.sp))
            }
        }
    }
}
