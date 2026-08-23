package com.cycling.beecount.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.beecount.domain.model.Tag
import com.cycling.beecount.domain.query.EntryQuery
import com.cycling.beecount.domain.usecase.ManageTagUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 标签管理独立页 ViewModel：观察全部标签并承载新建/改名/改色/删除操作（ADR 0007）。
 */
@HiltViewModel
class ManageTagsViewModel @Inject constructor(
    private val manageTagUseCase: ManageTagUseCase,
    private val entryQuery: EntryQuery,
) : ViewModel() {

    val tags: StateFlow<List<Tag>> = entryQuery.observeTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(name: String) = launchUi { manageTagUseCase.create(name) }

    fun rename(id: Long, name: String) = launchUi { manageTagUseCase.rename(id, name) }

    fun updateColor(id: Long, color: Long) = launchUi { manageTagUseCase.updateColor(id, color) }

    fun delete(id: Long) = launchUi { manageTagUseCase.delete(id) }

    private fun launchUi(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
        }
    }
}
