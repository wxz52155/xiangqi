package com.qilian.tv.xiangqi

import kotlin.math.max
import kotlin.math.min

/**
 * 本地象棋 AI：迭代加深 alpha-beta + 静态搜索 + 置换表 + 历史启发。
 * 联网时优先用在线 AI（Pikafish），断网或超时时用它兜底；“提示”也用它。
 */
class LocalEngine {
    data class Result(val move: Move?, val score: Int, val depth: Int, val nodes: Long)

    private val maxPly = 64
    private val board = IntArray(Xq.SIZE)
    private var side = Xq.RED
    private var nodes = 0L
    private var deadline = 0L
    private var stopped = false
    private val moveBuf = Array(maxPly + 8) { IntArray(128) }
    private val scoreBuf = Array(maxPly + 8) { IntArray(128) }
    private val history = Array(Xq.SIZE) { IntArray(Xq.SIZE) }
    private val killers = Array(maxPly + 8) { IntArray(2) }

    private val ttSize = 1 shl 18
    private val ttKey = LongArray(ttSize)
    private val ttMove = IntArray(ttSize)
    private val ttScore = IntArray(ttSize)
    private val ttDepth = ByteArray(ttSize)
    private val ttFlag = ByteArray(ttSize)
    private var hashKey = 0L

    /**
     * @param level 1 入门 / 2 进阶 / 3 挑战
     * @param forbidden 禁止走的着法（UCCI），用于悔棋后换思路
     */
    fun search(
        position: Position,
        level: Int,
        timeLimitMs: Long = defaultTime(level),
        forbidden: Set<String> = emptySet(),
        onDepth: ((Int, Long) -> Unit)? = null,
        cancelled: () -> Boolean = { false },
    ): Result {
        position.board.copyInto(board)
        side = position.turn
        nodes = 0
        stopped = false
        for (row in history) row.fill(0)
        for (k in killers) k.fill(0)
        hashKey = computeHash()
        deadline = System.currentTimeMillis() + timeLimitMs
        this.cancelled = cancelled

        val legal = Xq.legalMoves(position).filter { it.ucci !in forbidden }
        if (legal.isEmpty()) return Result(null, -MATE, 0, 0)
        if (legal.size == 1) return Result(legal[0], 0, 1, 1)

        val maxDepth = when (level) {
            1 -> 3
            2 -> 5
            else -> 8
        }
        val rootMoves = legal.map { Xq.encode(it.from, it.to) }.toIntArray()
        var best = rootMoves[0]
        var bestScore = 0
        var reached = 0
        for (depth in 1..maxDepth) {
            val (move, score) = searchRoot(rootMoves, depth, best)
            if (stopped && depth > 1) break
            best = move
            bestScore = score
            reached = depth
            onDepth?.invoke(depth, nodes)
            if (kotlin.math.abs(score) > MATE - 100) break
            if (System.currentTimeMillis() > deadline - (timeLimitMs / 3)) break
        }

        // 入门难度：在前几名里随机挑一步，让它像新手一点
        if (level == 1 && kotlin.math.abs(bestScore) < MATE - 100) {
            val pick = softRandom(rootMoves, best)
            if (pick != best) best = pick
        }
        val chosen = legal.first { Xq.encode(it.from, it.to) == best }
        return Result(chosen, bestScore, reached, nodes)
    }

    private var cancelled: () -> Boolean = { false }

    private fun defaultTime(level: Int): Long = when (level) {
        1 -> 600L
        2 -> 1800L
        else -> 4000L
    }

    private fun softRandom(rootMoves: IntArray, best: Int): Int {
        val scored = rootMoves.map { m ->
            makeMove(m)
            val s = -quiesce(-INF, INF, 1)
            unmakeMove(m)
            m to s
        }.sortedByDescending { it.second }
        val top = scored.first().second
        val candidates = scored.filter { it.second >= top - 60 }.take(3)
        return if (candidates.isEmpty()) best else candidates.random().first
    }

    private fun searchRoot(moves: IntArray, depth: Int, pv: Int): Pair<Int, Int> {
        // pv 优先
        val ordered = moves.sortedByDescending { if (it == pv) Int.MAX_VALUE else history[Xq.moveFrom(it)][Xq.moveTo(it)] }
        var alpha = -INF
        val beta = INF
        var bestMove = ordered[0]
        for (m in ordered) {
            makeMove(m)
            val score = -alphaBeta(depth - 1, -beta, -alpha, 1, true)
            unmakeMove(m)
            if (stopped) break
            if (score > alpha) {
                alpha = score
                bestMove = m
            }
        }
        return bestMove to alpha
    }

    private fun checkTime() {
        if ((nodes and 1023L) == 0L) {
            if (System.currentTimeMillis() > deadline || cancelled()) stopped = true
        }
    }

    private fun alphaBeta(depthIn: Int, alphaIn: Int, beta: Int, ply: Int, allowNull: Boolean): Int {
        nodes++
        checkTime()
        if (stopped) return 0
        var alpha = alphaIn
        val inCheck = Xq.inCheck(board, side)
        var depth = depthIn
        if (inCheck) depth++
        if (depth <= 0) return quiesce(alpha, beta, ply)
        if (ply >= maxPly) return evaluate()

        val slot = (hashKey and (ttSize - 1).toLong()).toInt()
        var ttBest = 0
        if (ttKey[slot] == hashKey) {
            ttBest = ttMove[slot]
            if (ttDepth[slot] >= depth) {
                val s = ttScore[slot]
                when (ttFlag[slot].toInt()) {
                    EXACT -> return s
                    LOWER -> if (s >= beta) return s
                    UPPER -> if (s <= alpha) return s
                }
            }
        }

        // 空着裁剪
        if (allowNull && !inCheck && depth >= 3 && hasMajorPieces()) {
            side = -side
            hashKey = hashKey xor SIDE_KEY
            val score = -alphaBeta(depth - 3, -beta, -beta + 1, ply + 1, false)
            side = -side
            hashKey = hashKey xor SIDE_KEY
            if (stopped) return 0
            if (score >= beta) return beta
        }

        val moves = moveBuf[ply]
        val scores = scoreBuf[ply]
        val n = Xq.pseudoMoves(board, side, moves)
        for (i in 0 until n) scores[i] = orderScore(moves[i], ttBest, ply)

        var legalCount = 0
        var bestScore = -INF
        var bestMove = 0
        val startAlpha = alpha
        for (i in 0 until n) {
            // 选择排序取当前最高分
            var bi = i
            for (j in i + 1 until n) if (scores[j] > scores[bi]) bi = j
            if (bi != i) {
                val tm = moves[i]; moves[i] = moves[bi]; moves[bi] = tm
                val ts = scores[i]; scores[i] = scores[bi]; scores[bi] = ts
            }
            val m = moves[i]
            if (Xq.typeOf(board[Xq.moveTo(m)]) == Xq.KING) return MATE - ply
            makeMove(m)
            if (Xq.inCheck(board, -side)) {
                unmakeMove(m)
                continue
            }
            legalCount++
            val score = if (legalCount == 1) {
                -alphaBeta(depth - 1, -beta, -alpha, ply + 1, true)
            } else {
                var s = -alphaBeta(depth - 1, -alpha - 1, -alpha, ply + 1, true)
                if (s > alpha && s < beta) s = -alphaBeta(depth - 1, -beta, -alpha, ply + 1, true)
                s
            }
            unmakeMove(m)
            if (stopped) return 0
            if (score > bestScore) {
                bestScore = score
                bestMove = m
            }
            if (score > alpha) {
                alpha = score
                if (score >= beta) {
                    if (board[Xq.moveTo(m)] == 0) {
                        history[Xq.moveFrom(m)][Xq.moveTo(m)] += depth * depth
                        if (killers[ply][0] != m) {
                            killers[ply][1] = killers[ply][0]
                            killers[ply][0] = m
                        }
                    }
                    break
                }
            }
        }
        if (legalCount == 0) return -(MATE - ply) // 将死与困毙都判负

        val flag = when {
            bestScore >= beta -> LOWER
            bestScore > startAlpha -> EXACT
            else -> UPPER
        }
        ttKey[slot] = hashKey
        ttMove[slot] = bestMove
        ttScore[slot] = bestScore
        ttDepth[slot] = min(depth, 127).toByte()
        ttFlag[slot] = flag.toByte()
        return bestScore
    }

    private fun quiesce(alphaIn: Int, beta: Int, ply: Int): Int {
        nodes++
        checkTime()
        if (stopped) return 0
        var alpha = alphaIn
        val stand = evaluate()
        if (stand >= beta) return stand
        if (stand > alpha) alpha = stand
        if (ply >= maxPly) return stand
        val moves = moveBuf[ply]
        val scores = scoreBuf[ply]
        val n = Xq.pseudoMoves(board, side, moves, capturesOnly = true)
        for (i in 0 until n) {
            val m = moves[i]
            scores[i] = VALUE[Xq.typeOf(board[Xq.moveTo(m)])] * 10 - VALUE[Xq.typeOf(board[Xq.moveFrom(m)])]
        }
        for (i in 0 until n) {
            var bi = i
            for (j in i + 1 until n) if (scores[j] > scores[bi]) bi = j
            if (bi != i) {
                val tm = moves[i]; moves[i] = moves[bi]; moves[bi] = tm
                val ts = scores[i]; scores[i] = scores[bi]; scores[bi] = ts
            }
            val m = moves[i]
            if (Xq.typeOf(board[Xq.moveTo(m)]) == Xq.KING) return MATE - ply
            makeMove(m)
            if (Xq.inCheck(board, -side)) {
                unmakeMove(m)
                continue
            }
            val score = -quiesce(-beta, -alpha, ply + 1)
            unmakeMove(m)
            if (stopped) return 0
            if (score >= beta) return score
            if (score > alpha) alpha = score
        }
        return alpha
    }

    private fun orderScore(m: Int, ttBest: Int, ply: Int): Int {
        if (m == ttBest) return 1_000_000
        val victim = board[Xq.moveTo(m)]
        if (victim != 0) return 100_000 + VALUE[Xq.typeOf(victim)] * 10 - VALUE[Xq.typeOf(board[Xq.moveFrom(m)])]
        if (m == killers[ply][0]) return 90_000
        if (m == killers[ply][1]) return 80_000
        return history[Xq.moveFrom(m)][Xq.moveTo(m)]
    }

    private fun hasMajorPieces(): Boolean {
        var count = 0
        for (p in board) if (p != 0 && Xq.colorOf(p) == side) {
            val t = Xq.typeOf(p)
            if (t == Xq.ROOK || t == Xq.KNIGHT || t == Xq.CANNON) count++
        }
        return count >= 2
    }

    // ------------------------------------------------------------------ 着法执行

    private val capturedStack = IntArray(maxPly + 16)
    private var stackTop = 0

    private fun makeMove(m: Int) {
        val from = Xq.moveFrom(m)
        val to = Xq.moveTo(m)
        val piece = board[from]
        val captured = board[to]
        capturedStack[stackTop++] = captured
        hashKey = hashKey xor zobrist(piece, from) xor zobrist(piece, to) xor SIDE_KEY
        if (captured != 0) hashKey = hashKey xor zobrist(captured, to)
        board[to] = piece
        board[from] = 0
        side = -side
    }

    private fun unmakeMove(m: Int) {
        val from = Xq.moveFrom(m)
        val to = Xq.moveTo(m)
        val piece = board[to]
        val captured = capturedStack[--stackTop]
        board[from] = piece
        board[to] = captured
        side = -side
        hashKey = hashKey xor zobrist(piece, from) xor zobrist(piece, to) xor SIDE_KEY
        if (captured != 0) hashKey = hashKey xor zobrist(captured, to)
    }

    private fun computeHash(): Long {
        var h = if (side == Xq.BLACK) SIDE_KEY else 0L
        for (sq in 0 until Xq.SIZE) if (board[sq] != 0) h = h xor zobrist(board[sq], sq)
        return h
    }

    private fun zobrist(piece: Int, sq: Int): Long = ZOBRIST[(piece + 7) * Xq.SIZE + sq]

    // ------------------------------------------------------------------ 评估

    /** 以当前走子方视角返回分数。 */
    private fun evaluate(): Int {
        var score = 0
        for (sq in 0 until Xq.SIZE) {
            val p = board[sq]
            if (p == 0) continue
            val t = Xq.typeOf(p)
            // PST 以红方视角定义；黑方镜像 rank
            val rank = if (p > 0) Xq.rankOf(sq) else 9 - Xq.rankOf(sq)
            val idx = rank * 9 + Xq.fileOf(sq)
            val v = VALUE[t] + pst(t, idx)
            score += if (p > 0) v else -v
        }
        return (if (side == Xq.RED) score else -score) + 8
    }

    private fun pst(type: Int, idx: Int): Int = when (type) {
        Xq.PAWN -> PST_PAWN[idx]
        Xq.KNIGHT -> PST_KNIGHT[idx]
        Xq.ROOK -> PST_ROOK[idx]
        Xq.CANNON -> PST_CANNON[idx]
        else -> 0
    }

    companion object {
        const val MATE = 30000
        const val INF = 32000
        private const val EXACT = 0
        private const val LOWER = 1
        private const val UPPER = 2
        private val VALUE = intArrayOf(0, 6000, 120, 120, 270, 600, 285, 30)

        private val ZOBRIST: LongArray
        private val SIDE_KEY: Long

        init {
            val rnd = java.util.Random(0x51C0FFEE)
            ZOBRIST = LongArray(15 * Xq.SIZE) { rnd.nextLong() }
            SIDE_KEY = rnd.nextLong()
        }

        // 位置分（红方视角，rank 0 = 红方底线，行内 file a..i）
        private val PST_PAWN = intArrayOf(
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0,
            -2, 0, -2, 0, 6, 0, -2, 0, -2,
            3, 0, 7, 0, 7, 0, 7, 0, 3,
            10, 18, 22, 35, 40, 35, 22, 18, 10,
            20, 27, 30, 40, 42, 40, 30, 27, 20,
            20, 30, 45, 55, 55, 55, 45, 30, 20,
            20, 30, 50, 65, 70, 65, 50, 30, 20,
            0, 3, 6, 9, 12, 9, 6, 3, 0,
        )
        private val PST_KNIGHT = intArrayOf(
            0, -3, 2, 0, 2, 0, 2, -3, 0,
            -3, 2, 4, 5, -10, 5, 4, 2, -3,
            5, 4, 6, 7, 4, 7, 6, 4, 5,
            4, 6, 10, 7, 10, 7, 10, 6, 4,
            2, 10, 13, 14, 15, 14, 13, 10, 2,
            2, 12, 11, 15, 16, 15, 11, 12, 2,
            5, 20, 12, 19, 12, 19, 12, 20, 5,
            4, 10, 11, 15, 11, 15, 11, 10, 4,
            2, 8, 15, 9, 6, 9, 15, 8, 2,
            2, 2, 2, 8, 2, 8, 2, 2, 2,
        )
        private val PST_ROOK = intArrayOf(
            -2, 10, 6, 14, 12, 14, 6, 10, -2,
            8, 4, 8, 16, 8, 16, 8, 4, 8,
            4, 8, 6, 14, 12, 14, 6, 8, 4,
            6, 10, 8, 14, 14, 14, 8, 10, 6,
            12, 16, 14, 20, 20, 20, 14, 16, 12,
            12, 14, 12, 18, 18, 18, 12, 14, 12,
            12, 18, 16, 22, 22, 22, 16, 18, 12,
            12, 12, 12, 18, 18, 18, 12, 12, 12,
            16, 20, 18, 24, 26, 24, 18, 20, 16,
            14, 14, 12, 18, 16, 18, 12, 14, 14,
        )
        private val PST_CANNON = intArrayOf(
            0, 0, 2, 6, 6, 6, 2, 0, 0,
            0, 2, 4, 6, 6, 6, 4, 2, 0,
            4, 0, 8, 6, 10, 6, 8, 0, 4,
            0, 0, 0, 2, 4, 2, 0, 0, 0,
            -2, 0, 4, 2, 6, 2, 4, 0, -2,
            0, 0, 0, 2, 8, 2, 0, 0, 0,
            0, 0, -2, 4, 10, 4, -2, 0, 0,
            2, 2, 0, -10, -8, -10, 0, 2, 2,
            2, 2, 0, -4, -14, -4, 0, 2, 2,
            6, 4, 0, -10, -12, -10, 0, 4, 6,
        )
    }
}
