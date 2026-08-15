package com.cycling.beecount

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cycling.beecount.ui.assistant.AssistantRoute
import com.cycling.beecount.ui.theme.BeeCountTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 暗色独占：系统栏强制浅色图标，避免系统处于亮色模式时图标在深色背景上不可见（见 docs/adr/0003）
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent {
            BeeCountTheme {
                AssistantRoute()
            }
        }
    }
}
