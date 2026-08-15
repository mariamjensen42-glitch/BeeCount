package com.cycling.beecount.ui.analytics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.beecount.domain.model.AnnualAnalytics
import com.cycling.beecount.domain.model.AnnualHighlights
import com.cycling.beecount.domain.model.CategoryRank
import com.cycling.beecount.domain.model.DailyExpense
import com.cycling.beecount.domain.model.MonthlyAnalytics
import com.cycling.beecount.domain.model.MonthlyExpensePoint
import com.cycling.beecount.ui.assistant.formatMoney
import com.cycling.beecount.ui.theme.ExpenseRed
import com.cycling.beecount.ui.theme.HoneyAmber
import com.cycling.beecount.ui.theme.IncomeGreen
import com.cycling.beecount.ui.theme.OnHoneyAmber
import com.woowla.compose.icon.collections.heroicons.*
import com.woowla.compose.icon.collections.heroicons.heroicons.Outline
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ChevronLeft
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ChevronRight
import java.time.Year
import java.time.YearMonth

@Composable
fun AnalyticsRoute(viewModel: AnalyticsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val monthly by viewModel.monthlyAnalytics.collectAsStateWithLifecycle()
    val annual by viewModel.annualAnalytics.collectAsStateWithLifecycle()
    AnalyticsScreen(
        uiState = uiState,
        monthly = monthly,
        annual = annual,
        onEvent = viewModel::onEvent,
    )
}

/**
 * 图表页（ADR 0009）：单页 + 顶部「月度 / 年度」粒度切换，
 * 两种粒度复用同一组区块（合计卡 → 分类排行 → 趋势），年度模式末尾加高亮数字卡。
 * 图表全部手绘：排行/日柱用纯 Compose 布局，年度趋势折线用 Canvas。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    uiState: AnalyticsUiState,
    monthly: MonthlyAnalytics?,
    annual: AnnualAnalytics?,
    onEvent: (AnalyticsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("图表") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AnalyticsHeader(uiState, onEvent)
            when (uiState.granularity) {
                AnalyticsGranularity.MONTH -> MonthlyContent(monthly, Modifier.weight(1f))
                AnalyticsGranularity.YEAR -> AnnualContent(annual, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AnalyticsHeader(uiState: AnalyticsUiState, onEvent: (AnalyticsEvent) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GranularitySwitch(uiState.granularity, onEvent)
        Spacer(Modifier.height(8.dp))
        when (uiState.granularity) {
            AnalyticsGranularity.MONTH -> PeriodSelector(
                label = "${uiState.selectedMonth.year}年${uiState.selectedMonth.monthValue}月",
                onShift = { delta -> onEvent(AnalyticsEvent.ShiftMonth(delta)) },
                isCurrent = uiState.selectedMonth == YearMonth.now(),
                onGoToCurrent = { onEvent(AnalyticsEvent.GoToCurrentMonth) },
                goLabel = "回本月",
            )

            AnalyticsGranularity.YEAR -> PeriodSelector(
                label = "${uiState.selectedYear}年",
                onShift = { delta -> onEvent(AnalyticsEvent.ShiftYear(delta)) },
                isCurrent = uiState.selectedYear == Year.now().value,
                onGoToCurrent = { onEvent(AnalyticsEvent.GoToCurrentYear) },
                goLabel = "回今年",
            )
        }
    }
}

/** 粒度切换：与悬浮胶囊同风格的蜂蜜金 pill，而非 M3 SegmentedButton */
@Composable
private fun GranularitySwitch(granularity: AnalyticsGranularity, onEvent: (AnalyticsEvent) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(4.dp),
    ) {
        GranularitySegment(
            label = "月度",
            selected = granularity == AnalyticsGranularity.MONTH,
            onClick = {
                if (granularity != AnalyticsGranularity.MONTH) onEvent(AnalyticsEvent.ToggleGranularity)
            },
        )
        GranularitySegment(
            label = "年度",
            selected = granularity == AnalyticsGranularity.YEAR,
            onClick = {
                if (granularity != AnalyticsGranularity.YEAR) onEvent(AnalyticsEvent.ToggleGranularity)
            },
        )
    }
}

@Composable
private fun GranularitySegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container by animateColorAsState(
        targetValue = if (selected) HoneyAmber else Color.Transparent,
        label = "granularityContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) OnHoneyAmber else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "granularityContent",
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

/** 周期选择（月/年共用）：左右箭头 + 中间标签；不在当前周期时下方显示「回本月/回今年」 */
@Composable
private fun PeriodSelector(
    label: String,
    onShift: (Int) -> Unit,
    isCurrent: Boolean,
    onGoToCurrent: () -> Unit,
    goLabel: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onShift(-1) }) {
                Icon(Heroicons.Outline.ChevronLeft, contentDescription = "上一个周期")
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.width(112.dp),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { onShift(1) }) {
                Icon(Heroicons.Outline.ChevronRight, contentDescription = "下一个周期")
            }
        }
        Box(
            modifier = Modifier.height(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!isCurrent) {
                TextButton(onClick = onGoToCurrent) { Text(goLabel) }
            }
        }
    }
}

@Composable
private fun MonthlyContent(monthly: MonthlyAnalytics?, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val data = monthly
        if (data == null) {
            // 切换粒度/翻页瞬间数据短暂为 null，首帧留白即可（Room Flow 随后立即发出）
        } else if (data.entryCount == 0) {
            item { EmptyState("这个月还没有账目，去「今日」记一笔吧") }
        } else {
            item { TotalsCard(data.expense, data.income, data.entryCount) }
            if (data.categoryRanks.isNotEmpty()) {
                item { RankingCard(data.categoryRanks) }
            }
            item { DailyTrendCard(data.dailyExpense, data.maxDaily) }
        }
    }
}

@Composable
private fun AnnualContent(annual: AnnualAnalytics?, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val data = annual
        if (data == null) {
            // 切换粒度/翻页瞬间数据短暂为 null，首帧留白即可（Room Flow 随后立即发出）
        } else if (data.entryCount == 0) {
            item { EmptyState("这一年还没有账目，去「今日」记一笔吧") }
        } else {
            item { TotalsCard(data.expense, data.income, data.entryCount) }
            if (data.categoryRanks.isNotEmpty()) {
                item { RankingCard(data.categoryRanks) }
            }
            item { YearTrendCard(data.monthlyExpense) }
            item { HighlightsCard(data.entryCount, data.highlights) }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Text(
            text = message,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 32.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 合计卡：支出/收入/笔数，月度与年度同构（ADR 0009） */
@Composable
private fun TotalsCard(expense: Double, income: Double, entryCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "支出 ¥${formatMoney(expense)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = ExpenseRed,
                )
                Text(
                    text = "收入 ¥${formatMoney(income)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = IncomeGreen,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "共 $entryCount 笔",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 分类排行：全部有支出的类别降序，蜂蜜金条宽按金额占比（ADR 0009） */
@Composable
private fun RankingCard(ranks: List<CategoryRank>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("分类排行", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            val maxAmount = ranks.firstOrNull()?.amount ?: 0.0
            ranks.forEach { rank ->
                RankRow(rank, maxAmount)
            }
        }
    }
}

@Composable
private fun RankRow(rank: CategoryRank, maxAmount: Double) {
    val fraction by animateFloatAsState(
        targetValue = if (maxAmount > 0.0) (rank.amount / maxAmount).toFloat() else 0f,
        label = "rankBar-${rank.name}",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = rank.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(76.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(5.dp))
                    .background(HoneyAmber),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "¥${formatMoney(rank.amount)}",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** 月内每日支出柱状：支出红、每日一格（无支出留空）、每 7 天标一次日期（ADR 0009）。
 *  柱与日期标签都用 weight(1f) 均分宽度，保证日期落在对应柱正下方。 */
@Composable
private fun DailyTrendCard(daily: List<DailyExpense>, maxDaily: DailyExpense?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("每日支出", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            if (maxDaily == null) {
                Text(
                    text = "本月没有支出",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // 最高单日注解贴在图表正上方，而非卡标题行
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = "最高 ¥${formatMoney(maxDaily.amount)}（${maxDaily.day}日）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                val maxAmount = maxDaily.amount
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    daily.forEach { day ->
                        val h = if (maxAmount > 0.0) (day.amount / maxAmount).toFloat() else 0f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(h)
                                .padding(horizontal = 0.5.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(ExpenseRed),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    daily.forEach { day ->
                        Text(
                            text = if (day.day % 7 == 1) "${day.day}" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 年度 12 个月支出折线：Canvas 手绘 polyline + 数据点（ADR 0009） */
@Composable
private fun YearTrendCard(monthly: List<MonthlyExpensePoint>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("每月支出", style = MaterialTheme.typography.titleMedium)
            val max = monthly.maxOfOrNull { it.amount }
            if (max == null || max == 0.0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "这一年还没有支出",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.height(12.dp))
                // 最高月度注解贴在图表正上方（max 必为某个月的值，first 安全）
                val maxMonth = monthly.first { it.amount == max }.month.monthValue
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = "最高 ¥${formatMoney(max)}（${maxMonth}月）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                // Canvas onDraw 非 Composable 作用域，先捕获主题色
                val baselineColor = MaterialTheme.colorScheme.outlineVariant
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                ) {
                    // 顶部留 4dp 绘图区边距，避免最高点圆被裁掉一半
                    val plotTop = 4.dp.toPx()
                    val usable = size.height - plotTop
                    val stepX = size.width / monthly.size
                    val lineY = { point: MonthlyExpensePoint ->
                        plotTop + (1f - (point.amount / max).toFloat()) * usable
                    }
                    drawLine(
                        color = baselineColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                    val path = Path()
                    monthly.forEachIndexed { i, point ->
                        val x = stepX * (i + 0.5f)
                        val y = lineY(point)
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    drawPath(
                        path = path,
                        color = ExpenseRed,
                        style = Stroke(
                            width = 3.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                    monthly.forEachIndexed { i, point ->
                        drawCircle(
                            color = ExpenseRed,
                            radius = 3.dp.toPx(),
                            center = Offset(stepX * (i + 0.5f), lineY(point)),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    monthly.forEach { point ->
                        Text(
                            text = "${point.month.monthValue}月",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

/** 年度高亮：4 格数字卡（笔数 / 日均 / 最忙月 / 单笔最大，ADR 0009） */
@Composable
private fun HighlightsCard(entryCount: Int, highlights: AnnualHighlights) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("年度高亮", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HighlightCell("记账笔数", "$entryCount 笔", Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                HighlightCell("日均支出", "¥${formatMoney(highlights.avgDailyExpense)}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HighlightCell(
                    label = "支出最高月",
                    value = highlights.busiestMonth?.let { "${it.monthValue}月 · ¥${formatMoney(highlights.busiestAmount)}" } ?: "—",
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(16.dp))
                HighlightCell(
                    label = "单笔最大",
                    value = highlights.biggestEntry?.let { "¥${formatMoney(it.amount)} · ${it.categoryName}" } ?: "—",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HighlightCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
