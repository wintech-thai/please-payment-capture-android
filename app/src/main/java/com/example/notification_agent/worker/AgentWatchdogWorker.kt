package com.example.notification_agent.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.notification_agent.NotificationAgentApp
import com.example.notification_agent.service.AgentForegroundService
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Backup watchdog. WorkManager periodic minimum is 15 minutes, so this is
 * only a safety net if the in-process foreground service was killed and
 * the alarm chain broken.
 */
class AgentWatchdogWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = NotificationAgentApp.from(applicationContext)
        val settings = app.settingsRepository.settings.first()
        if (settings.keepAliveEnabled) {
            if (!app.statusRepository.state.value.serviceRunning) {
                AgentForegroundService.start(applicationContext)
            }
            if (settings.probeEnabled) {
                AgentForegroundService.scheduleNextProbe(
                    applicationContext, settings.probeIntervalSec
                )
            }
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "agent_watchdog"

        fun enqueue(context: Context) {
            val req = PeriodicWorkRequestBuilder<AgentWatchdogWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                req
            )
        }
    }
}

