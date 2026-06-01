package com.example.notification_agent

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.notification_agent.data.FilterRuleEntity
import com.example.notification_agent.data.SourceType
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.FileInputStream
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deterministic notification end-to-end test.
 *
 * This test uses UiAutomation shell commands so it can deterministically:
 *  1) ensure the listener is enabled
 *  2) launch the app once so the listener/session is warm
 *  3) post a shell notification from `com.android.shell`
 *  4) assert that webhook delivery count increases
 */
@RunWith(AndroidJUnit4::class)
class NotificationListenerRealE2ETest {

    private val app: NotificationAgentApp = ApplicationProvider.getApplicationContext()

    @Test
    fun shell_notification_is_forwarded() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

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
        app.repository.upsertRule(
            FilterRuleEntity(
                sourceType = SourceType.NOTIFICATION,
                sourceKey = "com.android.shell",
                sourceLabel = "Android shell",
                enabled = true,
                forwardToWebhook = true
            )
        )

        runShell(instrumentation, "cmd notification allow_listener com.example.notification_agent/com.example.notification_agent.service.NotificationCaptureService")
        runShell(instrumentation, "monkey -p com.example.notification_agent -c android.intent.category.LAUNCHER 1")
        delay(2_000)

        val before = app.statusRepository.state.value.webhookSentCount
        runShell(instrumentation, "cmd notification post -t 'InstrNotif' tag 'Forward this via listener'")

        var after = before
        var attempts = 20
        while (attempts-- > 0 && after <= before) {
            delay(500)
            after = app.statusRepository.state.value.webhookSentCount
        }

        assertTrue(
            "Expected webhookSentCount to increase after a shell notification. before=$before after=$after",
            after > before
        )
    }

    private fun runShell(
        instrumentation: android.app.Instrumentation,
        command: String
    ): String {
        val pfd = instrumentation.uiAutomation.executeShellCommand(command)
        FileInputStream(pfd.fileDescriptor).use { stream ->
            return stream.readBytes().decodeToString()
        }
    }
}







