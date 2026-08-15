package com.cycling.beecount.ui.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.ui.theme.ExpenseRed
import com.cycling.beecount.ui.theme.HoneyAmber
import com.cycling.beecount.ui.theme.IncomeGreen
import java.time.format.DateTimeFormatter

@Composable
fun AssistantRoute(
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AssistantScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
    )
}

/**
 * 今日页布局（对话优先）：
 * 顶部为可展开的今日摘要卡（支出/收入 + 今日已记），主体为对话流，
 * 底部为输入框。已记反馈以 SavedRow 出现在对话流中，历史在账本页查看。
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    uiState: AssistantUiState,
    onEvent: (AssistantEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("AI 记账助手") })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = rememberLazyListState(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 今日概览是滚动流首项，随对话一起滚动（讨论结论）
                item {
                    TodayOverviewCard(uiState)
                }

                if (uiState.messages.isEmpty() && uiState.pendingResult == null && !uiState.isParsing) {
                    item {
                        AssistantBubble(
                            "你好呀！我是 BeeCount 记账助手 🐝\n告诉我一笔收支就能帮你记下，比如：昨天打车花了30块"
                        )
                    }
                }

                items(uiState.messages, key = { "message-${it.id}" }) { message ->
                    when (message) {
                        is AssistantMessage.User -> UserBubble(message.text)
                        is AssistantMessage.Assistant -> AssistantBubble(message.text)
                        is AssistantMessage.Saved -> SavedRow(message.entry) { id ->
                            onEvent(AssistantEvent.Undo(id))
                        }
                    }
                }

                uiState.pendingResult?.let { result ->
                    item {
                        ConfirmationCard(
                            result = result,
                            categories = uiState.categories,
                            tags = uiState.allTags,
                            originalText = uiState.pendingOriginalText,
                            onAmountChange = { amount -> onEvent(AssistantEvent.EditAmount(amount)) },
                            onCategoryChange = { name -> onEvent(AssistantEvent.EditCategory(name)) },
                            onTagsChange = { tags -> onEvent(AssistantEvent.EditTags(tags)) },
                            onConfirm = { onEvent(AssistantEvent.Confirm) },
                            onDismiss = { onEvent(AssistantEvent.DismissCard) },
                        )
                    }
                }

                if (uiState.isParsing) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(24.dp).height(24.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("正在理解…")
                        }
                    }
                }
            }

            InputBar(
                enabled = !uiState.isParsing,
                onSend = { text -> onEvent(AssistantEvent.SubmitInput(text)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 64.dp),
            )
        }
    }
}

/**
 * 今日概览卡（滚动流首项）：日期/笔数 + 支出收入 + 支出分类 Top3 + 可展开的今日已记明细。
 */
@Composable
private fun TodayOverviewCard(uiState: AssistantUiState) {
    var expanded by remember { mutableStateOf(false) }
    val entryCount = uiState.todayEntries.size
    val topCategories = uiState.todayEntries
        .filter { it.type == EntryType.EXPENSE }
        .groupBy { it.categoryName }
        .mapValues { (_, entries) -> entries.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }
        .take(3)
        .map { it.first }
    val dateText = uiState.today.format(
        DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINA)
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "今日 · $dateText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$entryCount 笔",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "支出 ¥${formatMoney(uiState.todayTotals.expense)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = ExpenseRed,
                )
                Text(
                    text = "收入 ¥${formatMoney(uiState.todayTotals.income)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = IncomeGreen,
                )
            }
            if (topCategories.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    topCategories.forEach { name ->
                        CategoryTag(name)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "今日已记 $entryCount 笔",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (expanded) "收起 ▴" else "展开 ▾",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column {
                    if (entryCount == 0) {
                        Text(
                            text = "今天还没记账，说一句话试试",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    } else {
                        uiState.todayEntries.forEach { entry ->
                            SummaryEntryRow(entry)
                        }
                    }
                }
            }
        }
    }
}

/** 分类小标签（支出分类 Top3） */
@Composable
private fun CategoryTag(name: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun SummaryEntryRow(entry: Entry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.categoryName, style = MaterialTheme.typography.bodyMedium)
            Text(
                entry.note.ifBlank { entry.amountRaw },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${if (entry.type == EntryType.EXPENSE) "-" else "+"}¥${formatMoney(entry.amount)}",
            style = MaterialTheme.typography.bodyMedium,
            color = if (entry.type == EntryType.EXPENSE) ExpenseRed else IncomeGreen,
        )
    }
}

/** 用户消息：右对齐，非对称圆角（右下角收进，聊天气泡感） */
@Composable
private fun UserBubble(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomEnd = 4.dp,
                bottomStart = 16.dp,
            ),
        ) {
            Text(text, modifier = Modifier.padding(12.dp))
        }
    }
}

/** 助手消息：带头像（🐝），非对称圆角（左下角收进） */
@Composable
private fun AssistantBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(HoneyAmber),
            contentAlignment = Alignment.Center,
        ) {
            Text("🐝", fontSize = 14.sp)
        }
        Spacer(Modifier.width(8.dp))
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomEnd = 16.dp,
                bottomStart = 4.dp,
            ),
        ) {
            Text(text, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun SavedRow(entry: Entry, onUndo: (Long) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已记：${entry.categoryName} ¥${formatMoney(entry.amount)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { onUndo(entry.id) }) { Text("撤销") }
        }
    }
}

@Composable
private fun InputBar(
    enabled: Boolean,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("例如：昨天打车花了30块") },
            singleLine = true,
            enabled = enabled,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = HoneyAmber,
                cursorColor = HoneyAmber,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = {
                onSend(text)
                text = ""
            },
            enabled = enabled && text.isNotBlank(),
        ) {
            Text("记一笔")
        }
    }
}
