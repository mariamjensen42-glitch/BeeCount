package com.cycling.beecount.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * 自动记账通知通道（ADR 0014）：
 * - [CONFIRM] 解析成功后的确认提醒（默认重要性）；
 * - [FAILED] 解析失败的低姿态提示（低重要性，减少打扰）。
 * 在 Application.onCreate 创建一次，幂等。
 */
object NotificationChannels {
    const val CONFIRM = "auto_entry_confirm"
    const val FAILED = "auto_entry_failed"

    fun create(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CONFIRM, "自动记账", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.createNotificationChannel(
            NotificationChannel(FAILED, "记账失败提醒", NotificationManager.IMPORTANCE_LOW)
        )
    }
}
