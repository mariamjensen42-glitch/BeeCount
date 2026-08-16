package com.cycling.beecount.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.cycling.beecount.R

// 字体体系（见 docs/adr/0005）：全文统一霞鹜文楷 Lite（OFL 常用字子集，生僻字回退系统字体）。
// 标题与正文只靠 M3 默认字号/字重区分，不再引入第二套展示字体。
val LXGWWenKaiLite = FontFamily(Font(R.font.lxgw_wenkai_lite_regular))

// M3 默认字号/字重保持不变，只替换字体族
private val m3 = Typography()

private fun TextStyle.withFamily(family: FontFamily) = copy(fontFamily = family)

val Typography = Typography(
    displayLarge = m3.displayLarge.withFamily(LXGWWenKaiLite),
    displayMedium = m3.displayMedium.withFamily(LXGWWenKaiLite),
    displaySmall = m3.displaySmall.withFamily(LXGWWenKaiLite),
    headlineLarge = m3.headlineLarge.withFamily(LXGWWenKaiLite),
    headlineMedium = m3.headlineMedium.withFamily(LXGWWenKaiLite),
    headlineSmall = m3.headlineSmall.withFamily(LXGWWenKaiLite),
    titleLarge = m3.titleLarge.withFamily(LXGWWenKaiLite),
    titleMedium = m3.titleMedium.withFamily(LXGWWenKaiLite),
    titleSmall = m3.titleSmall.withFamily(LXGWWenKaiLite),
    bodyLarge = m3.bodyLarge.withFamily(LXGWWenKaiLite),
    bodyMedium = m3.bodyMedium.withFamily(LXGWWenKaiLite),
    bodySmall = m3.bodySmall.withFamily(LXGWWenKaiLite),
    labelLarge = m3.labelLarge.withFamily(LXGWWenKaiLite),
    labelMedium = m3.labelMedium.withFamily(LXGWWenKaiLite),
    labelSmall = m3.labelSmall.withFamily(LXGWWenKaiLite),
)
