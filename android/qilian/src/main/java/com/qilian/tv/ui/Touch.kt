package com.qilian.tv.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 触屏点击（手机 / 平板）。
 * androidx.tv.material3 的 Surface、NavigationDrawerItem 只响应遥控器确定键，不处理触摸；
 * 在它们外面加上这个修饰符，点一下就等同于按 OK。
 */
fun Modifier.touchClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val current by rememberUpdatedState(onClick)
    val isEnabled by rememberUpdatedState(enabled)
    pointerInput(Unit) {
        detectTapGestures(onTap = { if (isEnabled) current() })
    }
}
