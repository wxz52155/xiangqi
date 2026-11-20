package com.qilian.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import com.qilian.tv.data.Content
import com.qilian.tv.data.Lesson
import com.qilian.tv.data.Tactic
import com.qilian.tv.data.TrainingItem
import com.qilian.tv.data.TrainingKind
import com.qilian.tv.data.levelLabel
import com.qilian.tv.game.TrainingSession
import com.qilian.tv.ui.FocusCard
import com.qilian.tv.ui.LocalDeps
import com.qilian.tv.ui.Palette
import com.qilian.tv.ui.Panel
import com.qilian.tv.ui.Pill
import com.qilian.tv.ui.ProgressBar
import com.qilian.tv.ui.Routes
import com.qilian.tv.ui.ScreenTitle
import com.qilian.tv.ui.Seal
import com.qilian.tv.ui.Segmented
import com.qilian.tv.ui.Toast
import com.qilian.tv.ui.TvButton
import com.qilian.tv.ui.Type
import com.qilian.tv.ui.board.MiniBoard
import com.qilian.tv.ui.board.XiangqiBoard
import com.qilian.tv.xiangqi.Xq

// ====================================================================== 训练营首页

@Composable
fun TrainingHubScreen(nav: NavHostController) {
    val deps = LocalDeps.current
    val store = deps.store
    @Suppress("UNUSED_VARIABLE") val rev = store.revision
    val counts by produceState(initialValue = emptyMap<TrainingKind, Int>()) {
        value = TrainingKind.entries.associateWith { Content.items(deps.context, it).size }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 34.dp)) {
        ScreenTitle(
            "训练营",
            "三类基本功，反复练",
            subtitle = "所有条目都标了入门 / 进阶 / 挑战。做过的题、走过的课都记在本机，可以随时继续。",
        )
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            TrainingKind.entries.forEachIndexed { index, kind ->
                Box(if (index == 0) Modifier.focusRequester(first) else Modifier) {
                    TrainingKindCard(kind, counts[kind] ?: 0, store.completed(kind).size, wide = true) {
                        nav.navigate(Routes.trainList(kind))
                    }
                }
            }
        }
        Spacer(Modifier.height(26.dp))
        Text("继续上次", style = Type.Label)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TrainingKind.entries.forEach { kind ->
                val last = store.lastTraining(kind)
                if (last != null) {
                    TvButton("${kind.label} · 接着练", { nav.navigate(Routes.trainPlay(kind, last)) }, seal = kind.label.take(1), compact = true)
                }
            }
            if (TrainingKind.entries.all { store.lastTraining(it) == null }) {
                Text("还没有练习记录，从上面选一类开始吧。", style = Type.Small)
            }
        }
    }
}

// ====================================================================== 列表

private enum class Filter(val label: String) { ALL("全部"), L1("入门"), L2("进阶"), L3("挑战"), TODO("未完成"), FAV("收藏") }

@Composable
fun TrainingListScreen(nav: NavHostController, kind: TrainingKind) {
    val deps = LocalDeps.current
    val store = deps.store
    val items by produceState<List<TrainingItem>?>(initialValue = Content.cached(kind)) {
        value = Content.items(deps.context, kind)
    }
    var filter by remember { mutableStateOf(Filter.ALL) }
    val rev = store.revision
    val done = remember(rev) { store.completed(kind) }
    val favs = remember(rev) { store.favorites(kind) }
    val shown = remember(items, filter, done, favs) {
        items.orEmpty().filter {
            when (filter) {
                Filter.ALL -> true
                Filter.L1 -> it.level == 1
                Filter.L2 -> it.level == 2
                Filter.L3 -> it.level == 3
                Filter.TODO -> it.id !in done
                Filter.FAV -> it.id in favs
            }
        }
    }
    val total = items?.size ?: 0

    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 28.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            ScreenTitle("训练营", kind.label)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${done.size}", style = Type.Headline.copy(fontSize = 34.sp, color = Palette.Gold))
                    Text(" / $total 完成", style = Type.Small)
                }
                ProgressBar(if (total > 0) done.size.toFloat() / total else 0f, Modifier.width(220.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Segmented(Filter.entries.map { it to it.label }, filter, { filter = it })
            Spacer(Modifier.width(16.dp))
            val firstTodo = shown.firstOrNull { it.id !in done } ?: shown.firstOrNull()
            if (firstTodo != null) {
                TvButton("从第一道未完成开始", { nav.navigate(Routes.trainPlay(kind, firstTodo.id)) }, seal = "始", primary = true, compact = true)
            }
            Spacer(Modifier.width(12.dp))
            if (shown.isNotEmpty()) {
                TvButton("随机一道", { nav.navigate(Routes.trainPlay(kind, shown.random().id)) }, seal = "随", compact = true)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (items == null) {
            Text("正在载入题库…", style = Type.Body)
        } else if (shown.isEmpty()) {
            Text(if (filter == Filter.FAV) "还没有收藏。在做题页面按“收藏”即可加入这里。" else "这里没有条目。", style = Type.Body)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(top = 6.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(shown, key = { it.id }) { item ->
                    TrainingItemCard(item, item.id in done, item.id in favs, total.toString().length) {
                        nav.navigate(Routes.trainPlay(kind, item.id))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrainingItemCard(item: TrainingItem, done: Boolean, fav: Boolean, @Suppress("UNUSED_PARAMETER") digits: Int, onClick: () -> Unit) {
    val board = remember(item.id) { runCatching { Xq.parseFen(item.fen).board }.getOrDefault(Xq.initial().board) }
    val flipped = (item as? Lesson)?.userColor == Xq.BLACK
    FocusCard(onClick = onClick, contentPadding = PaddingValues(10.dp), focusedScale = 1.08f) {
        Box {
            MiniBoard(board, Modifier.fillMaxWidth(), flipped = flipped)
            Row(Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                if (fav) Pill("藏", color = Palette.Gold)
                if (done) {
                    Spacer(Modifier.width(4.dp))
                    Pill("✓", color = Palette.Jade)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, style = Type.CardTitle.copy(fontSize = 14.sp), maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill(levelLabel(item.level), color = when (item.level) { 1 -> Palette.Jade; 2 -> Palette.Gold; else -> Palette.Cinnabar })
            Spacer(Modifier.width(6.dp))
            Text(item.subtitle, style = Type.Small.copy(fontSize = 11.sp), maxLines = 1)
        }
    }
}

// ====================================================================== 做题 / 跟练

@Composable
fun TrainingPlayScreen(nav: NavHostController, kind: TrainingKind, id: String) {
    val deps = LocalDeps.current
    val item by produceState<TrainingItem?>(initialValue = null, id) {
        value = Content.find(deps.context, kind, id)
    }
    val current = item
    if (current == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("正在载入…", style = Type.Body) }
        return
    }
    LaunchedEffect(id) { deps.store.setLastTraining(kind, id) }
    val scope = rememberCoroutineScope()
    val session = remember(id) { TrainingSession(current, scope, deps.store, deps.sounds) }
    DisposableEffect(id) { onDispose { session.dispose() } }
    TrainingPlayContent(nav, kind, current, session)
}

@Composable
fun TrainingPlayContent(nav: NavHostController, kind: TrainingKind, current: TrainingItem, session: TrainingSession) {
    val deps = LocalDeps.current
    val id = current.id
    val scope = rememberCoroutineScope()
    val boardFocus = remember { FocusRequester() }
    LaunchedEffect(id) { runCatching { boardFocus.requestFocus() } }
    BackHandler(enabled = session.selected != null) { session.clear() }
    @Suppress("UNUSED_VARIABLE") val rev = deps.store.revision
    val fav = deps.store.favorites(kind).contains(id)

    fun goNext() {
        scope.launchNext(deps, kind, id) { next ->
            if (next != null) {
                nav.navigate(Routes.trainPlay(kind, next.id)) { popUpTo(Routes.trainList(kind)) }
            } else nav.popBackStack()
        }
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 22.dp)) {
        Box(Modifier.fillMaxHeight().weight(1f), contentAlignment = Alignment.Center) {
            XiangqiBoard(
                board = session.position.board,
                flipped = session.flipped,
                marks = session.marks(),
                focusRequester = boardFocus,
                modifier = Modifier.fillMaxHeight(),
                onPoint = { session.onPoint(it) },
            )
            Column(Modifier.align(Alignment.TopCenter).padding(top = 14.dp)) {
                AnimatedVisibility(session.message != null, enter = fadeIn(), exit = fadeOut()) {
                    Toast(session.message ?: "")
                }
            }
            if (session.solved) {
                ResultCard(
                    title = if (session.isTactic) "绝杀！" else "完成",
                    detail = if (session.isTactic) "错试 ${session.errors} 次 · 提示 ${session.hints} 次" else "这一节已完成",
                    win = true,
                    onAgain = { goNext() },
                    onReview = null,
                    onExit = { nav.popBackStack() },
                    againText = if (session.isTactic) "下一题" else "下一节",
                )
            }
        }
        Spacer(Modifier.width(28.dp))
        Column(Modifier.width(380.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Seal(kind.label.take(1), size = 30.dp, background = when (kind) {
                    TrainingKind.TACTICS -> Palette.Cinnabar
                    TrainingKind.ENDGAME -> Palette.Jade
                    TrainingKind.OPENING -> Palette.GoldDeep
                })
                Spacer(Modifier.width(10.dp))
                Text(kind.label, style = Type.Label.copy(color = Palette.Gold))
                Spacer(Modifier.width(10.dp))
                Pill(levelLabel(current.level), color = when (current.level) { 1 -> Palette.Jade; 2 -> Palette.Gold; else -> Palette.Cinnabar })
                Spacer(Modifier.weight(1f))
                Text(if (session.flipped) "你执黑" else "你执红", style = Type.Small)
            }
            Panel(Modifier.weight(1f).fillMaxWidth(), padding = 16.dp) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(current.title, style = Type.Title.copy(fontSize = 21.sp))
                    Spacer(Modifier.height(4.dp))
                    Text(current.subtitle, style = Type.Small.copy(fontSize = 13.sp))
                    Spacer(Modifier.height(12.dp))
                    StatusBox(session, current)
                    Spacer(Modifier.height(12.dp))
                    when (current) {
                        is Tactic -> {
                            InfoBlock("目标", "${if (Xq.parseFen(current.fen).turn == Xq.RED) "红方" else "黑方"}先行，一步将死或困毙对方。")
                            session.hintText?.let { InfoBlock("提示", it, Palette.Gold) }
                        }
                        is Lesson -> {
                            if (session.lineIndex == 0 && session.comment == null) InfoBlock("讲解", current.intro)
                            if (current.keyPoints.isNotEmpty()) {
                                InfoBlock("要点", current.keyPoints.joinToString("\n") { "· $it" })
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("来源：${current.source.name}", style = Type.Small.copy(fontSize = 11.sp))
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TvButton("提示", { session.hint() }, Modifier.weight(1f), seal = "示", enabled = !session.solved, compact = true)
                    if (session.isTactic) {
                        TvButton("重做", { session.restart() }, Modifier.weight(1f), seal = "重", compact = true)
                    } else {
                        TvButton("悔棋", { session.undo() }, Modifier.weight(1f), seal = "悔", enabled = session.plies.isNotEmpty() && !session.autoPlaying, compact = true)
                    }
                    TvButton(if (fav) "已收藏" else "收藏", { deps.store.toggleFavorite(kind, id) }, Modifier.weight(1f), seal = "藏", compact = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!session.isTactic) {
                        TvButton("演示", { session.autoplay() }, Modifier.weight(1f), seal = "演", enabled = !session.solved && !session.autoPlaying, compact = true)
                        TvButton("重来", { session.restart() }, Modifier.weight(1f), seal = "重", compact = true)
                    }
                    TvButton(if (session.isTactic) "下一题" else "下一节", { goNext() }, Modifier.weight(1f), seal = "下", compact = true)
                }
            }
        }
    }
}

private fun kotlinx.coroutines.CoroutineScope.launchNext(
    deps: com.qilian.tv.ui.AppDeps,
    kind: TrainingKind,
    id: String,
    then: (TrainingItem?) -> Unit,
) {
    launch { then(Content.next(deps.context, kind, id)) }
}

@Composable
private fun StatusBox(session: TrainingSession, item: TrainingItem) {
    val text = when {
        session.solved -> session.comment ?: "完成"
        session.autoPlaying -> "演示中…"
        session.busy -> "对方应手中…"
        session.comment != null -> session.comment!!
        item is Tactic -> "找出一步绝杀：方向键选子，OK 确认落点。"
        else -> "按主线走子：轮到你时选子落子，对方会自动应手。"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Palette.Night.copy(alpha = 0.6f), Palette.InkCard.copy(alpha = 0.6f))))
            .padding(14.dp),
    ) {
        if (!session.isTactic) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("进度", style = Type.Label)
                Spacer(Modifier.width(10.dp))
                ProgressBar(if (session.totalSteps > 0) session.lineIndex.toFloat() / session.totalSteps else 0f, Modifier.weight(1f), Palette.Jade)
                Spacer(Modifier.width(10.dp))
                Text("${session.lineIndex}/${session.totalSteps}", style = Type.Small)
            }
            Spacer(Modifier.height(8.dp))
        }
        Text(text, style = Type.BodyStrong.copy(color = if (session.solved) Palette.GoldSoft else Palette.Ivory, lineHeight = 22.sp))
    }
}

@Composable
private fun InfoBlock(label: String, text: String, color: androidx.compose.ui.graphics.Color = Palette.Gold) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(label, style = Type.Label.copy(color = color))
        Spacer(Modifier.height(4.dp))
        Text(text, style = Type.Body)
    }
}
