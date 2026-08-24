package com.cycling.beecount.data.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cycling.beecount.domain.model.AnomalyAlert
import com.cycling.beecount.domain.usecase.AnomalyNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 异常消费预警的 Android 通知实现：懒建通知渠道，API33+ 检查 POST_NOTIFICATIONS 权限，
 * 无权限时静默跳过（不崩）。仅依赖本地数据，零云依赖。
 */
@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : AnomalyNotifier {

    override fun notify(alert: AnomalyAlert) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "异常消费预警",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "单笔或单日消费偏离历史基线时提醒" },
        )
        val id = (alert.date.toEpochDay() xor alert.category.hashCode().toLong()).toInt()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("异常消费预警")
            .setContentText(alert.message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
        runCatching { nm.notify(id, builder.build()) }
    }

    companion object {
        const val CHANNEL_ID = "anomaly_alert"
    }
}
