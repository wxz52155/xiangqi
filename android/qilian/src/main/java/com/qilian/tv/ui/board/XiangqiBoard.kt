package com.qilian.tv.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.qilian.tv.ui.Palette
import com.qilian.tv.ui.SerifFamily
import com.qilian.tv.xiangqi.Xq
import kotlin.math.atan2
import kotlin.math.min

/** 棋盘上的一组视觉标记。 */
data class BoardMarks(
    val selected: Int? = null,
    val targets: Set<Int> = emptySet(),
    val lastFrom: Int? = null,
    val lastTo: Int? = null,
    val hintFrom: Int? = null,
    val hintTo: Int? = null,
    val checkSquare: Int? = null,
    val mateSquare: Int? = null,
)

/**
 * 中国象棋棋盘。
 * - 方向键在棋盘交叉点之间移动金色光标，OK 键回调 [onPoint]；
 *   光标在边缘时继续按方向键会把焦点交还给旁边的按钮。
 * - [flipped] 为 true 时黑方在下。
 */
@Composable
fun XiangqiBoard(
    board: IntArray,
    modifier: Modifier = Modifier,
    flipped: Boolean = false,
    marks: BoardMarks = BoardMarks(),
    interactive: Boolean = true,
    focusRequester: FocusRequester = remember { FocusRequester() },
    initialCursor: Int? = null,
    /** 面对面模式：黑方棋子的字与“汉界”倒过来，朝向坐在对面的人。 */
    faceToFace: Boolean = false,
    onPoint: (Int) -> Unit = {},
) {
    val measurer = rememberTextMeasurer(cacheSize = 48)
    var focused by remember { mutableStateOf(false) }
    var cursor by remember { mutableIntStateOf(initialCursor ?: Xq.kingSquare(board, if (flipped) Xq.BLACK else Xq.RED).coerceAtLeast(4)) }

    // 落子动画：lastTo 变化时，棋子从 lastFrom 滑到 lastTo
    val moveAnim = remember { Animatable(1f) }
    val animKey = "${marks.lastFrom}-${marks.lastTo}"
    var lastKey by remember { mutableStateOf(animKey) }
    LaunchedEffect(animKey) {
        if (animKey != lastKey && marks.lastFrom != null && marks.lastTo != null) {
            moveAnim.snapTo(0f)
            moveAnim.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
        }
        lastKey = animKey
    }

    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "pulse",
    )

    fun displayOf(square: Int): Pair<Int, Int> {
        val file = Xq.fileOf(square)
        val rank = Xq.rankOf(square)
        return if (flipped) (8 - file) to rank else file to (9 - rank)
    }

    fun squareOf(col: Int, row: Int): Int =
        if (flipped) Xq.squareAt(8 - col, row) else Xq.squareAt(col, 9 - row)

    // 触屏（平板）：点哪个交叉点就选哪个，与遥控器 OK 走同一套逻辑
    val currentOnPoint by rememberUpdatedState(onPoint)
    val currentFlipped by rememberUpdatedState(flipped)
    val touchModifier = if (interactive) {
        Modifier.pointerInput(Unit) {
            detectTapGestures { tap ->
                val w = size.width.toFloat()
                val h = size.height.toFloat()
                val cell = min(w / 9f, h / 10f)
                val ox = (w - cell * 9f) / 2f
                val oy = (h - cell * 10f) / 2f
                val col = ((tap.x - ox) / cell).toInt()
                val row = ((tap.y - oy) / cell).toInt()
                if (col in 0..8 && row in 0..9) {
                    val sq = if (currentFlipped) Xq.squareAt(8 - col, row) else Xq.squareAt(col, 9 - row)
                    cursor = sq
                    currentOnPoint(sq)
                }
            }
        }
    } else Modifier

    val keyModifier = if (interactive) {
        Modifier
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val (col, row) = displayOf(cursor)
                when (event.key) {
                    Key.DirectionLeft -> if (col > 0) { cursor = squareOf(col - 1, row); true } else false
                    Key.DirectionRight -> if (col < 8) { cursor = squareOf(col + 1, row); true } else false
                    Key.DirectionUp -> if (row > 0) { cursor = squareOf(col, row - 1); true } else false
                    Key.DirectionDown -> if (row < 9) { cursor = squareOf(col, row + 1); true } else false
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onPoint(cursor); true }
                    else -> false
                }
            }
            .onFocusChanged { focused = it.isFocused }
            .focusRequester(focusRequester)
            .focusable()
    } else Modifier

    Canvas(
        modifier
            .aspectRatio(9f / 10f)
            .then(touchModifier)
            .then(keyModifier),
    ) {
        val cell = min(size.width / 9f, size.height / 10f)
        val boardW = cell * 9f
        val boardH = cell * 10f
        val origin = Offset((size.width - boardW) / 2f, (size.height - boardH) / 2f)
        fun point(col: Int, row: Int) = Offset(origin.x + cell * (col + 0.5f), origin.y + cell * (row + 0.5f))
        fun pointOf(square: Int): Offset {
            val (c, r) = displayOf(square)
            return point(c, r)
        }

        drawBoardBase(origin, boardW, boardH, cell, measurer, flipped, faceToFace = faceToFace)

        // 上一步：起点淡环，终点柔光
        marks.lastFrom?.let { sq ->
            drawCircle(Palette.GoldDeep.copy(alpha = 0.55f), cell * 0.16f, pointOf(sq), style = Stroke(cell * 0.04f))
        }
        marks.lastTo?.let { sq ->
            drawCircle(
                Brush.radialGradient(listOf(Palette.Gold.copy(alpha = 0.55f), Color.Transparent), pointOf(sq), cell * 0.62f),
                cell * 0.62f,
                pointOf(sq),
            )
        }

        // 将军 / 将死：红色脉动光圈
        (marks.mateSquare ?: marks.checkSquare)?.let { sq ->
            val center = pointOf(sq)
            val r = cell * (0.56f + 0.06f * pulse)
            drawCircle(
                Brush.radialGradient(listOf(Palette.Cinnabar.copy(alpha = 0.75f), Color.Transparent), center, r * 1.25f),
                r * 1.25f,
                center,
            )
        }

        // 棋子
        val animating = moveAnim.value < 1f && marks.lastFrom != null && marks.lastTo != null
        for (sq in 0 until Xq.SIZE) {
            val piece = board[sq]
            if (piece == 0) continue
            if (animating && sq == marks.lastTo) continue
            val lifted = sq == marks.selected
            drawPiece(pointOf(sq), cell, piece, measurer, lifted, upsideDown = faceToFace && piece < 0)
        }
        if (animating) {
            val from = pointOf(marks.lastFrom!!)
            val to = pointOf(marks.lastTo!!)
            val t = moveAnim.value
            val p = Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
            val piece = board[marks.lastTo!!]
            if (piece != 0) drawPiece(p, cell, piece, measurer, lifted = true, upsideDown = faceToFace && piece < 0)
        }

        // 可走位置
        for (sq in marks.targets) {
            val c = pointOf(sq)
            if (board[sq] != 0) {
                drawCircle(Palette.Jade, cell * 0.47f, c, style = Stroke(cell * 0.06f))
            } else {
                drawCircle(Palette.Jade.copy(alpha = 0.35f), cell * 0.17f, c)
                drawCircle(Palette.Jade, cell * 0.10f, c)
            }
        }

        // 提示箭头
        if (marks.hintFrom != null && marks.hintTo != null) {
            drawArrow(pointOf(marks.hintFrom), pointOf(marks.hintTo), cell)
        }

        // 遥控器光标：亮蓝色方框 + 白色内描边 + 深色外描边，在木色棋盘和红黑棋子上都清楚
        if (interactive && focused) {
            val c = pointOf(cursor)
            val half = cell * 0.52f
            val arm = cell * 0.26f
            val w = cell * 0.1f
            drawRoundRect(
                Palette.Cursor.copy(alpha = 0.22f + 0.14f * pulse),
                Offset(c.x - half, c.y - half), Size(half * 2, half * 2), CornerRadius(cell * 0.2f),
            )
            drawRoundRect(
                Palette.Cursor, Offset(c.x - half, c.y - half), Size(half * 2, half * 2), CornerRadius(cell * 0.2f),
                style = Stroke(cell * 0.035f),
            )
            for ((sx, sy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
                val corner = Offset(c.x + sx * half, c.y + sy * half)
                val h = Offset(corner.x - sx * arm, corner.y)
                val v = Offset(corner.x, corner.y - sy * arm)
                drawLine(Color(0xFF06223F), corner, h, w * 1.7f, StrokeCap.Round)
                drawLine(Color(0xFF06223F), corner, v, w * 1.7f, StrokeCap.Round)
                drawLine(Palette.Cursor, corner, h, w, StrokeCap.Round)
                drawLine(Palette.Cursor, corner, v, w, StrokeCap.Round)
                drawLine(Color.White, corner, Offset(corner.x - sx * arm * 0.55f, corner.y), w * 0.32f, StrokeCap.Round)
                drawLine(Color.White, corner, Offset(corner.x, corner.y - sy * arm * 0.55f), w * 0.32f, StrokeCap.Round)
            }
        }
    }
}

private val GLYPH_STYLE = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Bold)

internal fun DrawScope.drawBoardBase(
    origin: Offset,
    w: Float,
    h: Float,
    cell: Float,
    measurer: TextMeasurer?,
    flipped: Boolean,
    simple: Boolean = false,
    faceToFace: Boolean = false,
) {
    val corner = CornerRadius(cell * 0.28f)
    // 投影
    if (!simple) {
        drawRoundRect(Color.Black.copy(alpha = 0.35f), origin + Offset(0f, cell * 0.12f), Size(w, h), corner)
    }
    drawRoundRect(
        Brush.linearGradient(listOf(Palette.BoardLight, Palette.BoardDark), origin, origin + Offset(w, h)),
        origin,
        Size(w, h),
        corner,
    )
    // 木纹
    if (!simple) {
        var y = origin.y + cell * 0.3f
        var i = 0
        while (y < origin.y + h) {
            val alpha = if (i % 3 == 0) 0.06f else 0.035f
            drawLine(Color(0xFF7A4E22).copy(alpha = alpha), Offset(origin.x + cell * 0.1f, y), Offset(origin.x + w - cell * 0.1f, y + cell * 0.08f), cell * 0.02f)
            y += cell * 0.37f
            i++
        }
    }
    val line = Palette.BoardLine
    val stroke = (cell * 0.028f).coerceAtLeast(1f)
    val left = origin.x + cell * 0.5f
    val top = origin.y + cell * 0.5f
    val right = left + cell * 8f
    val bottom = top + cell * 9f
    // 外框双线
    drawRect(line, Offset(left - cell * 0.12f, top - cell * 0.12f), Size(cell * 8.24f, cell * 9.24f), style = Stroke(stroke * 2.2f))
    for (r in 0..9) drawLine(line, Offset(left, top + r * cell), Offset(right, top + r * cell), stroke)
    for (c in 0..8) {
        if (c == 0 || c == 8) {
            drawLine(line, Offset(left + c * cell, top), Offset(left + c * cell, bottom), stroke)
        } else {
            drawLine(line, Offset(left + c * cell, top), Offset(left + c * cell, top + 4 * cell), stroke)
            drawLine(line, Offset(left + c * cell, top + 5 * cell), Offset(left + c * cell, bottom), stroke)
        }
    }
    // 九宫斜线
    for (base in intArrayOf(0, 7)) {
        drawLine(line, Offset(left + 3 * cell, top + base * cell), Offset(left + 5 * cell, top + (base + 2) * cell), stroke)
        drawLine(line, Offset(left + 5 * cell, top + base * cell), Offset(left + 3 * cell, top + (base + 2) * cell), stroke)
    }
    // 炮位 / 兵位标记
    if (!simple) {
        val marks = listOf(1 to 2, 7 to 2, 1 to 7, 7 to 7, 0 to 3, 2 to 3, 4 to 3, 6 to 3, 8 to 3, 0 to 6, 2 to 6, 4 to 6, 6 to 6, 8 to 6)
        val g = cell * 0.08f
        val l = cell * 0.16f
        for ((c, r) in marks) {
            val p = Offset(left + c * cell, top + r * cell)
            for ((sx, sy) in listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f)) {
                if ((c == 0 && sx < 0) || (c == 8 && sx > 0)) continue
                val q = Offset(p.x + sx * g, p.y + sy * g)
                drawLine(line, q, Offset(q.x + sx * l, q.y), stroke)
                drawLine(line, q, Offset(q.x, q.y + sy * l), stroke)
            }
        }
    }
    // 楚河 汉界
    if (measurer != null && !simple) {
        val style = GLYPH_STYLE.copy(color = Color(0xFF7D5A30).copy(alpha = 0.85f), fontSize = (cell * 0.42f).toSp(), letterSpacing = (cell * 0.18f).toSp())
        val leftText = if (flipped) "汉界" else "楚河"
        val rightText = if (flipped) "楚河" else "汉界"
        val riverY = top + 4.5f * cell
        for ((text, cx) in listOf(leftText to (left + 2f * cell), rightText to (left + 6f * cell))) {
            val layout = measurer.measure(text, style)
            val topLeft = Offset(cx - layout.size.width / 2f, riverY - layout.size.height / 2f)
            // 面对面：靠黑方一侧（屏幕上方那半）的字倒过来。楚河在红方一侧，汉界在黑方一侧。
            if (faceToFace && text == "汉界") {
                rotate(180f, pivot = Offset(cx, riverY)) { drawText(layout, topLeft = topLeft) }
            } else {
                drawText(layout, topLeft = topLeft)
            }
        }
    }
}

internal fun DrawScope.drawPiece(
    center: Offset,
    cell: Float,
    piece: Int,
    measurer: TextMeasurer?,
    lifted: Boolean = false,
    upsideDown: Boolean = false,
) {
    val r = cell * (if (lifted) 0.47f else 0.44f)
    val red = piece > 0
    val ink = if (red) Palette.PieceRed else Palette.PieceBlack
    // 阴影
    drawCircle(Color.Black.copy(alpha = if (lifted) 0.42f else 0.3f), r * 1.02f, center + Offset(0f, cell * (if (lifted) 0.1f else 0.06f)))
    if (lifted) {
        drawCircle(
            Brush.radialGradient(listOf(Palette.CursorGlow.copy(alpha = 0.75f), Color.Transparent), center, r * 1.45f),
            r * 1.45f,
            center,
        )
    }
    // 棋子侧边与正面
    drawCircle(Brush.verticalGradient(listOf(Palette.PieceEdge, Color(0xFFC9A66A)), center.y - r, center.y + r), r, center)
    drawCircle(
        Brush.radialGradient(listOf(Color.White, Palette.PieceFace, Color(0xFFEBD8B0)), center - Offset(r * 0.3f, r * 0.35f), r * 1.4f),
        r * 0.9f,
        center,
    )
    drawCircle(ink.copy(alpha = 0.85f), r * 0.76f, center, style = Stroke(r * 0.055f))
    if (lifted) {
        drawCircle(Color(0xFF06223F), r * 1.04f, center, style = Stroke(r * 0.16f))
        drawCircle(Palette.Cursor, r * 1.04f, center, style = Stroke(r * 0.1f))
    }
    if (measurer != null) {
        val layout = measurer.measure(Xq.glyph(piece), GLYPH_STYLE.copy(color = ink, fontSize = (r * 0.98f).toSp()))
        val topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f - r * 0.02f)
        if (upsideDown) {
            rotate(180f, pivot = center) { drawText(layout, topLeft = topLeft) }
        } else {
            drawText(layout, topLeft = topLeft)
        }
    }
}

private fun DrawScope.drawArrow(from: Offset, to: Offset, cell: Float) {
    val color = Palette.Hint
    val width = cell * 0.12f
    val angle = atan2(to.y - from.y, to.x - from.x)
    val headLen = cell * 0.36f
    val end = Offset(to.x - kotlin.math.cos(angle) * headLen * 0.8f, to.y - kotlin.math.sin(angle) * headLen * 0.8f)
    drawLine(Color.Black.copy(alpha = 0.25f), from + Offset(0f, cell * 0.04f), end + Offset(0f, cell * 0.04f), width, StrokeCap.Round)
    drawLine(Color.White, from, end, width * 1.45f, StrokeCap.Round)
    drawLine(color.copy(alpha = 0.95f), from, end, width, StrokeCap.Round)
    rotate(Math.toDegrees(angle.toDouble()).toFloat(), pivot = to) {
        val path = Path().apply {
            moveTo(to.x, to.y)
            lineTo(to.x - headLen, to.y - headLen * 0.55f)
            lineTo(to.x - headLen, to.y + headLen * 0.55f)
            close()
        }
        drawPath(path, color)
    }
    drawCircle(color, cell * 0.1f, from)
}

/** 小棋盘预览（训练卡片、棋谱列表用）。 */
@Composable
fun MiniBoard(board: IntArray, modifier: Modifier = Modifier, flipped: Boolean = false, lastFrom: Int? = null, lastTo: Int? = null) {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    Canvas(modifier.aspectRatio(9f / 10f)) {
        val cell = min(size.width / 9f, size.height / 10f)
        val origin = Offset((size.width - cell * 9f) / 2f, (size.height - cell * 10f) / 2f)
        drawBoardBase(origin, cell * 9f, cell * 10f, cell, null, flipped, simple = true)
        fun pointOf(sq: Int): Offset {
            val file = Xq.fileOf(sq)
            val rank = Xq.rankOf(sq)
            val col = if (flipped) 8 - file else file
            val row = if (flipped) rank else 9 - rank
            return Offset(origin.x + cell * (col + 0.5f), origin.y + cell * (row + 0.5f))
        }
        lastTo?.let { drawCircle(Palette.Gold.copy(alpha = 0.6f), cell * 0.55f, pointOf(it)) }
        for (sq in 0 until Xq.SIZE) {
            val p = board[sq]
            if (p == 0) continue
            drawPiece(pointOf(sq), cell, p, if (cell > 7.dp.toPx()) measurer else null)
        }
    }
}
