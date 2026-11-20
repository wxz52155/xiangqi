package com.qilian.tv.xiangqi

/**
 * 中国象棋规则核（移植自 packages/xiangqi）。
 *
 * 坐标：file 0..8 = a..i（红方视角从左到右），rank 0..9 = 红方底线到黑方底线，square = rank * 9 + file。
 * 棋子用整数表示：红方为正、黑方为负；1 帅 2 仕 3 相 4 马 5 车 6 炮 7 兵。
 */
object Xq {
    const val FILES = 9
    const val RANKS = 10
    const val SIZE = 90

    const val KING = 1
    const val ADVISOR = 2
    const val BISHOP = 3
    const val KNIGHT = 4
    const val ROOK = 5
    const val CANNON = 6
    const val PAWN = 7

    const val RED = 1
    const val BLACK = -1

    const val START_FEN = "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR w - - 0 1"

    fun fileOf(sq: Int) = sq % FILES
    fun rankOf(sq: Int) = sq / FILES
    fun squareAt(file: Int, rank: Int) = rank * FILES + file
    fun inBoard(file: Int, rank: Int) = file in 0 until FILES && rank in 0 until RANKS
    fun colorOf(piece: Int) = if (piece > 0) RED else if (piece < 0) BLACK else 0
    fun typeOf(piece: Int) = if (piece < 0) -piece else piece

    fun isPalace(color: Int, file: Int, rank: Int): Boolean {
        if (file < 3 || file > 5) return false
        return if (color == RED) rank in 0..2 else rank in 7..9
    }

    private val RED_GLYPHS = arrayOf("", "帅", "仕", "相", "马", "车", "炮", "兵")
    private val BLACK_GLYPHS = arrayOf("", "将", "士", "象", "馬", "車", "砲", "卒")
    private val RED_NAMES = arrayOf("", "帅", "仕", "相", "马", "车", "炮", "兵")
    private val BLACK_NAMES = arrayOf("", "将", "士", "象", "马", "车", "炮", "卒")
    private const val LETTERS = " kabnrcp"
    private const val FILE_LETTERS = "abcdefghi"
    private val CN = arrayOf("一", "二", "三", "四", "五", "六", "七", "八", "九")

    fun glyph(piece: Int): String =
        if (piece > 0) RED_GLYPHS[piece] else if (piece < 0) BLACK_GLYPHS[-piece] else ""

    fun pieceName(piece: Int): String =
        if (piece > 0) RED_NAMES[piece] else if (piece < 0) BLACK_NAMES[-piece] else ""

    fun ucci(from: Int, to: Int): String =
        "${FILE_LETTERS[fileOf(from)]}${rankOf(from)}${FILE_LETTERS[fileOf(to)]}${rankOf(to)}"

    fun parseUcci(text: String): Pair<Int, Int>? {
        if (text.length != 4) return null
        val ff = FILE_LETTERS.indexOf(text[0])
        val fr = text[1] - '0'
        val tf = FILE_LETTERS.indexOf(text[2])
        val tr = text[3] - '0'
        if (!inBoard(ff, fr) || !inBoard(tf, tr)) return null
        return squareAt(ff, fr) to squareAt(tf, tr)
    }

    fun encode(from: Int, to: Int) = (from shl 7) or to
    fun moveFrom(move: Int) = move ushr 7
    fun moveTo(move: Int) = move and 127

    // ------------------------------------------------------------------ FEN

    fun parseFen(fen: String): Position {
        val parts = fen.trim().split(Regex("\\s+"))
        require(parts.size >= 2) { "FEN 不完整" }
        val rows = parts[0].split('/')
        require(rows.size == 10) { "FEN 需要 10 行" }
        val board = IntArray(SIZE)
        for (row in 0 until 10) {
            val rank = 9 - row
            var file = 0
            for (ch in rows[row]) {
                if (ch in '1'..'9') {
                    file += ch - '0'
                } else {
                    val type = LETTERS.indexOf(ch.lowercaseChar())
                    require(type > 0) { "FEN 棋子无效：$ch" }
                    require(inBoard(file, rank)) { "FEN 列数过多" }
                    board[squareAt(file, rank)] = if (ch.isUpperCase()) type else -type
                    file += 1
                }
            }
            require(file == 9) { "FEN 每行必须 9 列" }
        }
        require(kingSquare(board, RED) >= 0 && kingSquare(board, BLACK) >= 0) { "FEN 缺少将帅" }
        val turn = when (parts[1]) {
            "w", "r" -> RED
            "b" -> BLACK
            else -> throw IllegalArgumentException("FEN 行棋方无效")
        }
        val half = parts.getOrNull(4)?.toIntOrNull() ?: 0
        val full = parts.getOrNull(5)?.toIntOrNull() ?: 1
        return Position(board, turn, half, full)
    }

    fun toFen(position: Position): String {
        val sb = StringBuilder()
        for (rank in 9 downTo 0) {
            var empty = 0
            for (file in 0 until 9) {
                val p = position.board[squareAt(file, rank)]
                if (p == 0) {
                    empty++
                    continue
                }
                if (empty > 0) {
                    sb.append(empty)
                    empty = 0
                }
                val letter = LETTERS[typeOf(p)]
                sb.append(if (p > 0) letter.uppercaseChar() else letter)
            }
            if (empty > 0) sb.append(empty)
            if (rank > 0) sb.append('/')
        }
        sb.append(if (position.turn == RED) " w" else " b")
        sb.append(" - - ").append(position.halfmove).append(' ').append(position.fullmove)
        return sb.toString()
    }

    fun initial(): Position = parseFen(START_FEN)

    fun kingSquare(board: IntArray, color: Int): Int {
        val king = KING * color
        if (color == RED) {
            for (rank in 0..2) for (file in 3..5) if (board[squareAt(file, rank)] == king) return squareAt(file, rank)
        } else {
            for (rank in 7..9) for (file in 3..5) if (board[squareAt(file, rank)] == king) return squareAt(file, rank)
        }
        for (sq in 0 until SIZE) if (board[sq] == king) return sq
        return -1
    }

    // ------------------------------------------------------------------ 走法生成

    private val ORTHO = intArrayOf(1, 0, -1, 0, 0, 1, 0, -1)
    private val DIAG = intArrayOf(1, 1, 1, -1, -1, 1, -1, -1)
    private val KNIGHT_D = intArrayOf(1, 2, -1, 2, 1, -2, -1, -2, 2, 1, 2, -1, -2, 1, -2, -1)

    /** 伪合法着法（不检查自将），写入 out，返回数量。capturesOnly 只生成吃子着法（静态搜索用）。 */
    fun pseudoMoves(board: IntArray, color: Int, out: IntArray, capturesOnly: Boolean = false): Int {
        var n = 0
        fun add(from: Int, to: Int) {
            val target = board[to]
            if (target != 0 && colorOf(target) == color) return
            if (capturesOnly && target == 0) return
            out[n++] = encode(from, to)
        }
        for (from in 0 until SIZE) {
            val piece = board[from]
            if (piece == 0 || colorOf(piece) != color) continue
            val f = fileOf(from)
            val r = rankOf(from)
            when (typeOf(piece)) {
                KING -> {
                    var i = 0
                    while (i < 8) {
                        val nf = f + ORTHO[i]
                        val nr = r + ORTHO[i + 1]
                        if (isPalace(color, nf, nr)) add(from, squareAt(nf, nr))
                        i += 2
                    }
                }
                ADVISOR -> {
                    var i = 0
                    while (i < 8) {
                        val nf = f + DIAG[i]
                        val nr = r + DIAG[i + 1]
                        if (isPalace(color, nf, nr)) add(from, squareAt(nf, nr))
                        i += 2
                    }
                }
                BISHOP -> {
                    var i = 0
                    while (i < 8) {
                        val nf = f + DIAG[i] * 2
                        val nr = r + DIAG[i + 1] * 2
                        i += 2
                        if (!inBoard(nf, nr)) continue
                        if (if (color == RED) nr > 4 else nr < 5) continue
                        if (board[squareAt(f + (nf - f) / 2, r + (nr - r) / 2)] != 0) continue
                        add(from, squareAt(nf, nr))
                    }
                }
                KNIGHT -> {
                    var i = 0
                    while (i < 16) {
                        val df = KNIGHT_D[i]
                        val dr = KNIGHT_D[i + 1]
                        i += 2
                        val nf = f + df
                        val nr = r + dr
                        if (!inBoard(nf, nr)) continue
                        val legF = f + if (kotlin.math.abs(df) == 2) df / 2 else 0
                        val legR = r + if (kotlin.math.abs(dr) == 2) dr / 2 else 0
                        if (board[squareAt(legF, legR)] != 0) continue
                        add(from, squareAt(nf, nr))
                    }
                }
                ROOK, CANNON -> {
                    val cannon = typeOf(piece) == CANNON
                    var i = 0
                    while (i < 8) {
                        val df = ORTHO[i]
                        val dr = ORTHO[i + 1]
                        i += 2
                        var nf = f + df
                        var nr = r + dr
                        var screens = 0
                        while (inBoard(nf, nr)) {
                            val to = squareAt(nf, nr)
                            val target = board[to]
                            if (target == 0) {
                                if ((!cannon || screens == 0) && !capturesOnly) out[n++] = encode(from, to)
                            } else if (!cannon) {
                                if (colorOf(target) != color) out[n++] = encode(from, to)
                                break
                            } else {
                                screens++
                                if (screens == 2) {
                                    if (colorOf(target) != color) out[n++] = encode(from, to)
                                    break
                                }
                            }
                            nf += df
                            nr += dr
                        }
                    }
                }
                PAWN -> {
                    val fr = r + color
                    if (inBoard(f, fr)) add(from, squareAt(f, fr))
                    val crossed = if (color == RED) r >= 5 else r <= 4
                    if (crossed) {
                        if (f > 0) add(from, squareAt(f - 1, r))
                        if (f < 8) add(from, squareAt(f + 1, r))
                    }
                }
            }
        }
        return n
    }

    /** 某个格子是否被 byColor 方攻击（不含将帅照面）。 */
    fun attacked(board: IntArray, target: Int, byColor: Int): Boolean {
        val tf = fileOf(target)
        val tr = rankOf(target)
        // 车、炮、将（直线）
        var i = 0
        while (i < 8) {
            val df = ORTHO[i]
            val dr = ORTHO[i + 1]
            i += 2
            var nf = tf + df
            var nr = tr + dr
            var screens = 0
            while (inBoard(nf, nr)) {
                val p = board[squareAt(nf, nr)]
                if (p != 0) {
                    if (screens == 0) {
                        if (colorOf(p) == byColor && typeOf(p) == ROOK) return true
                        if (colorOf(p) == byColor && typeOf(p) == KING && (kotlin.math.abs(nf - tf) + kotlin.math.abs(nr - tr)) == 1) return true
                        screens = 1
                    } else {
                        if (colorOf(p) == byColor && typeOf(p) == CANNON) return true
                        break
                    }
                }
                nf += df
                nr += dr
            }
        }
        // 马：从目标反推马的位置，马腿在马旁边
        i = 0
        while (i < 16) {
            val df = KNIGHT_D[i]
            val dr = KNIGHT_D[i + 1]
            i += 2
            val nf = tf + df
            val nr = tr + dr
            if (!inBoard(nf, nr)) continue
            val p = board[squareAt(nf, nr)]
            if (p == 0 || colorOf(p) != byColor || typeOf(p) != KNIGHT) continue
            val legF = nf - if (kotlin.math.abs(df) == 2) df / 2 else 0
            val legR = nr - if (kotlin.math.abs(dr) == 2) dr / 2 else 0
            if (board[squareAt(legF, legR)] == 0) return true
        }
        // 兵
        val pawn = PAWN * byColor
        val behind = tr - byColor
        if (inBoard(tf, behind) && board[squareAt(tf, behind)] == pawn) return true
        val sideCrossed = if (byColor == RED) tr >= 5 else tr <= 4
        if (sideCrossed) {
            if (tf > 0 && board[squareAt(tf - 1, tr)] == pawn) return true
            if (tf < 8 && board[squareAt(tf + 1, tr)] == pawn) return true
        }
        // 仕、相不可能攻击到对方将帅（都在自己半场），这里与规则核一致仍做检查
        i = 0
        while (i < 8) {
            val nf = tf + DIAG[i]
            val nr = tr + DIAG[i + 1]
            i += 2
            if (!inBoard(nf, nr)) continue
            val p = board[squareAt(nf, nr)]
            if (p == ADVISOR * byColor && isPalace(byColor, tf, tr)) return true
        }
        return false
    }

    fun kingsFacing(board: IntArray): Boolean {
        val rk = kingSquare(board, RED)
        val bk = kingSquare(board, BLACK)
        if (rk < 0 || bk < 0 || fileOf(rk) != fileOf(bk)) return false
        var sq = rk + FILES
        while (sq < bk) {
            if (board[sq] != 0) return false
            sq += FILES
        }
        return true
    }

    fun inCheck(board: IntArray, color: Int): Boolean {
        val king = kingSquare(board, color)
        if (king < 0) return true
        if (kingsFacing(board)) return true
        return attacked(board, king, -color)
    }

    fun legalMoves(position: Position): List<Move> {
        val buf = IntArray(128)
        val n = pseudoMoves(position.board, position.turn, buf)
        val board = position.board.copyOf()
        val result = ArrayList<Move>(n)
        for (i in 0 until n) {
            val from = moveFrom(buf[i])
            val to = moveTo(buf[i])
            val captured = board[to]
            if (typeOf(captured) == KING) continue
            val piece = board[from]
            board[to] = piece
            board[from] = 0
            if (!inCheck(board, position.turn)) result.add(Move(from, to, piece, captured))
            board[from] = piece
            board[to] = captured
        }
        return result
    }

    fun findLegal(position: Position, from: Int, to: Int): Move? =
        legalMoves(position).firstOrNull { it.from == from && it.to == to }

    fun play(position: Position, move: Move): Position {
        val board = position.board.copyOf()
        board[move.to] = board[move.from]
        board[move.from] = 0
        val next = -position.turn
        return Position(
            board,
            next,
            if (move.captured != 0) 0 else position.halfmove + 1,
            position.fullmove + if (next == RED) 1 else 0,
        )
    }

    private fun cnNumber(color: Int, value: Int): String =
        if (value !in 1..9) value.toString() else if (color == RED) CN[value - 1] else value.toString()

    private fun fileNumber(color: Int, file: Int) = if (color == RED) 9 - file else file + 1

    /** 中文记谱（炮二平五 / 马8进7）；同列三子以上时退回 UCCI。 */
    fun notation(position: Position, move: Move): String {
        val piece = move.piece
        val color = colorOf(piece)
        val ff = fileOf(move.from)
        val fr = rankOf(move.from)
        val tf = fileOf(move.to)
        val tr = rankOf(move.to)
        val name = pieceName(piece)
        val sameFile = (0 until SIZE).filter { position.board[it] == piece && fileOf(it) == ff }
            .sortedWith { a, b -> if (color == RED) rankOf(b) - rankOf(a) else rankOf(a) - rankOf(b) }
        var prefix = ""
        if (sameFile.size > 2) return ucci(move.from, move.to)
        if (sameFile.size == 2) prefix = if (sameFile.indexOf(move.from) == 0) "前" else "后"
        val origin = cnNumber(color, fileNumber(color, ff))
        val dest = cnNumber(color, fileNumber(color, tf))
        val leading = if (prefix.isNotEmpty()) "$prefix$name" else "$name$origin"
        if (fr == tr) return "${leading}平$dest"
        val advancing = if (color == RED) tr > fr else tr < fr
        val dir = if (advancing) "进" else "退"
        val type = typeOf(piece)
        val amount = if (type == KNIGHT || type == ADVISOR || type == BISHOP) dest
        else cnNumber(color, kotlin.math.abs(tr - fr))
        return "$leading$dir$amount"
    }

    fun hash(position: Position): String {
        val sb = StringBuilder(92)
        sb.append(if (position.turn == RED) 'r' else 'b')
        for (p in position.board) sb.append((p + 'h'.code).toChar())
        return sb.toString()
    }

    /** 局面结果：将死 / 困毙 / 和棋 / 继续。 */
    fun outcome(position: Position, history: List<String> = emptyList()): Outcome {
        val legal = legalMoves(position)
        if (legal.isEmpty()) {
            return if (inCheck(position.board, position.turn)) Outcome.Win(-position.turn, WinReason.CHECKMATE)
            else Outcome.Win(-position.turn, WinReason.STALEMATE)
        }
        var pieces = 0
        var nonKings = 0
        for (p in position.board) if (p != 0) {
            pieces++
            if (typeOf(p) != KING) nonKings++
        }
        if (nonKings == 0) return Outcome.Draw("只剩将帅，和棋")
        if (position.halfmove >= 120) return Outcome.Draw("六十回合未吃子，和棋")
        if (history.isNotEmpty()) {
            val h = hash(position)
            if (history.count { it == h } >= 3) return Outcome.Draw("三次重复局面，和棋")
        }
        return Outcome.Playing
    }
}

class Position(
    val board: IntArray,
    val turn: Int,
    val halfmove: Int = 0,
    val fullmove: Int = 1,
) {
    fun copy(): Position = Position(board.copyOf(), turn, halfmove, fullmove)
    operator fun get(square: Int) = board[square]
}

data class Move(val from: Int, val to: Int, val piece: Int, val captured: Int) {
    val ucci: String get() = Xq.ucci(from, to)
}

enum class WinReason { CHECKMATE, STALEMATE, RESIGN, TIMEOUT }

sealed class Outcome {
    data object Playing : Outcome()
    data class Win(val winner: Int, val reason: WinReason) : Outcome()
    data class Draw(val reason: String) : Outcome()
}
