package com.qilian.tv

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.qilian.tv.ui.AppBackground
import com.qilian.tv.ui.QilianTheme
import com.qilian.tv.ui.board.BoardMarks
import com.qilian.tv.ui.board.XiangqiBoard
import com.qilian.tv.xiangqi.Xq
import org.junit.Rule
import org.junit.Test

/** 4K 像素密度（xxxhdpi）下 1:1 渲染棋盘局部，检查清晰度（保存图片不缩放）。 */
class ScreenshotTestDetail {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 1000,
            screenHeight = 1000,
            xdpi = 640,
            ydpi = 640,
            density = Density.XXXHIGH,
            orientation = ScreenOrientation.PORTRAIT,
            softButtons = false,
        ),
        maxPercentDifference = 100.0,
    )

    @Test
    fun board4kPixels() = paparazzi.snapshot {
        QilianTheme {
            AppBackground {
                val p = Xq.initial()
                XiangqiBoard(
                    board = p.board,
                    marks = BoardMarks(selected = Xq.squareAt(7, 2), targets = setOf(Xq.squareAt(4, 2), Xq.squareAt(7, 9))),
                    interactive = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
