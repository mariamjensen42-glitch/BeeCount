package com.cycling.beecount.ui.settings

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.beecount.domain.repository.AiKeyRepository
import com.cycling.beecount.domain.repository.AutoEntrySettingsRepository
import com.cycling.beecount.domain.usecase.ClearAllEntriesUseCase
import com.cycling.beecount.domain.usecase.ExportEntriesCsvUseCase
import com.cycling.beecount.domain.usecase.FillDemoDataUseCase
import com.cycling.beecount.domain.usecase.ImportWeChatBillUseCase
import com.cycling.beecount.domain.usecase.ManageCategoryUseCase
import com.cycling.beecount.domain.usecase.ManageTagUseCase
import com.cycling.beecount.domain.usecase.ObserveCategoriesUseCase
import com.cycling.beecount.domain.usecase.ObserveTagsUseCase
import com.cycling.beecount.domain.usecase.ParseWeChatBillUseCase
import com.cycling.beecount.domain.usecase.UndoWeChatImportUseCase
import com.cycling.beecount.notification.PaymentNotificationListener
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI 架构：设置页 ViewModel（ADR 0008）。
 * 观察 Key/类别/标签；CSV 导出以 [exportCsv] 暴露给 UI 层做系统分享。
 *
 * 微信账单导入（ADR 0012）：选文件 → 解析（Loading）→ 汇总确认（含去重预览）→
 * 一次性入库 → 30 秒撤销窗口（[PendingWeChatUndo]）。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val aiKeyRepository: AiKeyRepository,
    private val manageCategoryUseCase: ManageCategoryUseCase,
    private val manageTagUseCase: ManageTagUseCase,
    private val clearAllEntriesUseCase: ClearAllEntriesUseCase,
    private val fillDemoDataUseCase: FillDemoDataUseCase,
    private val exportEntriesCsvUseCase: ExportEntriesCsvUseCase,
    private val parseWeChatBillUseCase: ParseWeChatBillUseCase,
    private val importWeChatBillUseCase: ImportWeChatBillUseCase,
    private val undoWeChatImportUseCase: UndoWeChatImportUseCase,
    private val autoEntrySettingsRepository: AutoEntrySettingsRepository,
    @ApplicationContext private val appContext: Context,
    observeCategories: ObserveCategoriesUseCase,
    observeTags: ObserveTagsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            aiKeyRepository.observeKey().collect { key ->
                _uiState.update { it.copy(apiKeyMasked = maskKey(key)) }
            }
        }
        viewModelScope.launch {
            observeCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
        viewModelScope.launch {
            observeTags().collect { tags ->
                _uiState.update { it.copy(tags = tags) }
            }
        }
        viewModelScope.launch {
            autoEntrySettingsRepository.observeEnabled().collect { enabled ->
                _uiState.update { it.copy(autoEntryEnabled = enabled) }
            }
        }
        refreshAutoEntryPermissions()
    }

    fun onEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SaveApiKey -> {
                val key = event.key.trim()
                if (key.isEmpty()) {
                    _uiState.update { it.copy(transientError = "Key 不能为空") }
                    return
                }
                launchManage { aiKeyRepository.saveKey(key) }
            }

            SettingsEvent.ClearApiKey -> launchManage { aiKeyRepository.clearKey() }

            is SettingsEvent.CreateCategory ->
                launchManage { manageCategoryUseCase.create(event.name, event.type) }

            is SettingsEvent.RenameCategory ->
                launchManage { manageCategoryUseCase.rename(event.id, event.name) }

            is SettingsEvent.DeleteCategory ->
                launchManage { manageCategoryUseCase.delete(event.id) }

            is SettingsEvent.RenameTag ->
                launchManage { manageTagUseCase.rename(event.id, event.name) }

            is SettingsEvent.UpdateTagColor ->
                launchManage { manageTagUseCase.updateColor(event.id, event.color) }

            is SettingsEvent.DeleteTag ->
                launchManage { manageTagUseCase.delete(event.id) }

            SettingsEvent.ClearAllEntries -> launchManage { clearAllEntriesUseCase() }
            SettingsEvent.FillDemoData -> launchManage(
                successMessage = "已填充近五年 9,000 笔演示数据",
            ) { fillDemoDataUseCase() }

            is SettingsEvent.ImportWeChatBill -> startWeChatImport(event.uri)
            SettingsEvent.ConfirmWeChatImport -> confirmWeChatImport()
            SettingsEvent.DismissWeChatImport ->
                _uiState.update { it.copy(weChatImport = WeChatImportUiState.Idle) }
            SettingsEvent.UndoWeChatImport -> undoWeChatImport()

            is SettingsEvent.ToggleAutoEntry -> toggleAutoEntry(event.enable)
            SettingsEvent.RefreshAutoEntryPermissions -> refreshAutoEntryPermissions()
            SettingsEvent.DismissAutoEntrySetup ->
                _uiState.update { it.copy(autoEntrySetupPending = false) }

            SettingsEvent.DismissMessage -> _uiState.update { it.copy(transientMessage = null) }
            SettingsEvent.DismissError -> _uiState.update { it.copy(transientError = null) }
        }
    }

    /**
     * 自动记账总开关（ADR 0014）：关闭直接落库；开启需前置检查——
     * API Key 未配置先引导配置；NLS/通知权限未授权进入三步引导（授权齐后自动完成开启）。
     */
    private fun toggleAutoEntry(enable: Boolean) {
        if (!enable) {
            launchManage { autoEntrySettingsRepository.setEnabled(false) }
            return
        }
        if (_uiState.value.apiKeyMasked.isEmpty()) {
            _uiState.update { it.copy(transientError = "自动记账需要先配置 DeepSeek API Key") }
            return
        }
        val nlsGranted = _uiState.value.notificationListeningGranted
        val postGranted = _uiState.value.postNotificationsGranted
        if (!nlsGranted || !postGranted) {
            _uiState.update { it.copy(autoEntrySetupPending = true) }
            return
        }
        launchManage { autoEntrySettingsRepository.setEnabled(true) }
    }

    /** 从系统设置返回 / 权限请求回调后刷新授权状态；三步引导进行中且授权齐 → 自动完成开启 */
    private fun refreshAutoEntryPermissions() {
        val nlsGranted = isNotificationListenerGranted()
        val postGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        _uiState.update {
            it.copy(
                notificationListeningGranted = nlsGranted,
                postNotificationsGranted = postGranted,
            )
        }
        if (_uiState.value.autoEntrySetupPending && nlsGranted && postGranted) {
            completeAutoEntrySetup()
        }
    }

    private fun completeAutoEntrySetup() {
        _uiState.update { it.copy(autoEntrySetupPending = false) }
        launchManage { autoEntrySettingsRepository.setEnabled(true) }
    }

    /** 通知监听授权状态：系统"通知使用权"列表（enabled_notification_listeners）含本服务即已授权 */
    private fun isNotificationListenerGranted(): Boolean {
        val target = ComponentName(appContext, PaymentNotificationListener::class.java)
        // 常量 ENABLED_NOTIFICATION_LISTENERS 在部分 SDK 源码过滤下不可见，用字面量（API 18+ 稳定）
        val flat = Settings.Secure.getString(
            appContext.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return flat.split(":").any { ComponentName.unflattenFromString(it) == target }
    }

    /** 解析选中的微信账单 → 去重预览，进入确认层；解析失败直接报错 */
    private fun startWeChatImport(uri: android.net.Uri) {
        _uiState.update { it.copy(weChatImport = WeChatImportUiState.Loading) }
        viewModelScope.launch {
            when (val outcome = parseWeChatBillUseCase(uri)) {
                is ParseWeChatBillUseCase.Outcome.Parsed -> {
                    val preview = importWeChatBillUseCase.preview(outcome.draft)
                    _uiState.update {
                        it.copy(weChatImport = WeChatImportUiState.Confirm(preview, outcome.draft))
                    }
                }

                is ParseWeChatBillUseCase.Outcome.Error ->
                    _uiState.update {
                        it.copy(
                            weChatImport = WeChatImportUiState.Idle,
                            transientError = outcome.message,
                        )
                    }
            }
        }
    }

    /** 确认导入：重新跑去重后一次性入库，开启 30 秒撤销窗口 */
    private fun confirmWeChatImport() {
        val confirm = (_uiState.value.weChatImport as? WeChatImportUiState.Confirm) ?: return
        viewModelScope.launch {
            val result = importWeChatBillUseCase.confirm(confirm.draft)
            val undo = PendingWeChatUndo(
                sourceRefs = result.insertedRefs,
                imported = result.imported,
                duplicates = result.duplicates,
            )
            _uiState.update {
                it.copy(
                    weChatImport = WeChatImportUiState.Idle,
                    pendingWeChatUndo = undo,
                )
            }
            scheduleUndoExpiry(undo)
        }
    }

    /** 30 秒后撤销窗口过期；过期前用户可点 snackbar 的「撤销」 */
    private fun scheduleUndoExpiry(undo: PendingWeChatUndo) {
        viewModelScope.launch {
            delay(UNDO_WINDOW_MILLIS)
            _uiState.update { current ->
                // 只清自己对应的窗口，避免上一次导入的过期任务误清新一次导入的窗口
                if (current.pendingWeChatUndo === undo) current.copy(pendingWeChatUndo = null) else current
            }
        }
    }

    /** 撤销本次导入：按交易单号集合删除全部账目 */
    private fun undoWeChatImport() {
        val undo = _uiState.value.pendingWeChatUndo ?: return
        viewModelScope.launch {
            val deleted = undoWeChatImportUseCase(undo.sourceRefs)
            _uiState.update {
                it.copy(
                    pendingWeChatUndo = null,
                    transientMessage = "已撤销导入，删除 $deleted 笔",
                )
            }
        }
    }

    /** 生成 CSV 导出文本；失败返回 null 并提示 */
    suspend fun exportCsv(): String? = try {
        exportEntriesCsvUseCase()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        _uiState.update { it.copy(transientError = "导出失败，请重试") }
        null
    }

    private fun launchManage(
        successMessage: String? = null,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                block()
                if (successMessage != null) {
                    _uiState.update { it.copy(transientMessage = successMessage) }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(transientError = "操作失败，请重试") }
            }
        }
    }

    private fun maskKey(key: String?): String {
        val k = key?.trim().orEmpty()
        if (k.isEmpty()) return ""
        return if (k.length > 8) "sk-****${k.takeLast(4)}" else "已配置"
    }

    private companion object {
        /** 批量导入的撤销窗口：比逐笔删除的 5 秒放宽，用户需要反应时间（ADR 0012） */
        const val UNDO_WINDOW_MILLIS = 30_000L
    }
}
