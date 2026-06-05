package com.example.notification_agent.net

import org.junit.Assert.assertEquals
import org.junit.Test

class BankWebhookDispatcherTest {

    @Test
    fun formatsPaymentAmountWithTwoDecimalPlaces() {
        assertEquals("1000.00", BankWebhookDispatcher.toMoneyValue(1000.0).toPlainString())
        assertEquals("100.50", BankWebhookDispatcher.toMoneyValue(100.5).toPlainString())
    }
}

