package com.cycling.beecount.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

// 暗色独占主题：不跟随系统明暗、不用动态取色（见 docs/adr/0003）
@Composable
fun BeeCountTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = BeeCountDarkColorScheme,
        typography = Typography,
        shapes = BeeCountShapes,
        content = content
    )
}
