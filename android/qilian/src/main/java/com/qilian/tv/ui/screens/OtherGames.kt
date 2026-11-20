package com.qilian.tv.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.tv.material3.Text
import com.qilian.tv.data.OnlineAi
import com.qilian.tv.data.levelLabel
import com.qilian.tv.games.Connect4
import com.qilian.tv.games.Gomoku
import com.qilian.tv.games.Jungle
import com.qilian.tv.ui.FocusCard
import com.qilian.tv.ui.LocalDeps
import com.qilian.tv.ui.Palette
import com.qilian.tv.ui.Panel
import com.qilian.tv.ui.ScreenTitle
import com.qilian.tv.ui.Seal
import com.qilian.tv.ui.Segmented
import com.qilian.tv.ui.SerifFamily
import com.qilian.tv.ui.Toast
import com.qilian.tv.ui.TvButton
import com.qilian.tv.ui.Type
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.math.min

// ====================================================================== 其它棋类入口

@Composable
fun GamesHubScreen(nav: NavHostController) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 34.dp)) {
        ScreenTitle("其它棋类", "换个玩法，轻松一盘", subtitle = "每一种都可以选人机或双人同屏。人机优先用在线 AI，断网时自动换成本地 AI。")
        Spacer(Modifier.height(30.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            OTHER_GAMES.forEachIndexed { index, game ->
                FocusCard(
                    onClick = { nav.navigate(game.route) },
                    modifier = (if (index == 0) Modifier.focusRequester(first) else Modifier).width(258.dp).height(300.dp),
                    container = Brush.verticalGradient(listOf(Palette.InkCard, game.accent.copy(alpha = 0.18f))),
                ) {
                    Seal(game.seal, size = 56.dp, background = game.accent, foreground = Palette.Night)
                    Spacer(Modifier.height(18.dp))
                    Text(game.title, style = Type.Headline)
                    Spacer(Modifier.height(8.dp))
                    Text(game.body, style = Type.Body)
                    Spacer(Modifier.weight(1f))
                    Text(
                        when (game.route) {
                            "gomoku" -> "方向键移到交叉点，OK 落子"
                            "jungle" -> "OK 选中兽棋，再选目标格"
                            else -> "左右键选列，OK 落子"
                        },
                        style = Type.Small.copy(color = Palette.GoldSoft),
                    )
                }
            }
        }
    }
}

// ====================================================================== 通用：网格棋盘 + 遥控器光标

@Composable
private fun GridBoard(
    rows: Int,
    cols: Int,
    cursor: Int,
    onCursor: (Int) -> Unit,
    onOk: (Int) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    columnOnly: Boolean = false,
    draw: DrawScope.(cell: Float, origin: Offset, focused: Boolean, measurer: TextMeasurer) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val measurer = rememberTextMeasurer(cacheSize = 32)
    Canvas(
        modifier
            .aspectRatio(cols.toFloat() / rows.toFloat())
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                val r = cursor / cols
                val c = cursor % cols
                when (e.key) {
                    Key.DirectionLeft -> if (c > 0) { onCursor(cursor - 1); true } else false
                    Key.DirectionRight -> if (c < cols - 1) { onCursor(cursor + 1); true } else false
                    Key.DirectionUp -> if (!columnOnly && r > 0) { onCursor(cursor - cols); true } else false
                    Key.DirectionDown -> if (!columnOnly && r < rows - 1) { onCursor(cursor + cols); true } else false
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onOk(cursor); true }
                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        val cell = min(size.width / cols, size.height / rows)
        val origin = Offset((size.width - cell * cols) / 2f, (size.height - cell * rows) / 2f)
        draw(cell, origin, focused, measurer)
    }
}

private fun DrawScope.cursorBrackets(center: Offset, cell: Float, color: Color = Palette.Gold) {
    val half = cell * 0.5f
    val arm = cell * 0.22f
    val w = cell * 0.08f
    drawRoundRect(color.copy(alpha = 0.2f), Offset(center.x - half, center.y - half), Size(cell, cell), CornerRadius(cell * 0.18f))
    for ((sx, sy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
        val corner = Offset(center.x + sx * half, center.y + sy * half)
        drawLine(color, corner, Offset(corner.x - sx * arm, corner.y), w, StrokeCap.Round)
        drawLine(color, corner, Offset(corner.x, corner.y - sy * arm), w, StrokeCap.Round)
    }
}

/** 右侧面板：对手、难度、状态与按钮。 */
@Composable
private fun SidePanel(
    title: String,
    seal: String,
    vsAi: Boolean,
    onVsAi: (Boolean) -> Unit,
    level: Int,
    onLevel: (Int) -> Unit,
    status: String,
    statusHot: Boolean,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onNew: () -> Unit,
    onExit: () -> Unit,
    rules: String,
    players: @Composable () -> Unit,
) {
    Column(Modifier.width(360.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Seal(seal, size = 30.dp)
            Spacer(Modifier.width(10.dp))
            Text(title, style = Type.Title)
            Spacer(Modifier.weight(1f))
            Text(if (vsAi) "人机 · ${levelLabel(level)}" else "双人同屏", style = Type.Small)
        }
        Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
            Text("对手", style = Type.Label)
            Spacer(Modifier.height(6.dp))
            Segmented(listOf(true to "人机", false to "双人"), vsAi, onVsAi)
            if (vsAi) {
                Spacer(Modifier.height(10.dp))
                Text("难度", style = Type.Label)
                Spacer(Modifier.height(6.dp))
                Segmented(listOf(1 to "入门", 2 to "进阶", 3 to "挑战"), level, onLevel)
            }
        }
        Panel(Modifier.fillMaxWidth().weight(1f), padding = 16.dp) {
            players()
            Spacer(Modifier.height(12.dp))
            Text(status, style = Type.BodyStrong.copy(color = if (statusHot) Palette.Gold else Palette.GoldSoft))
            Spacer(Modifier.height(10.dp))
            Text(rules, style = Type.Small)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvButton("悔棋", onUndo, Modifier.weight(1f), seal = "悔", enabled = canUndo, compact = true)
            TvButton("新局", onNew, Modifier.weight(1f), seal = "新", compact = true)
            TvButton("退出", onExit, Modifier.weight(1f), seal = "退", compact = true)
        }
    }
}

@Composable
private fun PlayerLine(label: String, dot: Color, active: Boolean) {
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.width(26.dp).height(26.dp)) {
            drawCircle(Color.Black.copy(alpha = 0.3f), size.minDimension / 2f, center + Offset(0f, 2f))
            drawCircle(dot, size.minDimension / 2f)
            drawCircle(Palette.IvoryMuted.copy(alpha = 0.6f), size.minDimension / 2f, style = Stroke(2f))
            if (active) drawCircle(Palette.Gold, size.minDimension / 2f + 3f, style = Stroke(3f))
        }
        Spacer(Modifier.width(12.dp))
        Text(label, style = Type.BodyStrong.copy(color = if (active) Palette.Ivory else Palette.IvoryMuted))
    }
}

// ====================================================================== 五子棋

@Composable
fun GomokuScreen(nav: NavHostController) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    var board by remember { mutableStateOf(IntArray(Gomoku.CELLS)) }
    var turn by remember { mutableIntStateOf(1) }
    var history by remember { mutableStateOf(listOf<Int>()) }
    var winLine by remember { mutableStateOf<List<Int>?>(null) }
    var vsAi by remember { mutableStateOf(true) }
    var level by remember { mutableIntStateOf(deps.store.settings.level) }
    var cursor by remember { mutableIntStateOf(7 * Gomoku.SIZE + 7) }
    var thinking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var aiJob by remember { mutableStateOf<Job?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val over = winLine != null || board.none { it == 0 }

    fun place(index: Int) {
        if (board[index] != 0 || over) return
        val next = board.copyOf()
        next[index] = turn
        board = next
        history = history + index
        winLine = Gomoku.winLine(next, index)
        if (winLine != null) deps.sounds?.finish() else deps.sounds?.move()
        turn = 3 - turn
    }

    fun aiMove() {
        aiJob?.cancel()
        aiJob = scope.launch {
            thinking = true
            delay(250)
            val snapshot = board
            val me = turn
            val online = if (deps.store.settings.onlineAi) runCatching {
                withTimeout(9000) {
                    OnlineAi.move("gomoku", level, board = snapshot.map { if (it == 1) "b" else if (it == 2) "w" else "" }, turn = if (me == 1) "b" else "w", timeLimitMs = 5000)
                }
            }.getOrNull() else null
            val index = (online as? OnlineAi.AiMove.Place)?.index?.takeIf { it in 0 until Gomoku.CELLS && snapshot[it] == 0 }
                ?: withContext(Dispatchers.Default) { Gomoku.bestMove(snapshot, me, level) }
            thinking = false
            if (snapshot === board) place(index)
        }
    }

    fun onOk(index: Int) {
        if (over) return
        if (thinking) { message = "AI 正在思考"; return }
        if (board[index] != 0) { deps.sounds?.error(); return }
        place(index)
        if (vsAi && !over && winLine == null) aiMove()
    }

    fun newGame() {
        aiJob?.cancel()
        board = IntArray(Gomoku.CELLS); turn = 1; history = emptyList(); winLine = null; thinking = false
    }

    LaunchedEffect(message) { if (message != null) { delay(1800); message = null } }

    Row(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 22.dp)) {
        Box(Modifier.fillMaxHeight().weight(1f), contentAlignment = Alignment.Center) {
            GridBoard(Gomoku.SIZE, Gomoku.SIZE, cursor, { cursor = it }, { onOk(it) }, focus, Modifier.fillMaxHeight()) { cell, origin, focused, _ ->
                val n = Gomoku.SIZE
                drawRoundRect(
                    Brush.linearGradient(listOf(Palette.BoardLight, Palette.BoardDark), origin, origin + Offset(cell * n, cell * n)),
                    origin, Size(cell * n, cell * n), CornerRadius(cell * 0.4f),
                )
                for (i in 0 until n) {
                    val p = cell * (i + 0.5f)
                    drawLine(Palette.BoardLine, origin + Offset(cell * 0.5f, p), origin + Offset(cell * (n - 0.5f), p), cell * 0.04f)
                    drawLine(Palette.BoardLine, origin + Offset(p, cell * 0.5f), origin + Offset(p, cell * (n - 0.5f)), cell * 0.04f)
                }
                for ((r, c) in listOf(3 to 3, 3 to 11, 7 to 7, 11 to 3, 11 to 11)) {
                    drawCircle(Palette.BoardLine, cell * 0.1f, origin + Offset(cell * (c + 0.5f), cell * (r + 0.5f)))
                }
                for (i in 0 until Gomoku.CELLS) {
                    val v = board[i]
                    if (v == 0) continue
                    val center = origin + Offset(cell * (i % n + 0.5f), cell * (i / n + 0.5f))
                    drawCircle(Color.Black.copy(alpha = 0.35f), cell * 0.44f, center + Offset(0f, cell * 0.06f))
                    drawCircle(
                        if (v == 1) Brush.radialGradient(listOf(Color(0xFF5A5A5A), Color(0xFF111111)), center - Offset(cell * 0.12f, cell * 0.14f), cell * 0.5f)
                        else Brush.radialGradient(listOf(Color.White, Color(0xFFD9D4C8)), center - Offset(cell * 0.12f, cell * 0.14f), cell * 0.55f),
                        cell * 0.44f, center,
                    )
                    if (i == history.lastOrNull()) drawCircle(Palette.Cinnabar, cell * 0.1f, center)
                    if (winLine?.contains(i) == true) drawCircle(Palette.Gold, cell * 0.48f, center, style = Stroke(cell * 0.08f))
                }
                if (focused && !over) cursorBrackets(origin + Offset(cell * (cursor % n + 0.5f), cell * (cursor / n + 0.5f)), cell)
            }
            Column(Modifier.align(Alignment.TopCenter).padding(top = 14.dp)) {
                AnimatedVisibility(message != null, enter = fadeIn(), exit = fadeOut()) { Toast(message ?: "") }
            }
            if (over) {
                val w = winLine?.let { board[it[0]] }
                ResultCard(
                    title = when {
                        w == null -> "和棋"
                        vsAi -> if (w == 1) "你赢了" else "AI 获胜"
                        else -> if (w == 1) "黑方胜" else "白方胜"
                    },
                    detail = if (w == null) "棋盘已下满" else "五子连珠",
                    win = w == 1 || (!vsAi && w != null),
                    onAgain = { newGame(); runCatching { focus.requestFocus() } },
                    onReview = null,
                    onExit = { nav.popBackStack() },
                )
            }
        }
        Spacer(Modifier.width(28.dp))
        SidePanel(
            title = "五子棋", seal = "五", vsAi = vsAi, onVsAi = { vsAi = it; newGame() }, level = level, onLevel = { level = it },
            status = when {
                over -> "对局结束"
                thinking -> "AI 思考中…"
                vsAi -> "轮到你（黑子）落子"
                else -> if (turn == 1) "轮到黑方" else "轮到白方"
            },
            statusHot = thinking,
            canUndo = history.isNotEmpty() && !thinking,
            onUndo = {
                val steps = if (vsAi && history.size >= 2 && turn == 1) 2 else 1
                val next = board.copyOf()
                history.takeLast(steps).forEach { next[it] = 0 }
                board = next
                history = history.dropLast(steps)
                turn = if (history.size % 2 == 0) 1 else 2
                winLine = null
            },
            onNew = { newGame() },
            onExit = { nav.popBackStack() },
            rules = "15 × 15 棋盘，黑先。横、竖、斜任意方向先连成五子获胜。",
        ) {
            PlayerLine(if (vsAi) "你 · 黑子先行" else "黑方", Color(0xFF222222), !over && turn == 1)
            PlayerLine(if (vsAi) "AI · 白子" else "白方", Color(0xFFF2EEE4), !over && turn == 2)
        }
    }
}

// ====================================================================== 四子棋

@Composable
fun Connect4Screen(nav: NavHostController) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    var board by remember { mutableStateOf(IntArray(Connect4.ROWS * Connect4.COLS)) }
    var turn by remember { mutableIntStateOf(1) }
    var history by remember { mutableStateOf(listOf<Int>()) }
    var winLine by remember { mutableStateOf<List<Int>?>(null) }
    var vsAi by remember { mutableStateOf(true) }
    var level by remember { mutableIntStateOf(deps.store.settings.level) }
    var cursor by remember { mutableIntStateOf(3) }
    var thinking by remember { mutableStateOf(false) }
    var aiJob by remember { mutableStateOf<Job?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val over = winLine != null || board.none { it == 0 }

    fun drop(col: Int): Boolean {
        val idx = Connect4.dropRow(board, col)
        if (idx < 0 || over) return false
        val next = board.copyOf()
        next[idx] = turn
        board = next
        history = history + idx
        winLine = Connect4.winLine(next, idx)
        if (winLine != null) deps.sounds?.finish() else deps.sounds?.move()
        turn = 3 - turn
        return true
    }

    fun aiMove() {
        aiJob?.cancel()
        aiJob = scope.launch {
            thinking = true
            delay(300)
            val snapshot = board
            val me = turn
            val online = if (deps.store.settings.onlineAi) runCatching {
                withTimeout(8000) {
                    OnlineAi.move("connect4", level, board = snapshot.map { if (it == 1) "r" else if (it == 2) "y" else "" }, turn = if (me == 1) "r" else "y", timeLimitMs = 4000)
                }
            }.getOrNull() else null
            val col = (online as? OnlineAi.AiMove.Place)?.index?.let { it % Connect4.COLS }?.takeIf { Connect4.dropRow(snapshot, it) >= 0 }
                ?: withContext(Dispatchers.Default) { Connect4.bestColumn(snapshot, me, level) }
            thinking = false
            if (snapshot === board) drop(col)
        }
    }

    fun newGame() {
        aiJob?.cancel()
        board = IntArray(Connect4.ROWS * Connect4.COLS); turn = 1; history = emptyList(); winLine = null; thinking = false
    }

    Row(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 22.dp)) {
        Box(Modifier.fillMaxHeight().weight(1f), contentAlignment = Alignment.Center) {
            GridBoard(
                Connect4.ROWS + 1, Connect4.COLS, cursor, { cursor = it },
                { col ->
                    if (!thinking && drop(col % Connect4.COLS) && vsAi && winLine == null && board.any { it == 0 }) aiMove()
                },
                focus, Modifier.fillMaxHeight(0.92f), columnOnly = true,
            ) { cell, origin, focused, _ ->
                val top = origin + Offset(0f, cell)
                drawRoundRect(
                    Brush.verticalGradient(listOf(Color(0xFF1E4C7A), Color(0xFF123355)), top.y, top.y + cell * Connect4.ROWS),
                    top, Size(cell * Connect4.COLS, cell * Connect4.ROWS), CornerRadius(cell * 0.3f),
                )
                for (i in 0 until Connect4.ROWS * Connect4.COLS) {
                    val center = top + Offset(cell * (i % Connect4.COLS + 0.5f), cell * (i / Connect4.COLS + 0.5f))
                    val v = board[i]
                    if (v == 0) {
                        drawCircle(Color(0xFF0A1C30), cell * 0.38f, center)
                    } else {
                        val base = if (v == 1) Palette.Cinnabar else Color(0xFFF2C14E)
                        drawCircle(Brush.radialGradient(listOf(base.copy(alpha = 0.85f), base), center - Offset(cell * 0.1f, cell * 0.1f), cell * 0.45f), cell * 0.4f, center)
                        drawCircle(Color.Black.copy(alpha = 0.18f), cell * 0.28f, center, style = Stroke(cell * 0.04f))
                    }
                    if (winLine?.contains(i) == true) drawCircle(Color.White, cell * 0.43f, center, style = Stroke(cell * 0.07f))
                }
                // 顶部选择列的待落子
                val col = cursor % Connect4.COLS
                if (!over) {
                    val c = origin + Offset(cell * (col + 0.5f), cell * 0.5f)
                    drawCircle((if (turn == 1) Palette.Cinnabar else Color(0xFFF2C14E)).copy(alpha = if (focused) 1f else 0.45f), cell * 0.38f, c)
                    if (focused) {
                        drawRoundRect(Palette.Gold.copy(alpha = 0.16f), Offset(origin.x + cell * col, top.y), Size(cell, cell * Connect4.ROWS), CornerRadius(cell * 0.2f))
                        cursorBrackets(c, cell)
                    }
                }
            }
            if (over) {
                val w = winLine?.let { board[it[0]] }
                ResultCard(
                    title = when {
                        w == null -> "和棋"
                        vsAi -> if (w == 1) "你赢了" else "AI 获胜"
                        else -> if (w == 1) "红方胜" else "黄方胜"
                    },
                    detail = if (w == null) "棋盘已满" else "四子连线",
                    win = w == 1 || (!vsAi && w != null),
                    onAgain = { newGame(); runCatching { focus.requestFocus() } },
                    onReview = null,
                    onExit = { nav.popBackStack() },
                )
            }
        }
        Spacer(Modifier.width(28.dp))
        SidePanel(
            title = "四子棋", seal = "四", vsAi = vsAi, onVsAi = { vsAi = it; newGame() }, level = level, onLevel = { level = it },
            status = when {
                over -> "对局结束"
                thinking -> "AI 思考中…"
                vsAi -> "轮到你（红）· 左右键选列，OK 落子"
                else -> if (turn == 1) "轮到红方" else "轮到黄方"
            },
            statusHot = thinking,
            canUndo = history.isNotEmpty() && !thinking,
            onUndo = {
                val steps = if (vsAi && history.size >= 2 && turn == 1) 2 else 1
                val next = board.copyOf()
                history.takeLast(steps).forEach { next[it] = 0 }
                board = next
                history = history.dropLast(steps)
                turn = if (history.size % 2 == 0) 1 else 2
                winLine = null
            },
            onNew = { newGame() },
            onExit = { nav.popBackStack() },
            rules = "7 列 × 6 行，棋子落到最下方空位。横、竖、斜先连成四子获胜。",
        ) {
            PlayerLine(if (vsAi) "你 · 红子先行" else "红方", Palette.Cinnabar, !over && turn == 1)
            PlayerLine(if (vsAi) "AI · 黄子" else "黄方", Color(0xFFF2C14E), !over && turn == 2)
        }
    }
}

// ====================================================================== 斗兽棋

@Composable
fun JungleScreen(nav: NavHostController) {
    val deps = LocalDeps.current
    val scope = rememberCoroutineScope()
    var board by remember { mutableStateOf(Jungle.initial()) }
    var turn by remember { mutableIntStateOf(1) }
    var history by remember { mutableStateOf(listOf<IntArray>()) }
    var lastMove by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var winner by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Int?>(null) }
    var vsAi by remember { mutableStateOf(true) }
    var level by remember { mutableIntStateOf(deps.store.settings.level) }
    var cursor by remember { mutableIntStateOf(6 * Jungle.COLS + 3) }
    var thinking by remember { mutableStateOf(false) }
    var aiJob by remember { mutableStateOf<Job?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val over = winner != 0
    val targets = remember(board, selected, turn) {
        selected?.let { s -> Jungle.moves(board, turn).filter { it.from == s }.map { it.to }.toSet() } ?: emptySet()
    }

    fun apply(from: Int, to: Int) {
        history = history + board
        val next = board.copyOf()
        val captured = next[to]
        next[to] = next[from]
        next[from] = 0
        board = next
        lastMove = from to to
        selected = null
        winner = Jungle.winnerAfter(next, turn, to)
        if (winner != 0) deps.sounds?.finish() else if (captured != 0) deps.sounds?.capture() else deps.sounds?.move()
        turn = -turn
    }

    fun aiMove() {
        aiJob?.cancel()
        aiJob = scope.launch {
            thinking = true
            delay(300)
            val snapshot = board
            val me = turn
            val letters = " RCDWPTLE"
            val online = if (deps.store.settings.onlineAi) runCatching {
                withTimeout(8000) {
                    OnlineAi.move(
                        "jungle", level,
                        board = snapshot.map { if (it == 0) "" else (if (it > 0) "r" else "b") + letters[kotlin.math.abs(it)] },
                        turn = if (me > 0) "r" else "b", timeLimitMs = 4000,
                    )
                }
            }.getOrNull() else null
            val legal = Jungle.moves(snapshot, me)
            val step = (online as? OnlineAi.AiMove.Step)?.let { s -> legal.firstOrNull { it.from == s.from && it.to == s.to } }
                ?: withContext(Dispatchers.Default) { Jungle.bestMove(snapshot, me, level) }
            thinking = false
            if (step != null && snapshot === board) apply(step.from, step.to)
        }
    }

    fun onOk(index: Int) {
        if (over || thinking) return
        if (vsAi && turn != 1) return
        val sel = selected
        if (sel != null && index in targets) {
            apply(sel, index)
            if (vsAi && winner == 0) aiMove()
            return
        }
        val p = board[index]
        if (p != 0 && Jungle.colorOf(p) == turn) {
            selected = if (sel == index) null else index
            deps.sounds?.select()
        } else if (sel != null) {
            selected = null
        }
    }

    fun newGame() {
        aiJob?.cancel()
        board = Jungle.initial(); turn = 1; history = emptyList(); winner = 0; selected = null; lastMove = null; thinking = false
    }

    val glyphStyle = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold)
    Row(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 22.dp)) {
        Box(Modifier.fillMaxHeight().weight(1f), contentAlignment = Alignment.Center) {
            GridBoard(Jungle.ROWS, Jungle.COLS, cursor, { cursor = it }, { onOk(it) }, focus, Modifier.fillMaxHeight()) { cell, origin, focused, measurer ->
                for (i in 0 until Jungle.CELLS) {
                    val tl = origin + Offset(cell * (i % Jungle.COLS), cell * (i / Jungle.COLS))
                    val color = when {
                        i in Jungle.WATER -> Color(0xFF7FB3C8)
                        Jungle.isDen(i) -> Color(0xFF6B4A2E)
                        Jungle.isTrap(i) -> Color(0xFFD9A35F)
                        else -> if ((i / Jungle.COLS + i % Jungle.COLS) % 2 == 0) Color(0xFFE9D3A4) else Color(0xFFE2C995)
                    }
                    drawRect(color, tl, Size(cell, cell))
                    drawRect(Palette.BoardLine.copy(alpha = 0.35f), tl, Size(cell, cell), style = Stroke(cell * 0.02f))
                    val label = when {
                        Jungle.isDen(i) -> "穴"
                        Jungle.isTrap(i) -> "陷"
                        i in Jungle.WATER -> "水"
                        else -> null
                    }
                    if (label != null && board[i] == 0) {
                        val layout = measurer.measure(label, glyphStyle.copy(color = Color.White.copy(alpha = 0.55f), fontSize = (cell * 0.32f).toSp()))
                        drawText(layout, topLeft = tl + Offset((cell - layout.size.width) / 2f, (cell - layout.size.height) / 2f))
                    }
                }
                lastMove?.let { (_, to) ->
                    drawRect(Palette.Gold.copy(alpha = 0.35f), origin + Offset(cell * (to % Jungle.COLS), cell * (to / Jungle.COLS)), Size(cell, cell))
                }
                for (i in 0 until Jungle.CELLS) {
                    val p = board[i]
                    val center = origin + Offset(cell * (i % Jungle.COLS + 0.5f), cell * (i / Jungle.COLS + 0.5f))
                    if (i in targets) drawCircle(Palette.Jade, cell * (if (p != 0) 0.45f else 0.14f), center, style = if (p != 0) Stroke(cell * 0.06f) else androidx.compose.ui.graphics.drawscope.Fill)
                    if (p == 0) continue
                    val red = p > 0
                    drawCircle(Color.Black.copy(alpha = 0.3f), cell * 0.4f, center + Offset(0f, cell * 0.05f))
                    drawCircle(if (red) Palette.Cinnabar else Palette.PieceBlack, cell * 0.4f, center)
                    drawCircle(Color.White.copy(alpha = 0.35f), cell * 0.33f, center, style = Stroke(cell * 0.03f))
                    if (i == selected) drawCircle(Palette.Gold, cell * 0.45f, center, style = Stroke(cell * 0.07f))
                    val layout = measurer.measure(Jungle.GLYPHS[kotlin.math.abs(p)], glyphStyle.copy(color = Palette.Ivory, fontSize = (cell * 0.38f).toSp()))
                    drawText(layout, topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f))
                }
                if (focused && !over) cursorBrackets(origin + Offset(cell * (cursor % Jungle.COLS + 0.5f), cell * (cursor / Jungle.COLS + 0.5f)), cell)
            }
            if (over) {
                ResultCard(
                    title = if (vsAi) (if (winner == 1) "你赢了" else "AI 获胜") else (if (winner == 1) "红方胜" else "黑方胜"),
                    detail = "攻入兽穴或对方无子可走",
                    win = winner == 1 || !vsAi,
                    onAgain = { newGame(); runCatching { focus.requestFocus() } },
                    onReview = null,
                    onExit = { nav.popBackStack() },
                )
            }
        }
        Spacer(Modifier.width(28.dp))
        SidePanel(
            title = "斗兽棋", seal = "兽", vsAi = vsAi, onVsAi = { vsAi = it; newGame() }, level = level, onLevel = { level = it },
            status = when {
                over -> "对局结束"
                thinking -> "AI 思考中…"
                selected != null -> "选择亮起的目标格，OK 确认"
                vsAi -> "轮到你（红方）· OK 选中兽棋"
                else -> if (turn == 1) "轮到红方" else "轮到黑方"
            },
            statusHot = thinking,
            canUndo = history.isNotEmpty() && !thinking,
            onUndo = {
                val steps = if (vsAi && history.size >= 2 && turn == 1) 2 else 1
                board = history[history.size - steps]
                history = history.dropLast(steps)
                turn = if (history.size % 2 == 0) 1 else -1
                winner = 0; selected = null; lastMove = null
            },
            onNew = { newGame() },
            onExit = { nav.popBackStack() },
            rules = "象 > 狮 > 虎 > 豹 > 狼 > 狗 > 猫 > 鼠，鼠能吃象；只有鼠能下河，狮虎可跳河；进入对方兽穴获胜。",
        ) {
            PlayerLine(if (vsAi) "你 · 红方先行" else "红方", Palette.Cinnabar, !over && turn == 1)
            PlayerLine(if (vsAi) "AI · 黑方" else "黑方", Palette.PieceBlack, !over && turn == -1)
        }
    }
}

@Suppress("unused")
private val keepSp = 1.sp
