package com.cycling.beecount.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import com.cycling.beecount.R

// 字体体系（见 docs/adr/0005）：
// 标题 → 得意黑（Smiley Sans，OFL 斜体展示字，仅用于 headline/titleLarge 品牌标题）
// 正文 → 霞鹜文楷 Lite（OFL 常用字子集，生僻字回退系统字体）
val SmileySans = FontFamily(Font(R.font.smiley_sans_oblique))
val LXGWWenKaiLite = FontFamily(Font(R.font.lxgw_wenkai_lite_regular))

// M3 默认字号/字重保持不变，只替换字体族
private val m3 = Typography()

private fun TextStyle.withFamily(family: FontFamily) = copy(fontFamily = family)

val Typography = Typography(
    displayLarge = m3.displayLarge.withFamily(SmileySans),
    displayMedium = m3.displayMedium.withFamily(SmileySans),
    displaySmall = m3.displaySmall.withFamily(SmileySans),
    headlineLarge = m3.headlineLarge.withFamily(SmileySans),
    headlineMedium = m3.headlineMedium.withFamily(SmileySans),
    headlineSmall = m3.headlineSmall.withFamily(SmileySans),
    titleLarge = m3.titleLarge.withFamily(SmileySans),
    titleMedium = m3.titleMedium.withFamily(LXGWWenKaiLite),
    titleSmall = m3.titleSmall.withFamily(LXGWWenKaiLite),
    bodyLarge = m3.bodyLarge.withFamily(LXGWWenKaiLite),
    bodyMedium = m3.bodyMedium.withFamily(LXGWWenKaiLite),
    bodySmall = m3.bodySmall.withFamily(LXGWWenKaiLite),
    labelLarge = m3.labelLarge.withFamily(LXGWWenKaiLite),
    labelMedium = m3.labelMedium.withFamily(LXGWWenKaiLite),
    labelSmall = m3.labelSmall.withFamily(LXGWWenKaiLite),
)
