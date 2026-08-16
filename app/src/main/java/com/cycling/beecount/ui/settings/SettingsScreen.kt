package com.cycling.beecount.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.ui.common.TagManageSheet
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        exportCsv = viewModel::exportCsv,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    exportCsv: suspend () -> String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showKeyDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDemoConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    androidx.compose.runtime.LaunchedEffect(uiState.transientMessage, uiState.transientError) {
        val message = uiState.transientMessage ?: uiState.transientError
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onEvent(
                if (uiState.transientMessage != null) SettingsEvent.DismissMessage else SettingsEvent.DismissError,
            )
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("设置") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // 账户
            SettingsSectionHeader("账户")
            SettingsRow(
                title = "DeepSeek API Key",
                subtitle = if (uiState.apiKeyMasked.isEmpty()) "未设置" else uiState.apiKeyMasked,
                onClick = { showKeyDialog = true },
                trailing = {
                    if (uiState.apiKeyMasked.isNotEmpty()) {
                        TextButton(onClick = { onEvent(SettingsEvent.ClearApiKey) }) { Text("清除") }
                    }
                },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            // 管理
            SettingsSectionHeader("管理")
            SettingsRow("管理类别", subtitle = "新增 / 改名 / 删除", onClick = { showCategoryDialog = true })
            SettingsRow("管理标签", subtitle = "改名 / 改色 / 删除", onClick = { showTagDialog = true })
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            // 数据
            SettingsSectionHeader("数据")
            SettingsRow("导出 CSV", subtitle = "全部账目，经系统分享保存", onClick = {
                scope.launch {
                    val csv = exportCsv()
                    if (csv != null) shareCsv(context, csv)
                }
            })
            SettingsRow(
                title = "填充演示数据",
                subtitle = "清空账目后写入近五年 9,000 笔样本",
                onClick = { showDemoConfirm = true },
            )
            SettingsRow(
                title = "清空全部账目",
                subtitle = "只清账目，类别与标签保留",
                onClick = { showClearConfirm = true },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            // 关于
            SettingsSectionHeader("关于")
            SettingsRow("版本", subtitle = appVersionName(context))
            SettingsRow("字体", subtitle = "霞鹜文楷 / 得意黑（SIL OFL 1.1）")
            SettingsRow("AI 模型", subtitle = "DeepSeek · deepseek-v4-flash")
        }
    }

    if (showKeyDialog) {
        ApiKeyEditDialog(
            onSave = { key ->
                onEvent(SettingsEvent.SaveApiKey(key))
                showKeyDialog = false
            },
            onDismiss = { showKeyDialog = false },
        )
    }
    if (showCategoryDialog) {
        CategoryManageDialog(
            categories = uiState.categories,
            onClose = { showCategoryDialog = false },
            onCreate = { name, type -> onEvent(SettingsEvent.CreateCategory(name, type)) },
            onRename = { id, name -> onEvent(SettingsEvent.RenameCategory(id, name)) },
            onDelete = { id -> onEvent(SettingsEvent.DeleteCategory(id)) },
        )
    }
    if (showTagDialog) {
        TagManageSheet(
            tags = uiState.tags,
            onClose = { showTagDialog = false },
            onRename = { id, name -> onEvent(SettingsEvent.RenameTag(id, name)) },
            onUpdateColor = { id, color -> onEvent(SettingsEvent.UpdateTagColor(id, color)) },
            onDelete = { id -> onEvent(SettingsEvent.DeleteTag(id)) },
        )
    }
    if (showDemoConfirm) {
        AlertDialog(
            onDismissRequest = { showDemoConfirm = false },
            title = { Text("填充演示数据？") },
            text = { Text("将清空现有全部账目，并写入近五年覆盖多类收支场景的 9,000 笔样本。类别与标签会保留。") },
            confirmButton = {
                Button(
                    onClick = {
                        onEvent(SettingsEvent.FillDemoData)
                        showDemoConfirm = false
                    },
                ) { Text("填充") }
            },
            dismissButton = {
                TextButton(onClick = { showDemoConfirm = false }) { Text("取消") }
            },
        )
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空全部账目？") },
            text = { Text("将删除所有账目记录，此操作不可撤销。类别与标签会保留。") },
            confirmButton = {
                Button(
                    onClick = {
                        onEvent(SettingsEvent.ClearAllEntries)
                        showClearConfirm = false
                    },
                ) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
        Text(
            "›",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ApiKeyEditDialog(
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
                    placeholder = { Text("sk-...") },
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

/**
 * 类别管理底部弹层：支出/收入分组，支持新增、改名、删除。
 * 删除类别不影响已有账目（账目存的是类别名快照，ADR 0008）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryManageDialog(
    categories: List<Category>,
    onClose: () -> Unit,
    onCreate: (name: String, type: EntryType) -> Unit,
    onRename: (id: Long, name: String) -> Unit,
    onDelete: (id: Long) -> Unit,
) {
    var newName by remember { mutableStateOf("") }
    var newType by remember { mutableStateOf(EntryType.EXPENSE) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Text("管理类别", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "○ 预定义 / ● 自定义。删除类别不影响已有账目。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            // 新增类别
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { newType = EntryType.EXPENSE },
                    enabled = newType != EntryType.EXPENSE,
                ) { Text("支出") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { newType = EntryType.INCOME },
                    enabled = newType != EntryType.INCOME,
                ) { Text("收入") }
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新类别名") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        val name = newName.trim()
                        if (name.isNotEmpty()) onCreate(name, newType)
                        newName = ""
                    },
                ) { Text("添加") }
            }
            Spacer(Modifier.height(8.dp))
            // 类别列表（限高滚动）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                EntryType.entries.forEach { type ->
                    Text(
                        text = if (type == EntryType.EXPENSE) "支出类别" else "收入类别",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    categories.filter { it.type == type }.forEach { category ->
                        CategoryManageRow(category, onRename, onDelete)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onClose) { Text("完成") }
            }
        }
    }
}

@Composable
private fun CategoryManageRow(
    category: Category,
    onRename: (id: Long, name: String) -> Unit,
    onDelete: (id: Long) -> Unit,
) {
    var nameText by remember(category.id) { mutableStateOf(category.name) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (category.isCustom) "●" else "○",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = nameText,
            onValueChange = { nameText = it },
            singleLine = true,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        TextButton(
            onClick = {
                val name = nameText.trim()
                if (name.isNotEmpty() && name != category.name) {
                    onRename(category.id, name)
                }
            },
        ) { Text("改名") }
        TextButton(onClick = { onDelete(category.id) }) { Text("删除") }
    }
}

private fun shareCsv(context: Context, csv: String) {
    val stamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
    val file = File(context.cacheDir, "exports/beecount_$stamp.csv").apply {
        parentFile?.mkdirs()
        writeText(csv)
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "导出账目 CSV"))
}

private fun appVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.PackageInfoFlags.of(0),
    ).versionName
}.getOrNull() ?: "未知"
