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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.ui.assistant.formatMoney
import com.cycling.beecount.ui.common.TagManageDialog
import com.cycling.beecount.ui.theme.ExpenseRed
import com.cycling.beecount.ui.theme.IncomeGreen
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun LedgerRoute(viewModel: LedgerViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredEntries by viewModel.filteredEntries.collectAsStateWithLifecycle()
    val filteredTotals by viewModel.filteredTotals.collectAsStateWithLifecycle()
    LedgerScreen(
        uiState = uiState,
        filteredEntries = filteredEntries,
        filteredTotals = filteredTotals,
        onEvent = viewModel::onEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    uiState: LedgerUiState,
    filteredEntries: List<Entry>,
    filteredTotals: com.cycling.beecount.domain.repository.TodayTotals,
    onEvent: (LedgerEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("账本") },
                actions = {
                    TextButton(onClick = { onEvent(LedgerEvent.OpenTagManage) }) { Text("管理标签") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TagFilterRow(uiState, onEvent)
            if (uiState.selectedTagIds.isNotEmpty()) {
                FilteredTotalsBar(filteredTotals)
            }
            if (filteredEntries.isEmpty()) {
                Text(
                    text = if (uiState.entries.isEmpty()) "还没有账目，去今日页记一笔吧" else "没有同时带这些标签的账目",
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LedgerList(filteredEntries, Modifier.weight(1f))
            }
        }
    }
    if (uiState.showTagManage) {
        TagManageDialog(
            tags = uiState.allTags,
            onClose = { onEvent(LedgerEvent.CloseTagManage) },
            onRename = { id, name -> onEvent(LedgerEvent.RenameTag(id, name)) },
            onUpdateColor = { id, color -> onEvent(LedgerEvent.UpdateTagColor(id, color)) },
            onDelete = { id -> onEvent(LedgerEvent.DeleteTag(id)) },
        )
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
private fun LedgerList(entries: List<Entry>, modifier: Modifier = Modifier) {
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
                LedgerEntryRow(entry)
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, dayEntries: List<Entry>) {
    val expense = dayEntries.filter { it.type == EntryType.EXPENSE }.sumOf { it.amount }
    val income = dayEntries.filter { it.type == EntryType.INCOME }.sumOf { it.amount }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = date.format(DateTimeFormatter.ofPattern("M月d日")),
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
private fun LedgerEntryRow(entry: Entry) {
    Card(modifier = Modifier.fillMaxWidth()) {
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
                Column {
                    Text(entry.categoryName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        entry.note.ifBlank { entry.amountRaw },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "${if (entry.type == EntryType.EXPENSE) "-" else "+"}¥${formatMoney(entry.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (entry.type == EntryType.EXPENSE) ExpenseRed else IncomeGreen,
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
