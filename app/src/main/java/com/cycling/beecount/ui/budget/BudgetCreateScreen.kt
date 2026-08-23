package com.cycling.beecount.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.cycling.beecount.domain.model.BudgetCycle
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.ui.theme.BeeCountShapes
import com.cycling.beecount.ui.theme.Spacing
import com.woowla.compose.icon.collections.heroicons.Heroicons
import com.woowla.compose.icon.collections.heroicons.heroicons.Outline
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ArrowLeft

/**
 * 新建预算独立页：从预算管理页「+」进入。
 * 相比弹窗，独立页面有充足空间容纳周期/维度网格/金额/结转，分行排版更清晰。
 */
@Composable
fun BudgetCreateRoute(
    viewModel: ManageBudgetsViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    BudgetCreateScreen(
        topLevelCategories = categories.filter { it.parentId == null },
        onBack = onBack,
        onCreate = { cycle, amount, categoryName, lengthDays, carryOver ->
            viewModel.create(cycle, amount, categoryName, lengthDays, carryOver)
            onBack()
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun BudgetCreateScreen(
    topLevelCategories: List<Category>,
    onBack: () -> Unit,
    onCreate: (cycle: BudgetCycle, amount: Double, categoryName: String?, lengthDays: Int, carryOver: Boolean) -> Unit,
) {
    var selectedCycle by remember { mutableStateOf(BudgetCycle.MONTHLY) }
    var customDays by remember { mutableStateOf("30") }
    var dimension by remember { mutableStateOf<String?>(null) } // null = 总预算
    var amountText by remember { mutableStateOf("") }
    var carryOver by remember { mutableStateOf(true) }
    var amountError by remember { mutableStateOf(false) }

    fun submit() {
        val amount = amountText.toDoubleOrNull() ?: 0.0
        if (amount <= 0) {
            amountError = true
            return
        }
        val days = customDays.toIntOrNull()?.coerceAtLeast(1) ?: 30
        onCreate(selectedCycle, amount, dimension, days, carryOver)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新建预算") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Heroicons.Outline.ArrowLeft, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = ::submit) { Text("保存") }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(innerPadding)
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        ) {
            Text("周期", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                BudgetCycle.entries.forEachIndexed { index, cycle ->
                    SegmentedButton(
                        selected = selectedCycle == cycle,
                        onClick = { selectedCycle = cycle },
                        shape = SegmentedButtonDefaults.itemShape(index, BudgetCycle.entries.size),
                    ) { Text(cycle.label) }
                }
            }

            if (selectedCycle == BudgetCycle.CUSTOM_DAYS) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = customDays,
                    onValueChange = { customDays = it.filter { c -> c.isDigit() } },
                    label = { Text("周期天数") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("维度", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            val allDims = buildList<String?> {
                add(null) // 总预算
                addAll(topLevelCategories.map { it.name })
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 3,
            ) {
                allDims.forEach { dimName ->
                    val isTotal = dimName == null
                    val cat = topLevelCategories.firstOrNull { it.name == dimName }
                    DimensionCell(
                        label = if (isTotal) "总预算" else (cat?.displayName ?: dimName.orEmpty()),
                        icon = if (isTotal) null else cat?.icon,
                        selected = dimension == dimName,
                        onClick = { dimension = dimName },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("金额（¥）", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                isError = amountError,
                supportingText = if (amountError) { { Text("请输入大于 0 的金额") } } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("余额结转", style = MaterialTheme.typography.bodyLarge)
                    Text("上期剩余滚入本期", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = carryOver, onCheckedChange = { carryOver = it })
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "提示：预算消费只统计支出（不含收入/中性）；分类预算会覆盖该分类及其全部子分类。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 维度等宽网格格子 */
@Composable
private fun DimensionCell(
    label: String,
    icon: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(BeeCountShapes.extraSmall)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Text(
            if (icon.isNullOrEmpty()) "🏷️" else icon,
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        )
    }
}
