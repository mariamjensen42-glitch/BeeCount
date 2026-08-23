package com.cycling.beecount.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

// 字体体系（见 docs/adr/0005、0019）：终端黑客风使用等宽字体。
// 数字、符号、拉丁以 System Monospace 呈现，中文字形回退系统默认字体，
// 获得控制台/IDE 的「程序员专属」视觉；不再打包展示字体，体积为零。
// 标题与正文只靠 M3 默认字号/字重区分，不再引入第二套展示字体。
val TerminalMonospace = FontFamily.Monospace

// M3 默认字号/字重保持不变，只替换字体族
private val m3 = Typography()

private fun TextStyle.withFamily(family: FontFamily) = copy(fontFamily = family)

val Typography = Typography(
    displayLarge = m3.displayLarge.withFamily(TerminalMonospace),
    displayMedium = m3.displayMedium.withFamily(TerminalMonospace),
    displaySmall = m3.displaySmall.withFamily(TerminalMonospace),
    headlineLarge = m3.headlineLarge.withFamily(TerminalMonospace),
    headlineMedium = m3.headlineMedium.withFamily(TerminalMonospace),
    headlineSmall = m3.headlineSmall.withFamily(TerminalMonospace),
    titleLarge = m3.titleLarge.withFamily(TerminalMonospace),
    titleMedium = m3.titleMedium.withFamily(TerminalMonospace),
    titleSmall = m3.titleSmall.withFamily(TerminalMonospace),
    bodyLarge = m3.bodyLarge.withFamily(TerminalMonospace),
    bodyMedium = m3.bodyMedium.withFamily(TerminalMonospace),
    bodySmall = m3.bodySmall.withFamily(TerminalMonospace),
    labelLarge = m3.labelLarge.withFamily(TerminalMonospace),
    labelMedium = m3.labelMedium.withFamily(TerminalMonospace),
    labelSmall = m3.labelSmall.withFamily(TerminalMonospace),
)
