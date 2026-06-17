package com.example.notification_agent.bank

/**
 * Interface to allow the config UI module to trigger a test webhook
 * implemented in the app module.
 */
interface BankWebhookTester {
    suspend fun testWebhook(config: BankGlobalConfig): Result<Int>

    companion object {
        @Volatile
        var instance: BankWebhookTester? = null
    }
}
