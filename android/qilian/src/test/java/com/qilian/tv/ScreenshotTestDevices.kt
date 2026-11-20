package com.qilian.tv

import app.cash.paparazzi.DeviceConfig
import com.android.resources.Density
import com.android.resources.ScreenOrientation

/** 平板横屏（2560×1600）：界面按屏幕等比缩放。 */
class ScreenshotTestTablet : ScreenshotTest() {
    override val device = DeviceConfig.PIXEL_5.copy(
        screenWidth = 2560,
        screenHeight = 1600,
        xdpi = 320,
        ydpi = 320,
        density = Density.XHIGH,
        orientation = ScreenOrientation.LANDSCAPE,
        softButtons = false,
    )
}

/** 手机横屏（2400×1080）。 */
class ScreenshotTestPhone : ScreenshotTest() {
    override val device = DeviceConfig.PIXEL_5.copy(
        screenWidth = 2400,
        screenHeight = 1080,
        xdpi = 400,
        ydpi = 400,
        density = Density.DPI_420,
        orientation = ScreenOrientation.LANDSCAPE,
        softButtons = false,
    )
}
