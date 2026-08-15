package com.cycling.beecount.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.beecount.domain.repository.AiKeyRepository
import com.cycling.beecount.domain.usecase.ClearAllEntriesUseCase
import com.cycling.beecount.domain.usecase.ExportEntriesCsvUseCase
import com.cycling.beecount.domain.usecase.ManageCategoryUseCase
import com.cycling.beecount.domain.usecase.ManageTagUseCase
import com.cycling.beecount.domain.usecase.ObserveCategoriesUseCase
import com.cycling.beecount.domain.usecase.ObserveTagsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MVI 架构：设置页 ViewModel（ADR 0008）。
 * 观察 Key/类别/标签；CSV 导出以 [exportCsv] 暴露给 UI 层做系统分享。
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val aiKeyRepository: AiKeyRepository,
    private val manageCategoryUseCase: ManageCategoryUseCase,
    private val manageTagUseCase: ManageTagUseCase,
    private val clearAllEntriesUseCase: ClearAllEntriesUseCase,
    private val exportEntriesCsvUseCase: ExportEntriesCsvUseCase,
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
            SettingsEvent.DismissError -> _uiState.update { it.copy(transientError = null) }
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

    private fun launchManage(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
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
}
