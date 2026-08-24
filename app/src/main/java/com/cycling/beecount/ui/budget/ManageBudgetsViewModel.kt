package com.cycling.beecount.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.beecount.domain.model.Budget
import com.cycling.beecount.domain.model.BudgetCycle
import com.cycling.beecount.domain.model.BudgetException
import com.cycling.beecount.domain.model.BudgetProgress
import com.cycling.beecount.domain.model.BudgetForecast
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.query.EntryQuery
import com.cycling.beecount.domain.usecase.BudgetForecastUseCase
import com.cycling.beecount.domain.usecase.ManageBudgetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 预算管理页 ViewModel：观察预算进度 + 承载预算/例外的新增、编辑、删除。
 */
@HiltViewModel
class ManageBudgetsViewModel @Inject constructor(
    private val manageBudgetUseCase: ManageBudgetUseCase,
    private val entryQuery: EntryQuery,
    private val budgetForecastUseCase: BudgetForecastUseCase,
) : ViewModel() {

    private val today = LocalDate.now()

    val progress: StateFlow<List<BudgetProgress>> = entryQuery.observeBudgetProgress(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 预算执行预测：基于当前进度线性外推周期末支出（纯本地，见 BudgetForecastUseCase） */
    val forecast: StateFlow<List<BudgetForecast>> = progress
        .map { budgetForecastUseCase.forecast(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val budgets: StateFlow<List<Budget>> = entryQuery.observeBudgets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val exceptions: StateFlow<List<BudgetException>> = manageBudgetUseCase.observeExceptions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val categories: StateFlow<List<Category>> = entryQuery.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 一级分类（总预算之外的维度候选） */
    val topLevelCategories: List<Category> get() = categories.value.filter { it.parentId == null }

    fun create(
        cycle: BudgetCycle,
        amount: Double,
        categoryName: String?,
        lengthDays: Int,
        carryOver: Boolean,
    ) = launchUi {
        if (amount <= 0) return@launchUi
        val name = categoryName?.trim()?.takeIf { it.isNotEmpty() }
        manageBudgetUseCase.create(
            Budget(
                cycle = cycle,
                lengthDays = lengthDays,
                categoryName = name,
                amount = amount,
                carryOver = carryOver,
            ),
        )
    }

    fun updateAmount(id: Long, amount: Double) = launchUi { manageBudgetUseCase.updateAmount(id, amount) }

    fun updateCarryOver(id: Long, carryOver: Boolean) = launchUi { manageBudgetUseCase.updateCarryOver(id, carryOver) }

    fun updateEnabled(id: Long, enabled: Boolean) = launchUi { manageBudgetUseCase.updateEnabled(id, enabled) }

    fun delete(id: Long) = launchUi { manageBudgetUseCase.delete(id) }

    fun addException(date: LocalDate) = launchUi { manageBudgetUseCase.addException(date) }

    fun removeException(date: LocalDate) = launchUi { manageBudgetUseCase.removeException(date) }

    private fun launchUi(block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }
        }
    }
}
