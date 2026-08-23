package com.cycling.beecount.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cycling.beecount.domain.model.AnnualAnalytics
import com.cycling.beecount.domain.model.ComparisonAnalytics
import com.cycling.beecount.domain.model.MonthlyAnalytics
import com.cycling.beecount.domain.query.EntryQuery
import com.cycling.beecount.domain.usecase.BuildComparisonAnalyticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Year
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * MVI 架构：图表页 ViewModel（ADR 0009）。
 * [monthlyAnalytics]/[annualAnalytics] 各自随粒度与选中周期 flatMapLatest；
 * 非当前粒度时置 null——切换粒度即切换数据源，不做无谓查询。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val entryQuery: EntryQuery,
    private val buildComparison: BuildComparisonAnalyticsUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    /** 月度报表：仅在「月度」粒度下订阅选中的月份 */
    val monthlyAnalytics: StateFlow<MonthlyAnalytics?> = _uiState
        .map { state ->
            if (state.granularity == AnalyticsGranularity.MONTH) state.selectedMonth else null
        }
        .distinctUntilChanged()
        .flatMapLatest { month -> if (month == null) flowOf(null) else entryQuery.buildMonth(month) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 年度报告：仅在「年度」粒度下订阅选中的年份 */
    val annualAnalytics: StateFlow<AnnualAnalytics?> = _uiState
        .map { state ->
            if (state.granularity == AnalyticsGranularity.YEAR) state.selectedYear else null
        }
        .distinctUntilChanged()
        .flatMapLatest { year -> if (year == null) flowOf(null) else entryQuery.buildAnnual(year) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 时间段对比：月度=本月 vs 上月，年度=本年 vs 去年 */
    val comparisonAnalytics: StateFlow<ComparisonAnalytics?> = _uiState
        .map { state ->
            if (state.granularity == AnalyticsGranularity.MONTH) {
                ComparisonQuery.MONTH(state.selectedMonth)
            } else {
                ComparisonQuery.YEAR(state.selectedYear)
            }
        }
        .distinctUntilChanged()
        .flatMapLatest { query -> buildComparisonFlow(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun buildComparisonFlow(query: ComparisonQuery): Flow<ComparisonAnalytics?> =
        when (query) {
            is ComparisonQuery.MONTH -> buildComparison(query.month)
            is ComparisonQuery.YEAR -> buildComparison(year = query.year)
        }

    fun onEvent(event: AnalyticsEvent) {
        when (event) {
            AnalyticsEvent.ToggleGranularity ->
                _uiState.update { state ->
                    state.copy(
                        granularity = if (state.granularity == AnalyticsGranularity.MONTH) {
                            AnalyticsGranularity.YEAR
                        } else {
                            AnalyticsGranularity.MONTH
                        }
                    )
                }

            is AnalyticsEvent.ShiftMonth ->
                _uiState.update { state ->
                    state.copy(selectedMonth = state.selectedMonth.plusMonths(event.delta.toLong()))
                }

            AnalyticsEvent.GoToCurrentMonth ->
                _uiState.update { state -> state.copy(selectedMonth = YearMonth.now()) }

            is AnalyticsEvent.ShiftYear ->
                _uiState.update { state ->
                    state.copy(
                        selectedYear = state.selectedYear + event.delta,
                        selectedHeatmapDate = null,
                    )
                }

            AnalyticsEvent.GoToCurrentYear ->
                _uiState.update { state ->
                    state.copy(selectedYear = Year.now().value, selectedHeatmapDate = null)
                }

            is AnalyticsEvent.SetMonth ->
                _uiState.update { state -> state.copy(selectedMonth = event.month, selectedYear = event.month.year) }

            is AnalyticsEvent.SelectHeatmapMetric ->
                _uiState.update { state -> state.copy(heatmapMetric = event.metric) }

            is AnalyticsEvent.SelectHeatmapDate ->
                _uiState.update { state -> state.copy(selectedHeatmapDate = event.date) }
        }
    }
}

/** 时间段对比查询：月度用 month，年度用 year */
private sealed interface ComparisonQuery {
    data class MONTH(val month: YearMonth) : ComparisonQuery
    data class YEAR(val year: Int) : ComparisonQuery
}
