package com.qilian.tv.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.qilian.tv.data.GameRecord
import com.qilian.tv.data.OnlineAi
import com.qilian.tv.data.Store
import com.qilian.tv.ui.board.BoardMarks
import com.qilian.tv.xiangqi.LocalEngine
import com.qilian.tv.xiangqi.Move
import com.qilian.tv.xiangqi.Outcome
import com.qilian.tv.xiangqi.Position
import com.qilian.tv.xiangqi.WinReason
import com.qilian.tv.xiangqi.Xq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class Mode { AI, HUMAN }

data class PlyRecord(val position: Position, val move: Move, val notation: String)

/**
 * 一局象棋（人机 / 双人同屏）的状态与流程。所有字段都是 Compose 状态，界面直接读。
 */
class XiangqiGame(
    private val scope: CoroutineScope,
    private val store: Store,
    private val sounds: Sounds?,
) {
    var mode by mutableStateOf(Mode.AI); private set
    /** 平板面对面模式（双人，红左黑右）。 */
    var faceToFace by mutableStateOf(false); private set
    var userColor by mutableStateOf(Xq.RED); private set
    var level by mutableStateOf(2); private set
    var startFen by mutableStateOf(Xq.START_FEN); private set
    var position by mutableStateOf(Xq.initial()); private set
    var plies by mutableStateOf(listOf<PlyRecord>()); private set
    var selected by mutableStateOf<Int?>(null); private set
    var targets by mutableStateOf(setOf<Int>()); private set
    var hint by mutableStateOf<Pair<Int, Int>?>(null); private set
    var outcome by mutableStateOf<Outcome>(Outcome.Playing); private set
    var thinking by mutableStateOf(false); private set
    var thinkingInfo by mutableStateOf(""); private set
    var aiSource by mutableStateOf(""); private set
    var message by mutableStateOf<String?>(null)
    var flipped by mutableStateOf(false); private set
    var hintsUsed by mutableStateOf(0); private set
    var hintBusy by mutableStateOf(false); private set

    private var id = newId()
    private var startedAt = System.currentTimeMillis()
    private var aiJob: Job? = null
    private var messageJob: Job? = null
    private val engine = LocalEngine()
    private val hashes = ArrayList<String>()

    val finished get() = outcome !is Outcome.Playing
    val isUserTurn get() = mode == Mode.HUMAN || position.turn == userColor
    val lastMove get() = plies.lastOrNull()?.move

    fun marks(showTargets: Boolean): BoardMarks {
        val last = lastMove
        val checkSq = if (Xq.inCheck(position.board, position.turn)) Xq.kingSquare(position.board, position.turn) else null
        val mate = (outcome as? Outcome.Win)?.takeIf { it.reason == WinReason.CHECKMATE || it.reason == WinReason.STALEMATE }
        return BoardMarks(
            selected = selected,
            targets = if (showTargets) targets else emptySet(),
            lastFrom = last?.from,
            lastTo = last?.to,
            hintFrom = hint?.first,
            hintTo = hint?.second,
            checkSquare = checkSq,
            mateSquare = if (mate != null) Xq.kingSquare(position.board, -mate.winner) else null,
        )
    }

    fun start(mode: Mode, userColor: Int, level: Int, fen: String = Xq.START_FEN, faceToFace: Boolean = this.faceToFace) {
        aiJob?.cancel()
        this.mode = mode
        this.faceToFace = faceToFace && mode == Mode.HUMAN
        this.userColor = userColor
        this.level = level
        startFen = fen
        position = Xq.parseFen(fen)
        plies = emptyList()
        hashes.clear()
        hashes.add(Xq.hash(position))
        clearSelection()
        hint = null
        outcome = Outcome.Playing
        thinking = false
        hintsUsed = 0
        flipped = this.mode == Mode.AI && userColor == Xq.BLACK
        id = newId()
        startedAt = System.currentTimeMillis()
        save()
        maybeAi()
    }

    /** 从存档继续。 */
    fun resume(record: GameRecord): Boolean = runCatching {
        mode = if (record.mode == "human" || record.mode == "face") Mode.HUMAN else Mode.AI
        faceToFace = record.mode == "face"
        userColor = record.userColor
        level = record.level
        startFen = record.startFen
        var pos = Xq.parseFen(record.startFen)
        val list = ArrayList<PlyRecord>()
        hashes.clear()
        hashes.add(Xq.hash(pos))
        for (ucci in record.moves) {
            val (from, to) = Xq.parseUcci(ucci) ?: break
            val move = Xq.findLegal(pos, from, to) ?: break
            list.add(PlyRecord(pos, move, Xq.notation(pos, move)))
            pos = Xq.play(pos, move)
            hashes.add(Xq.hash(pos))
        }
        position = pos
        plies = list
        id = record.id
        startedAt = record.startedAt
        flipped = mode == Mode.AI && userColor == Xq.BLACK
        outcome = Xq.outcome(position, hashes)
        clearSelection()
        maybeAi()
        true
    }.getOrDefault(false)

    fun onPoint(square: Int) {
        if (finished) return
        if (thinking || !isUserTurn) {
            flash("AI 正在思考，请稍候")
            return
        }
        val piece = position.board[square]
        val sel = selected
        if (sel != null && square in targets) {
            val move = Xq.findLegal(position, sel, square) ?: return
            applyMove(move)
            maybeAi()
            return
        }
        if (piece != 0 && Xq.colorOf(piece) == position.turn) {
            if (sel == square) {
                clearSelection()
            } else {
                selected = square
                targets = Xq.legalMoves(position).filter { it.from == square }.map { it.to }.toSet()
                if (targets.isEmpty()) flash("这枚棋子现在没有可走的位置")
                sounds?.select()
            }
            return
        }
        if (sel != null) {
            clearSelection()
            flash("这步不能走")
            sounds?.error()
        }
    }

    fun clearSelection() {
        selected = null
        targets = emptySet()
    }

    private fun applyMove(move: Move) {
        val notation = Xq.notation(position, move)
        plies = plies + PlyRecord(position, move, notation)
        position = Xq.play(position, move)
        hashes.add(Xq.hash(position))
        clearSelection()
        hint = null
        outcome = Xq.outcome(position, hashes)
        when {
            outcome !is Outcome.Playing -> sounds?.finish()
            Xq.inCheck(position.board, position.turn) -> {
                sounds?.check()
                flash("将军！")
            }
            move.captured != 0 -> sounds?.capture()
            else -> sounds?.move()
        }
        if (finished) finishGame() else save()
    }

    private fun maybeAi() {
        if (mode != Mode.AI || finished || position.turn == userColor) return
        aiJob?.cancel()
        aiJob = scope.launch {
            thinking = true
            thinkingInfo = "AI 思考中…"
            delay(260)
            val snapshot = position
            val move = findAiMove(snapshot)
            thinking = false
            if (move != null && snapshot === position) applyMove(move)
            else if (move == null && snapshot === position) flash("AI 暂时无法走棋，请重试")
        }
    }

    private suspend fun findAiMove(pos: Position): Move? {
        if (store.settings.onlineAi) {
            val online = runCatching {
                withTimeout(9500) {
                    OnlineAi.move(
                        game = "xiangqi",
                        level = level,
                        fen = Xq.toFen(pos),
                        timeLimitMs = when (level) { 1 -> 2000L; 2 -> 4000L; else -> 8000L },
                    ) { progress ->
                        thinkingInfo = "在线 AI 思考中 · 深度 ${progress.depth}"
                    }
                }
            }.getOrNull()
            val ucci = (online as? OnlineAi.AiMove.Ucci)?.ucci
            val parsed = ucci?.let { Xq.parseUcci(it) }
            val legal = parsed?.let { Xq.findLegal(pos, it.first, it.second) }
            if (legal != null) {
                aiSource = "在线 AI"
                return legal
            }
        }
        aiSource = "本地 AI"
        thinkingInfo = "本地 AI 思考中…"
        return withContext(Dispatchers.Default) {
            engine.search(pos, level, onDepth = { d, _ -> thinkingInfo = "本地 AI 思考中 · 深度 $d" }).move
        }
    }

    fun requestHint() {
        if (finished || !isUserTurn || hintBusy) return
        hintBusy = true
        scope.launch {
            val pos = position
            val result = withContext(Dispatchers.Default) { LocalEngine().search(pos, 3, timeLimitMs = 1500) }
            hintBusy = false
            if (pos !== position) return@launch
            val m = result.move ?: return@launch
            hint = m.from to m.to
            hintsUsed++
            selected = m.from
            targets = Xq.legalMoves(position).filter { it.from == m.from }.map { it.to }.toSet()
            flash("提示：${Xq.notation(position, m)}")
        }
    }

    val canUndo: Boolean
        get() = store.settings.allowUndo && !thinking && plies.isNotEmpty() &&
            (mode == Mode.HUMAN || plies.any { Xq.colorOf(it.move.piece) == userColor })

    fun undo() {
        if (!canUndo) return
        aiJob?.cancel()
        thinking = false
        var steps = 1
        if (mode == Mode.AI) {
            // 机器最后一步：退两手；自己最后一步：退一手
            val last = plies.last()
            steps = if (Xq.colorOf(last.move.piece) == userColor) 1 else 2
        }
        steps = minOf(steps, plies.size)
        val target = plies[plies.size - steps]
        position = target.position
        plies = plies.dropLast(steps)
        repeat(steps) { if (hashes.size > 1) hashes.removeAt(hashes.lastIndex) }
        outcome = Outcome.Playing
        clearSelection()
        hint = null
        sounds?.move()
        save()
        maybeAi()
    }

    fun resign() {
        if (finished) return
        aiJob?.cancel()
        thinking = false
        val loser = if (mode == Mode.AI) userColor else position.turn
        outcome = Outcome.Win(-loser, WinReason.RESIGN)
        finishGame()
    }

    /** 面对面模式：指定某一方认输。 */
    fun resignSide(color: Int) {
        if (finished) return
        aiJob?.cancel()
        thinking = false
        outcome = Outcome.Win(-color, WinReason.RESIGN)
        sounds?.finish()
        finishGame()
    }

    fun flip() {
        flipped = !flipped
    }

    fun restart() = start(mode, userColor, level, startFen)

    fun switchSide() {
        if (mode != Mode.AI) {
            flip()
            return
        }
        userColor = -userColor
        flipped = userColor == Xq.BLACK
        clearSelection()
        save()
        maybeAi()
    }

    fun flash(text: String) {
        message = text
        messageJob?.cancel()
        messageJob = scope.launch {
            delay(2200)
            message = null
        }
    }

    fun resultTitle(): String = when (val o = outcome) {
        is Outcome.Draw -> "和棋"
        is Outcome.Win -> if (mode == Mode.AI) {
            if (o.winner == userColor) "你赢了" else "AI 获胜"
        } else {
            if (o.winner == Xq.RED) "红方胜" else "黑方胜"
        }
        else -> ""
    }

    fun resultDetail(): String = when (val o = outcome) {
        is Outcome.Draw -> o.reason
        is Outcome.Win -> when (o.reason) {
            WinReason.CHECKMATE -> "绝杀，将死对方"
            WinReason.STALEMATE -> "困毙，对方无子可走"
            WinReason.RESIGN -> if (mode == Mode.AI && o.winner != userColor) "你认输了" else "对方认输"
            WinReason.TIMEOUT -> "超时"
        }
        else -> ""
    }

    private fun finishGame() {
        val o = outcome
        val result = when (o) {
            is Outcome.Draw -> "draw"
            is Outcome.Win -> if (mode == Mode.AI) (if (o.winner == userColor) "win" else "loss") else (if (o.winner == Xq.RED) "red" else "black")
            else -> ""
        }
        if (plies.isNotEmpty()) store.addHistory(record(result, "${resultTitle()} · ${resultDetail()}"))
        store.saveCurrentGame(null)
    }

    private fun record(result: String, text: String) = GameRecord(
        id = id,
        startFen = startFen,
        moves = plies.map { it.move.ucci },
        notations = plies.map { it.notation },
        mode = if (faceToFace) "face" else if (mode == Mode.HUMAN) "human" else "ai",
        userColor = userColor,
        level = level,
        result = result,
        resultText = text,
        startedAt = startedAt,
        endedAt = System.currentTimeMillis(),
    )

    private fun save() {
        if (finished) return
        store.saveCurrentGame(record("", ""))
    }

    fun dispose() {
        aiJob?.cancel()
    }

    companion object {
        fun newId() = "g" + System.currentTimeMillis().toString(36) + (100..999).random()
    }
}
