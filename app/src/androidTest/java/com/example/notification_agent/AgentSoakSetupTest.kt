package com.example.notification_agent

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.notification_agent.data.FilterRuleEntity
import com.example.notification_agent.data.SourceType
import com.example.notification_agent.service.AgentForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One-shot setup used by the host-side soak script.
 *
 * It configures:
 * - webhook + probe endpoints pointing at 10.0.2.2:8088
 * - keep-alive enabled
 * - notification forwarding for com.android.shell
 * - SMS forwarding for any sender containing 6681
 */
@RunWith(AndroidJUnit4::class)
class AgentSoakSetupTest {

    private val app: NotificationAgentApp = ApplicationProvider.getApplicationContext()

    @Test
    fun setup() = runBlocking {
        app.settingsRepository.update {
            it.copy(
                webhookUrl = "http://10.0.2.2:8088/webhook",
                webhookEnabled = true,
                probeUrl = "http://10.0.2.2:8088/probe",
                probeEnabled = true,
                probeIntervalSec = 15,
                probeTimeoutMs = 3_000,
                keepAliveEnabled = true
            )
        }
        app.repository.upsertRule(
            FilterRuleEntity(
                sourceType = SourceType.NOTIFICATION,
                sourceKey = "com.android.shell",
                sourceLabel = "Android shell",
                enabled = true,
                forwardToWebhook = true
            )
        )
        app.repository.upsertRule(
            FilterRuleEntity(
                sourceType = SourceType.SMS,
                sourceKey = "6681",
                sourceLabel = "6681",
                enabled = true,
                forwardToWebhook = true
            )
        )
        AgentForegroundService.start(app)
        delay(1_000)
    }
}


