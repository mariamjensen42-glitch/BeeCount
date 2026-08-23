package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Entry
import com.cycling.beecount.domain.repository.EntryRepository
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * 用例：把全部账目导出为 CSV 文本（ADR 0008）。
 * 字段：日期、类型、类别、标签、金额、备注、交易对方；逗号/引号字段做转义，Excel/WPS 可直接打开。
 */
class ExportEntriesCsvUseCase @Inject constructor(
    private val entryRepository: EntryRepository,
) {

    suspend operator fun invoke(): String {
        Timber.d("开始导出 CSV")
        val entries = entryRepository.observeAllWithTags().first()
        Timber.d("导出的账目数量=%d", entries.size)
        return buildString {
            append("日期,类型,类别,标签,金额,备注,交易对方\n")
            entries.forEach { entry ->
                append(escape(entry.date.format(DATE_FORMAT)))
                append(',')
                append(
                    when (entry.type) {
                        com.cycling.beecount.domain.model.EntryType.EXPENSE -> "支出"
                        com.cycling.beecount.domain.model.EntryType.INCOME -> "收入"
                        com.cycling.beecount.domain.model.EntryType.NEUTRAL -> "中性"
                    }
                )
                append(',')
                append(escape(entry.categoryName))
                append(',')
                append(escape(entry.tags.joinToString("、") { it.name }))
                append(',')
                append(entry.amount)
                append(',')
                append(escape(entry.note))
                append(',')
                append(escape(entry.counterparty.orEmpty()))
                append('\n')
            }
        }.also { Timber.i("CSV 导出完成：%d 条账目", entries.size) }
    }

    private fun escape(field: String): String {
        val needsQuote = field.contains(',') || field.contains('"') || field.contains('\n')
        val escaped = field.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    private companion object {
        val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
