package com.cycling.beecount.ui.analytics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.beecount.domain.model.AnnualAnalytics
import com.cycling.beecount.domain.model.AnnualHeatmapDay
import com.cycling.beecount.domain.model.AnnualHighlights
import com.cycling.beecount.domain.model.CategoryRank
import com.cycling.beecount.domain.model.CategorySlice
import com.cycling.beecount.domain.model.ComparisonAnalytics
import com.cycling.beecount.domain.model.DailyExpense
import com.cycling.beecount.domain.model.ExpenseRigidity
import com.cycling.beecount.domain.model.FinanceHealthScore
import com.cycling.beecount.domain.model.GrowthAnalytics
import com.cycling.beecount.domain.model.HealthMetric
import com.cycling.beecount.domain.model.MonthlyAnalytics
import com.cycling.beecount.domain.model.MonthlyExpensePoint
import com.cycling.beecount.domain.model.NetAssetTrend
import com.cycling.beecount.domain.model.PeriodSummary
import com.cycling.beecount.domain.model.TagCloudItem
import com.cycling.beecount.domain.model.TimeSlotAmount
import com.cycling.beecount.domain.model.WeekendVsWeekday
import com.cycling.beecount.domain.usecase.GrowthAggregator
import com.cycling.beecount.ui.assistant.formatMoney
import com.cycling.beecount.ui.theme.ExpenseRed
import com.cycling.beecount.ui.theme.TerminalCyan
import com.cycling.beecount.ui.theme.TerminalPurple
import com.cycling.beecount.ui.theme.IncomeGreen
import com.cycling.beecount.ui.theme.OnTerminalCyan
import com.cycling.beecount.ui.theme.TerminalGreen
import com.woowla.compose.icon.collections.heroicons.*
import com.woowla.compose.icon.collections.heroicons.heroicons.Outline
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ChevronLeft
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ChevronRight
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ArrowLeft
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

@Composable
fun AnalyticsRoute(
    initialMonth: YearMonth,
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val monthly by viewModel.monthlyAnalytics.collectAsStateWithLifecycle()
    val annual by viewModel.annualAnalytics.collectAsStateWithLifecycle()
    val comparison by viewModel.comparisonAnalytics.collectAsStateWithLifecycle()
    val growth by viewModel.growthAnalytics.collectAsStateWithLifecycle()
    val netAssetTrend by viewModel.netAssetTrend.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(initialMonth) { viewModel.onEvent(AnalyticsEvent.SetMonth(initialMonth)) }
    AnalyticsScreen(
        uiState = uiState,
        monthly = monthly,
        annual = annual,
        comparison = comparison,
        growth = growth,
        netAssetTrend = netAssetTrend,
        onEvent = viewModel::onEvent,
        onBack = onBack,
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
    comparison: ComparisonAnalytics?,
    growth: GrowthAnalytics?,
    netAssetTrend: NetAssetTrend?,
    onEvent: (AnalyticsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("图表") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Heroicons.Outline.ArrowLeft, contentDescription = "返回日历") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AnalyticsHeader(uiState, onEvent)
            when (uiState.granularity) {
                AnalyticsGranularity.MONTH -> MonthlyContent(
                    monthly = monthly,
                    comparison = comparison,
                    growth = growth,
                    netAssetTrend = netAssetTrend,
                    report = uiState.monthlyReport,
                    onGenerate = { onEvent(AnalyticsEvent.GenerateMonthlyReport) },
                    modifier = Modifier.weight(1f),
                )
                AnalyticsGranularity.YEAR -> AnnualContent(
                    annual = annual,
                    comparison = comparison,
                    growth = growth,
                    netAssetTrend = netAssetTrend,
                    uiState = uiState,
                    onEvent = onEvent,
                    modifier = Modifier.weight(1f),
                )
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
        targetValue = if (selected) TerminalCyan else Color.Transparent,
        label = "granularityContainer",
    )
    val content by animateColorAsState(
        targetValue = if (selected) OnTerminalCyan else MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun MonthlyContent(
    monthly: MonthlyAnalytics?,
    comparison: ComparisonAnalytics?,
    growth: GrowthAnalytics?,
    netAssetTrend: NetAssetTrend?,
    report: MonthlyReportState,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { MonthlyReportBlock(report, onGenerate) }
        val data = monthly
        if (data == null) {
            // 切换粒度/翻页瞬间数据短暂为 null，首帧留白即可（Room Flow 随后立即发出）
        } else if (data.entryCount == 0) {
            item { EmptyState("这个月还没有账目，去「今日」记一笔吧") }
        } else {
            item { TotalsCard(data.expense, data.income, data.entryCount) }
            if (comparison != null) {
                item { ComparisonCard(comparison) }
            }
            item { SpendingComparisonCard(data.expense, data.income) }
            if (data.categoryRanks.isNotEmpty()) {
                item { RankingCard(data.categoryRanks) }
            }
            if (data.categoryRanks.isNotEmpty()) {
                item { CategoryDonutCard(data.categoryRanks) }
            }
            item { DailyTrendCard(data.dailyExpense, data.maxDaily) }
            if (growth != null) {
                item { GrowthSpendingCard(growth) }
                item { GrowthHabitsCard(growth) }
                item { GrowthRigidityCard(growth) }
                item { HealthScoreCard(growth.health) }
                if (growth.tagCloud.isNotEmpty()) {
                    item { TagCloudCard(growth.tagCloud) }
                }
            }
            if (netAssetTrend != null && netAssetTrend.points.isNotEmpty()) {
                item { NetAssetTrendCard(netAssetTrend, growth) }
            }
        }
    }
}

/** AI 月度报告卡（P0）：按钮始终可见；按状态展示 Idle/Loading/Content/KeyMissing/Error */
@Composable
private fun MonthlyReportBlock(report: MonthlyReportState, onGenerate: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "AI 月度报告",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onGenerate,
                    enabled = report !is MonthlyReportState.Loading,
                ) {
                    Text(if (report is MonthlyReportState.Loading) "生成中…" else "生成月报")
                }
            }
            Spacer(Modifier.height(10.dp))
            when (report) {
                MonthlyReportState.Idle ->
                    Text(
                        text = "一键生成这个月的中文财务报告（基于真实数据，配置 Key 后由 AI 润色）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                MonthlyReportState.Loading ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "正在生成…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                is MonthlyReportState.Content -> {
                    if (report.isLocal) {
                        Text(
                            text = "（本地模板生成；配置 DeepSeek API Key 后可生成更口语化的 AI 版）",
                            style = MaterialTheme.typography.labelSmall,
                            color = TerminalCyan,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        text = report.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                MonthlyReportState.KeyMissing ->
                    Text(
                        text = "未配置 DeepSeek API Key，已用本地模板生成；去「设置」配置 Key 可生成 AI 版。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                is MonthlyReportState.Error ->
                    Text(
                        text = report.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = ExpenseRed,
                    )
            }
        }
    }
}

@Composable
private fun AnnualContent(
    annual: AnnualAnalytics?,
    comparison: ComparisonAnalytics?,
    growth: GrowthAnalytics?,
    netAssetTrend: NetAssetTrend?,
    uiState: AnalyticsUiState,
    onEvent: (AnalyticsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
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
            item {
                AnnualHeatmapCard(data.year, data.dailyHeatmap)
            }
        } else {
            item { TotalsCard(data.expense, data.income, data.entryCount) }
            if (comparison != null) {
                item { ComparisonCard(comparison) }
            }
            if (data.categoryRanks.isNotEmpty()) {
                item { RankingCard(data.categoryRanks) }
            }
            if (data.categoryRanks.isNotEmpty()) {
                item { CategoryDonutCard(data.categoryRanks) }
            }
            item { YearTrendCard(data.monthlyExpense) }
            item {
                AnnualHeatmapCard(data.year, data.dailyHeatmap)
            }
            item { HighlightsCard(data.entryCount, data.highlights) }
            if (growth != null) {
                item { GrowthSpendingCard(growth) }
                item { GrowthHabitsCard(growth) }
                item { GrowthRigidityCard(growth) }
                item { HealthScoreCard(growth.health) }
                if (growth.tagCloud.isNotEmpty()) {
                    item { TagCloudCard(growth.tagCloud) }
                }
                item { AnnualReportCard(data.year, growth, data.expense, data.income) }
            }
            if (netAssetTrend != null && netAssetTrend.points.isNotEmpty()) {
                item { NetAssetTrendCard(netAssetTrend, growth) }
            }
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
                    .background(TerminalCyan),
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

/** 年度热力图：只读展示全年每日支出强度。 */
@Composable
private fun AnnualHeatmapCard(year: Int, days: List<AnnualHeatmapDay>) {
    val first = LocalDate.of(year, 1, 1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val last = LocalDate.of(year, 12, 31).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val weeks = ((last.toEpochDay() - first.toEpochDay()) / 7 + 1).toInt()
    val expenseByDate = remember(days) { days.associateBy { it.date } }
    val nonZero = days.map { it.expense.toFloat() }.filter { it > 0f }.sorted()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("年度热力图", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
            ) {
                Column {
                    Row(Modifier.padding(start = 28.dp)) {
                        repeat(weeks) { week ->
                            val date = first.plusDays(week * 7L)
                            val monthLabel = (1..12).firstOrNull { month ->
                                YearMonth.of(year, month).atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) == date
                            }
                            Text(monthLabel?.let { "${it}月" } ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(17.dp))
                        }
                    }
                    Row {
                        Column(Modifier.width(28.dp)) {
                            listOf("一", "三", "五", "日").forEach { label ->
                                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.height(17.dp))
                                if (label != "日") Spacer(Modifier.height(20.dp))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(weeks) { week ->
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    repeat(7) { dayIndex ->
                                        val item = expenseByDate[first.plusDays((week * 7 + dayIndex).toLong())]
                                        val level = item?.let { expenseHeatmapLevel(it.expense.toFloat(), nonZero) } ?: 0
                                        Box(Modifier.width(14.dp).height(14.dp).clip(RoundedCornerShape(3.dp)).background(ExpenseRed.copy(alpha = listOf(0f, .24f, .45f, .7f, 1f)[level])))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("少", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                listOf(.18f, .4f, .65f, 1f).forEach { alpha -> Box(Modifier.width(14.dp).height(14.dp).clip(RoundedCornerShape(3.dp)).background(ExpenseRed.copy(alpha))) }
                Text("多", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun expenseHeatmapLevel(value: Float, nonZero: List<Float>): Int {
    if (value <= 0f) return 0
    val rank = nonZero.indexOfLast { it <= value }.coerceAtLeast(0)
    return (rank * 4 / nonZero.size + 1).coerceIn(1, 4)
}

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

/** 时间段对比卡：当前期间 vs 上一期间，展示支出/收入变化与同比/环比百分比 */
@Composable
private fun ComparisonCard(comparison: ComparisonAnalytics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("时间段对比", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            ComparisonRow(comparison.currentLabel, comparison.current, comparison.previousLabel, comparison.previous)
        }
    }
}

@Composable
private fun ComparisonRow(
    currentLabel: String,
    current: PeriodSummary,
    previousLabel: String,
    previous: PeriodSummary,
) {
    val expenseDelta = percentChange(current.expense, previous.expense)
    val incomeDelta = percentChange(current.income, previous.income)
    Column {
        ComparisonText(
            currentLabel = currentLabel,
            previousLabel = previousLabel,
            title = "支出",
            current = current.expense,
            previous = previous.expense,
            delta = expenseDelta,
            color = ExpenseRed,
        )
        Spacer(Modifier.height(10.dp))
        ComparisonText(
            currentLabel = currentLabel,
            previousLabel = previousLabel,
            title = "收入",
            current = current.income,
            previous = previous.income,
            delta = incomeDelta,
            color = IncomeGreen,
        )
    }
}

@Composable
private fun ComparisonText(
    currentLabel: String,
    previousLabel: String,
    title: String,
    current: Double,
    previous: Double,
    delta: Float?,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(40.dp))
        Column(Modifier.weight(1f)) {
            Text("$currentLabel ¥${formatMoney(current)}", style = MaterialTheme.typography.bodyMedium, color = color)
            Text("$previousLabel ¥${formatMoney(previous)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val deltaText = if (delta == null) {
            "—"
        } else {
            val prefix = if (delta > 0) "+" else ""
            "$prefix${"%.1f".format(delta)}%"
        }
        val deltaColor = when {
            delta == null -> MaterialTheme.colorScheme.onSurfaceVariant
            delta > 0 -> ExpenseRed
            delta < 0 -> IncomeGreen
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(deltaText, style = MaterialTheme.typography.bodyMedium, color = deltaColor)
    }
}

private fun percentChange(current: Double, previous: Double): Float? {
    if (previous == 0.0) return null
    return ((current - previous) / previous * 100).toFloat()
}

/** 收支对比柱状图：当前期间支出/收入两根柱，与上一期间对比（净变化） */
@Composable
private fun SpendingComparisonCard(expense: Double, income: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("收支对比", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            val maxBar = maxOf(expense, income).coerceAtLeast(1.0)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                BarColumn("支出", expense, ExpenseRed, maxBar, Modifier.weight(1f))
                Spacer(Modifier.width(24.dp))
                BarColumn("收入", income, IncomeGreen, maxBar, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BarColumn(label: String, value: Double, barColor: Color, maxBar: Double, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("¥${formatMoney(value)}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .height(120.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight((value / maxBar).toFloat())
                    .background(barColor, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 分类占比环形图：由分类排行数据绘制 donut，中心显示总支出 */
@Composable
private fun CategoryDonutCard(ranks: List<CategoryRank>) {
    val total = ranks.sumOf { it.amount }
    if (total <= 0.0) return
    val slices = ranks.map { CategorySlice(it.name, it.amount, (it.amount / total).toFloat()) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("分类占比", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart(slices, Modifier.size(160.dp), centerText = "¥${formatMoney(total)}")
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    slices.forEachIndexed { index, slice ->
                        DonutLegendRow(slice, index)
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutChart(slices: List<CategorySlice>, modifier: Modifier = Modifier, centerText: String) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 28.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val center = Offset(size.width / 2, size.height / 2)
            var startAngle = -90f
            slices.forEachIndexed { index, slice ->
                val sweep = slice.fraction * 360f
                drawArc(
                    color = paletteColor(index),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - diameter / 2, center.y - diameter / 2),
                    size = androidx.compose.ui.geometry.Size(diameter, diameter),
                    style = Stroke(width = strokeWidth),
                )
                startAngle += sweep
            }
        }
        Text(centerText, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DonutLegendRow(slice: CategorySlice, index: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(paletteColor(index), RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(6.dp))
        Text(slice.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${(slice.fraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 环形图配色：与分类排行条形同风格，但为 donut 提供连续可区分色板 */
private fun paletteColor(index: Int): Color {
    return PaletteColors[index % PaletteColors.size]
}

private val PaletteColors = listOf(
    TerminalCyan,
    ExpenseRed,
    IncomeGreen,
    TerminalPurple,
    TerminalGreen,
    Color(0xFF8D6E63),
    Color(0xFF9575CD),
    Color(0xFF4FC3F7),
)

// ==================================================================================
// 模块 G：高级统计分析区块
// ==================================================================================

/** 支出分布统计卡（176-180）：频次 / 客单价 / 中位数 / 单笔极值 / 波动 */
@Composable
private fun GrowthSpendingCard(growth: GrowthAnalytics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("支出分布", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            val stats = growth.spendingStats
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCell("支出笔数", "${stats.expenseCount} 笔", Modifier.weight(1f))
                StatCell("客单价", "¥${formatMoney(stats.avgTicket)}", Modifier.weight(1f))
                StatCell("中位数", "¥${formatMoney(stats.median)}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCell("最大支出", stats.maxExpense?.let { "¥${formatMoney(it.amount)} · ${it.categoryName}" } ?: "—", Modifier.weight(1f))
                StatCell("最大收入", stats.maxIncome?.let { "¥${formatMoney(it.amount)} · ${it.categoryName}" } ?: "—", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            val varianceLine = buildAnnotatedString {
                append("方差 ")
                append("${formatMoney(stats.variance)}")
                append("   标准差 ")
                append("${formatMoney(stats.stdDev)}")
                append("   变异系数 ")
                append("${"%.2f".format(stats.coefficientOfVariation)}")
            }
            Text(varianceLine, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (stats.perCategoryCounts.isNotEmpty()) {
                Text("消费频次 TOP", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                stats.perCategoryCounts.take(5).forEach { categoryCount ->
                    FrequencyRow(categoryCount.name, categoryCount.count)
                }
            }
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
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

@Composable
private fun FrequencyRow(name: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(76.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(10.dp))
        Text(
            text = "$count 次",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
        )
    }
}

/** 消费习惯卡（181-182）：周末 vs 工作日 + 时间段分布 */
@Composable
private fun GrowthHabitsCard(growth: GrowthAnalytics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("消费习惯", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            WeekendVsWeekdayBlock(growth.weekendVsWeekday)
            Spacer(Modifier.height(12.dp))
            TimeSlotBlock(growth.timeSlots)
            Spacer(Modifier.height(12.dp))
            WeekdayDotBlock(growth.weekdayStats)
        }
    }
}

@Composable
private fun WeekendVsWeekdayBlock(wv: WeekendVsWeekday) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        StatCell("周末日均", "¥${formatMoney(wv.weekendPerDay)}", Modifier.weight(1f))
        StatCell("工作日日均", "¥${formatMoney(wv.weekdayPerDay)}", Modifier.weight(1f))
    }
    Spacer(Modifier.height(6.dp))
    val extraText = if (wv.extraPercent >= 0) "周末比工作日多花" else "周末比工作日少花"
    val extraAbs = "%.1f%%".format(kotlin.math.abs(wv.extraPercent))
    Text(
        text = "$extraText $extraAbs",
        style = MaterialTheme.typography.bodySmall,
        color = if (wv.extraPercent > 0) ExpenseRed else IncomeGreen,
    )
}

@Composable
private fun TimeSlotBlock(slots: List<TimeSlotAmount>) {
    Text("时间段分布", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    slots.forEach { slot ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(slot.slot.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(48.dp))
            Box(
                modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(slot.fraction).clip(RoundedCornerShape(5.dp)).background(TerminalCyan))
            }
            Spacer(Modifier.width(12.dp))
            Text("¥${formatMoney(slot.amount)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(72.dp), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun WeekdayDotBlock(stats: List<com.cycling.beecount.domain.model.WeekdayStats>) {
    Text("星期几节奏", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(8.dp))
    val max = stats.maxOfOrNull { it.expense } ?: 0.0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        stats.forEach { stat ->
            val label = when (stat.dayOfWeek) {
                java.time.DayOfWeek.MONDAY -> "一"
                java.time.DayOfWeek.TUESDAY -> "二"
                java.time.DayOfWeek.WEDNESDAY -> "三"
                java.time.DayOfWeek.THURSDAY -> "四"
                java.time.DayOfWeek.FRIDAY -> "五"
                java.time.DayOfWeek.SATURDAY -> "六"
                java.time.DayOfWeek.SUNDAY -> "日"
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                val h = if (max > 0) (stat.expense / max).toFloat() else 0f
                Box(Modifier.height(48.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(h).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(ExpenseRed))
                }
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** 刚性结构卡（183-185）：刚性 / 可变 / 冲动支出占比 */
@Composable
private fun GrowthRigidityCard(growth: GrowthAnalytics) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("支出结构", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            RigidityRatioBlock(growth.rigidity)
            Spacer(Modifier.height(12.dp))
            Text(
                text = "刚性 = 维持基本生活的必要支出（居住/医疗/教育/交通）；冲动 = 可变支出中偏「想要」的非必需部分（购物/娱乐/人情）。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RigidityRatioBlock(rigidity: ExpenseRigidity) {
    RigidityBar("刚性支出", rigidity.rigidExpense, rigidity.rigidRatio, TerminalGreen)
    Spacer(Modifier.height(8.dp))
    RigidityBar("可选支出", rigidity.variableExpense, rigidity.variableRatio, TerminalCyan)
    Spacer(Modifier.height(8.dp))
    RigidityBar("冲动消费", rigidity.impulseExpense, rigidity.impulseRatio, ExpenseRed)
}

@Composable
private fun RigidityBar(label: String, amount: Double, ratio: Float, barColor: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
        Box(
            modifier = Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(ratio).clip(RoundedCornerShape(6.dp)).background(barColor))
        }
        Spacer(Modifier.width(12.dp))
        Text("${"%.0f%%".format(ratio * 100)}  ¥${formatMoney(amount)}", style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.End)
    }
}

/** 财务健康评分卡（188）：加权总分 + 分维度 */
@Composable
private fun HealthScoreCard(health: FinanceHealthScore) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("财务健康评分", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    text = "${health.total} · ${health.grade}",
                    style = MaterialTheme.typography.titleLarge,
                    color = healthColor(health.total),
                )
            }
            Spacer(Modifier.height(12.dp))
            health.metrics.forEach { metric ->
                HealthMetricRow(metric)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun HealthMetricRow(metric: HealthMetric) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(metric.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(72.dp))
        Box(
            modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(metric.score / 100f).clip(RoundedCornerShape(5.dp)).background(healthColor(metric.score)))
        }
        Spacer(Modifier.width(10.dp))
        Text("${metric.score}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(28.dp), textAlign = TextAlign.End)
    }
    Text(
        text = metric.detail,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

private fun healthColor(score: Int): Color = when {
    score >= 80 -> IncomeGreen
    score >= 50 -> TerminalCyan
    else -> ExpenseRed
}

/** 净资产趋势折线卡（186-187）：历史累计净资产折线 + 资产负债率（超支占比）变化曲线，可回溯 */
@Composable
private fun NetAssetTrendCard(trend: NetAssetTrend, growth: GrowthAnalytics?) {
    val points = trend.points
    val maxAbs = points.maxOfOrNull { kotlin.math.abs(it.netAsset) } ?: 0.0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("净资产趋势", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            val latest = points.last()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatCell("当前净资产", "¥${formatMoney(latest.netAsset)}", Modifier.weight(1f))
                StatCell("资产负债率", "${"%.1f%%".format(latest.deficitRatio * 100)}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            if (points.size < 2) {
                Text("数据不足，无法绘制趋势", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val baselineColor = MaterialTheme.colorScheme.outlineVariant
                Canvas(
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                ) {
                    // 上侧（0..50%）绘制净资产（正上负下，0 为中轴）；下侧（50%..95%）绘制资产负债率 0..1
                    val midY = size.height * 0.5f
                    val netRange = (size.height * 0.5f - 8.dp.toPx()).coerceAtLeast(1f)
                    val debtBand = size.height * 0.44f
                    val netY = { value: Double ->
                        midY - (value / maxAbs).toFloat().coerceIn(-1f, 1f) * netRange
                    }
                    val debtY = { ratio: Float -> size.height * 0.96f - ratio.coerceIn(0f, 1f) * debtBand }
                    // 净资产 0 基准线
                    drawLine(color = baselineColor, start = Offset(0f, midY), end = Offset(size.width, midY), strokeWidth = 1.dp.toPx())
                    val stepX = size.width / (points.size - 1)
                    val netPath = Path()
                    val debtPath = Path()
                    points.forEachIndexed { i, p ->
                        val x = stepX * i
                        if (i == 0) {
                            netPath.moveTo(x, netY(p.netAsset))
                            debtPath.moveTo(x, debtY(p.deficitRatio))
                        } else {
                            netPath.lineTo(x, netY(p.netAsset))
                            debtPath.lineTo(x, debtY(p.deficitRatio))
                        }
                    }
                    drawPath(path = netPath, color = TerminalCyan, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(path = debtPath, color = ExpenseRed, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NetTrendLegend(TerminalCyan, "净资产")
                    NetTrendLegend(ExpenseRed, "资产负债率")
                }
            }
            Spacer(Modifier.height(6.dp))
            val startLabel = points.first().point
            val endLabel = points.last().point
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${startLabel.year}·${startLabel.monthValue}月${startLabel.dayOfMonth}日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${endLabel.year}·${endLabel.monthValue}月${endLabel.dayOfMonth}日", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NetTrendLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 标签云卡（模块 G）：消费越多字体越大，颜色取标签自身色 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagCloudCard(items: List<TagCloudItem>) {
    val maxAmount = items.maxOfOrNull { it.amount } ?: 0.0
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("标签云", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "消费越多字体越大",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items.forEach { item ->
                    TagCloudWord(item, maxAmount)
                }
            }
        }
    }
}

@Composable
private fun TagCloudWord(item: TagCloudItem, maxAmount: Double) {
    // 字号 12..28sp，按金额相对最大值对数缩放；仅一个标签时取最小字号
    val ratio = if (maxAmount > 0.0) (item.amount / maxAmount).toFloat() else 0f
    val fontSize = 12f + ratio.coerceIn(0f, 1f) * 16f
    val color = Color(item.color)
    Box {
        Text(
            text = item.name,
            fontSize = fontSize.sp,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = 1,
        )
    }
}

/** 年度收支报告卡（189）：自动生成的图文报告 */
@Composable
private fun AnnualReportCard(year: Int, growth: GrowthAnalytics, expense: Double, income: Double) {
    val report = remember(growth, expense, income, year) {
        GrowthAggregator.annualReport(year, growth, income, expense)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("年度收支报告 · ${year}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            report.sections.forEach { section ->
                Text(section.title, style = MaterialTheme.typography.labelLarge, color = TerminalCyan)
                Spacer(Modifier.height(4.dp))
                section.lines.forEach { line ->
                    Text("· $line", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(2.dp))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}
