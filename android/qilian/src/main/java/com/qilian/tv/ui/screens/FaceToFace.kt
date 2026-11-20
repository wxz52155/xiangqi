package com.qilian.tv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.qilian.tv.game.XiangqiGame
import com.qilian.tv.ui.Avatar
import com.qilian.tv.ui.LocalDeps
import com.qilian.tv.ui.Palette
import com.qilian.tv.ui.TvButton
import com.qilian.tv.ui.Type
import com.qilian.tv.ui.board.XiangqiBoard
import com.qilian.tv.xiangqi.Outcome
import com.qilian.tv.xiangqi.WinReason
import com.qilian.tv.xiangqi.Xq
import kotlinx.coroutines.delay

/**
 * 把内容旋转 90°，并交换宽高参与布局（graphicsLayer 只旋转绘制，不改变占位）。
 * 触摸坐标会随图层一起变换，点哪里就是哪里。
 */
fun Modifier.rotate90(clockwise: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(
        Constraints(
            minWidth = constraints.minHeight,
            maxWidth = constraints.maxHeight,
            minHeight = constraints.minWidth,
            maxHeight = constraints.maxWidth,
        ),
    )
    layout(placeable.height, placeable.width) {
        placeable.placeWithLayer(
            x = -(placeable.width - placeable.height) / 2,
            y = -(placeable.height - placeable.width) / 2,
        ) { rotationZ = if (clockwise) 90f else -90f }
    }
}

/**
 * 平板面对面下棋：平板横放在两人中间，红方坐左侧、黑方坐右侧。
 * 棋盘几乎占满屏幕，每位棋手的控制条和棋子上的字都朝向自己。
 */
@Composable
fun FaceToFaceLayout(game: XiangqiGame, onExit: () -> Unit) {
    val store = LocalDeps.current.store
    BackHandler(enabled = game.selected != null) { game.clearSelection() }

    Row(
        Modifier
            .fillMaxSize()
            .padding(vertical = 10.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SeatPanel(game, Xq.RED, onExit, Modifier.rotate90(clockwise = true))
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            // 原棋盘红方在下；顺时针转 90° 后红方在左
            XiangqiBoard(
                board = game.position.board,
                flipped = false,
                marks = game.marks(store.settings.showLegalMoves),
                interactive = true,
                faceToFace = true,
                modifier = Modifier.rotate90(clockwise = true),
                onPoint = { game.onPoint(it) },
            )
        }
        SeatPanel(game, Xq.BLACK, onExit, Modifier.rotate90(clockwise = false))
    }
}

/** 一位棋手面前的控制条（在自己的坐标系里横着排，再整体转向这位棋手）。 */
@Composable
private fun SeatPanel(game: XiangqiGame, color: Int, onExit: () -> Unit, modifier: Modifier) {
    val isRed = color == Xq.RED
    val myTurn = !game.finished && game.position.turn == color
    val inCheck = myTurn && Xq.inCheck(game.position.board, color)
    var armed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(armed) {
        if (armed != null) {
            delay(3000)
            armed = null
        }
    }

    val outcome = game.outcome
    val (statusText, statusColor) = when {
        outcome is Outcome.Draw -> "和棋 · ${outcome.reason}" to Palette.Gold
        outcome is Outcome.Win && outcome.winner == color -> "你赢了 · " + when (outcome.reason) {
            WinReason.CHECKMATE -> "绝杀"
            WinReason.STALEMATE -> "困毙"
            WinReason.RESIGN -> "对方认输"
            WinReason.TIMEOUT -> "超时"
        } to Palette.Gold
        outcome is Outcome.Win -> "你输了" to Palette.Muted
        inCheck -> "将军！快应将" to Palette.Cinnabar
        myTurn -> (if (game.selected != null) "选择落点" else "轮到你走") to Palette.Jade
        else -> "等待对方走棋" to Palette.Muted
    }

    Column(
        modifier
            .fillMaxWidth()
            .height(132.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    if (myTurn) listOf(Palette.InkCard, (if (isRed) Palette.Cinnabar else Palette.Jade).copy(alpha = 0.18f))
                    else listOf(Palette.InkRaised, Palette.InkRaised),
                ),
            )
            .border(
                if (myTurn) 2.5.dp else 1.dp,
                if (myTurn) Palette.Gold else Palette.InkLine,
                RoundedCornerShape(20.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(if (isRed) "帅" else "将", red = isRed, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isRed) "红方" else "黑方", style = Type.CardTitle.copy(fontSize = 18.sp))
                Text(statusText, style = Type.BodyStrong.copy(color = statusColor), maxLines = 1)
            }
            Text("${game.plies.size} 手", style = Type.Small)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TvButton(
                "悔棋",
                { game.undo() },
                Modifier.weight(1f),
                enabled = game.canUndo && !game.finished,
                compact = true,
            )
            TvButton(
                if (armed == "resign") "确认认输" else "认输",
                {
                    if (armed == "resign") {
                        armed = null
                        game.resignSide(color)
                    } else armed = "resign"
                },
                Modifier.weight(1f),
                danger = true,
                enabled = !game.finished,
                compact = true,
            )
            TvButton(
                if (armed == "new") "确认新局" else "新局",
                {
                    if (armed == "new" || game.finished || game.plies.isEmpty()) {
                        armed = null
                        game.restart()
                    } else armed = "new"
                },
                Modifier.weight(1f),
                primary = game.finished,
                compact = true,
            )
            TvButton(
                if (armed == "exit") "确认退出" else "退出",
                {
                    if (armed == "exit") onExit() else armed = "exit"
                },
                Modifier.weight(1f),
                compact = true,
            )
        }
    }
}
