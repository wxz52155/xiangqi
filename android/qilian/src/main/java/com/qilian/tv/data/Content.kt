package com.qilian.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class TrainingKind(val id: String, val label: String, val file: String) {
    TACTICS("tactics", "一步杀法", "tactics.json"),
    ENDGAME("endgame", "残棋定式", "endgames.json"),
    OPENING("opening", "开局训练", "openings.json");

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: TACTICS
    }
}

fun levelLabel(level: Int) = when (level) {
    1 -> "入门"
    2 -> "进阶"
    else -> "挑战"
}

data class Source(val name: String, val author: String, val license: String)

/** 一步杀法 / 残棋定式 / 开局 的公共字段。 */
sealed class TrainingItem {
    abstract val id: String
    abstract val kind: TrainingKind
    abstract val title: String
    abstract val subtitle: String
    abstract val level: Int
    abstract val tags: List<String>
    abstract val source: Source
    abstract val fen: String
}

data class Tactic(
    override val id: String,
    override val title: String,
    override val subtitle: String,
    override val level: Int,
    override val tags: List<String>,
    override val source: Source,
    override val fen: String,
    val solutions: List<List<String>>,
    val hint: String,
    val explanation: String,
) : TrainingItem() {
    override val kind = TrainingKind.TACTICS
}

data class GuidedMove(val ucci: String, val comment: String?)
data class GuidedLine(val id: String, val label: String, val moves: List<GuidedMove>)

data class Lesson(
    override val id: String,
    override val kind: TrainingKind,
    override val title: String,
    override val subtitle: String,
    override val level: Int,
    override val tags: List<String>,
    override val source: Source,
    override val fen: String,
    /** 1 红方 / -1 黑方 */
    val userColor: Int,
    val objective: String,
    val intro: String,
    val keyPoints: List<String>,
    val lines: List<GuidedLine>,
    val completionText: String,
) : TrainingItem()

/** 训练内容（随 APK 打包在 assets/content/，由 pnpm export:tv-content 从网页版题库导出）。 */
object Content {
    private val mutex = Mutex()
    private val cache = HashMap<TrainingKind, List<TrainingItem>>()

    suspend fun items(context: Context, kind: TrainingKind): List<TrainingItem> = mutex.withLock {
        cache[kind] ?: withContext(Dispatchers.IO) {
            val text = runCatching {
                context.assets.open("content/${kind.file}").bufferedReader().use { it.readText() }
            }.getOrDefault("[]")
            parse(kind, text)
        }.also { cache[kind] = it }
    }

    fun cached(kind: TrainingKind): List<TrainingItem>? = cache[kind]

    /** 预置内容（截图测试用）。 */
    fun seed(kind: TrainingKind, items: List<TrainingItem>) {
        cache[kind] = items
    }

    suspend fun find(context: Context, kind: TrainingKind, id: String): TrainingItem? =
        items(context, kind).firstOrNull { it.id == id }

    suspend fun next(context: Context, kind: TrainingKind, id: String): TrainingItem? {
        val list = items(context, kind)
        val index = list.indexOfFirst { it.id == id }
        return if (index >= 0) list.getOrNull(index + 1) else null
    }

    fun parse(kind: TrainingKind, text: String): List<TrainingItem> {
        val array = JSONArray(text)
        val out = ArrayList<TrainingItem>(array.length())
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            runCatching { out.add(if (kind == TrainingKind.TACTICS) tactic(o) else lesson(kind, o)) }
        }
        return out
    }

    private fun strings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return List(array.length()) { array.optString(it) }
    }

    private fun source(o: JSONObject?): Source = Source(
        o?.optString("name").orEmpty(),
        o?.optString("author").orEmpty(),
        o?.optString("license").orEmpty(),
    )

    private fun tactic(o: JSONObject): Tactic {
        val sol = o.optJSONArray("solutions") ?: JSONArray()
        return Tactic(
            id = o.getString("id"),
            title = o.optString("title"),
            subtitle = o.optString("subtitle"),
            level = o.optInt("level", 1),
            tags = strings(o.optJSONArray("tags")),
            source = source(o.optJSONObject("source")),
            fen = o.getString("fen"),
            solutions = List(sol.length()) { strings(sol.getJSONArray(it)) },
            hint = o.optString("hint"),
            explanation = o.optString("explanation"),
        )
    }

    private fun lesson(kind: TrainingKind, o: JSONObject): Lesson {
        val lines = o.optJSONArray("lines") ?: JSONArray()
        return Lesson(
            id = o.getString("id"),
            kind = kind,
            title = o.optString("title"),
            subtitle = o.optString("subtitle"),
            level = o.optInt("level", 1),
            tags = strings(o.optJSONArray("tags")),
            source = source(o.optJSONObject("source")),
            fen = o.getString("fen"),
            userColor = if (o.optString("userColor", "r") == "b") -1 else 1,
            objective = o.optString("objective"),
            intro = o.optString("intro"),
            keyPoints = strings(o.optJSONArray("keyPoints")),
            lines = List(lines.length()) { index ->
                val line = lines.getJSONObject(index)
                val moves = line.optJSONArray("moves") ?: JSONArray()
                GuidedLine(
                    id = line.optString("id"),
                    label = line.optString("label"),
                    moves = List(moves.length()) { m ->
                        val mv = moves.getJSONObject(m)
                        GuidedMove(mv.getString("ucci"), mv.optString("comment").takeIf { it.isNotBlank() })
                    },
                )
            },
            completionText = o.optString("completionText"),
        )
    }
}
