package com.qilian.tv

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import com.qilian.tv.ui.QilianApp
import com.qilian.tv.ui.rememberDeps

class MainActivity : ComponentActivity() {
    private var lastBack = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 首页再按返回：两秒内连按两次退出
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val now = SystemClock.elapsedRealtime()
                if (now - lastBack < 2000) {
                    finish()
                } else {
                    lastBack = now
                    Toast.makeText(this@MainActivity, "再按一次返回键退出南坡象棋", Toast.LENGTH_SHORT).show()
                }
            }
        })
        setContent {
            QilianApp(rememberDeps(applicationContext))
        }
    }
}
