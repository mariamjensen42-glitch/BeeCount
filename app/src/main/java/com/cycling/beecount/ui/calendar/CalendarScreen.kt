package com.cycling.beecount.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.beecount.domain.model.CalendarDaySummary
import com.cycling.beecount.domain.model.CalendarMonth
import com.cycling.beecount.ui.assistant.formatMoney
import com.cycling.beecount.ui.theme.ExpenseRed
import com.cycling.beecount.ui.theme.HoneyAmber
import com.cycling.beecount.ui.theme.IncomeGreen
import com.woowla.compose.icon.collections.heroicons.Heroicons
import com.woowla.compose.icon.collections.heroicons.heroicons.Outline
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ChartBar
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ChevronLeft
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ChevronRight
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

@Composable
fun CalendarRoute(
    onOpenAnalytics: (YearMonth) -> Unit,
    onAddEntry: (LocalDate) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val calendarMonth by viewModel.calendarMonth.collectAsStateWithLifecycle()
    val dayEntries by viewModel.dayEntries.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onEvent(CalendarEvent.OpenInitialToday) }
    CalendarScreen(uiState, calendarMonth, dayEntries, viewModel::onEvent, onOpenAnalytics, onAddEntry)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    uiState: CalendarUiState,
    calendarMonth: CalendarMonth?,
    dayEntries: List<com.cycling.beecount.domain.model.Entry>,
    onEvent: (CalendarEvent) -> Unit,
    onOpenAnalytics: (YearMonth) -> Unit,
    onAddEntry: (LocalDate) -> Unit,
) {
    var showMonthPicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.pendingUndo) {
        if (uiState.pendingUndo != null) {
            val result = snackbarHostState.showSnackbar(
                message = "已删除账目",
                actionLabel = "撤销",
                withDismissAction = false,
                duration = androidx.compose.material3.SnackbarDuration.Indefinite,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) onEvent(CalendarEvent.UndoDelete)
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("日历") },
                actions = {
                    IconButton(onClick = { onOpenAnalytics(uiState.selectedMonth) }) {
                        Icon(Heroicons.Outline.ChartBar, contentDescription = "查看图表")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { MonthSelector(uiState.selectedMonth, onEvent, onOpenPicker = { showMonthPicker = true }) }
            item { MonthSummary(calendarMonth) }
            item { MonthGrid(uiState, calendarMonth, onEvent) }
        }
    }
    if (showMonthPicker) {
        MonthPickerDialog(
            selectedMonth = uiState.selectedMonth,
            onDismiss = { showMonthPicker = false },
            onSelect = {
                onEvent(CalendarEvent.SelectMonth(it))
                showMonthPicker = false
            },
        )
    }
    val selectedDay = calendarMonth?.days?.firstOrNull { it.date == uiState.selectedDate }
    if (uiState.showDaySheet && uiState.selectedDate != null) {
        DaySheet(
            date = uiState.selectedDate,
            day = selectedDay,
            entries = dayEntries,
            onDismiss = { onEvent(CalendarEvent.CloseDaySheet) },
            onDelete = { onEvent(CalendarEvent.DeleteEntry(it)) },
            onAddEntry = { onAddEntry(uiState.selectedDate) },
        )
    }
}

@Composable
private fun MonthSelector(month: YearMonth, onEvent: (CalendarEvent) -> Unit, onOpenPicker: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onEvent(CalendarEvent.ShiftMonth(-1)) }) { Icon(Heroicons.Outline.ChevronLeft, "上个月") }
            TextButton(onClick = onOpenPicker) {
                Text("${month.year}年${month.monthValue}月", style = MaterialTheme.typography.titleMedium)
            }
            IconButton(onClick = { onEvent(CalendarEvent.ShiftMonth(1)) }) { Icon(Heroicons.Outline.ChevronRight, "下个月") }
        }
        if (month != YearMonth.now()) TextButton(onClick = { onEvent(CalendarEvent.GoToCurrentMonth) }) { Text("回本月") }
    }
}

@Composable
private fun MonthSummary(month: CalendarMonth?) {
    val expense = month?.expense ?: 0.0
    val income = month?.income ?: 0.0
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        SummaryValue("支出", expense, ExpenseRed)
        SummaryValue("收入", income, IncomeGreen)
        SummaryValue("结余", income - expense, MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SummaryValue(label: String, amount: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("¥${formatMoney(amount)}", style = MaterialTheme.typography.titleMedium, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MonthGrid(uiState: CalendarUiState, month: CalendarMonth?, onEvent: (CalendarEvent) -> Unit) {
    val selectedMonth = uiState.selectedMonth
    val first = selectedMonth.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val last = selectedMonth.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val dates = generateSequence(first) { date -> date.takeIf { it < last }?.plusDays(1) }.toList()
    val summaryByDate = month?.days?.associateBy { it.date }.orEmpty()
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        dates.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth().height(70.dp)) {
                week.forEach { date ->
                    val inMonth = YearMonth.from(date) == selectedMonth
                    val enabled = inMonth && !date.isAfter(LocalDate.now())
                    CalendarCell(
                        date = date,
                        day = summaryByDate[date],
                        enabled = enabled,
                        selected = date == uiState.selectedDate,
                        today = date == LocalDate.now(),
                        modifier = Modifier.weight(1f),
                        onClick = { onEvent(CalendarEvent.SelectDate(date)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarCell(date: LocalDate, day: CalendarDaySummary?, enabled: Boolean, selected: Boolean, today: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val border = when {
        selected -> MaterialTheme.colorScheme.onSurface
        today -> HoneyAmber
        else -> Color.Transparent
    }
    Box(
        modifier = modifier.padding(2.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(5.dp))
            .then(if (border != Color.Transparent) Modifier.border(if (selected) 2.dp else 1.dp, border, androidx.compose.foundation.shape.RoundedCornerShape(5.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 5.dp, horizontal = 2.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${date.dayOfMonth}", style = MaterialTheme.typography.bodyMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            if (enabled && (day?.expense ?: 0.0) > 0) Text(compactMoney(day!!.expense), style = MaterialTheme.typography.labelSmall, color = ExpenseRed, maxLines = 1, overflow = TextOverflow.Clip)
            if (enabled && (day?.income ?: 0.0) > 0) Box(Modifier.padding(top = 2.dp).size(5.dp).clip(androidx.compose.foundation.shape.CircleShape).background(IncomeGreen))
        }
    }
}

private fun compactMoney(amount: Double): String = when {
    amount >= 10_000 -> "¥${formatMoney(amount / 1_000)}k"
    amount >= 1_000 -> "¥${formatMoney(amount / 1_000)}k"
    else -> "¥${formatMoney(amount)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaySheet(
    date: LocalDate,
    day: CalendarDaySummary?,
    entries: List<com.cycling.beecount.domain.model.Entry>,
    onDismiss: () -> Unit,
    onDelete: (Long) -> Unit,
    onAddEntry: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("${date.year}年${date.monthValue}月${date.dayOfMonth}日", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryValue("支出", day?.expense ?: 0.0, ExpenseRed)
                SummaryValue("收入", day?.income ?: 0.0, IncomeGreen)
                SummaryValue("结余", (day?.income ?: 0.0) - (day?.expense ?: 0.0), MaterialTheme.colorScheme.onSurface)
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${day?.entryCount ?: 0} 笔", style = MaterialTheme.typography.titleMedium); Text("记账笔数", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(18.dp))
            if ((day?.entryCount ?: 0) == 0) {
                Text("暂无账目", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                entries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.categoryName, style = MaterialTheme.typography.bodyLarge)
                            Text(entry.note.ifBlank { entry.amountRaw }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        when (entry.type) {
                            com.cycling.beecount.domain.model.EntryType.EXPENSE -> Text(
                                text = "-¥${formatMoney(entry.amount)}",
                                color = ExpenseRed,
                            )
                            com.cycling.beecount.domain.model.EntryType.INCOME -> Text(
                                text = "+¥${formatMoney(entry.amount)}",
                                color = IncomeGreen,
                            )
                            com.cycling.beecount.domain.model.EntryType.NEUTRAL -> Text(
                                text = "¥${formatMoney(entry.amount)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onDelete(entry.id) }) { Text("删除") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onAddEntry, modifier = Modifier.align(Alignment.End)) { Text("新增记账") }
        }
    }
}
