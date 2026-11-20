package com.qilian.tv.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import com.qilian.tv.data.Content
import com.qilian.tv.data.GameRecord
import com.qilian.tv.data.TrainingKind
import com.qilian.tv.ui.FocusCard
import com.qilian.tv.ui.LocalDeps
import com.qilian.tv.ui.Palette
import com.qilian.tv.ui.Pill
import com.qilian.tv.ui.ProgressBar
import com.qilian.tv.ui.Routes
import com.qilian.tv.ui.Seal
import com.qilian.tv.ui.SectionHeader
import com.qilian.tv.ui.TvButton
import com.qilian.tv.ui.Type
import com.qilian.tv.ui.board.MiniBoard
import com.qilian.tv.xiangqi.Xq

@Composable
fun HomeScreen(nav: NavHostController) {
    val deps = LocalDeps.current
    val store = deps.store
    @Suppress("UNUSED_VARIABLE") val revision = store.revision
    val current = remember(store.revision) { store.currentGame() }
    val history = remember(store.revision) { store.history().take(8) }
    val counts = remember { mutableStateMapOf<TrainingKind, Int>() }
    LaunchedEffect(Unit) {
        for (kind in TrainingKind.entries) counts[kind] = Content.items(deps.context, kind).size
    }
    val primaryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { primaryFocus.requestFocus() } }
    val settings = store.settings

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        item {
            Hero(
                current = current,
                onContinue = { nav.navigate(Routes.game("ai", 1, 2, resume = true)) },
                onPlayAi = { nav.navigate(Routes.game("ai", settings.userColor, settings.level)) },
                onPlayHuman = { nav.navigate(Routes.game("human", 1, settings.level)) },
                onSetup = { nav.navigate(Routes.SETUP) },
                primaryFocus = primaryFocus,
            )
        }

        item {
            Row(Modifier.padding(horizontal = 48.dp)) { SectionHeader("快速开局", trailing = "按 OK 立即开始") }
            Spacer(Modifier.height(14.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                val quick = listOf(
                    Triple("入门", "初", "更短思考，适合熟悉规则") to 1,
                    Triple("进阶", "中", "默认强度，兼顾棋力与速度") to 2,
                    Triple("挑战", "高", "完整强度，认真对弈") to 3,
                )
                items(quick) { (info, level) ->
                    QuickCard(
                        title = "人机 · ${info.first}",
                        seal = info.second,
                        body = info.third,
                        accent = when (level) { 1 -> Palette.Jade; 2 -> Palette.Gold; else -> Palette.Cinnabar },
                    ) { nav.navigate(Routes.game("ai", settings.userColor, level)) }
                }
                item {
                    QuickCard("双人同屏", "双", "两个人轮流用遥控器走子", Palette.GoldSoft) {
                        nav.navigate(Routes.game("human", 1, settings.level))
                    }
                }
                item {
                    QuickCard("自选执子", "选", "执红或执黑、选择难度", Palette.IvoryMuted) { nav.navigate(Routes.SETUP) }
                }
            }
        }

        item {
            Row(Modifier.padding(horizontal = 48.dp)) { SectionHeader("训练营", trailing = "杀法 · 残局 · 开局") }
            Spacer(Modifier.height(14.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(TrainingKind.entries.toList()) { kind ->
                    val total = counts[kind] ?: 0
                    val done = store.completed(kind).size
                    TrainingKindCard(kind, total, done) { nav.navigate(Routes.trainList(kind)) }
                }
            }
        }

        if (history.isNotEmpty()) {
            item {
                Row(Modifier.padding(horizontal = 48.dp)) { SectionHeader("最近棋谱", trailing = "共 ${store.history().size} 局") }
                Spacer(Modifier.height(14.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(history, key = { it.id }) { record ->
                        RecordCard(record) { nav.navigate(Routes.review(record.id)) }
                    }
                }
            }
        }
    }
}

internal fun kindSeal(kind: TrainingKind) = when (kind) {
    TrainingKind.TACTICS -> "杀"
    TrainingKind.ENDGAME -> "残"
    TrainingKind.OPENING -> "开"
}

@Composable
private fun Hero(
    current: GameRecord?,
    onContinue: () -> Unit,
    onPlayAi: () -> Unit,
    onPlayHuman: () -> Unit,
    onSetup: () -> Unit,
    primaryFocus: FocusRequester,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(372.dp),
    ) {
        // 右侧装饰棋盘
        val board = remember(current) {
            runCatching {
                if (current != null) {
                    var pos = Xq.parseFen(current.startFen)
                    for (m in current.moves) {
                        val (f, t) = Xq.parseUcci(m) ?: break
                        pos = Xq.play(pos, Xq.findLegal(pos, f, t) ?: break)
                    }
                    pos.board
                } else Xq.parseFen("2bakab2/9/1cn3nc1/p1p1p1p1p/9/2P6/P3P1P1P/1C2B2C1/9/RN1AKABNR b - - 0 3").board
            }.getOrDefault(Xq.initial().board)
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-20).dp, y = 6.dp)
                .size(340.dp)
                .rotate(-6f)
                .alpha(0.95f),
        ) {
            MiniBoard(board, Modifier.fillMaxSize())
        }
        // 左侧渐隐，让文字更清晰
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.horizontalGradient(
                    0.0f to Palette.Night.copy(alpha = 0.9f),
                    0.5f to Palette.Night.copy(alpha = 0.55f),
                    0.66f to Color.Transparent,
                    1.0f to Color.Transparent,
                ),
            )
            drawRect(Brush.verticalGradient(0.75f to Color.Transparent, 1f to Palette.Ink.copy(alpha = 0.9f)))
        }
        Column(
            Modifier
                .fillMaxHeight()
                .padding(start = 48.dp, top = 30.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Seal("南", size = 30.dp)
                Spacer(Modifier.width(10.dp))
                Text("南坡象棋 · 中国象棋", style = Type.Label.copy(color = Palette.GoldSoft, fontSize = 13.sp))
            }
            Spacer(Modifier.height(18.dp))
            Text("静下来，", style = Type.Display)
            Row {
                Text("下一盘", style = Type.Display)
                Text("好棋", style = Type.Display.copy(color = Palette.Cinnabar))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "和 AI 对弈，也能双人同屏；练杀法、拆残局、走开局。棋谱和进度只留在这台设备里。",
                style = Type.Body.copy(fontSize = 15.sp),
                modifier = Modifier.width(470.dp),
            )
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (current != null) {
                    TvButton(
                        "继续对局 · 第 ${(current.moves.size + 1) / 2} 回合",
                        onContinue,
                        seal = "续",
                        primary = true,
                        modifier = Modifier.focusRequester(primaryFocus),
                    )
                    TvButton("新开一局", onPlayAi, seal = "弈")
                } else {
                    TvButton("和 AI 下一盘", onPlayAi, seal = "弈", primary = true, modifier = Modifier.focusRequester(primaryFocus))
                    TvButton("双人同屏", onPlayHuman, seal = "双")
                }
                TvButton("对局设置", onSetup, seal = "设")
            }
        }
    }
}

@Composable
private fun QuickCard(
    title: String,
    seal: String,
    body: String,
    accent: Color,
    width: androidx.compose.ui.unit.Dp = 196.dp,
    onClick: () -> Unit,
) {
    FocusCard(
        onClick = onClick,
        modifier = Modifier.width(width).height(128.dp),
        container = Brush.linearGradient(listOf(Palette.InkCard, accent.copy(alpha = 0.16f))),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Seal(seal, size = 34.dp, background = accent.copy(alpha = 0.9f), foreground = Palette.Night)
            Spacer(Modifier.width(12.dp))
            Text(title, style = Type.CardTitle.copy(fontSize = 18.sp))
        }
        Spacer(Modifier.weight(1f))
        Text(body, style = Type.Small.copy(color = Palette.IvoryMuted), maxLines = 2)
    }
}

@Composable
internal fun TrainingKindCard(kind: TrainingKind, total: Int, done: Int, wide: Boolean = false, onClick: () -> Unit) {
    val (seal, accent, blurb) = when (kind) {
        TrainingKind.TACTICS -> Triple("杀", Palette.Cinnabar, "红先一步将死或困毙，练的是看见杀形的眼睛。")
        TrainingKind.ENDGAME -> Triple("残", Palette.Jade, "古谱与原创多步杀，按难度把残棋走成胜势。")
        TrainingKind.OPENING -> Triple("开", Palette.Gold, "从经典主线建立出子顺序，黑方按谱自动应手。")
    }
    FocusCard(
        onClick = onClick,
        modifier = Modifier.width(if (wide) 268.dp else 290.dp).height(if (wide) 214.dp else 168.dp),
        container = Brush.linearGradient(listOf(Palette.InkCard, accent.copy(alpha = 0.22f))),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Seal(seal, size = 42.dp, background = accent, foreground = if (accent == Palette.Gold) Palette.Night else Palette.Ivory)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(kind.label, style = Type.Title)
                Text(if (total > 0) "$total ${if (kind == TrainingKind.TACTICS) "道题" else if (kind == TrainingKind.ENDGAME) "节残局" else "套开局"}" else "载入中…", style = Type.Small)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(blurb, style = Type.Small.copy(color = Palette.IvoryMuted), maxLines = if (wide) 4 else 2)
        Spacer(Modifier.weight(1f))
        if (wide) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(if (total > 0) "${done * 100 / total}" else "0", style = Type.Headline.copy(fontSize = 34.sp, color = accent))
                Text(" %", style = Type.Small)
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressBar(if (total > 0) done.toFloat() / total else 0f, Modifier.weight(1f), accent)
            Spacer(Modifier.width(10.dp))
            Text("已完成 $done", style = Type.Small)
        }
    }
}

@Composable
internal fun RecordCard(record: GameRecord, onClick: () -> Unit) {
    val board = remember(record.id) {
        runCatching {
            var pos = Xq.parseFen(record.startFen)
            for (m in record.moves) {
                val (f, t) = Xq.parseUcci(m) ?: break
                pos = Xq.play(pos, Xq.findLegal(pos, f, t) ?: break)
            }
            pos.board
        }.getOrDefault(Xq.initial().board)
    }
    FocusCard(onClick = onClick, modifier = Modifier.width(300.dp).height(150.dp), contentPadding = PaddingValues(14.dp)) {
        Row {
            MiniBoard(board, Modifier.height(122.dp), flipped = record.mode == "ai" && record.userColor == -1)
            Spacer(Modifier.width(14.dp))
            Column {
                Pill(
                    when (record.result) {
                        "win" -> "胜"
                        "loss" -> "负"
                        "draw" -> "和"
                        "red" -> "红胜"
                        "black" -> "黑胜"
                        else -> "未完"
                    },
                    color = when (record.result) {
                        "win", "red" -> Palette.Cinnabar
                        "loss" -> Palette.Muted
                        else -> Palette.Gold
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(if (record.mode == "human") "双人同屏" else "人机 · ${com.qilian.tv.data.levelLabel(record.level)}", style = Type.CardTitle)
                Spacer(Modifier.height(4.dp))
                Text("${record.moves.size} 手 · ${if (record.userColor == 1) "执红" else "执黑"}", style = Type.Small)
                Spacer(Modifier.weight(1f))
                Text(formatDate(record.endedAt), style = Type.Small)
            }
        }
    }
}

internal fun formatDate(time: Long): String =
    java.text.SimpleDateFormat("M月d日 HH:mm", java.util.Locale.CHINA).format(java.util.Date(time))
