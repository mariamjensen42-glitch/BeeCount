package com.cycling.beecount.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.model.QuickTemplate
import com.cycling.beecount.ui.theme.BeeCountShapes
import com.cycling.beecount.ui.theme.Spacing
import com.cycling.beecount.ui.assistant.formatMoney
import com.woowla.compose.icon.collections.heroicons.Heroicons
import com.woowla.compose.icon.collections.heroicons.heroicons.Outline
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ArrowLeft
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.PencilSquare
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.Plus
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.Trash

/**
 * 快捷模板管理独立页：全屏页面，列表行点击进入编辑；支持新建/保存/删除。
 * 模板在今日页输入栏上方以横向胶囊展示，点击即一键填入确认卡。
 */
@Composable
fun ManageQuickTemplatesRoute(
    viewModel: ManageQuickTemplatesViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    ManageQuickTemplatesScreen(
        templates = templates,
        onBack = onBack,
        onUpsert = viewModel::upsert,
        onDelete = viewModel::delete,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ManageQuickTemplatesScreen(
    templates: List<QuickTemplate>,
    onBack: () -> Unit,
    onUpsert: (QuickTemplate) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var editing by remember { mutableStateOf<QuickTemplate?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("管理快捷模板") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Heroicons.Outline.ArrowLeft, contentDescription = "返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            TemplateTopBar(
                onAdd = { editing = QuickTemplate(title = "", categoryName = "", amount = 0.0) },
            )

            Spacer(Modifier.height(8.dp))

            if (templates.isEmpty()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(48.dp))
                    Text(
                        "还没有快捷模板",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "点击右上角「+」新建，模板会出现在今日页输入栏上方",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(BeeCountShapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = Spacing.xxs, vertical = Spacing.xxs),
                ) {
                    items(templates, key = { it.id }) { template ->
                        TemplateRow(
                            template = template,
                            onEdit = { editing = template },
                            onDelete = { onDelete(template.id) },
                        )
                    }
                }
            }
        }
    }

    editing?.let { template ->
        OpenTemplateDialog(
            template = template,
            onDismiss = { editing = null },
            onSave = { edited -> onUpsert(edited); editing = null },
        )
    }
}

@Composable
private fun TemplateTopBar(onAdd: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "点击模板即可在今日页一键填入确认卡",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onAdd,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(Heroicons.Outline.Plus, contentDescription = "新建", tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun TemplateRow(
    template: QuickTemplate,
    onEdit: () -> Unit,
    onDelete: (Long) -> Unit,
) {
    var deleteOpen by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(BeeCountShapes.extraSmall)
            .clickable { onEdit() }
            .padding(horizontal = Spacing.mdSm, vertical = Spacing.mdSm),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                template.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                "${typeLabel(template.type)} · ${template.categoryName} · ¥${formatMoney(template.amount)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
            Icon(Heroicons.Outline.PencilSquare, contentDescription = "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { deleteOpen = true }, modifier = Modifier.size(34.dp)) {
            Icon(Heroicons.Outline.Trash, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
        }
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("删除模板「${template.title}」？") },
            text = { Text("删除后今日页不再显示该模板，已记账目不受影响。") },
            confirmButton = {
                TextButton(onClick = { deleteOpen = false }) { Text("取消") }
                TextButton(onClick = { onDelete(template.id); deleteOpen = false }) { Text("删除") }
            },
        )
    }
}

@Composable
private fun OpenTemplateDialog(
    template: QuickTemplate,
    onDismiss: () -> Unit,
    onSave: (QuickTemplate) -> Unit,
) {
    var title by remember(template.id) { mutableStateOf(template.title) }
    var category by remember(template.id) { mutableStateOf(template.categoryName) }
    var amount by remember(template.id) { mutableStateOf(if (template.amount > 0) formatMoney(template.amount) else "") }
    var note by remember(template.id) { mutableStateOf(template.note) }
    var type by remember(template.id) { mutableStateOf(template.type) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (template.id == 0L) "新建模板" else "编辑模板") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    placeholder = { Text("如：早餐") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("类别") },
                    placeholder = { Text("如：餐饮") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("金额（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    placeholder = { Text("如：豆浆油条") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TypeChip("支出", type == EntryType.EXPENSE, Modifier.weight(1f)) { type = EntryType.EXPENSE }
                    TypeChip("收入", type == EntryType.INCOME, Modifier.weight(1f)) { type = EntryType.INCOME }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
            TextButton(
                onClick = {
                    val money = amount.toDoubleOrNull()
                    if (title.isNotBlank() && category.isNotBlank() && money != null && money > 0) {
                        onSave(
                            template.copy(
                                title = title,
                                categoryName = category,
                                amount = money,
                                amountRaw = amount,
                                note = note,
                                type = type,
                            ),
                        )
                    }
                },
                enabled = title.isNotBlank() && category.isNotBlank() && amount.toDoubleOrNull()?.let { it > 0 } == true,
            ) { Text("保存") }
        },
    )
}

@Composable
private fun TypeChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun typeLabel(type: EntryType): String = when (type) {
    EntryType.EXPENSE -> "支出"
    EntryType.INCOME -> "收入"
    EntryType.REFUND -> "退款"
    EntryType.NEUTRAL -> "中性"
}
