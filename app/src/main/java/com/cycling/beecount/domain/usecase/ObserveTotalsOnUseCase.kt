package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.repository.EntryRepository
import com.cycling.beecount.domain.repository.TodayTotals
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * 用例：观察某一天的收支合计（今日合计，Q22）
 */
class ObserveTotalsOnUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
) {
    operator fun invoke(date: LocalDate): Flow<TodayTotals> = entryRepository.observeTotalsOn(date)
}
