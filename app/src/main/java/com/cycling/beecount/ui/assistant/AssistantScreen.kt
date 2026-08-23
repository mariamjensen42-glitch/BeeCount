package com.cycling.beecount.ui.assistant

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.beecount.R
import com.cycling.beecount.domain.model.BudgetProgress
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.ui.FLOATING_PILL_CLEARANCE
import com.cycling.beecount.ui.theme.ComponentDefaults
import com.cycling.beecount.ui.theme.Dimens
import com.cycling.beecount.ui.theme.ExpenseRed
import com.cycling.beecount.ui.theme.TerminalCyan
import com.cycling.beecount.ui.theme.IncomeGreen
import com.cycling.beecount.ui.theme.OnTerminalCyan
import com.cycling.beecount.ui.theme.Spacing
import com.woowla.compose.icon.collections.heroicons.Heroicons
import com.woowla.compose.icon.collections.heroicons.heroicons.Outline
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.Camera
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ChevronDown
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ChevronUp
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.PaperAirplane
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.Photo
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun AssistantRoute(
    prefillDate: LocalDate? = null,
    onEntrySaved: (LocalDate) -> Unit = {},
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(prefillDate) {
        viewModel.onEvent(AssistantEvent.SetTargetDate(prefillDate))
    }
    LaunchedEffect(uiState.savedEntryDate) {
        uiState.savedEntryDate?.let {
            viewModel.onEvent(AssistantEvent.ConsumeSavedEntryDate)
            if (prefillDate != null) onEntrySaved(it)
        }
    }
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
    val context = LocalContext.current
    val hasCameraHardware = remember {
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY)
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onEvent(AssistantEvent.OcrImageSelected(uri))
    }

    if (uiState.showCameraSheet) {
        CameraCaptureSheet(
            onImageCaptured = { uri ->
                onEvent(AssistantEvent.DismissCamera)
                onEvent(AssistantEvent.OcrImageSelected(uri))
            },
            onDismiss = { onEvent(AssistantEvent.DismissCamera) },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("今日") },
                // OCR 双入口放顶栏，输入行只留输入框与发送，避免一行四个元素拥挤
                actions = {
                    if (hasCameraHardware) {
                        IconButton(onClick = { onEvent(AssistantEvent.ShowCamera) }) {
                            Icon(
                                imageVector = Heroicons.Outline.Camera,
                                contentDescription = stringResource(R.string.cd_camera_capture),
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            imagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    ) {
                        Icon(
                            imageVector = Heroicons.Outline.Photo,
                            contentDescription = stringResource(R.string.cd_ocr_pick_image),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                // 底部留出悬浮胶囊高度，输入栏不被胶囊盖住（常量已含胶囊底部留白）
                .padding(bottom = FLOATING_PILL_CLEARANCE),
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
                        WelcomeCard(
                            onExampleSubmit = { text -> onEvent(AssistantEvent.SubmitInput(text)) },
                        )
                    }
                }

                items(uiState.messages, key = { "message-${it.id}" }) { message ->
                    when (message) {
                        is AssistantMessage.User -> UserBubble(message.text, Modifier.animateItem())
                        is AssistantMessage.Assistant -> AssistantBubble(message.text, Modifier.animateItem())
                        is AssistantMessage.Saved -> SavedRow(
                            entry = message.entry,
                            onUndo = { id -> onEvent(AssistantEvent.Undo(id)) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                uiState.pendingResult?.let { result ->
                    item(key = "pending-card") {
                        ConfirmationCard(
                            result = result,
                            categories = uiState.categories,
                            tags = uiState.allTags,
                            originalText = uiState.pendingOriginalText,
                            onAmountChange = { amount -> onEvent(AssistantEvent.EditAmount(amount)) },
                            onCategoryChange = { name -> onEvent(AssistantEvent.EditCategory(name)) },
                            onTagsChange = { tags -> onEvent(AssistantEvent.EditTags(tags)) },
                            onDateChange = { date -> onEvent(AssistantEvent.EditDate(date)) },
                            onConfirm = { onEvent(AssistantEvent.Confirm) },
                            onDismiss = { onEvent(AssistantEvent.DismissCard) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                if (uiState.isParsing) {
                    item(key = "parsing") {
                        Row(
                            modifier = Modifier.animateItem(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                targetDate = uiState.targetDate,
                onSend = { text -> onEvent(AssistantEvent.SubmitInput(text)) },
                modifier = Modifier
                    .fillMaxWidth()
                    // 底部留白交给外层 Column 的 FLOATING_PILL_CLEARANCE，这里只留上下间距
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
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
        shape = ComponentDefaults.cardShape,
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
            if (uiState.budgetProgress.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                BudgetProgressSection(uiState.budgetProgress)
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
                Icon(
                    imageVector = if (expanded) Heroicons.Outline.ChevronUp else Heroicons.Outline.ChevronDown,
                    contentDescription = if (expanded) "收起今日已记" else "展开今日已记",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(tween(220)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(200)),
            ) {
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

/** 首次进入的引导卡：一句话介绍 + 三条示例快捷输入。 */
@Composable
private fun WelcomeCard(onExampleSubmit: (String) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = ComponentDefaults.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(TerminalCyan),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🐝", fontSize = 14.sp)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "我是 BeeCount 记账助手",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "说一句收支就能帮你自动记下来，也可以点下面的示例试试：",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            val examples = listOf(
                "昨天打车花了30块",
                "老板发了500红包",
                "早饭 12 元",
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                examples.chunked(2).forEach { rowExamples ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowExamples.forEach { example ->
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onExampleSubmit(example) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ) {
                                Text(
                                    text = example,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                )
                            }
                        }
                        // 奇数示例时补一个占位，保持行内对齐
                        if (rowExamples.size == 1) Spacer(Modifier.weight(1f))
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
        when (entry.type) {
            EntryType.EXPENSE -> Text(
                text = "-¥${formatMoney(entry.amount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = ExpenseRed,
            )
            EntryType.INCOME -> Text(
                text = "+¥${formatMoney(entry.amount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = IncomeGreen,
            )
            EntryType.NEUTRAL -> Text(
                text = "¥${formatMoney(entry.amount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 用户消息：右对齐，非对称圆角（右下角收进，聊天气泡感） */
@Composable
private fun UserBubble(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
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
private fun AssistantBubble(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(TerminalCyan),
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
private fun SavedRow(entry: Entry, onUndo: (Long) -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
    targetDate: LocalDate?,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = targetDate?.let { date -> { Text("本次记入：${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}") } },
            placeholder = { Text("例如：昨天打车花了30块") },
            singleLine = true,
            enabled = enabled,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TerminalCyan,
                cursorColor = TerminalCyan,
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(4.dp))
        androidx.compose.material3.FilledIconButton(
            onClick = {
                onSend(text)
                text = ""
            },
            enabled = enabled && text.isNotBlank(),
            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                containerColor = TerminalCyan,
                contentColor = OnTerminalCyan,
            ),
        ) {
            Icon(
                imageVector = Heroicons.Outline.PaperAirplane,
                contentDescription = stringResource(R.string.cd_send_message),
            )
        }
    }
}

/**
 * 今日页预算进度区块：展示所有启用预算的当前周期进度（进度条 + 已花/预算 + 超支红）。
 */
@Composable
private fun BudgetProgressSection(progress: List<BudgetProgress>) {
    val enabled = progress.filter { it.budget.enabled }
    if (enabled.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        enabled.forEach { p ->
            val dimensionName = p.budget.categoryName ?: "总预算"
            val isOver = p.isOver
            val fraction = p.fraction
            val color = when {
                isOver -> MaterialTheme.colorScheme.error
                fraction >= 0.9 -> ExpenseRed.copy(alpha = 0.5f)
                else -> TerminalCyan
            }
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = dimensionName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (isOver) "已超支 ¥${formatMoney(p.overAmount)}"
                        else "¥${formatMoney(p.spent)} / ¥${formatMoney(p.base)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { fraction.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}
