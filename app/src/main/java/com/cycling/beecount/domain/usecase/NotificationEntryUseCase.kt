package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.toPendingDraft
import com.cycling.beecount.domain.repository.AutoEntrySettingsRepository
import com.cycling.beecount.domain.repository.PendingDraftRepository
import com.cycling.beecount.domain.repository.ProcessedNotificationRepository
import javax.inject.Inject

/**
 * 用例：处理一条到达的支付通知（ADR 0014 自动记账主链路）。
 *
 * 流程：通知闸门（包名 + 金额/关键词，不过即静默丢弃）→ 去重键落库（重复投递跳过）→
 * 复用 [ParseEntryUseCase] 解析（isNotificationInput + 通知时间戳日期）→
 * 成功：草稿入待确认队列 + 发确认提醒；失败：低姿态提示（同渠道 15 分钟节流）；
 * KeyMissing 静默（设置页开关处已引导）。
 */
class NotificationEntryUseCase @Inject constructor(
    private val gate: NotificationGate,
    private val processedNotificationRepository: ProcessedNotificationRepository,
    private val pendingDraftRepository: PendingDraftRepository,
    private val autoEntrySettingsRepository: AutoEntrySettingsRepository,
    private val parseEntryUseCase: ParseEntryUseCase,
    private val notifier: AutoEntryNotifier,
) {

    suspend fun handle(
        packageName: String,
        notifyKey: String,
        title: String,
        text: String,
        transactionDate: java.time.LocalDate,
    ) {
        val fullText = listOf(title, text).filter { it.isNotBlank() }.joinToString(" ")
        if (fullText.isBlank()) return
        if (!gate.shouldProcess(packageName, title, text)) return

        // 去重：首次记录返回 false 继续处理；重复投递（同 key 同文本）直接跳过
        val seen = processedNotificationRepository.markProcessedOrAlreadySeen(packageName, notifyKey, fullText)
        if (seen) return

        when (val outcome = parseEntryUseCase(fullText, isNotificationInput = true, referenceDate = transactionDate)) {
            is ParseEntryUseCase.Outcome.KeyMissing -> Unit // 开关处引导过，不逐条提示

            is ParseEntryUseCase.Outcome.Error -> notifyFailureThrottled(packageName, fullText)

            is ParseEntryUseCase.Outcome.Success -> {
                if (!outcome.result.recordable) return
                val draft = outcome.result.toPendingDraft(originalText = fullText)
                val draftId = pendingDraftRepository.add(draft)
                notifier.notifyDraftReady(packageName, draftId, outcome.result)
            }
        }
    }

    /** 解析失败低姿态提示：同包名 15 分钟内最多一条，避免网络故障期刷屏 */
    private suspend fun notifyFailureThrottled(packageName: String, originalText: String) {
        val now = System.currentTimeMillis()
        val last = autoEntrySettingsRepository.lastFailureAt(packageName)
        if (now - last < THROTTLE_MILLIS) return
        autoEntrySettingsRepository.setLastFailureAt(packageName, now)
        notifier.notifyParseFailed(originalText)
    }

    private companion object {
        /** 失败提示节流窗口（ADR 0014） */
        const val THROTTLE_MILLIS = 15 * 60 * 1000L
    }
}
