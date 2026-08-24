package com.cycling.beecount.ui.budget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.spacedBy
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.beecount.domain.model.Budget
import com.cycling.beecount.domain.model.BudgetCycle
import com.cycling.beecount.domain.model.BudgetException
import com.cycling.beecount.domain.model.BudgetProgress
import com.cycling.beecount.domain.model.BudgetForecast
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.ui.theme.ComponentDefaults
import com.cycling.beecount.ui.theme.Dimens
import com.cycling.beecount.ui.theme.Spacing
import com.woowla.compose.icon.collections.heroicons.Heroicons
import com.woowla.compose.icon.collections.heroicons.heroicons.Outline
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ArrowLeft
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.PencilSquare
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.Plus
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.Trash
import java.time.LocalDate

/** 进度警示阈值：>= 此占比视为「接近超支」 */
private const val WARN_FRACTION = 0.9

private fun fmt(amount: Double): String = "%.2f".format(amount)

/** 超支红 */
private val ErrorColor @Composable get() = MaterialTheme.colorScheme.error

@Composable
fun BudgetManageRoute(
    viewModel: ManageBudgetsViewModel = hiltViewModel(),
    onBack: () -> Unit,
    onCreateBudget: () -> Unit,
) {
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val exceptions by viewModel.exceptions.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val forecast by viewModel.forecast.collectAsStateWithLifecycle()
    BudgetManageScreen(
        progress = progress,
        forecast = forecast,
        exceptions = exceptions,
        topLevelCategories = categories.filter { it.parentId == null },
        onBack = onBack,
        onCreateBudget = onCreateBudget,
        onUpdateAmount = viewModel::updateAmount,
        onUpdateCarryOver = viewModel::updateCarryOver,
        onUpdateEnabled = viewModel::updateEnabled,
        onDelete = viewModel::delete,
        onAddException = viewModel::addException,
        onRemoveException = viewModel::removeException,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetManageScreen(
    progress: List<BudgetProgress>,
    forecast: List<BudgetForecast>,
    exceptions: List<BudgetException>,
    topLevelCategories: List<Category>,
    onBack: () -> Unit,
    onCreateBudget: () -> Unit,
    onUpdateAmount: (id: Long, amount: Double) -> Unit,
    onUpdateCarryOver: (id: Long, carryOver: Boolean) -> Unit,
    onUpdateEnabled: (id: Long, enabled: Boolean) -> Unit,
    onDelete: (id: Long) -> Unit,
    onAddException: (LocalDate) -> Unit,
    onRemoveException: (LocalDate) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理预算") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Heroicons.Outline.ArrowLeft, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onCreateBudget) {
                        Icon(Heroicons.Outline.Plus, contentDescription = "新建预算")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (progress.isEmpty() && exceptions.isEmpty()) {
            EmptyState(onAdd = onCreateBudget, modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = spacedBy(10.dp),
            ) {
                if (progress.isEmpty()) {
                    item { Text("还没有预算", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    item {
                        Text("预算", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    items(progress, key = { it.budget.id }) { p ->
                        BudgetCard(
                            progress = p,
                            forecast = forecast.firstOrNull { it.progress.budget.id == p.budget.id },
                            topLevelCategories = topLevelCategories,
                            onUpdateAmount = onUpdateAmount,
                            onUpdateCarryOver = onUpdateCarryOver,
                            onUpdateEnabled = onUpdateEnabled,
                            onDelete = onDelete,
                        )
                    }
                }
                if (exceptions.isNotEmpty()) {
                    item {
                        Text("预算例外日", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    items(exceptions, key = { it.date.toString() }) { e ->
                        ExceptionRow(date = e.date, onRemove = { onRemoveException(e.date) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("还没有预算", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "为总支出或某个分类设定周期性预算，首页与这里都会展示进度",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onAdd) { Text("+ 新建预算") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetCard(
    progress: BudgetProgress,
    forecast: BudgetForecast?,
    topLevelCategories: List<Category>,
    onUpdateAmount: (id: Long, amount: Double) -> Unit,
    onUpdateCarryOver: (id: Long, carryOver: Boolean) -> Unit,
    onUpdateEnabled: (id: Long, enabled: Boolean) -> Unit,
    onDelete: (id: Long) -> Unit,
) {
    val budget = progress.budget
    var editOpen by remember(budget.id) { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }

    val dimensionName = when {
        budget.categoryName == null -> "总预算"
        else -> topLevelCategories.firstOrNull { it.name == budget.categoryName }?.displayName ?: budget.categoryName
    }
    val fraction = progress.fraction
    val color = when {
        progress.isOver -> MaterialTheme.colorScheme.error
        fraction >= WARN_FRACTION -> colorOf(0xFFFFA726) // 橙
        else -> MaterialTheme.colorScheme.primary
    }
    val cycleLabel = if (budget.cycle == BudgetCycle.CUSTOM_DAYS) {
        "每 ${budget.lengthDays} 天"
    } else {
        budget.cycle.label
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = ComponentDefaults.cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (progress.isOver) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 环形进度
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { fraction.toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxSize(),
                        color = color,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeWidth = 5.dp,
                    )
                    Text(
                        "${(fraction * 100).toInt().coerceAtLeast(0)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = color,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(dimensionName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "$cycleLabel · 预算 ¥${fmt(budget.amount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (progress.isOver) "已超支 ¥${fmt(progress.overAmount)}"
                        else "已花 ¥${fmt(progress.spent)} / 可花 ¥${fmt(progress.base)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (progress.isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 操作
                IconButton(onClick = { editOpen = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Heroicons.Outline.PencilSquare, contentDescription = "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { deleteOpen = true }, modifier = Modifier.size(34.dp)) {
                    Icon(Heroicons.Outline.Trash, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (progress.isOver) "日均 0（已超支）"
                    else "剩余 ¥${fmt(progress.available.coerceAtLeast(0.0))} · 日均 ¥${fmt(progress.dailyAllowance)}（${progress.remainingDays} 天）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text("结转", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Switch(checked = budget.carryOver, onCheckedChange = { onUpdateCarryOver(budget.id, it) })
            }
            if (forecast != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    forecast.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (forecast.willOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (editOpen) {
        EditBudgetAmountDialog(
            budget = budget,
            onDismiss = { editOpen = false },
            onConfirm = { newAmount ->
                if (newAmount > 0) onUpdateAmount(budget.id, newAmount)
                editOpen = false
            },
        )
    }
    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("删除该预算？") },
            text = { Text("「$dimensionName」（$cycleLabel）将从预算中被移除。") },
            confirmButton = {
                TextButton(onClick = { deleteOpen = false }) { Text("取消") }
                TextButton(onClick = { onDelete(budget.id); deleteOpen = false }) { Text("删除") }
            },
        )
    }
}

@Composable
private fun ExceptionRow(date: LocalDate, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(color = MaterialTheme.colorScheme.error, text = "·")
        Spacer(Modifier.width(8.dp))
        Text(
            date.toString(),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            "该日支出不计入预算",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
            Icon(Heroicons.Outline.Trash, contentDescription = "移除例外日", tint = MaterialTheme.colorScheme.error)
        }
    }
}

/** 编辑预算金额对话框 */
@Composable
private fun EditBudgetAmountDialog(
    budget: Budget,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var amountText by remember(budget.id) { mutableStateOf(fmt(budget.amount)) }
    var error by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑预算金额") },
        text = {
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text("预算金额（¥）" ) },
                isError = error,
                supportingText = if (error) { { Text("请输入大于 0 的金额") } } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
            TextButton(
                onClick = {
                    val v = amountText.toDoubleOrNull() ?: 0.0
                    if (v <= 0) { error = true } else { onConfirm(v) }
                },
            ) { Text("保存") }
        },
    )
}

/** 维度选项行（选中态高亮） */
/** 简单色值辅助（避免每次写 Color(0xFF…)） */
private fun colorOf(argb: Long): Color = Color(argb)
