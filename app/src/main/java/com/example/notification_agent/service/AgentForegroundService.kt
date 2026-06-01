package com.example.notification_agent.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.notification_agent.MainActivity
import com.example.notification_agent.NotificationAgentApp
import com.example.notification_agent.R
import com.example.notification_agent.receiver.AgentAlarmReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Always-on foreground service that owns the liveness probe loop.
 * The actual probe firing is delegated to [AgentAlarmReceiver] via
 * [AlarmManager.setExactAndAllowWhileIdle] so it survives Doze; the service
 * only exists to keep the process alive and to react to settings changes.
 */
class AgentForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var settingsJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForegroundSafely()
        acquireWakeLock()
        val app = NotificationAgentApp.from(applicationContext)
        app.statusRepository.setServiceRunning(true)
        // React to interval changes by rescheduling the alarm.
        settingsJob = scope.launch {
            app.settingsRepository.settings
                .distinctUntilChanged { a, b ->
                    a.probeEnabled == b.probeEnabled &&
                        a.probeIntervalSec == b.probeIntervalSec &&
                        a.keepAliveEnabled == b.keepAliveEnabled
                }
                .collect { s ->
                    if (s.keepAliveEnabled && s.probeEnabled) {
                        scheduleNextProbe(this@AgentForegroundService, s.probeIntervalSec)
                    } else {
                        cancelProbe(this@AgentForegroundService)
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundSafely()
        return START_STICKY
    }

    override fun onDestroy() {
        cancelProbe(this)
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
        scope.cancel()
        NotificationAgentApp.from(applicationContext).statusRepository.setServiceRunning(false)
        super.onDestroy()
    }

    private fun startForegroundSafely() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val contentPi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.agent_running_title))
            .setContentText(getString(R.string.agent_running_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentPi)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompatStartForeground.start(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.agent_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.agent_channel_desc)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NotificationAgent::ForegroundService"
        ).apply { setReferenceCounted(false) }
        // Long-lived; bounded by service lifetime. Acquire indefinitely.
        wakeLock?.acquire()
    }

    companion object {
        private const val TAG = "AgentForegroundService"
        const val CHANNEL_ID = "agent_running"
        const val NOTIFICATION_ID = 4242
        private const val ALARM_REQUEST_CODE = 9100

        fun start(context: Context) {
            val intent = Intent(context, AgentForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AgentForegroundService::class.java))
        }

        /**
         * Schedule the next exact alarm to fire the liveness probe. The
         * receiver re-arms itself, so this only needs to be called when
         * settings change or the service first starts.
         */
        fun scheduleNextProbe(context: Context, intervalSec: Int) {
            val am = context.getSystemService(ALARM_SERVICE) as AlarmManager
            val pi = probePendingIntent(context)
            val triggerAt = SystemClock.elapsedRealtime() + intervalSec * 1000L
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (am.canScheduleExactAlarms()) {
                        am.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi
                        )
                    } else {
                        // Fall back to inexact alarm if user hasn't granted exact.
                        am.setAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi
                        )
                    }
                } else {
                    am.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi
                    )
                }
                NotificationAgentApp.from(context).statusRepository
                    .setNextProbeAt(System.currentTimeMillis() + intervalSec * 1000L)
            } catch (sec: SecurityException) {
                Log.w(TAG, "scheduleExactAlarm denied: ${sec.message}")
            }
        }

        fun cancelProbe(context: Context) {
            val am = context.getSystemService(ALARM_SERVICE) as AlarmManager
            am.cancel(probePendingIntent(context))
            NotificationAgentApp.from(context).statusRepository.setNextProbeAt(0L)
        }

        private fun probePendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, AgentAlarmReceiver::class.java)
                .setAction(AgentAlarmReceiver.ACTION_PROBE)
            return PendingIntent.getBroadcast(
                context, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}

/** API-version split for [Service.startForeground] with FGS type. */
private object ServiceCompatStartForeground {
    fun start(service: Service, id: Int, notification: Notification, type: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(id, notification, type)
        } else {
            service.startForeground(id, notification)
        }
    }
}

