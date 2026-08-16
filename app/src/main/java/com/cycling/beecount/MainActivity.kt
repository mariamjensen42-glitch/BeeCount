package com.cycling.beecount

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.cycling.beecount.notification.AutoEntryDeepLink
import com.cycling.beecount.ui.BeeCountApp
import com.cycling.beecount.ui.theme.BeeCountTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * 自动记账确认/失败通知的深链（ADR 0014）：CLEAR_TOP 复用本 Activity 时走 onNewIntent，
     * 用 Compose 状态承载，BeeCountApp 据此导航到助手页（展示待确认草稿 / 预填失败原文）。
     */
    private var deepLink by mutableStateOf<AutoEntryDeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deepLink = AutoEntryDeepLink.from(intent)
        // 暗色独占：系统栏强制浅色图标，避免系统处于亮色模式时图标在深色背景上不可见（见 docs/adr/0003）
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            BeeCountTheme {
                BeeCountApp(
                    deepLink = deepLink,
                    // 深链一次性消费：失败重试的预填由输入框落地后回调，确认深链由导航回调
                    onDeepLinkConsumed = { deepLink = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        deepLink = AutoEntryDeepLink.from(intent)
    }
}
