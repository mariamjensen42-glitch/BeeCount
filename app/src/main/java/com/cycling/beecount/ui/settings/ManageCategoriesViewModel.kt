package com.cycling.beecount.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.EntryType
import com.cycling.beecount.domain.query.EntryQuery
import com.cycling.beecount.domain.usecase.ManageCategoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 类别管理独立页 ViewModel：观察全部类别，并承载新增/子分类/改名/删除归并/图标/颜色/排序/隐藏操作。
 */
@HiltViewModel
class ManageCategoriesViewModel @Inject constructor(
    private val manageCategoryUseCase: ManageCategoryUseCase,
    private val entryQuery: EntryQuery,
) : ViewModel() {

    val categories: StateFlow<List<Category>> = entryQuery.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String, type: EntryType) = launchUi { manageCategoryUseCase.create(name, type) }

    fun createChild(parentId: Long, name: String) = launchUi { manageCategoryUseCase.createChild(parentId, name) }

    fun rename(id: Long, name: String) = launchUi { manageCategoryUseCase.rename(id, name) }

    fun deleteWithMerge(id: Long, targetId: Long) = launchUi { manageCategoryUseCase.deleteWithMerge(id, targetId) }

    fun moveParent(id: Long, parentId: Long?) = launchUi { manageCategoryUseCase.moveParent(id, parentId) }

    fun updateIcon(id: Long, icon: String) = launchUi { manageCategoryUseCase.updateIcon(id, icon) }

    fun updateColor(id: Long, color: Long) = launchUi { manageCategoryUseCase.updateColor(id, color) }

    fun updateSortOrder(id: Long, sortOrder: Int) = launchUi { manageCategoryUseCase.updateSortOrder(id, sortOrder) }

    fun updateHidden(id: Long, isHidden: Boolean) = launchUi { manageCategoryUseCase.updateHidden(id, isHidden) }

    private fun launchUi(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
        }
    }
}
