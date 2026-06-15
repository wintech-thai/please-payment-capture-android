package com.example.notification_agent.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BankWebhookDispatcherTest {

    @Test
    fun formatsPaymentAmountWithTwoDecimalPlaces() {
        assertEquals("1000.00", BankWebhookDispatcher.toMoneyValue(1000.0).toPlainString())
        assertEquals("100.50", BankWebhookDispatcher.toMoneyValue(100.5).toPlainString())
    }

    @Test
    fun usesPostForBankForwarding() {
        assertEquals("POST", BankWebhookDispatcher.HTTP_METHOD)
    }

    @Test
    fun buildJsonOmitsUnknownDestinationFields() {
        val json = BankWebhookDispatcher.buildJson(
            bankName = "SCB",
            amount = 10.45,
            fromAccount = null
        )

        assertTrue(json.contains("\"PaymentAmount\":10.45"))
        assertTrue(json.contains("\"RemainAmount\":0.00"))
        assertTrue(json.contains("\"TxType\":\"PayIn\""))
        assertTrue(json.contains("\"SourceBankCode\":\"SCB\""))
        assertFalse(json.contains("DestinationBankCode"))
        assertFalse(json.contains("DestinationAccountNo"))
        assertFalse(json.contains("SourceBankAccountNo"))
    }

    @Test
    fun buildJsonIncludesSourceAccountOnlyWhenProvided() {
        val json = BankWebhookDispatcher.buildJson(
            bankName = "SCB",
            amount = 10.45,
            fromAccount = "X-7985"
        )

        assertTrue(json.contains("\"SourceBankAccountNo\":\"X-7985\""))
    }

    @Test
    fun buildJsonIncludesRawDataObjWhenProvided() {
        val rawDataJson = "{\"title\":\"Test\"}"
        val json = BankWebhookDispatcher.buildJson(
            bankName = "SCB",
            amount = 10.45,
            fromAccount = null,
            rawDataJson = rawDataJson
        )

        assertTrue(json.contains("\"rawDataObj\":{\"title\":\"Test\"}"))
    }
}

