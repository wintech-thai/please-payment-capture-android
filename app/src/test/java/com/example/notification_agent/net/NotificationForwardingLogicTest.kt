package com.example.notification_agent.net

import com.example.notification_agent.bank.SupportedBank
import com.example.notification_agent.data.MessageEntity
import com.example.notification_agent.data.SourceType
import com.example.notification_agent.bank.BankGlobalConfig
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationForwardingLogicTest {

    @Test
    fun testLinePaymentParsing() {
        val message = MessageEntity(
            sourceType = SourceType.NOTIFICATION,
            sourceKey = LineBankPaymentParser.LINE_PACKAGE_NAME,
            sourceLabel = "LINE",
            title = "SCB Connect",
            text = "รายการเงินเข้า 1,000.00 บาท เข้าบัญชี X-7985 วันที่ 18/06/2024 22:00",
            timestamp = System.currentTimeMillis()
        )
        
        val payment = LineBankPaymentParser.parse(message)
        assertNotNull("SCB payment should be parsed", payment)
        assertEquals(SupportedBank.SCB, payment?.bank)
        assertEquals(1000.0, payment?.amount ?: 0.0, 0.001)
    }

    private fun assertEquals(expected: Any?, actual: Any?, delta: Double = 0.0) {
        if (expected is Double && actual is Double) {
            org.junit.Assert.assertEquals(expected, actual, delta)
        } else {
            org.junit.Assert.assertEquals(expected, actual)
        }
    }
}
