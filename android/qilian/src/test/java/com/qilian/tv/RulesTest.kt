package com.qilian.tv

import com.qilian.tv.xiangqi.LocalEngine
import com.qilian.tv.xiangqi.Outcome
import com.qilian.tv.xiangqi.Position
import com.qilian.tv.xiangqi.Xq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private fun perft(p: Position, depth: Int): Long {
        if (depth == 0) return 1
        var n = 0L
        for (m in Xq.legalMoves(p)) n += perft(Xq.play(p, m), depth - 1)
        return n
    }

    @Test
    fun perftFromStart() {
        val start = Xq.initial()
        assertEquals(44L, perft(start, 1))
        assertEquals(1920L, perft(start, 2))
        assertEquals(79666L, perft(start, 3))
    }

    @Test
    fun fenRoundTrip() {
        assertEquals(Xq.START_FEN, Xq.toFen(Xq.initial()))
    }

    @Test
    fun notation() {
        val p = Xq.initial()
        val (f, t) = Xq.parseUcci("h2e2")!!
        val m = Xq.findLegal(p, f, t)!!
        assertEquals("炮二平五", Xq.notation(p, m))
        val p2 = Xq.play(p, m)
        val (f2, t2) = Xq.parseUcci("h9g7")!!
        assertEquals("马8进7", Xq.notation(p2, Xq.findLegal(p2, f2, t2)!!))
    }

    @Test
    fun mateInOneDetected() {
        // 题库第一题：前车平六 一步将死
        val p = Xq.parseFen("9/3k5/8b/4R4/4R4/9/9/9/4K4/9 w - - 0 1")
        val (f, t) = Xq.parseUcci("e5d5")!!
        val after = Xq.play(p, Xq.findLegal(p, f, t)!!)
        val o = Xq.outcome(after)
        assertTrue(o is Outcome.Win && o.winner == Xq.RED)
    }

    @Test
    fun engineFindsMate() {
        val p = Xq.parseFen("9/3k5/8b/4R4/4R4/9/9/9/4K4/9 w - - 0 1")
        val r = LocalEngine().search(p, 2, timeLimitMs = 2000)
        assertNotNull(r.move)
        val after = Xq.play(p, r.move!!)
        assertTrue(Xq.outcome(after) is Outcome.Win)
    }

    @Test
    fun engineReturnsLegalMoveFromStart() {
        val r = LocalEngine().search(Xq.initial(), 2, timeLimitMs = 1500)
        assertNotNull(r.move)
        assertTrue(Xq.legalMoves(Xq.initial()).contains(r.move))
    }
}
