package com.qilian.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import com.qilian.tv.data.levelLabel
import com.qilian.tv.game.Mode
import com.qilian.tv.game.PlyRecord
import com.qilian.tv.game.XiangqiGame
import com.qilian.tv.ui.Avatar
import com.qilian.tv.ui.ConfirmDialog
import com.qilian.tv.ui.LocalDeps
import com.qilian.tv.ui.Palette
import com.qilian.tv.ui.Panel
import com.qilian.tv.ui.Routes
import com.qilian.tv.ui.Seal
import com.qilian.tv.ui.Toast
import com.qilian.tv.ui.TvButton
import com.qilian.tv.ui.Type
import com.qilian.tv.ui.board.XiangqiBoard
import com.qilian.tv.xiangqi.Xq

@Composable
fun GameScreen(nav: NavHostController, mode: String, color: Int, level: Int, resume: Boolean, fen: String? = null) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    val game = remember { XiangqiGame(scope, deps.store, deps.sounds) }
    DisposableEffect(Unit) { onDispose { game.dispose() } }
    LaunchedEffect(Unit) {
        val saved = if (resume) deps.store.currentGame() else null
        if (saved == null || !game.resume(saved)) {
            game.start(if (mode == "human") Mode.HUMAN else Mode.AI, color, level, fen?.takeIf { it.isNotBlank() } ?: Xq.START_FEN)
        }
    }
    GameLayout(
        game = game,
        onExit = { nav.popBackStack() },
        onReview = { id -> nav.navigate(Routes.review(id)) },
    )
}

@Composable
fun GameLayout(game: XiangqiGame, onExit: () -> Unit, onReview: (String) -> Unit) {
    val store = LocalDeps.current.store
    val boardFocus = remember { FocusRequester() }
    var confirm by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { boardFocus.requestFocus() } }

    BackHandler(enabled = game.selected != null) { game.clearSelection() }

    Row(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 22.dp)) {
        // ---------------- 棋盘
        Box(Modifier.fillMaxHeight().weight(1f), contentAlignment = Alignment.Center) {
            XiangqiBoard(
                board = game.position.board,
                flipped = game.flipped,
                marks = game.marks(store.settings.showLegalMoves),
                interactive = true,
                focusRequester = boardFocus,
                modifier = Modifier.fillMaxHeight(),
                onPoint = { game.onPoint(it) },
            )
            Column(Modifier.align(Alignment.TopCenter).padding(top = 14.dp)) {
                AnimatedVisibility(game.message != null, enter = fadeIn(), exit = fadeOut()) {
                    Toast(game.message ?: "")
                }
            }
            if (game.finished) {
                ResultCard(
                    title = game.resultTitle(),
                    detail = game.resultDetail(),
                    win = game.resultTitle().contains("赢") || (game.mode == Mode.HUMAN && game.resultTitle() != "和棋"),
                    onAgain = { game.restart(); runCatching { boardFocus.requestFocus() } },
                    onReview = { store.history().firstOrNull()?.let { onReview(it.id) } },
                    onExit = onExit,
                )
            }
        }
        Spacer(Modifier.width(28.dp))
        // ---------------- 侧栏
        Column(Modifier.width(360.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Seal(if (game.mode == Mode.AI) "弈" else "双", size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (game.mode == Mode.AI) "人机对弈 · ${levelLabel(game.level)}" else "双人同屏",
                    style = Type.Title,
                )
                Spacer(Modifier.weight(1f))
                Text("返回键 / 退出按钮离开", style = Type.Small)
            }
            PlayersPanel(game)
            MoveListPanel(game.plies, Modifier.weight(1f))
            ActionGrid {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("悔棋", { game.undo() }, Modifier.weight(1f), seal = "悔", enabled = game.canUndo && !game.finished, compact = true)
                    TvButton(
                        if (game.hintBusy) "思考中" else "提示",
                        { game.requestHint() },
                        Modifier.weight(1f),
                        seal = "示",
                        enabled = !game.finished && game.isUserTurn && !game.thinking,
                        compact = true,
                    )
                    TvButton(
                        if (game.mode == Mode.AI) "换边" else "翻转",
                        { game.switchSide() },
                        Modifier.weight(1f),
                        seal = "换",
                        enabled = !game.finished && !game.thinking,
                        compact = true,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("新局", { confirm = "new" }, Modifier.weight(1f), seal = "新", compact = true)
                    TvButton("认输", { confirm = "resign" }, Modifier.weight(1f), seal = "降", danger = true, enabled = !game.finished, compact = true)
                    TvButton("退出", onExit, Modifier.weight(1f), seal = "退", compact = true)
                }
            }
        }
    }

    when (confirm) {
        "new" -> ConfirmDialog(
            title = "开始新的一局？",
            message = if (game.finished || game.plies.isEmpty()) "按当前设置重新摆棋。" else "当前这盘棋会被放弃（不计入棋谱）。",
            confirmText = "开新局",
            danger = false,
            onConfirm = { confirm = null; game.restart() },
            onDismiss = { confirm = null },
        )
        "resign" -> ConfirmDialog(
            title = "确认认输？",
            message = if (game.mode == Mode.AI) "认输后本局判 AI 获胜，并保存到棋谱。" else "当前走子方（${if (game.position.turn == Xq.RED) "红方" else "黑方"}）认输。",
            confirmText = "认输",
            onConfirm = { confirm = null; game.resign() },
            onDismiss = { confirm = null },
        )
    }
}

@Composable
private fun PlayersPanel(game: XiangqiGame) {
    Panel(padding = 16.dp) {
        val topColor = if (game.flipped) Xq.RED else Xq.BLACK
        val bottomColor = -topColor
        PlayerRow(game, topColor)
        Box(
            Modifier
                .padding(vertical = 10.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(Palette.InkLine),
        )
        PlayerRow(game, bottomColor)
        Spacer(Modifier.height(12.dp))
        StatusLine(game)
    }
}

@Composable
private fun PlayerRow(game: XiangqiGame, color: Int) {
    val isAi = game.mode == Mode.AI && color != game.userColor
    val active = !game.finished && game.position.turn == color
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(if (color == Xq.RED) "帅" else "将", red = color == Xq.RED, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    isAi -> "AI · ${levelLabel(game.level)}"
                    game.mode == Mode.AI -> "你"
                    color == Xq.RED -> "红方"
                    else -> "黑方"
                },
                style = Type.CardTitle,
            )
            Text(
                (if (color == Xq.RED) "执红" else "执黑") + if (isAi && game.aiSource.isNotEmpty()) " · ${game.aiSource}" else "",
                style = Type.Small,
            )
        }
        if (active) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (isAi) Palette.Gold else Palette.Jade),
            )
        }
    }
}

@Composable
private fun StatusLine(game: XiangqiGame) {
    val text = when {
        game.finished -> "${game.resultTitle()} · ${game.resultDetail()}"
        game.thinking -> game.thinkingInfo
        game.mode == Mode.HUMAN -> if (game.position.turn == Xq.RED) "轮到红方走" else "轮到黑方走"
        game.isUserTurn -> if (game.selected != null) "选择落点（绿点为可走位置）" else "轮到你走 · 先选子，再选落点"
        else -> "等待 AI"
    }
    val inCheck = !game.finished && Xq.inCheck(game.position.board, game.position.turn)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (inCheck) Palette.Cinnabar.copy(alpha = 0.22f) else Palette.Night.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (inCheck) "将军！$text" else text,
            style = Type.BodyStrong.copy(color = if (inCheck) Palette.Ivory else Palette.GoldSoft),
            maxLines = 1,
        )
    }
}

@Composable
fun MoveListPanel(plies: List<PlyRecord>, modifier: Modifier = Modifier, highlight: Int = plies.size - 1) {
    Panel(modifier.fillMaxWidth(), padding = 14.dp) {
        Row {
            Text("棋谱", style = Type.Label.copy(color = Palette.Gold))
            Spacer(Modifier.weight(1f))
            Text("${plies.size} 手", style = Type.Small)
        }
        Spacer(Modifier.height(8.dp))
        val rounds = (plies.size + 1) / 2
        val state = rememberLazyListState()
        LaunchedEffect(plies.size, highlight) {
            if (rounds > 0) state.animateScrollToItem(((highlight.coerceAtLeast(0)) / 2).coerceAtMost(rounds - 1))
        }
        if (plies.isEmpty()) {
            Text("还没有走子。", style = Type.Small)
        } else {
            LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                itemsIndexed(List(rounds) { it }) { _, round ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${round + 1}.", style = Type.Small.copy(fontSize = 13.sp), modifier = Modifier.width(34.dp))
                        for (side in 0..1) {
                            val index = round * 2 + side
                            val ply = plies.getOrNull(index)
                            val isHi = index == highlight
                            Text(
                                ply?.notation ?: "",
                                style = Type.BodyStrong.copy(
                                    fontSize = 15.sp,
                                    color = if (isHi) Palette.Night else if (ply != null && Xq.colorOf(ply.move.piece) == Xq.RED) Palette.Ivory else Palette.IvoryMuted,
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isHi) Palette.Gold else Color.Transparent)
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionGrid(content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
fun ResultCard(
    title: String,
    detail: String,
    win: Boolean,
    onAgain: () -> Unit,
    onReview: (() -> Unit)?,
    onExit: () -> Unit,
    againText: String = "再来一局",
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AnimatedVisibility(true, enter = fadeIn() + scaleIn(initialScale = 0.9f)) {
        Column(
            Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Palette.InkCard.copy(alpha = 0.97f), Palette.Night.copy(alpha = 0.97f))))
                .border(1.5.dp, Palette.Gold.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Seal(if (win) "胜" else if (title.contains("和")) "和" else "负", size = 64.dp, background = if (win) Palette.Cinnabar else Palette.InkRaised)
            Spacer(Modifier.height(14.dp))
            Text(title, style = Type.Headline.copy(fontSize = 32.sp))
            Spacer(Modifier.height(6.dp))
            Text(detail, style = Type.Body)
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(againText, onAgain, Modifier.focusRequester(focus), primary = true, compact = true)
                if (onReview != null) TvButton("复盘", onReview, compact = true)
                TvButton("退出", onExit, compact = true)
            }
        }
    }
}
