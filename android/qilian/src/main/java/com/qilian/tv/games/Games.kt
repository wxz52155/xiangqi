package com.qilian.tv.games

import kotlin.math.abs

/** 棋盘统一用 IntArray：0 空，1 先手方，2 后手方（斗兽棋另有编码）。 */

// ====================================================================== 五子棋

object Gomoku {
    const val SIZE = 15
    const val CELLS = SIZE * SIZE
    private val DIRS = arrayOf(intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(1, -1))

    /** 返回连成五子的格子（含刚落的子），没有则 null。 */
    fun winLine(board: IntArray, index: Int): List<Int>? {
        val color = board[index]
        if (color == 0) return null
        val row = index / SIZE
        val col = index % SIZE
        for (d in DIRS) {
            val line = ArrayList<Int>()
            line.add(index)
            for (sign in intArrayOf(-1, 1)) {
                var r = row + d[0] * sign
                var c = col + d[1] * sign
                while (r in 0 until SIZE && c in 0 until SIZE && board[r * SIZE + c] == color) {
                    if (sign < 0) line.add(0, r * SIZE + c) else line.add(r * SIZE + c)
                    r += d[0] * sign
                    c += d[1] * sign
                }
            }
            if (line.size >= 5) return line.take(5)
        }
        return null
    }

    /** 本地 AI：基于棋型打分的贪心 + 一层防守。level 越高看得越全。 */
    fun bestMove(board: IntArray, me: Int, level: Int): Int {
        if (board.all { it == 0 }) return 7 * SIZE + 7
        val opp = 3 - me
        var best = -1
        var bestScore = Long.MIN_VALUE
        val rnd = java.util.Random()
        for (i in 0 until CELLS) {
            if (board[i] != 0 || !hasNeighbor(board, i)) continue
            val attack = scorePoint(board, i, me)
            val defend = scorePoint(board, i, opp)
            val weightDefend = when (level) {
                1 -> 0.6
                2 -> 0.9
                else -> 1.0
            }
            var s = attack + (defend * weightDefend).toLong()
            if (level == 1) s += rnd.nextInt(400).toLong()
            if (s > bestScore) {
                bestScore = s
                best = i
            }
        }
        return if (best >= 0) best else board.indexOfFirst { it == 0 }
    }

    private fun hasNeighbor(board: IntArray, i: Int): Boolean {
        val r = i / SIZE
        val c = i % SIZE
        for (dr in -2..2) for (dc in -2..2) {
            if (dr == 0 && dc == 0) continue
            val nr = r + dr
            val nc = c + dc
            if (nr in 0 until SIZE && nc in 0 until SIZE && board[nr * SIZE + nc] != 0) return true
        }
        return false
    }

    private fun scorePoint(board: IntArray, i: Int, color: Int): Long {
        val r = i / SIZE
        val c = i % SIZE
        var total = 0L
        for (d in DIRS) {
            var count = 1
            var open = 0
            for (sign in intArrayOf(-1, 1)) {
                var nr = r + d[0] * sign
                var nc = c + d[1] * sign
                while (nr in 0 until SIZE && nc in 0 until SIZE && board[nr * SIZE + nc] == color) {
                    count++
                    nr += d[0] * sign
                    nc += d[1] * sign
                }
                if (nr in 0 until SIZE && nc in 0 until SIZE && board[nr * SIZE + nc] == 0) open++
            }
            total += when {
                count >= 5 -> 10_000_000
                count == 4 && open == 2 -> 1_000_000
                count == 4 && open == 1 -> 100_000
                count == 3 && open == 2 -> 50_000
                count == 3 && open == 1 -> 5_000
                count == 2 && open == 2 -> 2_000
                count == 2 && open == 1 -> 300
                count == 1 && open == 2 -> 60
                else -> 10
            }
        }
        return total
    }
}

// ====================================================================== 四子棋

object Connect4 {
    const val ROWS = 6
    const val COLS = 7

    /** 在 col 列落子，返回落点 index；满列返回 -1。 */
    fun dropRow(board: IntArray, col: Int): Int {
        for (row in ROWS - 1 downTo 0) if (board[row * COLS + col] == 0) return row * COLS + col
        return -1
    }

    fun winLine(board: IntArray, index: Int): List<Int>? {
        val color = board[index]
        if (color == 0) return null
        val row = index / COLS
        val col = index % COLS
        val dirs = arrayOf(intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(1, 1), intArrayOf(1, -1))
        for (d in dirs) {
            val line = ArrayList<Int>()
            line.add(index)
            for (sign in intArrayOf(-1, 1)) {
                var r = row + d[0] * sign
                var c = col + d[1] * sign
                while (r in 0 until ROWS && c in 0 until COLS && board[r * COLS + c] == color) {
                    if (sign < 0) line.add(0, r * COLS + c) else line.add(r * COLS + c)
                    r += d[0] * sign
                    c += d[1] * sign
                }
            }
            if (line.size >= 4) return line.take(4)
        }
        return null
    }

    /** 本地 AI：负极大值搜索。 */
    fun bestColumn(board: IntArray, me: Int, level: Int): Int {
        val depth = when (level) {
            1 -> 2
            2 -> 5
            else -> 7
        }
        val order = intArrayOf(3, 2, 4, 1, 5, 0, 6)
        var best = order.first { dropRow(board, it) >= 0 }
        var bestScore = Int.MIN_VALUE
        val b = board.copyOf()
        for (col in order) {
            val idx = dropRow(b, col)
            if (idx < 0) continue
            b[idx] = me
            val s = if (winLine(b, idx) != null) 100_000 else -negamax(b, 3 - me, depth - 1, -1_000_000, 1_000_000)
            b[idx] = 0
            val noisy = if (level == 1) java.util.Random().nextInt(30) else 0
            if (s + noisy > bestScore) {
                bestScore = s + noisy
                best = col
            }
        }
        return best
    }

    private fun negamax(b: IntArray, color: Int, depth: Int, alphaIn: Int, beta: Int): Int {
        if (b.none { it == 0 }) return 0
        if (depth == 0) return heuristic(b, color)
        var alpha = alphaIn
        for (col in intArrayOf(3, 2, 4, 1, 5, 0, 6)) {
            val idx = dropRow(b, col)
            if (idx < 0) continue
            b[idx] = color
            val s = if (winLine(b, idx) != null) 10_000 + depth else -negamax(b, 3 - color, depth - 1, -beta, -alpha)
            b[idx] = 0
            if (s > alpha) alpha = s
            if (alpha >= beta) break
        }
        return alpha
    }

    private fun heuristic(b: IntArray, color: Int): Int {
        var score = 0
        for (r in 0 until ROWS) {
            if (b[r * COLS + 3] == color) score += 3 else if (b[r * COLS + 3] != 0) score -= 3
        }
        return score
    }
}

// ====================================================================== 斗兽棋

/**
 * 斗兽棋（9 行 × 7 列，黑方在上，红方在下先走）。
 * 棋子编码：红方 1..8，黑方 -1..-8；1 鼠 2 猫 3 狗 4 狼 5 豹 6 虎 7 狮 8 象。
 */
object Jungle {
    const val ROWS = 9
    const val COLS = 7
    const val CELLS = ROWS * COLS
    val GLYPHS = arrayOf("", "鼠", "猫", "狗", "狼", "豹", "虎", "狮", "象")

    const val BLACK_DEN = 3
    const val RED_DEN = 3 + 8 * COLS
    val BLACK_TRAPS = setOf(2, 4, 3 + COLS)
    val RED_TRAPS = setOf(2 + 8 * COLS, 4 + 8 * COLS, 3 + 7 * COLS)
    val WATER: Set<Int> = (3..5).flatMap { r -> listOf(1, 2, 4, 5).map { c -> r * COLS + c } }.toSet()

    fun initial(): IntArray {
        val b = IntArray(CELLS)
        fun put(p: Int, r: Int, c: Int) { b[r * COLS + c] = p }
        put(-7, 0, 0); put(-6, 0, 6); put(-3, 1, 1); put(-2, 1, 5)
        put(-1, 2, 0); put(-5, 2, 2); put(-4, 2, 4); put(-8, 2, 6)
        put(6, 8, 0); put(7, 8, 6); put(2, 7, 1); put(3, 7, 5)
        put(8, 6, 0); put(4, 6, 2); put(5, 6, 4); put(1, 6, 6)
        return b
    }

    fun colorOf(p: Int) = if (p > 0) 1 else if (p < 0) -1 else 0
    fun ownDen(color: Int) = if (color > 0) RED_DEN else BLACK_DEN
    fun enemyTraps(color: Int) = if (color > 0) BLACK_TRAPS else RED_TRAPS
    fun isTrap(i: Int) = i in BLACK_TRAPS || i in RED_TRAPS
    fun isDen(i: Int) = i == RED_DEN || i == BLACK_DEN

    private fun power(piece: Int, at: Int): Int = if (at in enemyTraps(colorOf(piece))) 0 else abs(piece)

    private fun canEnter(piece: Int, to: Int): Boolean {
        if (to == ownDen(colorOf(piece))) return false
        if (to in WATER && abs(piece) != 1) return false
        return true
    }

    private fun canCapture(a: Int, from: Int, d: Int, to: Int): Boolean {
        if (colorOf(a) == colorOf(d)) return false
        if (abs(d) == 1 && to in WATER && abs(a) != 1) return false
        if (abs(a) == 1 && abs(d) == 8 && from !in WATER) return true
        if (abs(a) == 8 && abs(d) == 1) return false
        return power(a, from) >= power(d, to)
    }

    data class JMove(val from: Int, val to: Int)

    fun moves(board: IntArray, color: Int): List<JMove> {
        val out = ArrayList<JMove>()
        val dirs = arrayOf(intArrayOf(-1, 0), intArrayOf(1, 0), intArrayOf(0, -1), intArrayOf(0, 1))
        for (from in 0 until CELLS) {
            val p = board[from]
            if (p == 0 || colorOf(p) != color) continue
            val r = from / COLS
            val c = from % COLS
            for (d in dirs) {
                var nr = r + d[0]
                var nc = c + d[1]
                if (nr !in 0 until ROWS || nc !in 0 until COLS) continue
                var to = nr * COLS + nc
                if (to in WATER && (abs(p) == 6 || abs(p) == 7)) {
                    // 狮虎跳河，河里有鼠挡住则不能跳
                    var blocked = false
                    while (nr in 0 until ROWS && nc in 0 until COLS && (nr * COLS + nc) in WATER) {
                        if (board[nr * COLS + nc] != 0) blocked = true
                        nr += d[0]
                        nc += d[1]
                    }
                    if (blocked || nr !in 0 until ROWS || nc !in 0 until COLS) continue
                    to = nr * COLS + nc
                }
                if (!canEnter(p, to)) continue
                val t = board[to]
                if (t != 0 && !canCapture(p, from, t, to)) continue
                out.add(JMove(from, to))
            }
        }
        return out
    }

    /** 走完之后的胜负：进对方兽穴 / 对方无子可走 → 胜方颜色；否则 0。 */
    fun winnerAfter(board: IntArray, mover: Int, to: Int): Int {
        if (to == ownDen(-mover)) return mover
        if (moves(board, -mover).isEmpty()) return mover
        return 0
    }

    /** 本地 AI：浅层搜索 + 子力/推进评估。 */
    fun bestMove(board: IntArray, color: Int, level: Int): JMove? {
        val list = moves(board, color)
        if (list.isEmpty()) return null
        val depth = when (level) {
            1 -> 1
            2 -> 3
            else -> 4
        }
        var best = list[0]
        var bestScore = Int.MIN_VALUE
        for (m in list) {
            val b = board.copyOf()
            b[m.to] = b[m.from]
            b[m.from] = 0
            val s = if (m.to == ownDen(-color)) 100_000 else -search(b, -color, depth - 1, -1_000_000, 1_000_000)
            val noisy = if (level == 1) java.util.Random().nextInt(60) else 0
            if (s + noisy > bestScore) {
                bestScore = s + noisy
                best = m
            }
        }
        return best
    }

    private fun search(b: IntArray, color: Int, depth: Int, alphaIn: Int, beta: Int): Int {
        if (depth <= 0) return evaluate(b, color)
        val list = moves(b, color)
        if (list.isEmpty()) return -90_000
        var alpha = alphaIn
        for (m in list) {
            val captured = b[m.to]
            val p = b[m.from]
            b[m.to] = p
            b[m.from] = 0
            val s = if (m.to == ownDen(-color)) 90_000 + depth else -search(b, -color, depth - 1, -beta, -alpha)
            b[m.from] = p
            b[m.to] = captured
            if (s > alpha) alpha = s
            if (alpha >= beta) break
        }
        return alpha
    }

    private val VALUE = intArrayOf(0, 500, 200, 300, 400, 500, 800, 900, 1000)

    private fun evaluate(b: IntArray, color: Int): Int {
        var s = 0
        for (i in 0 until CELLS) {
            val p = b[i]
            if (p == 0) continue
            val row = i / COLS
            // 红方往上（row 变小）推进，黑方往下
            val advance = if (p > 0) (8 - row) else row
            val v = VALUE[abs(p)] + advance * 12
            s += if (colorOf(p) == color) v else -v
        }
        return s
    }
}
