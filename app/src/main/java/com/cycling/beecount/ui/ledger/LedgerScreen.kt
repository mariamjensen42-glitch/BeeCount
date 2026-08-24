package com.cycling.beecount.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.repository.TodayTotals
import com.cycling.beecount.ui.assistant.EditEntrySheet
import com.cycling.beecount.ui.assistant.MILLIS_PER_DAY
import com.cycling.beecount.ui.assistant.formatMoney
import com.cycling.beecount.ui.assistant.toUtcMillis
import com.cycling.beecount.ui.theme.ExpenseRed
import com.cycling.beecount.ui.theme.IncomeGreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun LedgerRoute(
    viewModel: LedgerViewModel = hiltViewModel(),
    onOpenTagManage: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredEntries by viewModel.filteredEntries.collectAsStateWithLifecycle()
    val filteredTotals by viewModel.filteredTotals.collectAsStateWithLifecycle()
    LedgerScreen(
        uiState = uiState,
        filteredEntries = filteredEntries,
        filteredTotals = filteredTotals,
        onEvent = viewModel::onEvent,
        onOpenTagManage = onOpenTagManage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    uiState: LedgerUiState,
    filteredEntries: List<Entry>,
    filteredTotals: TodayTotals,
    onEvent: (LedgerEvent) -> Unit,
    onOpenTagManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("账本") },
                actions = {
                    TextButton(onClick = { onEvent(LedgerEvent.ToggleShowFilters) }) {
                        Text(if (uiState.showFilters) "收起筛选" else "筛选")
                    }
                    TextButton(onClick = onOpenTagManage) { Text("管理标签") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            FilterToolbarRow(uiState, onEvent)
            TagFilterRow(uiState, onEvent)
            if (uiState.showFilters) {
                FilterPanel(
                    uiState = uiState,
                    onEvent = onEvent,
                )
            }
            val hasAnyFilter = uiState.selectedTagIds.isNotEmpty() || uiState.filters.isActive
            if (hasAnyFilter) {
                FilteredTotalsBar(filteredTotals)
            }
            if (filteredEntries.isEmpty()) {
                Text(
                    text = when {
                        uiState.entries.isEmpty() -> "还没有账目，去今日页记一笔吧"
                        hasAnyFilter -> "没有符合条件的账目"
                        else -> "没有同时带这些标签的账目"
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LedgerList(
                    entries = filteredEntries,
                    onEntryClick = { entry -> onEvent(LedgerEvent.OpenEditEntry(entry)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
    uiState.editingEntry?.let { entry ->
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { onEvent(LedgerEvent.CloseEditEntry) }) {
            EditEntrySheet(
                entry = entry,
                categories = uiState.allCategories,
                tags = uiState.allTags,
                onSave = { type, amount, categoryName, date, note, tagNames, counterparty, isReimbursed ->
                    onEvent(
                        LedgerEvent.SaveEditEntry(
                            entryId = entry.id,
                            editedType = type,
                            editedAmount = amount,
                            editedCategoryName = categoryName,
                            editedDate = date,
                            editedNote = note,
                            tagNames = tagNames,
                            editedCounterparty = counterparty,
                            editedIsReimbursed = isReimbursed,
                        )
                    )
                },
                onDismiss = { onEvent(LedgerEvent.CloseEditEntry) },
            )
        }
    }
}

/** 顶部工具行：仅当综合筛选激活时显示右对齐「重置筛选」 */
@Composable
private fun FilterToolbarRow(uiState: LedgerUiState, onEvent: (LedgerEvent) -> Unit) {
    if (uiState.filters.isActive) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { onEvent(LedgerEvent.ResetFilters) }) { Text("重置筛选") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagFilterRow(uiState: LedgerUiState, onEvent: (LedgerEvent) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (uiState.selectedTagIds.isNotEmpty()) {
            item {
                TextButton(onClick = { onEvent(LedgerEvent.ClearFilter) }) { Text("清除筛选") }
            }
        }
        items(uiState.allTags, key = { "filter-tag-${it.id}" }) { tag ->
            FilterChip(
                selected = tag.id in uiState.selectedTagIds,
                onClick = { onEvent(LedgerEvent.ToggleTag(tag.id)) },
                label = { Text(tag.name) },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(tag.color))
                    )
                },
            )
        }
    }
}

/** 综合筛选面板：关键词、日期范围、分类、金额区间、交易对方、收支类型（多条件「与」组合） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterPanel(uiState: LedgerUiState, onEvent: (LedgerEvent) -> Unit) {
    val filters = uiState.filters
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .heightIn(max = 320.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // 关键词
            OutlinedTextField(
                value = filters.keyword,
                onValueChange = { onEvent(LedgerEvent.SetKeyword(it)) },
                label = { Text("关键词（备注/对方/类别）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))

            // 日期范围
            Text("日期范围", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateRangeChip("今天", filters.dateRange == LedgerDateRange.Today) {
                    onEvent(LedgerEvent.SetDateRange(LedgerDateRange.Today))
                }
                DateRangeChip("本周", filters.dateRange == LedgerDateRange.ThisWeek) {
                    onEvent(LedgerEvent.SetDateRange(LedgerDateRange.ThisWeek))
                }
                DateRangeChip("本月", filters.dateRange == LedgerDateRange.ThisMonth) {
                    onEvent(LedgerEvent.SetDateRange(LedgerDateRange.ThisMonth))
                }
                DateRangeChip("自定义", filters.dateRange is LedgerDateRange.Custom) {
                    if (filters.dateRange is LedgerDateRange.Custom) {
                        onEvent(LedgerEvent.SetDateRange(null))
                    } else {
                        val today = LocalDate.now()
                        onEvent(LedgerEvent.SetDateRange(LedgerDateRange.Custom(today.minusDays(7), today)))
                    }
                }
                DateRangeChip("不限", filters.dateRange == null) {
                    onEvent(LedgerEvent.SetDateRange(null))
                }
            }
            // 自定义日期区间的起止选择
            val customRange = filters.dateRange as? LedgerDateRange.Custom
            if (customRange != null) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateTextField(
                        label = "开始",
                        date = customRange.start,
                        onDateChange = { newStart ->
                            onEvent(LedgerEvent.SetDateRange(customRange.copy(start = newStart)))
                        },
                        modifier = Modifier.weight(1f),
                    )
                    DateTextField(
                        label = "结束",
                        date = customRange.end,
                        onDateChange = { newEnd ->
                            onEvent(LedgerEvent.SetDateRange(customRange.copy(end = newEnd)))
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 分类
            Text("分类", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filters.categoryName == null,
                    onClick = { onEvent(LedgerEvent.SetCategory(null)) },
                    label = { Text("全部") },
                )
                uiState.allCategories.forEach { category ->
                    FilterChip(
                        selected = filters.categoryName == category.name,
                        onClick = { onEvent(LedgerEvent.SetCategory(category.name)) },
                        label = { Text(category.name) },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // 收支类型
            Text("收支类型", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filters.type == null,
                    onClick = { onEvent(LedgerEvent.SetEntryType(null)) },
                    label = { Text("全部") },
                )
                FilterChip(
                    selected = filters.type == EntryType.EXPENSE,
                    onClick = { onEvent(LedgerEvent.SetEntryType(EntryType.EXPENSE)) },
                    label = { Text("支出") },
                )
                FilterChip(
                    selected = filters.type == EntryType.INCOME,
                    onClick = { onEvent(LedgerEvent.SetEntryType(EntryType.INCOME)) },
                    label = { Text("收入") },
                )
                FilterChip(
                    selected = filters.type == EntryType.REFUND,
                    onClick = { onEvent(LedgerEvent.SetEntryType(EntryType.REFUND)) },
                    label = { Text("退款") },
                )
                FilterChip(
                    selected = filters.type == EntryType.NEUTRAL,
                    onClick = { onEvent(LedgerEvent.SetEntryType(EntryType.NEUTRAL)) },
                    label = { Text("中性") },
                )
            }

            Spacer(Modifier.height(10.dp))

            // 金额区间
            Text("金额区间（元）", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = filters.minAmount?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "",
                    onValueChange = { input ->
                        onEvent(LedgerEvent.SetAmountRange(input.toDoubleOrNull(), filters.maxAmount))
                    },
                    label = { Text("最小") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = filters.maxAmount?.let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() } ?: "",
                    onValueChange = { input ->
                        onEvent(LedgerEvent.SetAmountRange(filters.minAmount, input.toDoubleOrNull()))
                    },
                    label = { Text("最大") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(10.dp))

            // 交易对方：从条目提取去重候选
            val counterparties = remember(uiState.entries) {
                uiState.entries.mapNotNull { it.counterparty?.takeIf { c -> c.isNotBlank() } }
                    .distinct()
                    .sorted()
            }
            Text("交易对方", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filters.counterparty == null,
                    onClick = { onEvent(LedgerEvent.SetCounterparty(null)) },
                    label = { Text("全部") },
                )
                counterparties.forEach { counterparty ->
                    FilterChip(
                        selected = filters.counterparty == counterparty,
                        onClick = { onEvent(LedgerEvent.SetCounterparty(if (filters.counterparty == counterparty) null else counterparty)) },
                        label = { Text(counterparty) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DateRangeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

/** 自定义日期区间的起止文本（只读），点击弹出 DatePicker 修改 */
@Composable
private fun DateTextField(
    label: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.toUtcMillis())
    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onDateChange(LocalDate.ofEpochDay(millis / MILLIS_PER_DAY))
                        }
                        showPicker = false
                    },
                    enabled = pickerState.selectedDateMillis != null,
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
    OutlinedTextField(
        value = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        singleLine = true,
        trailingIcon = {
            TextButton(onClick = { showPicker = true }) { Text("选择") }
        },
        modifier = modifier,
    )
}

@Composable
private fun FilteredTotalsBar(totals: com.cycling.beecount.domain.repository.TodayTotals) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "筛选支出 ¥${formatMoney(totals.expense)}",
            style = MaterialTheme.typography.titleMedium,
            color = ExpenseRed,
        )
        Text(
            text = "筛选收入 ¥${formatMoney(totals.income)}",
            style = MaterialTheme.typography.titleMedium,
            color = IncomeGreen,
        )
    }
}

@Composable
private fun LedgerList(
    entries: List<Entry>,
    onEntryClick: (Entry) -> Unit,
    modifier: Modifier = Modifier,
) {
    // entries 已按日期倒序（DAO 排序），groupBy 保持顺序 → 按天分组
    val groupedByDate: List<Pair<LocalDate, List<Entry>>> = remember(entries) {
        entries.groupBy { it.date }.toList()
    }
    LazyColumn(
        modifier = modifier,
        // bottom 留白让最后一项能滚到悬浮胶囊上方（覆盖式悬浮导航）
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groupedByDate.forEach { (date, dayEntries) ->
            item(key = "ledger-date-$date") {
                DayHeader(date, dayEntries)
            }
            items(dayEntries, key = { "ledger-entry-${it.id}" }) { entry ->
                LedgerEntryRow(entry, onEntryClick)
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, dayEntries: List<Entry>) {
    val netExpense = dayEntries.filter { it.type == EntryType.EXPENSE }.sumOf { it.amount } -
        dayEntries.filter { it.type == EntryType.REFUND }.sumOf { it.amount }
    val expense = netExpense.coerceAtLeast(0.0)
    val income = dayEntries.filter { it.type == EntryType.INCOME }.sumOf { it.amount }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
            style = MaterialTheme.typography.titleMedium,
        )
        Row {
            if (expense > 0) {
                Text(
                    text = "支 ¥${formatMoney(expense)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ExpenseRed,
                )
            }
            if (income > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "收 ¥${formatMoney(income)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IncomeGreen,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LedgerEntryRow(entry: Entry, onClick: (Entry) -> Unit) {
    Card(
        onClick = { onClick(entry) },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.categoryName, style = MaterialTheme.typography.bodyLarge)
                    entry.counterparty?.takeIf { it.isNotBlank() }?.let { cp ->
                        Text(
                            "$cp · ${entry.note.ifBlank { entry.amountRaw }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    } ?: Text(
                        entry.note.ifBlank { entry.amountRaw },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                when (entry.type) {
                    EntryType.EXPENSE -> Text(
                        text = "-¥${formatMoney(entry.amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = ExpenseRed,
                    )
                    EntryType.INCOME -> Text(
                        text = "+¥${formatMoney(entry.amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = IncomeGreen,
                    )
                    EntryType.REFUND -> Text(
                        text = "-¥${formatMoney(entry.amount)}（退）",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    EntryType.NEUTRAL -> Text(
                        text = "¥${formatMoney(entry.amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (entry.type == EntryType.EXPENSE && entry.isReimbursed) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "已报销",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (entry.tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    entry.tags.forEach { tag ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(tag.color))
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                tag.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(tag.color),
                            )
                        }
                    }
                }
            }
        }
    }
}
