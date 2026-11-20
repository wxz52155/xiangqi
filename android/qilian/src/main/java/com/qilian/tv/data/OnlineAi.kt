package com.qilian.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * 在线 AI（auto-chess 服务，象棋为 Pikafish + NNUE）。与网页版使用同一个接口：
 * POST /move/stream，SSE 返回逐层进度与最终着法。
 */
object OnlineAi {
    private const val API_BASE = "https://home.usercao.com/auto-chess/api/v1"

    data class Progress(val depth: Int, val nodes: Long)

    sealed class AiMove {
        data class Ucci(val ucci: String) : AiMove()
        data class Place(val index: Int) : AiMove()
        data class Step(val from: Int, val to: Int) : AiMove()
    }

    fun difficulty(level: Int) = when (level) {
        1 -> "easy"
        2 -> "hard"
        else -> "master"
    }

    suspend fun health(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("$API_BASE/health").openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
            }
            try {
                conn.responseCode == 200 && JSONObject(conn.inputStream.bufferedReader().readText()).optBoolean("ok")
            } finally {
                conn.disconnect()
            }
        }.getOrDefault(false)
    }

    /**
     * @param game xiangqi / gomoku / connect4 / jungle
     * @param fen 象棋局面
     * @param board 网格棋局面（自上而下、自左而右）
     */
    suspend fun move(
        game: String,
        level: Int,
        fen: String? = null,
        board: List<String>? = null,
        turn: String? = null,
        timeLimitMs: Long = 8000,
        onProgress: (Progress) -> Unit = {},
    ): AiMove = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("game", game)
            if (fen != null) put("fen", fen)
            if (board != null) put("board", JSONArray(board))
            if (turn != null) put("turn", turn)
            put("difficulty", difficulty(level))
            put("timeLimitMs", timeLimitMs)
        }.toString().toByteArray(Charsets.UTF_8)

        val conn = (URL("$API_BASE/move/stream").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 6000
            readTimeout = (timeLimitMs + 6000).toInt()
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            setFixedLengthStreamingMode(body.size)
        }
        try {
            conn.outputStream.use { it.write(body) }
            if (conn.responseCode != 200) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
                val message = runCatching { JSONObject(err ?: "").optJSONObject("error")?.optString("message") }.getOrNull()
                throw IllegalStateException(message ?: "AI 服务返回 HTTP ${conn.responseCode}")
            }
            val reader = conn.inputStream.bufferedReader(Charsets.UTF_8)
            var event = "message"
            val data = StringBuilder()
            while (true) {
                coroutineContext.ensureActive()
                val line = reader.readLine() ?: break
                when {
                    line.startsWith("event:") -> event = line.substring(6).trim()
                    line.startsWith("data:") -> {
                        if (data.isNotEmpty()) data.append('\n')
                        data.append(line.substring(5).trim())
                    }
                    line.isEmpty() -> {
                        if (data.isNotEmpty()) {
                            val json = JSONObject(data.toString())
                            when (event) {
                                "depth" -> onProgress(Progress(json.optInt("depth"), json.optLong("nodes")))
                                "result" -> return@withContext parseMove(json)
                                "error" -> throw IllegalStateException(
                                    json.optJSONObject("error")?.optString("message") ?: "AI 走子失败",
                                )
                                "done" -> if (!json.optBoolean("ok", true)) throw IllegalStateException("AI 思考未完成")
                            }
                        }
                        event = "message"
                        data.setLength(0)
                    }
                }
            }
            throw IllegalStateException("AI 未返回着法")
        } finally {
            conn.disconnect()
        }
    }

    private fun parseMove(result: JSONObject): AiMove {
        val move = result.optJSONObject("move") ?: throw IllegalStateException("AI 返回为空")
        move.optString("uci").takeIf { it.isNotBlank() }?.let { return AiMove.Ucci(it) }
        move.optJSONObject("index")?.let { return AiMove.Place(it.optInt("index")) }
        val from = move.optJSONObject("from")
        val to = move.optJSONObject("to")
        if (from != null && to != null) return AiMove.Step(from.optInt("index"), to.optInt("index"))
        throw IllegalStateException("AI 返回的着法无法识别")
    }
}
