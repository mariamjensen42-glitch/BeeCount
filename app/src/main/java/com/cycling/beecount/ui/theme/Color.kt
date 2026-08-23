package com.cycling.beecount.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// ===== 终端黑客暗色板（暗色独占，见 docs/adr/0003、0019） =====
// 组件只消费 M3 语义角色（BeeCountDarkColorScheme）与领域语义色，
// 不直接引用下面的原始色值。

// 主色：荧光青（终端 cyan / IDE 高亮青）
val TerminalCyan = Color(0xFF00E5FF)
val OnTerminalCyan = Color(0xFF002A31)
val TerminalCyanContainer = Color(0xFF003A45)
val OnTerminalCyanContainer = Color(0xFF9CEFFF)

// 次级：终端绿（Matrix 荧光绿）
val TerminalGreen = Color(0xFF00FF41)
val OnTerminalGreen = Color(0xFF00350F)
val TerminalGreenContainer = Color(0xFF013F16)
val OnTerminalGreenContainer = Color(0xFF8BFF9E)

// 三级：电紫（IDE 高亮紫）
val TerminalPurple = Color(0xFFC792EA)
val OnTerminalPurple = Color(0xFF36034D)
val TerminalPurpleContainer = Color(0xFF4C1C63)
val OnTerminalPurpleContainer = Color(0xFFEFD7FA)

// 表面：深黑 / 青白 / 灰绿
val TerminalBlack = Color(0xFF070B0C)
val TerminalWhite = Color(0xFFE6F5F2)
val TerminalGrey = Color(0xFF26332E)
val OnTerminalGrey = Color(0xFFA9C4BD)

// 表面容器层级（Material 3 高度层级用）
val SurfaceContainerLowest = Color(0xFF030607)
val SurfaceContainerLow = Color(0xFF0B1112)
val SurfaceContainer = Color(0xFF101718)
val SurfaceContainerHigh = Color(0xFF161F20)
val SurfaceContainerHighest = Color(0xFF1C2627)
val SurfaceBright = Color(0xFF253031)
val SurfaceDim = Color(0xFF070B0C)

// 反色 / 描边
val InverseSurface = Color(0xFFE6F5F2)
val OnInverseSurface = Color(0xFF1C2627)
val InversePrimary = Color(0xFF006E7E)
val SurfaceTint = Color(0xFF00E5FF)
val Outline = Color(0xFF8A9E9B)
val OutlineVariant = Color(0xFF22302E)
val Scrim = Color(0xFF000000)

// 错误（M3 暗色标准四件套）
val ErrorRed = Color(0xFFFFB4AB)
val OnErrorRed = Color(0xFF690005)
val ErrorRedContainer = Color(0xFF93000A)
val OnErrorRedContainer = Color(0xFFFFDAD6)

// ===== 领域语义色（Domain tokens，与 M3 角色独立，见 ADR 0004、0019） =====
// 支出/收入不借用 error/primary：错误提示与支出金额、品牌强调与收入，视觉上必须能区分。
val ExpenseRed = Color(0xFFFF5C5C)
val IncomeGreen = Color(0xFF2DFF9E)

// ===== M3 暗色语义角色组装 =====
val BeeCountDarkColorScheme = darkColorScheme(
    primary = TerminalCyan,
    onPrimary = OnTerminalCyan,
    primaryContainer = TerminalCyanContainer,
    onPrimaryContainer = OnTerminalCyanContainer,
    secondary = TerminalGreen,
    onSecondary = OnTerminalGreen,
    secondaryContainer = TerminalGreenContainer,
    onSecondaryContainer = OnTerminalGreenContainer,
    tertiary = TerminalPurple,
    onTertiary = OnTerminalPurple,
    tertiaryContainer = TerminalPurpleContainer,
    onTertiaryContainer = OnTerminalPurpleContainer,
    background = TerminalBlack,
    onBackground = TerminalWhite,
    surface = TerminalBlack,
    onSurface = TerminalWhite,
    surfaceVariant = TerminalGrey,
    onSurfaceVariant = OnTerminalGrey,
    surfaceContainerLowest = SurfaceContainerLowest,
    surfaceContainerLow = SurfaceContainerLow,
    surfaceContainer = SurfaceContainer,
    surfaceContainerHigh = SurfaceContainerHigh,
    surfaceContainerHighest = SurfaceContainerHighest,
    surfaceBright = SurfaceBright,
    surfaceDim = SurfaceDim,
    inverseSurface = InverseSurface,
    inverseOnSurface = OnInverseSurface,
    inversePrimary = InversePrimary,
    surfaceTint = SurfaceTint,
    outline = Outline,
    outlineVariant = OutlineVariant,
    scrim = Scrim,
    error = ErrorRed,
    onError = OnErrorRed,
    errorContainer = ErrorRedContainer,
    onErrorContainer = OnErrorRedContainer,
)
