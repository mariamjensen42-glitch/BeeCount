package com.cycling.beecount.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.cycling.beecount.domain.model.AiParseResult
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.Tag
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 确认卡片：展示 AI 草拟的账目，金额/类别/标签可编辑（Q17 + ADR 0007），
 * 类别选择支持输入新类别名（确认卡片内创建，Q13），日期可编辑。
 * [originalText] 为用户原话，作为备注展示（Q16：原文保留）。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationCard(
    result: AiParseResult,
    categories: List<Category>,
    tags: List<Tag>,
    originalText: String,
    onAmountChange: (Double) -> Unit,
    onCategoryChange: (String) -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onDateChange: (LocalDate) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = result.type ?: EntryType.EXPENSE
    // 退款/红字冲销对应原支出类别
    val typeCategories = categories.filter { it.type == (if (type == EntryType.REFUND) EntryType.EXPENSE else type) && !it.isHidden }

    var amountText by remember {
        mutableStateOf(result.amount?.let { formatMoney(it) } ?: "")
    }
    var categoryText by remember {
        mutableStateOf(result.categoryName.orEmpty())
    }
    var showDatePicker by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = result.date?.toUtcMillis(),
        selectableDates = PastAndPresentDates(today),
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDateMillis ->
                            onDateChange(LocalDate.ofEpochDay(selectedDateMillis / MILLIS_PER_DAY))
                        }
                        showDatePicker = false
                    },
                    enabled = datePickerState.selectedDateMillis != null,
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = when (type) {
                    EntryType.EXPENSE -> "记一笔支出"
                    EntryType.INCOME -> "记一笔收入"
                    EntryType.REFUND -> "记一笔退款"
                    EntryType.NEUTRAL -> "记一笔中性记录"
                },
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.height(12.dp))

            // 金额（可编辑）
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    amountText = input
                    input.toDoubleOrNull()?.let(onAmountChange)
                },
                label = { Text("金额（元）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // 类别：可编辑文本 + 快捷选择 chips（含自定义输入）
            OutlinedTextField(
                value = categoryText,
                onValueChange = { input ->
                    categoryText = input
                    onCategoryChange(input)
                },
                label = { Text("类别") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                typeCategories.forEach { category ->
                    FilterChip(
                        selected = categoryText == category.name,
                        onClick = {
                            categoryText = category.name
                            onCategoryChange(category.name)
                        },
                        leadingIcon = {
                            if (category.icon.isNotEmpty()) {
                                Text(category.icon)
                            } else {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color(category.color))
                                )
                            }
                        },
                        label = { Text(category.displayName) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 标签：多选 chips（带颜色圆点）+ 新建（ADR 0007：卡片建标）
            Text(
                text = "标签（可选）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            var newTagText by remember { mutableStateOf("") }
            var showNewTagInput by remember { mutableStateOf(false) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = result.tags.contains(tag.name),
                        onClick = {
                            val next = if (result.tags.contains(tag.name)) {
                                result.tags - tag.name
                            } else {
                                (result.tags + tag.name).distinct()
                            }
                            onTagsChange(next)
                        },
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
                if (showNewTagInput) {
                    OutlinedTextField(
                        value = newTagText,
                        onValueChange = { newTagText = it },
                        label = { Text("新标签名") },
                        singleLine = true,
                        modifier = Modifier.width(140.dp),
                    )
                    TextButton(
                        onClick = {
                            val name = newTagText.trim()
                            if (name.isNotEmpty()) {
                                onTagsChange((result.tags + name).distinct())
                            }
                            newTagText = ""
                            showNewTagInput = false
                        },
                    ) { Text("添加") }
                } else {
                    TextButton(onClick = { showNewTagInput = true }) { Text("+ 新建") }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 只读字段：金额原文、备注
            Text(
                text = "金额原文：${result.amountRaw.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "日期：${result.date?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showDatePicker = true },
            )
            result.counterparty?.let { counterparty ->
                Text(
                    text = "交易对方：$counterparty",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (type == EntryType.EXPENSE && result.isReimbursed) {
                Text(
                    text = "已报销（该笔支出已标记为报销）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "备注：$originalText",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onConfirm) { Text("确认") }
            }
        }
    }
}

internal const val MILLIS_PER_DAY = 86_400_000L

internal fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

internal class PastAndPresentDates(
    private val latestDate: LocalDate,
) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        LocalDate.ofEpochDay(utcTimeMillis / MILLIS_PER_DAY) <= latestDate

    override fun isSelectableYear(year: Int): Boolean = year <= latestDate.year
}
