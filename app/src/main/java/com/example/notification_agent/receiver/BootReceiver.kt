package com.example.notification_agent.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.notification_agent.NotificationAgentApp
import com.example.notification_agent.service.AgentForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Re-launch the always-on agent after device boot or app upgrade. */
class BootReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        val pending = goAsync()
        val app = NotificationAgentApp.from(context.applicationContext)
        scope.launch {
            try {
                val settings = app.settingsRepository.settings.first()
                if (settings.keepAliveEnabled) {
                    AgentForegroundService.start(context.applicationContext)
                    if (settings.probeEnabled) {
                        AgentForegroundService.scheduleNextProbe(
                            context.applicationContext, settings.probeIntervalSec
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "boot start failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object { private const val TAG = "BootReceiver" }
}

