package com.cycling.beecount.widget

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.cycling.beecount.BeeCountApplication
import com.cycling.beecount.MainActivity
import com.cycling.beecount.ui.assistant.formatMoney
import com.cycling.beecount.ui.theme.ExpenseRed
import com.cycling.beecount.ui.theme.IncomeGreen
import com.cycling.beecount.ui.theme.OnTerminalGrey
import com.cycling.beecount.ui.theme.SurfaceContainer
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 收支速览桌面小组件（ADR 0013）。
 *
 * 小（2×1）：一行两列——今日支出 / 今日收入；
 * 中（4×2）：四宫格——今日支出 / 今日收入 / 本月支出 / 本月收入。
 * 深色卡片沿用 App 视觉身份（暗色独占、支出红/收入绿；Glance 1.1 不支持打包字体，
 * 文本用系统默认字体），点击打开 App。数据直接查 Application 持有的 Room 实例
 * （receiver 由系统实例化，不走 Hilt 图）。圆角由启动器自动裁剪。
 */
class OverviewWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as BeeCountApplication
        val today = LocalDate.now()
        val stats = withContext(Dispatchers.IO) {
            val dao = app.database.entryDao()
            val todayTotals = dao.totalsBetween(today, today)
            val monthTotals = dao.totalsBetween(
                today.withDayOfMonth(1),
                today.withDayOfMonth(today.lengthOfMonth()),
            )
            OverviewStats(
                todayExpense = todayTotals.expense,
                todayIncome = todayTotals.income,
                monthExpense = monthTotals.expense,
                monthIncome = monthTotals.income,
            )
        }
        provideContent {
            OverviewWidgetContent(stats)
        }
    }
}

@Composable
private fun OverviewWidgetContent(stats: OverviewStats) {
    val size = LocalSize.current
    // 4×2 中尺寸：宽高都够才用四宫格，避免 4×1 拉宽后把两行布局挤进一行槽位
    val isMedium = size.width >= MEDIUM_WIDTH_THRESHOLD && size.height >= MEDIUM_HEIGHT_THRESHOLD
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetCardBackground)
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center,
    ) {
        if (isMedium) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                    StatCell("今日支出", stats.todayExpense, ExpenseRed, GlanceModifier.defaultWeight())
                    StatCell("今日收入", stats.todayIncome, IncomeGreen, GlanceModifier.defaultWeight())
                }
                Row(modifier = GlanceModifier.defaultWeight().fillMaxWidth()) {
                    StatCell("本月支出", stats.monthExpense, ExpenseRed, GlanceModifier.defaultWeight())
                    StatCell("本月收入", stats.monthIncome, IncomeGreen, GlanceModifier.defaultWeight())
                }
            }
        } else {
            Row(modifier = GlanceModifier.fillMaxSize()) {
                StatCell("今日支出", stats.todayExpense, ExpenseRed, GlanceModifier.defaultWeight())
                StatCell("今日收入", stats.todayIncome, IncomeGreen, GlanceModifier.defaultWeight())
            }
        }
    }
}

@SuppressLint("RestrictedApi")
@Composable
private fun StatCell(
    label: String,
    amount: Double,
    amountColor: Color,
    modifier: GlanceModifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(modifier = GlanceModifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = TextStyle(color = ColorProvider(WidgetLabelColor), fontSize = 11.sp),
            )
            Text(
                text = "¥${formatMoney(amount)}",
                style = TextStyle(color = ColorProvider(amountColor), fontSize = 16.sp),
            )
        }
    }
}

/** 深色卡片底：与 App 内 M3 表面容器同色（ui.theme.SurfaceContainer） */
private val WidgetCardBackground = SurfaceContainer

/** 标签用次级文字色（ui.theme.OnTerminalGrey） */
private val WidgetLabelColor = OnTerminalGrey

/** 中尺寸阈值：宽 ≥ 240dp 且高 ≥ 90dp 视为 4×2 四宫格，否则按 2×1 一行两列 */
private val MEDIUM_WIDTH_THRESHOLD = 240.dp
private val MEDIUM_HEIGHT_THRESHOLD = 90.dp
