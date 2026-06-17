package com.example.notification_agent.net

import org.junit.Assert.assertEquals
import org.junit.Test

class BankWebhookDispatcherTest {

    @Test
    fun buildJsonReturnsRawDataDirectly() {
        val rawDataJson = "{\"title\":\"Test\",\"id\":123}"
        val json = BankWebhookDispatcher.buildJson(rawDataJson)
        assertEquals(rawDataJson, json)
    }

    @Test
    fun buildJsonReturnsEmptyObjectWhenRawDataIsNull() {
        val json = BankWebhookDispatcher.buildJson(null)
        assertEquals("{}", json)
    }
}
