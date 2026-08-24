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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.Tag
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 编辑账目底部弹层：展示已有账目的金额/类别/日期/备注/标签，可整体修改后保存。
 * 与 [ConfirmationCard] 同风格：金额/类别/标签可编辑、日期选择器、类别 chips、标签 chips。
 * 额外支持编辑类型（支出/收入）与备注。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditEntrySheet(
    entry: Entry,
    categories: List<Category>,
    tags: List<Tag>,
    onSave: (editedType: EntryType, editedAmount: Double, editedCategoryName: String, editedDate: LocalDate, editedNote: String, tagNames: List<String>, editedCounterparty: String?, editedIsReimbursed: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var type by remember(entry.id) { mutableStateOf(entry.type) }
    var amountText by remember(entry.id) { mutableStateOf(formatMoney(entry.amount)) }
    var categoryText by remember(entry.id) { mutableStateOf(entry.categoryName) }
    var noteText by remember(entry.id) { mutableStateOf(entry.note) }
    var counterpartyText by remember(entry.id) { mutableStateOf(entry.counterparty.orEmpty()) }
    var selectedTagNames by remember(entry.id) { mutableStateOf<Set<String>>(entry.tags.map { it.name }.toSet()) }
    var isReimbursed by remember(entry.id) { mutableStateOf(entry.isReimbursed) }
    var showDatePicker by remember(entry.id) { mutableStateOf(false) }
    var showNewTagInput by remember(entry.id) { mutableStateOf(false) }
    var newTagText by remember(entry.id) { mutableStateOf("") }
    val today = LocalDate.now()
    var date by remember(entry.id) { mutableStateOf(entry.date) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = entry.date.toUtcMillis(),
        selectableDates = PastAndPresentDates(today),
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedDateMillis ->
                            date = LocalDate.ofEpochDay(selectedDateMillis / MILLIS_PER_DAY)
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

    val typeCategories = categories.filter { it.type == type && !it.isHidden }

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
            Text("编辑账目", style = MaterialTheme.typography.titleLarge)

            Spacer(Modifier.height(12.dp))

            // 类型切换（支出/收入/退款）
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    EntryType.EXPENSE to "支出",
                    EntryType.INCOME to "收入",
                    EntryType.REFUND to "退款",
                ).forEach { (t, label) ->
                    FilterChip(
                        selected = type == t,
                        onClick = {
                            type = t
                            categoryText = ""
                        },
                        label = { Text(label) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 金额（可编辑）
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("金额（元）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // 类别：可编辑文本 + 快捷选择 chips（含自定义输入）
            OutlinedTextField(
                value = categoryText,
                onValueChange = { categoryText = it },
                label = { Text("类别") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                typeCategories.forEach { category ->
                    FilterChip(
                        selected = categoryText == category.name,
                        onClick = { categoryText = category.name },
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tags.forEach { tag ->
                    FilterChip(
                        selected = tag.name in selectedTagNames,
                        onClick = {
                            selectedTagNames = if (tag.name in selectedTagNames) {
                                selectedTagNames - tag.name
                            } else {
                                selectedTagNames + tag.name
                            }
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
                                selectedTagNames = selectedTagNames + name
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

            // 报销标记（仅支出显示）
            if (type == EntryType.EXPENSE) {
                FilterChip(
                    selected = isReimbursed,
                    onClick = { isReimbursed = !isReimbursed },
                    label = { Text(if (isReimbursed) "已报销" else "未报销") },
                )
                Spacer(Modifier.height(12.dp))
            }

            // 日期（可编辑）
            Text(
                text = "日期：${date.format(DateTimeFormatter.ISO_LOCAL_DATE)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { showDatePicker = true },
            )

            Spacer(Modifier.height(8.dp))

            // 备注（可编辑）
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                label = { Text("备注") },
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            // 交易对方（可编辑，可留空）
            OutlinedTextField(
                value = counterpartyText,
                onValueChange = { counterpartyText = it },
                label = { Text("交易对方（可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        val amount = amountText.toDoubleOrNull()
                        if (amount != null && amount > 0 && categoryText.isNotBlank()) {
                            onSave(
                                type,
                                amount,
                                categoryText.trim(),
                                date,
                                noteText.trim(),
                                selectedTagNames.toList(),
                                counterpartyText.trim().takeIf { it.isNotEmpty() },
                                isReimbursed,
                            )
                        }
                    },
                ) { Text("保存") }
            }
        }
    }
}


