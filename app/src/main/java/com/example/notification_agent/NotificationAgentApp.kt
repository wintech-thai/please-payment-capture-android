package com.example.notification_agent

import android.app.Application
import android.os.SystemClock
import android.provider.Settings
import androidx.room.Room
import com.example.notification_agent.data.AppDatabase
import com.example.notification_agent.data.MessageRepository
import com.example.notification_agent.bank.BankConfigRepository
import com.example.notification_agent.data.settings.AgentSettingsRepository
import com.example.notification_agent.net.BankWebhookDispatcher
import com.example.notification_agent.net.LivenessProbe
import com.example.notification_agent.net.WebhookDispatcher
import com.example.notification_agent.status.AgentStatusRepository
import com.example.notification_agent.worker.AgentWatchdogWorker

/** Process-wide DI container (manual). */
class NotificationAgentApp : Application() {

    private val processStartElapsed = SystemClock.elapsedRealtime()

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "notification_agent.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
    }

    val statusRepository: AgentStatusRepository by lazy { AgentStatusRepository() }

    val settingsRepository: AgentSettingsRepository by lazy {
        AgentSettingsRepository(applicationContext)
    }

    /** Primary multi-bank forwarding config store. */
    val bankConfigRepository: BankConfigRepository by lazy {
        BankConfigRepository(applicationContext)
    }

    private val deviceId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
    }

    /** Primary forwarding dispatcher. Call sites supply parsed amount/account. */
    val bankWebhookDispatcher: BankWebhookDispatcher by lazy {
        BankWebhookDispatcher(bankConfigRepository, statusRepository)
    }

    @Suppress("DEPRECATION")
    val webhookDispatcher: WebhookDispatcher by lazy {
        WebhookDispatcher(settingsRepository, statusRepository, deviceId)
    }

    val livenessProbe: LivenessProbe by lazy {
        LivenessProbe(
            settings = settingsRepository,
            status = statusRepository,
            deviceId = deviceId,
            statusProvider = {
                val s = statusRepository.state.value
                LivenessProbe.ProbePayload(
                    uptimeSec = (SystemClock.elapsedRealtime() - processStartElapsed) / 1000L,
                    lastCaptureTs = s.lastCaptureAt,
                    queuedWebhooks = s.webhookQueueDepth
                )
            }
        )
    }

    val repository: MessageRepository by lazy {
        MessageRepository(database) { message ->
            // The forwarding decision has already been made by the repository.
            // NOTE: this still uses the legacy single-URL webhook. The primary
            // path is now BankWebhookDispatcher.sendWebhook(configId, amount,
            // fromAccount) — wire the notification parser to it here.
            @Suppress("DEPRECATION")
            webhookDispatcher.enqueue(message)
            statusRepository.recordCapture()
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Eagerly initialise the legacy dispatcher so its consumer coroutine
        // starts. Kept for backwards compatibility / instrumentation tests.
        @Suppress("DEPRECATION")
        webhookDispatcher
        AgentWatchdogWorker.enqueue(this)
    }

    companion object {
        fun from(context: android.content.Context): NotificationAgentApp =
            context.applicationContext as NotificationAgentApp
    }
}
