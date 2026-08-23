package com.cycling.beecount.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 应用统一形状：终端黑客风采用锐利硬边，比 Material3 默认更「有棱角」，
 * 与深黑底、荧光青绿的命令行观感一致。
 *
 * - extraSmall: 按钮、文本输入框等小型控件
 * - small: 列表项、小卡片
 * - medium: 卡片、对话框
 * - large: 底部抽屉、大卡片
 * - extraLarge: 底部横幅等
 */
val BeeCountShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(16.dp),
)
