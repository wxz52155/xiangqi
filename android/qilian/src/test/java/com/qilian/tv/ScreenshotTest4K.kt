package com.qilian.tv

import app.cash.paparazzi.DeviceConfig
import com.android.resources.Density
import com.android.resources.ScreenOrientation

/** 4K 电视（3840×2160，xxxhdpi）：同样 960×540dp 的布局，以 4 倍像素渲染。 */
class ScreenshotTest4K : ScreenshotTest() {
    override val device = DeviceConfig.PIXEL_5.copy(
        screenWidth = 3840,
        screenHeight = 2160,
        xdpi = 640,
        ydpi = 640,
        density = Density.XXXHIGH,
        orientation = ScreenOrientation.LANDSCAPE,
        softButtons = false,
    )
}
