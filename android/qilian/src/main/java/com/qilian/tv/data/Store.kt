package com.qilian.tv.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Settings(
    /** 1 执红 / -1 执黑 */
    val userColor: Int = 1,
    /** 1 入门 2 进阶 3 挑战 */
    val level: Int = 2,
    val sound: Boolean = true,
    val allowUndo: Boolean = true,
    val showLegalMoves: Boolean = true,
    val onlineAi: Boolean = true,
)

/** 一局已结束（或进行中）的象棋对局。 */
data class GameRecord(
    val id: String,
    val startFen: String,
    val moves: List<String>,
    val notations: List<String>,
    /** "ai" / "human" */
    val mode: String,
    val userColor: Int,
    val level: Int,
    /** "win" / "loss" / "draw" / "red" / "black" / "" */
    val result: String,
    val resultText: String,
    val startedAt: Long,
    val endedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("startFen", startFen)
        put("moves", JSONArray(moves))
        put("notations", JSONArray(notations))
        put("mode", mode)
        put("userColor", userColor)
        put("level", level)
        put("result", result)
        put("resultText", resultText)
        put("startedAt", startedAt)
        put("endedAt", endedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): GameRecord {
            fun list(key: String): List<String> {
                val a = o.optJSONArray(key) ?: return emptyList()
                return List(a.length()) { a.optString(it) }
            }
            return GameRecord(
                id = o.optString("id"),
                startFen = o.optString("startFen"),
                moves = list("moves"),
                notations = list("notations"),
                mode = o.optString("mode", "ai"),
                userColor = o.optInt("userColor", 1),
                level = o.optInt("level", 2),
                result = o.optString("result"),
                resultText = o.optString("resultText"),
                startedAt = o.optLong("startedAt"),
                endedAt = o.optLong("endedAt"),
            )
        }
    }
}

/** 本机存储：设置、训练进度、收藏、棋谱、未下完的对局。没有账号，不上传。 */
class Store private constructor(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("qilian", Context.MODE_PRIVATE)
    private val dir: File = runCatching { context.filesDir }.getOrNull() ?: File(System.getProperty("java.io.tmpdir") ?: "/tmp")
    private val historyFile = File(dir, "history.json")
    private val currentFile = File(dir, "current-game.json")

    var settings by mutableStateOf(loadSettings())
        private set

    /** 改动计数，用来驱动界面刷新（进度、收藏、棋谱）。 */
    var revision by mutableStateOf(0)
        private set

    private fun loadSettings() = Settings(
        userColor = prefs.getInt("userColor", 1),
        level = prefs.getInt("level", 2),
        sound = prefs.getBoolean("sound", true),
        allowUndo = prefs.getBoolean("allowUndo", true),
        showLegalMoves = prefs.getBoolean("showLegalMoves", true),
        onlineAi = prefs.getBoolean("onlineAi", true),
    )

    fun updateSettings(next: Settings) {
        settings = next
        prefs.edit()
            .putInt("userColor", next.userColor)
            .putInt("level", next.level)
            .putBoolean("sound", next.sound)
            .putBoolean("allowUndo", next.allowUndo)
            .putBoolean("showLegalMoves", next.showLegalMoves)
            .putBoolean("onlineAi", next.onlineAi)
            .apply()
    }

    // ---------------------------------------------------------------- 训练进度 / 收藏

    fun completed(kind: TrainingKind): Set<String> = prefs.getStringSet("done:${kind.id}", emptySet()) ?: emptySet()

    fun markCompleted(kind: TrainingKind, id: String) {
        val set = completed(kind).toMutableSet()
        if (set.add(id)) {
            prefs.edit().putStringSet("done:${kind.id}", set).apply()
            revision++
        }
    }

    fun favorites(kind: TrainingKind): Set<String> = prefs.getStringSet("fav:${kind.id}", emptySet()) ?: emptySet()

    fun toggleFavorite(kind: TrainingKind, id: String) {
        val set = favorites(kind).toMutableSet()
        if (!set.remove(id)) set.add(id)
        prefs.edit().putStringSet("fav:${kind.id}", set).apply()
        revision++
    }

    fun lastTraining(kind: TrainingKind): String? = prefs.getString("last:${kind.id}", null)

    fun setLastTraining(kind: TrainingKind, id: String) {
        prefs.edit().putString("last:${kind.id}", id).apply()
    }

    // ---------------------------------------------------------------- 棋谱

    fun history(): List<GameRecord> = runCatching {
        if (!historyFile.exists()) return emptyList()
        val array = JSONArray(historyFile.readText())
        List(array.length()) { GameRecord.fromJson(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun addHistory(record: GameRecord) {
        val list = listOf(record) + history().filter { it.id != record.id }
        runCatching { historyFile.writeText(JSONArray(list.take(200).map { it.toJson() }).toString()) }
        revision++
    }

    fun deleteHistory(id: String) {
        val list = history().filter { it.id != id }
        runCatching { historyFile.writeText(JSONArray(list.map { it.toJson() }).toString()) }
        revision++
    }

    // ---------------------------------------------------------------- 未下完的对局

    fun currentGame(): GameRecord? = runCatching {
        if (!currentFile.exists()) null else GameRecord.fromJson(JSONObject(currentFile.readText()))
    }.getOrNull()

    fun saveCurrentGame(record: GameRecord?) {
        runCatching { if (record == null) currentFile.delete() else currentFile.writeText(record.toJson().toString()) }
        revision++
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        historyFile.delete()
        currentFile.delete()
        settings = Settings()
        revision++
    }

    companion object {
        @Volatile
        private var instance: Store? = null

        fun get(context: Context): Store = instance ?: synchronized(this) {
            instance ?: Store(context.applicationContext).also { instance = it }
        }
    }
}
