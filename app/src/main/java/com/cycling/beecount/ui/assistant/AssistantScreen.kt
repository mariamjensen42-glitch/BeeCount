package com.cycling.beecount.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.ui.theme.ExpenseRed
import com.cycling.beecount.ui.theme.IncomeGreen

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
            TopAppBar(
                title = { Text("AI 记账助手") },
                actions = {
                    IconButton(onClick = { onEvent(AssistantEvent.OpenKeySetup) }) {
                        Text("设置")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            TodayTotalsSection(uiState, Modifier.padding(horizontal = 16.dp))
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            // 今日已记列表 + 对话流共用一个 LazyColumn，账目列表在上、对话在下
            LazyColumn(
                modifier = Modifier.weight(1f),
                state = rememberLazyListState(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (uiState.todayEntries.isNotEmpty()) {
                    item { Text("今日已记", style = MaterialTheme.typography.titleMedium) }
                    items(uiState.todayEntries, key = { "entry-${it.id}" }) { entry ->
                        TodayEntryRow(entry)
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                    item { HorizontalDivider() }
                    item { Spacer(Modifier.height(8.dp)) }
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
                            originalText = uiState.pendingOriginalText,
                            onAmountChange = { amount -> onEvent(AssistantEvent.EditAmount(amount)) },
                            onCategoryChange = { name -> onEvent(AssistantEvent.EditCategory(name)) },
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
                    .padding(16.dp),
            )
        }
    }

    if (uiState.showKeySetup) {
        ApiKeyDialog(
            onSave = { key -> onEvent(AssistantEvent.SaveApiKey(key)) },
            onDismiss = { onEvent(AssistantEvent.CloseKeySetup) },
        )
    }
}

@Composable
private fun ApiKeyDialog(
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var keyText by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置 DeepSeek API Key") },
        text = {
            Column {
                Text(
                    "这是个人自用应用：请输入你自己的 DeepSeek API Key，请求将直接发往 api.deepseek.com。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(keyText) }, enabled = keyText.isNotBlank()) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun TodayTotalsSection(uiState: AssistantUiState, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = "今日支出 ¥${formatMoney(uiState.todayTotals.expense)}",
            style = MaterialTheme.typography.titleMedium,
            color = ExpenseRed,
        )
        Text(
            text = "今日收入 ¥${formatMoney(uiState.todayTotals.income)}",
            style = MaterialTheme.typography.titleMedium,
            color = IncomeGreen,
        )
    }
}

@Composable
private fun TodayEntryRow(entry: com.cycling.beecount.domain.model.Entry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
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
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Text(text, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun AssistantBubble(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Text(text, modifier = Modifier.padding(12.dp))
        }
    }
}

@Composable
private fun SavedRow(entry: com.cycling.beecount.domain.model.Entry, onUndo: (Long) -> Unit) {
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
