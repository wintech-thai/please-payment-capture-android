package com.example.notification_agent

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.notification_agent.data.FilterRuleEntity
import com.example.notification_agent.data.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Helper run before sending real SMS / notifications via adb. It just
 * configures the agent so that `com.android.shell` notifications and a
 * known SMS sender substring are captured AND forwarded.
 */
@RunWith(AndroidJUnit4::class)
class AgentSetupForAdbTest {

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
                probeTimeoutMs = 3000
            )
        }
        // Forward shell notifications.
        app.repository.upsertRule(
            FilterRuleEntity(
                sourceType = SourceType.NOTIFICATION,
                sourceKey = "com.android.shell",
                sourceLabel = "Android shell",
                enabled = true,
                forwardToWebhook = true
            )
        )
        // Forward any SMS whose sender contains "6681" (covers our test number).
        app.repository.upsertRule(
            FilterRuleEntity(
                sourceType = SourceType.SMS,
                sourceKey = "6681",
                sourceLabel = "6681",
                enabled = true,
                forwardToWebhook = true
            )
        )
    }
}

