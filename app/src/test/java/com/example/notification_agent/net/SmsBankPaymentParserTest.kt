package com.example.notification_agent.net

import com.example.notification_agent.bank.SupportedBank
import com.example.notification_agent.data.MessageEntity
import com.example.notification_agent.data.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsBankPaymentParserTest {

    @Test
    fun parsesScbIncomingSms() {
        val message = MessageEntity(
            sourceType = SourceType.SMS,
            sourceKey = "+6627777777",
            sourceLabel = "SCB",
            title = "+6627777777",
            text = "SCB: ได้รับเงิน 100.00 บ. จาก x1234 เข้าบัญชี x7890",
            timestamp = System.currentTimeMillis()
        )
        val result = SmsBankPaymentParser.parse(message)

        requireNotNull(result)
        assertEquals(SupportedBank.SCB, result.bank)
        assertEquals(100.0, result.amount, 0.0)
        assertEquals("x7890", result.sourceAccount)
    }

    @Test
    fun parsesCommaSeparatedAmountFromSms() {
        val message = MessageEntity(
            sourceType = SourceType.SMS,
            sourceKey = "+6627777777",
            sourceLabel = "SCB",
            title = "+6627777777",
            text = "SCB: ได้รับเงิน 1,234.56 บ. เข้าบัญชี x7890",
            timestamp = System.currentTimeMillis()
        )
        val result = SmsBankPaymentParser.parse(message)

        requireNotNull(result)
        assertEquals(SupportedBank.SCB, result.bank)
        assertEquals(1234.56, result.amount, 0.0)
    }

    @Test
    fun rejectsSmsFromUnknownSender() {
        val message = MessageEntity(
            sourceType = SourceType.SMS,
            sourceKey = "+66812345678",
            sourceLabel = "Unknown",
            title = "+66812345678",
            text = "ได้รับเงิน 100.00 บ.",
            timestamp = System.currentTimeMillis()
        )
        val result = SmsBankPaymentParser.parse(message)

        assertNull(result)
    }

    @Test
    fun rejectsNonSmsMessage() {
        val message = MessageEntity(
            sourceType = SourceType.NOTIFICATION,
            sourceKey = "com.example.app",
            sourceLabel = "App",
            title = "App",
            text = "ได้รับเงิน 100.00 บ.",
            timestamp = System.currentTimeMillis()
        )
        val result = SmsBankPaymentParser.parse(message)

        assertNull(result)
    }
}
