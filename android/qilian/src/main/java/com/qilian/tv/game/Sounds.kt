package com.qilian.tv.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import com.qilian.tv.R
import com.qilian.tv.data.Store
import com.qilian.tv.xiangqi.Move
import com.qilian.tv.xiangqi.Outcome
import com.qilian.tv.xiangqi.Position
import com.qilian.tv.xiangqi.WinReason
import com.qilian.tv.xiangqi.Xq

/**
 * 落子音效（木质轻响）+ 语音播报（红方男声、黑方女声）。
 * 语音由 tools/tv-voices/generate.py 用 Kokoro（Apache-2.0）合成。
 */
class Sounds(context: Context, private val store: Store) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(6)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val handler = Handler(Looper.getMainLooper())

    private val moveId = pool.load(context, R.raw.piece_move, 1)
    private val selectId = pool.load(context, R.raw.piece_select, 1)
    private val captureId = pool.load(context, R.raw.piece_capture, 1)
    private val checkId = pool.load(context, R.raw.check, 1)
    private val errorId = pool.load(context, R.raw.error, 1)
    private val finishId = pool.load(context, R.raw.finish, 1)

    /** 语音：key → (男声, 女声)。显式引用 R.raw，避免被资源压缩删掉。 */
    private val voices: Map<String, Pair<Int, Int>> = mapOf(
        "check" to (R.raw.voice_check_m to R.raw.voice_check_f),
        "check2" to (R.raw.voice_check2_m to R.raw.voice_check2_f),
        "mate" to (R.raw.voice_mate_m to R.raw.voice_mate_f),
        "stalemate" to (R.raw.voice_stalemate_m to R.raw.voice_stalemate_f),
        "eat_rook" to (R.raw.voice_eat_rook_m to R.raw.voice_eat_rook_f),
        "eat_knight" to (R.raw.voice_eat_knight_m to R.raw.voice_eat_knight_f),
        "eat_cannon" to (R.raw.voice_eat_cannon_m to R.raw.voice_eat_cannon_f),
        "eat_bishop" to (R.raw.voice_eat_bishop_m to R.raw.voice_eat_bishop_f),
        "eat_advisor" to (R.raw.voice_eat_advisor_m to R.raw.voice_eat_advisor_f),
        "eat_pawn" to (R.raw.voice_eat_pawn_m to R.raw.voice_eat_pawn_f),
        "out_rook" to (R.raw.voice_out_rook_m to R.raw.voice_out_rook_f),
        "out_knight" to (R.raw.voice_out_knight_m to R.raw.voice_out_knight_f),
        "center_cannon" to (R.raw.voice_center_cannon_m to R.raw.voice_center_cannon_f),
        "out_cannon" to (R.raw.voice_out_cannon_m to R.raw.voice_out_cannon_f),
        "out_bishop" to (R.raw.voice_out_bishop_m to R.raw.voice_out_bishop_f),
        "out_advisor" to (R.raw.voice_out_advisor_m to R.raw.voice_out_advisor_f),
        "out_pawn" to (R.raw.voice_out_pawn_m to R.raw.voice_out_pawn_f),
        "cross_river" to (R.raw.voice_cross_river_m to R.raw.voice_cross_river_f),
        "king_move" to (R.raw.voice_king_move_m to R.raw.voice_king_move_f),
        "start" to (R.raw.voice_start_m to R.raw.voice_start_f),
        "win" to (R.raw.voice_win_m to R.raw.voice_win_f),
        "lose" to (R.raw.voice_lose_m to R.raw.voice_lose_f),
        "draw" to (R.raw.voice_draw_m to R.raw.voice_draw_f),
        "red_win" to (R.raw.voice_red_win_m to R.raw.voice_red_win_f),
        "black_win" to (R.raw.voice_black_win_m to R.raw.voice_black_win_f),
        "resign" to (R.raw.voice_resign_m to R.raw.voice_resign_f),
        "undo" to (R.raw.voice_undo_m to R.raw.voice_undo_f),
        "hint" to (R.raw.voice_hint_m to R.raw.voice_hint_f),
        "illegal" to (R.raw.voice_illegal_m to R.raw.voice_illegal_f),
        "solved" to (R.raw.voice_solved_m to R.raw.voice_solved_f),
        "wrong" to (R.raw.voice_wrong_m to R.raw.voice_wrong_f),
        "lesson_done" to (R.raw.voice_lesson_done_m to R.raw.voice_lesson_done_f),
    ).mapValues { (_, ids) -> pool.load(context, ids.first, 1) to pool.load(context, ids.second, 1) }

    private fun play(id: Int, volume: Float = 0.9f) {
        if (!store.settings.sound) return
        pool.play(id, volume, volume, 1, 0, 1f)
    }

    fun move() = play(moveId)
    fun select() = play(selectId, 0.6f)
    fun capture() = play(captureId)
    fun check() = play(checkId, 0.8f)
    fun error() = play(errorId, 0.5f)
    fun finish() = play(finishId, 0.8f)

    /**
     * 播一句语音。[color] 决定男声（红方）还是女声（黑方）；[delayMs] 让语音落在木头声之后。
     */
    fun say(key: String, color: Int = Xq.RED, delayMs: Long = 120) {
        if (!store.settings.voice) return
        val ids = voices[key] ?: return
        val id = if (color == Xq.BLACK) ids.second else ids.first
        handler.postDelayed({ pool.play(id, 1f, 1f, 2, 0, 1f) }, delayMs)
    }

    /**
     * 一步棋落下后的完整反馈：木头声 + 按优先级挑一句播报。
     * @param history 这步之前已经走过的着法（判断“第一次出车”等）
     */
    fun announceMove(move: Move, after: Position, history: List<Move>, outcome: Outcome) {
        val color = Xq.colorOf(move.piece)
        val type = Xq.typeOf(move.piece)
        val givesCheck = Xq.inCheck(after.board, -color)
        when {
            outcome is Outcome.Win && outcome.reason == WinReason.CHECKMATE -> { capture(); say("mate", color) }
            outcome is Outcome.Win && outcome.reason == WinReason.STALEMATE -> { move(); say("stalemate", color) }
            givesCheck -> {
                if (move.captured != 0) capture() else move()
                // 同一方连续两手都在将军：第二次换一句更有气势的
                val wasChecking = lastCheckBy == color && lastCheckPly == history.size - 2
                say(if (wasChecking) "check2" else "check", color)
                lastCheckBy = color
                lastCheckPly = history.size
            }
            move.captured != 0 -> {
                capture()
                val key = when (Xq.typeOf(move.captured)) {
                    Xq.ROOK -> "eat_rook"
                    Xq.KNIGHT -> "eat_knight"
                    Xq.CANNON -> "eat_cannon"
                    Xq.BISHOP -> "eat_bishop"
                    Xq.ADVISOR -> "eat_advisor"
                    Xq.PAWN -> "eat_pawn"
                    else -> null
                }
                if (key != null) say(key, color)
            }
            else -> {
                move()
                val firstOfType = history.none { it.piece == move.piece }
                val key = when {
                    type == Xq.CANNON && Xq.fileOf(move.to) == 4 && Xq.fileOf(move.from) != 4 &&
                        history.none { it.piece == move.piece && Xq.fileOf(it.to) == 4 } -> "center_cannon"
                    type == Xq.PAWN && crossedRiver(color, move) -> "cross_river"
                    !firstOfType -> null
                    type == Xq.ROOK -> "out_rook"
                    type == Xq.KNIGHT -> "out_knight"
                    type == Xq.CANNON -> "out_cannon"
                    type == Xq.BISHOP -> "out_bishop"
                    type == Xq.ADVISOR -> "out_advisor"
                    type == Xq.PAWN -> "out_pawn"
                    type == Xq.KING -> "king_move"
                    else -> null
                }
                if (key != null) say(key, color)
            }
        }
    }

    private var lastCheckBy = 0
    private var lastCheckPly = -10

    private fun crossedRiver(color: Int, move: Move): Boolean {
        val fromRank = Xq.rankOf(move.from)
        val toRank = Xq.rankOf(move.to)
        return if (color == Xq.RED) fromRank == 4 && toRank == 5 else fromRank == 5 && toRank == 4
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        pool.release()
    }
}
