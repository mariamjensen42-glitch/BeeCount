package com.cycling.beecount.domain.usecase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGateTest {

    private val gate = NotificationGate()

    private val wechat = "com.tencent.mm"
    private val alipay = "com.eg.android.AlipayGphone"

    @Test
    fun `passes wechat payment notification`() {
        assertTrue(gate.shouldProcess(wechat, "微信支付", "你已成功支付15.00元\n商户名称：蜜雪冰城"))
    }

    @Test
    fun `passes alipay payment notification`() {
        assertTrue(gate.shouldProcess(alipay, "支付宝", "你已付款 15.00 元，收款方：滴滴出行"))
    }

    @Test
    fun `passes income notification`() {
        assertTrue(gate.shouldProcess(wechat, "微信支付", "你已收款 100.00 元，对方：张三"))
    }

    @Test
    fun `passes red packet notification`() {
        assertTrue(gate.shouldProcess(wechat, "微信红包", "收到红包20.00元"))
    }

    @Test
    fun `passes amount with currency prefix`() {
        assertTrue(gate.shouldProcess(alipay, "支付宝", "转账到账 ¥38.50"))
    }

    @Test
    fun `ignores non whitelisted package even with payment text`() {
        assertFalse(gate.shouldProcess("com.some.bank", "消费提醒", "您尾号1234卡消费15.00元"))
    }

    @Test
    fun `ignores notification without amount`() {
        assertFalse(gate.shouldProcess(wechat, "微信支付", "支付成功，感谢使用"))
    }

    @Test
    fun `ignores notification without payment keyword`() {
        assertFalse(gate.shouldProcess(wechat, "微信", "本周步数 15.00 步，继续保持"))
    }

    @Test
    fun `ignores blank text`() {
        assertFalse(gate.shouldProcess(wechat, "", ""))
    }
}
