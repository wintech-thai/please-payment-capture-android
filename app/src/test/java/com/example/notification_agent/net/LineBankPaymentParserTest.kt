package com.example.notification_agent.net

import com.example.notification_agent.bank.SupportedBank
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineBankPaymentParserTest {

    @Test
    fun parsesScbIncomingPaymentFromLine() {
        val result = LineBankPaymentParser.parseNotification(
            packageName = LineBankPaymentParser.LINE_PACKAGE_NAME,
            title = "SCB Connect",
            text = "รายการเงินเข้า 100.00 บาท เข้าบัญชี X-7985 วันที่ "
        )

        requireNotNull(result)
        assertEquals(SupportedBank.SCB, result.bank)
        assertEquals(100.0, result.amount, 0.0)
        assertEquals("X-7985", result.sourceAccount)
    }

    @Test
    fun parsesKtbIncomingPaymentFromLine() {
        val result = LineBankPaymentParser.parseNotification(
            packageName = LineBankPaymentParser.LINE_PACKAGE_NAME,
            title = "Krungthai Connext",
            text = "เงินเข้า: 100.00 บาท เข้าบัญชี XX7157 เมื่อ 04/06/"
        )

        requireNotNull(result)
        assertEquals(SupportedBank.KTB, result.bank)
        assertEquals(100.0, result.amount, 0.0)
        assertEquals("XX7157", result.sourceAccount)
    }

    @Test
    fun parsesCommaSeparatedAmountFromLine() {
        val result = LineBankPaymentParser.parseNotification(
            packageName = LineBankPaymentParser.LINE_PACKAGE_NAME,
            title = "SCB Connect",
            text = "รายการเงินเข้า 1,000.00 บาท เข้าบัญชี X-7985 วันที่ "
        )

        requireNotNull(result)
        assertEquals(SupportedBank.SCB, result.bank)
        assertEquals(1000.0, result.amount, 0.0)
        assertEquals("X-7985", result.sourceAccount)
    }

    @Test
    fun rejectsNonIncomingMessages() {
        val result = LineBankPaymentParser.parseNotification(
            packageName = LineBankPaymentParser.LINE_PACKAGE_NAME,
            title = "SCB Connect",
            text = "ยอดเงินคงเหลือ 100.00 บาท"
        )

        assertNull(result)
    }

    @Test
    fun rejectsUnsupportedNotificationSource() {
        val result = LineBankPaymentParser.parseNotification(
            packageName = "com.instagram.android",
            title = "SCB Connect",
            text = "เงินเข้า: 100.00 บาท เข้าบัญชี XX7157"
        )

        assertNull(result)
    }
}


