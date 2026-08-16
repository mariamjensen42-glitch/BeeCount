package com.cycling.beecount.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

// ===== 蜂蜜金暗色板（暗色独占，见 docs/adr/0003、0004） =====
// 组件只消费 M3 语义角色（BeeCountDarkColorScheme）与领域语义色，
// 不直接引用下面的原始色值。

// 主色：蜂蜜金
val HoneyAmber = Color(0xFFFFC963)
val OnHoneyAmber = Color(0xFF3F2D00)
val HoneyAmberContainer = Color(0xFF5C4400)
val OnHoneyAmberContainer = Color(0xFFFFDE9A)

// 次级：暖沙
val WarmSand = Color(0xFFD5CBB8)
val OnWarmSand = Color(0xFF39352A)
val WarmSandContainer = Color(0xFF514D3F)
val OnWarmSandContainer = Color(0xFFF2EBD9)

// 三级：蜂蜜褐
val HoneyBrown = Color(0xFFC9B18C)
val OnHoneyBrown = Color(0xFF30200A)
val HoneyBrownContainer = Color(0xFF473612)
val OnHoneyBrownContainer = Color(0xFFE7CDA5)

// 表面：暖黑 / 暖白 / 暖灰
val WarmBlack = Color(0xFF17130D)
val WarmWhite = Color(0xFFEAE2D5)
val WarmGrey = Color(0xFF4A463C)
val OnWarmGrey = Color(0xFFCCC5B8)

// 表面容器层级（Material 3 高度层级用）
val SurfaceContainerLowest = Color(0xFF110E09)
val SurfaceContainerLow = Color(0xFF1F1B14)
val SurfaceContainer = Color(0xFF232018)
val SurfaceContainerHigh = Color(0xFF2E2A21)
val SurfaceContainerHighest = Color(0xFF39352C)
val SurfaceBright = Color(0xFF3E382F)
val SurfaceDim = Color(0xFF17130D)

// 反色 / 描边
val InverseSurface = Color(0xFFEAE2D5)
val OnInverseSurface = Color(0xFF2E2A21)
val InversePrimary = Color(0xFF6F5200)
val SurfaceTint = Color(0xFFFFC963)
val Outline = Color(0xFF958F83)
val OutlineVariant = Color(0xFF45403A)
val Scrim = Color(0xFF000000)

// 错误（M3 暗色标准四件套）
val ErrorRed = Color(0xFFFFB4AB)
val OnErrorRed = Color(0xFF690005)
val ErrorRedContainer = Color(0xFF93000A)
val OnErrorRedContainer = Color(0xFFFFDAD6)

// ===== 领域语义色（Domain tokens，与 M3 角色独立，见 ADR 0004） =====
// 支出/收入不借用 error/primary：错误提示与支出金额、品牌强调与收入，视觉上必须能区分。
val ExpenseRed = Color(0xFFFF8A80)
val IncomeGreen = Color(0xFF6EE79A)

// ===== M3 暗色语义角色组装 =====
val BeeCountDarkColorScheme = darkColorScheme(
    primary = HoneyAmber,
    onPrimary = OnHoneyAmber,
    primaryContainer = HoneyAmberContainer,
    onPrimaryContainer = OnHoneyAmberContainer,
    secondary = WarmSand,
    onSecondary = OnWarmSand,
    secondaryContainer = WarmSandContainer,
    onSecondaryContainer = OnWarmSandContainer,
    tertiary = HoneyBrown,
    onTertiary = OnHoneyBrown,
    tertiaryContainer = HoneyBrownContainer,
    onTertiaryContainer = OnHoneyBrownContainer,
    background = WarmBlack,
    onBackground = WarmWhite,
    surface = WarmBlack,
    onSurface = WarmWhite,
    surfaceVariant = WarmGrey,
    onSurfaceVariant = OnWarmGrey,
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
