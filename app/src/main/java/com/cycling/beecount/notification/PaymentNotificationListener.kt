package com.cycling.beecount.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.cycling.beecount.domain.repository.AutoEntrySettingsRepository
import com.cycling.beecount.domain.usecase.NotificationEntryUseCase
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 通知监听服务（ADR 0014 自动记账主触发）：系统绑定、只收实时通知回调。
 *
 * 只读通知文本（包名 + 标题 + 正文），包名白名单过滤在 [NotificationGate]；
 * 总开关关闭时直接忽略；处理在 IO 协程，去重唯一索引兜底并发。
 * Android 不向第三方暴露通知历史，本服务只覆盖实时通知（错过的靠 OCR 兜底）。
 */
@AndroidEntryPoint
class PaymentNotificationListener : NotificationListenerService() {

    @Inject
    lateinit var notificationEntryUseCase: NotificationEntryUseCase

    @Inject
    lateinit var autoEntrySettingsRepository: AutoEntrySettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        serviceScope.launch {
            if (!autoEntrySettingsRepository.isEnabled()) return@launch
            val extras = sbn.notification.extras
            val title = extras.getString(Notification.EXTRA_TITLE, "").orEmpty()
            val text = extras.getString(Notification.EXTRA_TEXT, "").orEmpty()
            val transactionDate = Instant.ofEpochMilli(sbn.postTime)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            notificationEntryUseCase.handle(
                packageName = sbn.packageName,
                notifyKey = sbn.key,
                title = title,
                text = text,
                transactionDate = transactionDate,
            )
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
