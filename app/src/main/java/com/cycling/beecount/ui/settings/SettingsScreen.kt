package com.cycling.beecount.ui.settings

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cycling.beecount.domain.usecase.WeChatImportPreview
import com.cycling.beecount.ui.FLOATING_PILL_CLEARANCE
import com.woowla.compose.icon.collections.heroicons.Heroicons
import com.woowla.compose.icon.collections.heroicons.heroicons.Outline
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.AdjustmentsHorizontal
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ArrowDownTray
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ArrowUpTray
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ChartBar
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.ChevronRight
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.CpuChip
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.InformationCircle
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.Key
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.Sparkles
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.Squares2x2
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.Tag
import com.woowla.compose.icon.collections.heroicons.heroicons.outline.Trash
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel = hiltViewModel(),
    onOpenCategoryManage: () -> Unit,
    onOpenTagManage: () -> Unit,
    onOpenBudgetManage: () -> Unit,
    onOpenQuickTemplateManage: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        exportCsv = viewModel::exportCsv,
        onOpenCategoryManage = onOpenCategoryManage,
        onOpenTagManage = onOpenTagManage,
        onOpenBudgetManage = onOpenBudgetManage,
        onOpenQuickTemplateManage = onOpenQuickTemplateManage,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    exportCsv: suspend () -> String?,
    onOpenCategoryManage: () -> Unit,
    onOpenTagManage: () -> Unit,
    onOpenBudgetManage: () -> Unit,
    onOpenQuickTemplateManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showKeyDialog by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showDemoConfirm by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val wechatImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onEvent(SettingsEvent.ImportWeChatBill(uri))
    }

    androidx.compose.runtime.LaunchedEffect(uiState.transientMessage, uiState.transientError) {
        val message = uiState.transientMessage ?: uiState.transientError
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            onEvent(
                if (uiState.transientMessage != null) SettingsEvent.DismissMessage else SettingsEvent.DismissError,
            )
        }
    }

    // 导入完成后的撤销窗口（30 秒，ADR 0012）：snackbar 常驻带「撤销」，
    // 窗口过期或已撤销时由 pendingWeChatUndo 置空触发收起
    LaunchedEffect(uiState.pendingWeChatUndo) {
        val undo = uiState.pendingWeChatUndo
        if (undo != null) {
            val message = if (undo.duplicates > 0) {
                "已导入 ${undo.imported} 笔（跳过 ${undo.duplicates} 笔重复）"
            } else {
                "已导入 ${undo.imported} 笔"
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "撤销",
                duration = SnackbarDuration.Indefinite,
            )
            if (result == SnackbarResult.ActionPerformed) {
                onEvent(SettingsEvent.UndoWeChatImport)
            }
        }
        snackbarHostState.currentSnackbarData?.dismiss()
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("设置") }) },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                // 悬浮胶囊底部栏覆盖在内容之上，snackbar 需垫到它上方（ADR 0009 胶囊导航）
                modifier = Modifier.navigationBarsPadding().padding(bottom = FLOATING_PILL_CLEARANCE),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                // 底部留出悬浮胶囊高度，最后一行（关于区）能滚到胶囊上方
                .padding(bottom = FLOATING_PILL_CLEARANCE),
        ) {
            // 账户
            SettingsSectionHeader("账户")
            SettingsRow(
                title = "DeepSeek API Key",
                subtitle = if (uiState.apiKeyMasked.isEmpty()) "未设置" else uiState.apiKeyMasked,
                icon = Heroicons.Outline.Key,
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
            SettingsRow("管理类别", subtitle = "新增 / 子分类 / 排序 / 图标 / 删除归并", icon = Heroicons.Outline.Squares2x2, onClick = onOpenCategoryManage)
            SettingsRow("管理标签", subtitle = "改名 / 改色 / 删除", icon = Heroicons.Outline.Tag, onClick = onOpenTagManage)
            SettingsRow("管理快捷模板", subtitle = "一键填入高频记账", icon = Heroicons.Outline.Sparkles, onClick = onOpenQuickTemplateManage)
            SettingsRow("管理预算", subtitle = "月度/年度/自定义周期 · 分类预算 · 结余结转", icon = Heroicons.Outline.ChartBar, onClick = onOpenBudgetManage)
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            // 数据
            SettingsSectionHeader("数据")
            SettingsRow(
                title = "导入微信账单",
                subtitle = "读取微信支付账单 xlsx，恢复为账目",
                icon = Heroicons.Outline.ArrowDownTray,
                onClick = {
                    wechatImportLauncher.launch(
                        arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                    )
                },
            )
            SettingsRow("导出 CSV", subtitle = "全部账目，经系统分享保存", icon = Heroicons.Outline.ArrowUpTray, onClick = {
                scope.launch {
                    val csv = exportCsv()
                    if (csv != null) shareCsv(context, csv)
                }
            })
            SettingsRow(
                title = "填充演示数据",
                subtitle = "清空账目后写入近五年 9,000 笔样本",
                icon = Heroicons.Outline.Sparkles,
                danger = true,
                onClick = { showDemoConfirm = true },
            )
            SettingsRow(
                title = "清空全部账目",
                subtitle = "只清账目，类别与标签保留",
                icon = Heroicons.Outline.Trash,
                danger = true,
                onClick = { showClearConfirm = true },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))

            // 关于
            SettingsSectionHeader("关于")
            SettingsRow("版本", subtitle = appVersionName(context), icon = Heroicons.Outline.InformationCircle)
            SettingsRow("字体", subtitle = "终端等宽（系统 Monospace）", icon = Heroicons.Outline.AdjustmentsHorizontal)
            SettingsRow("AI 模型", subtitle = "DeepSeek · deepseek-v4-flash", icon = Heroicons.Outline.CpuChip)
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
    when (val importState = uiState.weChatImport) {
        WeChatImportUiState.Idle -> Unit
        WeChatImportUiState.Loading -> {
            AlertDialog(
                onDismissRequest = { /* 解析中不可关闭 */ },
                title = { Text("正在解析账单…") },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.height(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("读取微信账单并归类，请稍候")
                    }
                },
                confirmButton = {},
            )
        }
        is WeChatImportUiState.Confirm -> WeChatImportConfirmSheet(
            preview = importState.preview,
            onConfirm = { onEvent(SettingsEvent.ConfirmWeChatImport) },
            onDismiss = { onEvent(SettingsEvent.DismissWeChatImport) },
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
    icon: ImageVector? = null,
    danger: Boolean = false,
    showChevron: Boolean = true,
    onClick: () -> Unit = {},
    trailing: (@Composable () -> Unit)? = null,
) {
    val accent = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (danger) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing?.invoke()
        if (showChevron) {
            Icon(
                imageVector = Heroicons.Outline.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsRow(
        title = title,
        subtitle = subtitle,
        showChevron = false,
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
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

/**
 * 微信账单导入确认层（ADR 0012）：汇总确认一次性入库。
 * 展示账单时间范围、收支/中性笔数、分类分布与去重/跳过说明，不逐笔确认。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeChatImportConfirmSheet(
    preview: WeChatImportPreview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val totalNew = preview.expenseCount + preview.incomeCount + preview.refundCount + preview.neutralCount
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Text("导入微信账单", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = preview.from?.let { from ->
                    preview.to?.let { to -> "账单范围：$from 至 $to" }
                } ?: "账单范围：未知",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = buildString {
                    append("支出 ${preview.expenseCount} 笔 · 收入 ${preview.incomeCount} 笔")
                    if (preview.refundCount > 0) append(" · 退款 ${preview.refundCount} 笔")
                    if (preview.neutralCount > 0) append(" · 中性 ${preview.neutralCount} 笔")
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "全部账目将统一打上「微信」标签",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (preview.duplicateCount > 0 || preview.skippedCount > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = buildString {
                        if (preview.duplicateCount > 0) append("其中 ${preview.duplicateCount} 笔与已有账目重复，不会重复导入")
                        if (preview.duplicateCount > 0 && preview.skippedCount > 0) append("；")
                        if (preview.skippedCount > 0) append("跳过 ${preview.skippedCount} 笔（充值/提现等中性交易）")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("分类分布", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                preview.categoryDistribution.forEach { (category, count) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(category, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "$count 笔",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onConfirm, enabled = totalNew > 0) {
                    Text(if (totalNew > 0) "导入 $totalNew 笔" else "没有可导入的账目")
                }
            }
        }
    }
}
