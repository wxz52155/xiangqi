package com.qilian.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import com.qilian.tv.data.GameRecord
import com.qilian.tv.data.levelLabel
import com.qilian.tv.game.PlyRecord
import com.qilian.tv.ui.ConfirmDialog
import com.qilian.tv.ui.LocalDeps
import com.qilian.tv.ui.Palette
import com.qilian.tv.ui.Panel
import com.qilian.tv.ui.ProgressBar
import com.qilian.tv.ui.Routes
import com.qilian.tv.ui.ScreenTitle
import com.qilian.tv.ui.Seal
import com.qilian.tv.ui.TvButton
import com.qilian.tv.ui.Type
import com.qilian.tv.ui.board.BoardMarks
import com.qilian.tv.ui.board.XiangqiBoard
import com.qilian.tv.xiangqi.Position
import com.qilian.tv.xiangqi.Xq
import kotlinx.coroutines.delay

@Composable
fun HistoryScreen(nav: NavHostController) {
    val store = LocalDeps.current.store
    val records = remember(store.revision) { store.history() }
    val wins = records.count { it.result == "win" }
    val losses = records.count { it.result == "loss" }
    val draws = records.count { it.result == "draw" }

    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 34.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            ScreenTitle("我的棋谱", "下过的每一盘", subtitle = "棋谱只保存在这台电视里。选中一局按 OK 逐步回放，也可以从任意一步接着下。")
            Spacer(Modifier.weight(1f))
            Stat("对局", records.size.toString(), Palette.Ivory)
            Spacer(Modifier.width(26.dp))
            Stat("人机胜", wins.toString(), Palette.Cinnabar)
            Spacer(Modifier.width(26.dp))
            Stat("负", losses.toString(), Palette.Muted)
            Spacer(Modifier.width(26.dp))
            Stat("和", draws.toString(), Palette.Gold)
        }
        Spacer(Modifier.height(24.dp))
        if (records.isEmpty()) {
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Seal("谱", size = 48.dp)
                    Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text("还没有完成的棋局", style = Type.Title)
                        Text("下完一盘，它会自动出现在这里。", style = Type.Body)
                    }
                    val focus = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                    TvButton("去下一盘", { nav.navigate(Routes.SETUP) }, Modifier.focusRequester(focus), seal = "弈", primary = true)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(300.dp),
                contentPadding = PaddingValues(bottom = 40.dp, top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(records, key = { it.id }) { record ->
                    RecordCard(record) { nav.navigate(Routes.review(record.id)) }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = Type.Headline.copy(fontSize = 34.sp, color = color))
        Text(label, style = Type.Small)
    }
}

/** 回放一局棋：左右键逐步前进 / 后退。 */
@Composable
fun ReviewScreen(nav: NavHostController, id: String) {
    val store = LocalDeps.current.store
    val record = remember(id) { store.history().firstOrNull { it.id == id } }
    if (record == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("没有找到这局棋谱", style = Type.Body) }
        return
    }
    val plies = remember(id) { replay(record) }
    var index by remember { mutableIntStateOf(plies.size) }
    var playing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scrubFocus = remember { FocusRequester() }
    var scrubFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { scrubFocus.requestFocus() } }
    LaunchedEffect(playing) {
        if (playing && index >= plies.size) index = 0
        while (playing && index < plies.size) {
            delay(1100)
            index++
        }
        playing = false
    }
    DisposableEffect(Unit) { onDispose { playing = false } }

    val position: Position = if (index == 0) Xq.parseFen(record.startFen) else Xq.play(plies[index - 1].position, plies[index - 1].move)
    val last = plies.getOrNull(index - 1)?.move

    Row(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 22.dp)) {
        Box(Modifier.fillMaxHeight().weight(1f), contentAlignment = Alignment.Center) {
            XiangqiBoard(
                board = position.board,
                flipped = record.mode == "ai" && record.userColor == Xq.BLACK,
                marks = BoardMarks(lastFrom = last?.from, lastTo = last?.to),
                interactive = false,
                modifier = Modifier.fillMaxHeight(),
            )
        }
        Spacer(Modifier.width(28.dp))
        Column(Modifier.width(360.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Seal("谱", size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(if (record.mode == "human") "双人同屏" else "人机 · ${levelLabel(record.level)}", style = Type.Title)
                    Text("${formatDate(record.endedAt)} · ${record.resultText.ifBlank { "未完成" }}", style = Type.Small)
                }
            }
            // 回放控制条：聚焦后左右键逐步
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (scrubFocused) Palette.InkCard else Palette.InkRaised)
                    .border(if (scrubFocused) 2.5.dp else 1.dp, if (scrubFocused) Palette.Gold else Palette.InkLine, RoundedCornerShape(16.dp))
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionLeft -> { playing = false; if (index > 0) index--; true }
                            Key.DirectionRight -> { playing = false; if (index < plies.size) index++; true }
                            Key.DirectionCenter, Key.Enter -> { playing = !playing; true }
                            else -> false
                        }
                    }
                    .onFocusChanged { scrubFocused = it.isFocused }
                    .focusRequester(scrubFocus)
                    .focusable()
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("◀  第 $index / ${plies.size} 手  ▶", style = Type.Title.copy(fontSize = 18.sp))
                    Spacer(Modifier.weight(1f))
                    Text(if (playing) "播放中 · OK 暂停" else "OK 自动播放", style = Type.Small.copy(color = Palette.GoldSoft))
                }
                Spacer(Modifier.height(10.dp))
                ProgressBar(if (plies.isEmpty()) 0f else index.toFloat() / plies.size, Modifier.fillMaxWidth())
            }
            MoveListPanel(plies, Modifier.weight(1f), highlight = index - 1)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TvButton("开局", { playing = false; index = 0 }, Modifier.weight(1f), seal = "始", compact = true)
                TvButton("终局", { playing = false; index = plies.size }, Modifier.weight(1f), seal = "终", compact = true)
                TvButton("删除", { confirmDelete = true }, Modifier.weight(1f), seal = "删", danger = true, compact = true)
            }
            TvButton(
                "从这一步接着下（${if (record.mode == "human") "双人" else "人机"}）",
                {
                    val fen = Xq.toFen(position)
                    nav.navigate(Routes.gameFromFen(record.mode, if (record.mode == "ai") record.userColor else 1, record.level, fen))
                },
                Modifier.fillMaxWidth(),
                seal = "续",
                primary = true,
                compact = true,
            )
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "删除这局棋谱？",
            message = "删除后无法恢复。",
            confirmText = "删除",
            onConfirm = {
                confirmDelete = false
                store.deleteHistory(id)
                nav.popBackStack()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

private fun replay(record: GameRecord): List<PlyRecord> {
    val out = ArrayList<PlyRecord>()
    var pos = runCatching { Xq.parseFen(record.startFen) }.getOrElse { return out }
    for (ucci in record.moves) {
        val (f, t) = Xq.parseUcci(ucci) ?: break
        val move = Xq.findLegal(pos, f, t) ?: break
        out.add(PlyRecord(pos, move, Xq.notation(pos, move)))
        pos = Xq.play(pos, move)
    }
    return out
}
