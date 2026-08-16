package com.cycling.beecount.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cycling.beecount.MainActivity
import com.cycling.beecount.R
import com.cycling.beecount.domain.model.AiParseResult
import com.cycling.beecount.domain.usecase.AutoEntryNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 自动记账提醒的 Android 实现（ADR 0014）：
 * 确认提醒深链到助手页（待确认草稿卡片直接展示），失败提醒预填通知原文。
 * API 33+ 未授予 POST_NOTIFICATIONS 时 notify 静默失效——草稿仍在队列，进 App 可见。
 */
@Singleton
class AutoEntryNotificationPoster @Inject constructor(
    @ApplicationContext private val context: Context,
) : AutoEntryNotifier {

    override fun notifyDraftReady(packageName: String, draftId: Long, result: AiParseResult) {
        val label = PACKAGE_LABELS[packageName] ?: "支付"
        val text = buildString {
            append(result.categoryName ?: "账目")
            append(" ¥")
            append(formatAmount(result.amount))
            append(" · ")
            append(label)
            append("，点此确认")
        }
        val pendingIntent = activityPendingIntent(
            AutoEntryDeepLink.EXTRA_SHOW_PENDING to true,
            AutoEntryDeepLink.EXTRA_DRAFT_ID to draftId,
        )
        notify(NotificationChannels.CONFIRM, NOTIFICATION_ID_DRAFT, "待确认一笔账", text, pendingIntent)
    }

    override fun notifyParseFailed(originalText: String) {
        val pendingIntent = activityPendingIntent(AutoEntryDeepLink.EXTRA_RETRY_TEXT to originalText)
        notify(
            NotificationChannels.FAILED,
            NOTIFICATION_ID_FAILED,
            "自动记账未成功",
            "有一笔支付未能自动记账，点此手动处理",
            pendingIntent,
        )
    }

    private fun activityPendingIntent(vararg extra: Pair<String, Any>): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            extra.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putExtra(key, value)
                    is String -> putExtra(key, value)
                    is Long -> putExtra(key, value)
                }
            }
        }
        val requestCode = extra.firstOrNull()?.first?.hashCode() ?: 0
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notify(channelId: String, id: Int, title: String, text: String, pendingIntent: PendingIntent) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_auto_entry)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun formatAmount(amount: Double?): String =
        if (amount == null) "?" else String.format(Locale.ROOT, "%.2f", amount)

    private companion object {
        const val NOTIFICATION_ID_DRAFT = 0xAE01
        const val NOTIFICATION_ID_FAILED = 0xAE02

        val PACKAGE_LABELS: Map<String, String> = mapOf(
            "com.tencent.mm" to "微信支付",
            "com.eg.android.AlipayGphone" to "支付宝",
        )
    }
}
