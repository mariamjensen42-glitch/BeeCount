package com.cycling.beecount.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.cycling.beecount.domain.model.AiParseResult
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.EntryType
import java.time.format.DateTimeFormatter

/**
 * 确认卡片：展示 AI 草拟的账目，金额与类别可编辑（Q17），
 * 类别选择支持输入新类别名（确认卡片内创建，Q13），日期/备注只读。
 * [originalText] 为用户原话，作为备注展示（Q16：原文保留）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfirmationCard(
    result: AiParseResult,
    categories: List<Category>,
    originalText: String,
    onAmountChange: (Double) -> Unit,
    onCategoryChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val type = result.type ?: EntryType.EXPENSE
    val typeCategories = categories.filter { it.type == type }

    var amountText by remember {
        mutableStateOf(result.amount?.let { formatMoney(it) } ?: "")
    }
    var categoryText by remember {
        mutableStateOf(result.categoryName.orEmpty())
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Text(
                text = if (type == EntryType.EXPENSE) "记一笔支出" else "记一笔收入",
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
                        label = { Text(category.name) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 只读字段：金额原文、日期、备注
            Text(
                text = "金额原文：${result.amountRaw.orEmpty()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "日期：${result.date?.format(DateTimeFormatter.ISO_LOCAL_DATE) ?: "—"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

