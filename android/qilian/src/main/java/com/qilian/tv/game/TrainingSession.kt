package com.qilian.tv.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.qilian.tv.data.Lesson
import com.qilian.tv.data.Store
import com.qilian.tv.data.Tactic
import com.qilian.tv.data.TrainingItem
import com.qilian.tv.ui.board.BoardMarks
import com.qilian.tv.xiangqi.Move
import com.qilian.tv.xiangqi.Outcome
import com.qilian.tv.xiangqi.Position
import com.qilian.tv.xiangqi.Xq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SessionState { PLAYING, SOLVED, FAILED_FLASH }

/**
 * 训练会话：一步杀法（自由寻找杀着）与残棋定式 / 开局（按主线跟练，对方自动应手）。
 */
class TrainingSession(
    val item: TrainingItem,
    private val scope: CoroutineScope,
    private val store: Store,
    private val sounds: Sounds?,
) {
    private val start: Position = Xq.parseFen(item.fen)
    private val lesson = item as? Lesson
    private val tactic = item as? Tactic
    private val mainLine = lesson?.lines?.firstOrNull()?.moves.orEmpty()

    /** 用户执哪方（象棋颜色）。 */
    val userColor: Int = lesson?.userColor ?: start.turn

    var position by mutableStateOf(start); private set
    var plies by mutableStateOf(listOf<PlyRecord>()); private set
    var selected by mutableStateOf<Int?>(null); private set
    var targets by mutableStateOf(setOf<Int>()); private set
    var hintArrow by mutableStateOf<Pair<Int, Int>?>(null); private set
    var hintText by mutableStateOf<String?>(null); private set
    var comment by mutableStateOf<String?>(null); private set
    var message by mutableStateOf<String?>(null); private set
    var solved by mutableStateOf(false); private set
    var errors by mutableStateOf(0); private set
    var hints by mutableStateOf(0); private set
    var busy by mutableStateOf(false); private set
    var autoPlaying by mutableStateOf(false); private set
    var lineIndex by mutableStateOf(0); private set

    private var job: Job? = null
    private var messageJob: Job? = null

    val flipped get() = userColor == Xq.BLACK
    val totalSteps get() = mainLine.size
    val isTactic get() = tactic != null

    init {
        scheduleOpponent()
    }

    fun marks(): BoardMarks {
        val last = plies.lastOrNull()?.move
        val check = if (Xq.inCheck(position.board, position.turn)) Xq.kingSquare(position.board, position.turn) else null
        return BoardMarks(
            selected = selected,
            targets = targets,
            lastFrom = last?.from,
            lastTo = last?.to,
            hintFrom = hintArrow?.first,
            hintTo = hintArrow?.second,
            checkSquare = if (solved) null else check,
            mateSquare = if (solved && tactic != null) check ?: Xq.kingSquare(position.board, position.turn) else null,
        )
    }

    fun onPoint(square: Int) {
        if (solved || busy || autoPlaying) return
        if (position.turn != userColor) return
        val piece = position.board[square]
        val sel = selected
        if (sel != null && square in targets) {
            val move = Xq.findLegal(position, sel, square) ?: return
            userMove(move)
            return
        }
        if (piece != 0 && Xq.colorOf(piece) == position.turn) {
            if (sel == square) clear() else {
                selected = square
                targets = Xq.legalMoves(position).filter { it.from == square }.map { it.to }.toSet()
                sounds?.select()
            }
            return
        }
        if (sel != null) clear()
    }

    fun clear() {
        selected = null
        targets = emptySet()
    }

    private fun push(move: Move) {
        val history = plies.map { it.move }
        plies = plies + PlyRecord(position, move, Xq.notation(position, move))
        position = Xq.play(position, move)
        clear()
        sounds?.announceMove(move, position, history, Xq.outcome(position))
    }

    private fun userMove(move: Move) {
        hintArrow = null
        if (tactic != null) {
            val before = position
            push(move)
            val outcome = Xq.outcome(position)
            val firstMoves = tactic.solutions.mapNotNull { it.firstOrNull() }
            if (outcome is Outcome.Win && outcome.winner == userColor || (move.ucci in firstMoves && tactic.solutions.any { it.size > 1 })) {
                if (outcome is Outcome.Win) complete() else continueTacticLine(move.ucci)
            } else {
                errors++
                sounds?.error()
                sounds?.say("wrong", userColor, 700)
                flash("这步没有形成杀棋，再想想")
                busy = true
                job = scope.launch {
                    delay(900)
                    position = before
                    plies = plies.dropLast(1)
                    busy = false
                }
            }
            return
        }

        val expected = mainLine.getOrNull(lineIndex)
        if (expected != null && move.ucci != expected.ucci) {
            errors++
            sounds?.error()
            sounds?.say("wrong", userColor)
            val target = Xq.parseUcci(expected.ucci)
            val m = target?.let { Xq.findLegal(position, it.first, it.second) }
            flash(if (m != null && errors >= 2) "这步不在主线，参考着法：${Xq.notation(position, m)}" else "这步不在主线，再想想")
            if (errors >= 2 && target != null) hintArrow = target
            clear()
            return
        }
        push(move)
        comment = expected?.comment
        lineIndex++
        afterLineStep()
    }

    private fun continueTacticLine(played: String) {
        // 多步杀题：对方按题目给定的应手
        val line = tactic?.solutions?.firstOrNull { it.firstOrNull() == played } ?: return
        scope.launch {
            busy = true
            delay(600)
            val reply = line.getOrNull(1)?.let { Xq.parseUcci(it) }?.let { Xq.findLegal(position, it.first, it.second) }
            if (reply != null) push(reply)
            busy = false
        }
    }

    private fun afterLineStep() {
        if (lineIndex >= mainLine.size) {
            complete()
            return
        }
        scheduleOpponent()
    }

    private fun scheduleOpponent() {
        if (lesson == null || solved) return
        if (position.turn == userColor) return
        val next = mainLine.getOrNull(lineIndex) ?: return
        job?.cancel()
        job = scope.launch {
            busy = true
            delay(750)
            val parsed = Xq.parseUcci(next.ucci)
            val move = parsed?.let { Xq.findLegal(position, it.first, it.second) }
            busy = false
            if (move == null) {
                complete()
                return@launch
            }
            push(move)
            comment = next.comment
            lineIndex++
            afterLineStep()
        }
    }

    private fun complete() {
        solved = true
        busy = false
        autoPlaying = false
        hintArrow = null
        sounds?.finish()
        sounds?.say(if (tactic != null) "solved" else "lesson_done", userColor, 1100)
        store.markCompleted(item.kind, item.id)
        comment = when {
            tactic != null -> tactic.explanation
            else -> lesson?.completionText ?: comment
        }
    }

    fun hint() {
        if (solved || busy) return
        hints++
        if (tactic != null) {
            if (hintText == null) {
                hintText = tactic.hint.ifBlank { "先找能直接将军的着法。" }
                return
            }
            val first = tactic.solutions.firstOrNull()?.firstOrNull() ?: return
            hintArrow = Xq.parseUcci(first)
            return
        }
        val next = mainLine.getOrNull(lineIndex) ?: return
        hintArrow = Xq.parseUcci(next.ucci)
        val m = hintArrow?.let { Xq.findLegal(position, it.first, it.second) }
        if (m != null) flash("参考着法：${Xq.notation(position, m)}")
    }

    /** 自动演示剩下的主线。 */
    fun autoplay() {
        if (lesson == null || solved) return
        job?.cancel()
        autoPlaying = true
        job = scope.launch {
            while (lineIndex < mainLine.size) {
                delay(900)
                val next = mainLine[lineIndex]
                val parsed = Xq.parseUcci(next.ucci) ?: break
                val move = Xq.findLegal(position, parsed.first, parsed.second) ?: break
                push(move)
                comment = next.comment
                lineIndex++
            }
            autoPlaying = false
            complete()
        }
    }

    fun restart() {
        job?.cancel()
        position = start
        plies = emptyList()
        clear()
        hintArrow = null
        hintText = null
        comment = null
        solved = false
        errors = 0
        busy = false
        autoPlaying = false
        lineIndex = 0
        scheduleOpponent()
    }

    /** 退回到上一步用户着法之前。 */
    fun undo() {
        if (lesson == null || plies.isEmpty() || busy || autoPlaying) return
        job?.cancel()
        var count = 0
        while (plies.isNotEmpty()) {
            val last = plies.last()
            position = last.position
            plies = plies.dropLast(1)
            lineIndex = (lineIndex - 1).coerceAtLeast(0)
            count++
            if (Xq.colorOf(last.move.piece) == userColor) break
        }
        solved = false
        comment = mainLine.getOrNull(lineIndex - 1)?.comment
        clear()
        if (count > 0) sounds?.move()
    }

    private fun flash(text: String) {
        message = text
        messageJob?.cancel()
        messageJob = scope.launch {
            delay(2400)
            message = null
        }
    }

    fun dispose() {
        job?.cancel()
    }
}
