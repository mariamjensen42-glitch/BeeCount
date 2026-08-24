package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.AnomalyAlert

/**
 * 异常预警通知出口（解耦 Android 框架，仿 [com.cycling.beecount.data.repository.WidgetRefresher]）：
 * 仓库写后钩子只产出 [AnomalyAlert]，具体如何弹通知由 Android 实现（[Notifier]）决定，
 * 便于单元测试用 no-op 替换。
 */
fun interface AnomalyNotifier {
    fun notify(alert: AnomalyAlert)
}
