package com.qilian.tv.game

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.qilian.tv.R
import com.qilian.tv.data.Store

/** 落子音效（木质轻响）。设置里关掉音效时静音。 */
class Sounds(context: Context, private val store: Store) {
    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val moveId = pool.load(context, R.raw.piece_move, 1)
    private val selectId = pool.load(context, R.raw.piece_select, 1)
    private val captureId = pool.load(context, R.raw.piece_capture, 1)
    private val checkId = pool.load(context, R.raw.check, 1)
    private val errorId = pool.load(context, R.raw.error, 1)
    private val finishId = pool.load(context, R.raw.finish, 1)

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

    fun release() = pool.release()
}
