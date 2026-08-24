package com.cycling.beecount.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.beecount.domain.model.QuickTemplate
import com.cycling.beecount.domain.query.EntryQuery
import com.cycling.beecount.domain.usecase.ManageQuickTemplateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 快捷模板管理页 ViewModel：观察全部模板并承载新建/更新/删除操作。
 */
@HiltViewModel
class ManageQuickTemplatesViewModel @Inject constructor(
    private val manageQuickTemplateUseCase: ManageQuickTemplateUseCase,
    private val entryQuery: EntryQuery,
) : ViewModel() {

    val templates: StateFlow<List<QuickTemplate>> = entryQuery.observeQuickTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun upsert(template: QuickTemplate) = launchUi {
        if (template.id == 0L) manageQuickTemplateUseCase.create(template)
        else manageQuickTemplateUseCase.update(template)
    }

    fun delete(id: Long) = launchUi { manageQuickTemplateUseCase.delete(id) }

    private fun launchUi(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
        }
    }
}
