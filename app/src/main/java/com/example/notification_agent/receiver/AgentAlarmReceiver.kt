package com.example.notification_agent.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import com.example.notification_agent.NotificationAgentApp
import com.example.notification_agent.service.AgentForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Fired by [AlarmManager.setExactAndAllowWhileIdle] for the liveness probe.
 * Always re-arms itself so the probe self-heals even if the foreground
 * service was momentarily killed.
 */
class AgentAlarmReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PROBE) return
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NotificationAgent::ProbeAlarm")
        wl.setReferenceCounted(false)
        wl.acquire(10_000L)

        val pending = goAsync()
        val app = NotificationAgentApp.from(context.applicationContext)
        scope.launch {
            try {
                val settings = app.settingsRepository.settings.first()
                if (settings.probeEnabled && settings.probeUrl.isNotBlank()) {
                    runCatching { app.livenessProbe.ping() }
                        .onFailure { Log.w(TAG, "probe error: ${it.message}") }
                }
                if (settings.keepAliveEnabled && settings.probeEnabled) {
                    AgentForegroundService.scheduleNextProbe(
                        context.applicationContext, settings.probeIntervalSec
                    )
                    // Self-heal: make sure the FG service is still up.
                    if (!app.statusRepository.state.value.serviceRunning) {
                        AgentForegroundService.start(context.applicationContext)
                    }
                }
            } finally {
                if (wl.isHeld) wl.release()
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "AgentAlarmReceiver"
        const val ACTION_PROBE = "com.example.notification_agent.action.PROBE"
    }
}

