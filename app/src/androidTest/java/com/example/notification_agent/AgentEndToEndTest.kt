package com.example.notification_agent

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.notification_agent.data.FilterRuleEntity
import com.example.notification_agent.data.MessageEntity
import com.example.notification_agent.data.SourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E test that drives the agent against a host-side webhook listener
 * (see scripts/agent_webhook.py). Run with:
 *
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=com.example.notification_agent.AgentEndToEndTest
 *
 * The host listener must be reachable at http://10.0.2.2:8088 from the
 * emulator. This test does NOT assert HTTP responses (the host log file
 * is the source of truth for delivery) — it only configures the agent
 * and triggers the dispatcher / probe so they generate traffic.
 */
@RunWith(AndroidJUnit4::class)
class AgentEndToEndTest {

    private val app: NotificationAgentApp =
        ApplicationProvider.getApplicationContext()

    @Test
    fun configureAndFire() = runBlocking {
        val settings = app.settingsRepository
        settings.update {
            it.copy(
                webhookUrl = "http://10.0.2.2:8088/webhook",
                webhookEnabled = true,
                probeUrl = "http://10.0.2.2:8088/probe",
                probeEnabled = true,
                probeIntervalSec = 15,
                probeTimeoutMs = 3000
            )
        }

        // 1) Direct webhook test payload.
        @Suppress("DEPRECATION")
        val webhookCode = app.webhookDispatcher.sendTest().getOrThrow()
        Log.i(TAG, "test webhook returned HTTP $webhookCode")
        assertEquals(200, webhookCode)

        // 2) Direct probe ping.
        val probeResult = app.livenessProbe.ping()
        Log.i(TAG, "probe ok=${probeResult.ok} code=${probeResult.httpCode} latency=${probeResult.latencyMs}ms")
        assertEquals(true, probeResult.ok)

        // 3) End-to-end: insert a NOTIFICATION rule with forwardToWebhook = true,
        //    then push a synthetic captured message through the repository.
        app.repository.upsertRule(
            FilterRuleEntity(
                sourceType = SourceType.NOTIFICATION,
                sourceKey = "com.example.fake",
                sourceLabel = "Fake app",
                enabled = true,
                forwardToWebhook = true
            )
        )
        app.repository.storeMessage(
            MessageEntity(
                sourceType = SourceType.NOTIFICATION,
                sourceKey = "com.example.fake",
                sourceLabel = "Fake app",
                title = "End-to-end title",
                text = "End-to-end body from instrumented test",
                timestamp = System.currentTimeMillis()
            )
        )
        // Allow the dispatcher coroutine to drain.
        kotlinx.coroutines.delay(2_500)

        val status = app.statusRepository.state.value
        Log.i(TAG, "status sent=${status.webhookSentCount} failed=${status.webhookFailedCount} " +
            "lastProbeOk=${status.lastProbeOk} lastWebhookOk=${status.lastWebhookOk}")
        assertNotNull(status.lastProbeOk)
        assertNotNull(status.lastWebhookOk)
    }

    companion object { private const val TAG = "AgentE2E" }
}

