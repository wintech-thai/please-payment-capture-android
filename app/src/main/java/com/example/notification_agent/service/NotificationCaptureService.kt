package com.example.notification_agent.service

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.notification_agent.NotificationAgentApp
import com.example.notification_agent.data.MessageEntity
import com.example.notification_agent.data.SourceType
import com.example.notification_agent.net.LineBankPaymentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reads notifications posted by other apps (requires the user to grant
 * notification access in system Settings).
 */
class NotificationCaptureService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onListenerDisconnected() {
        // Ask the system to rebind us as soon as it can.
        runCatching { requestRebind(ComponentName(this, NotificationCaptureService::class.java)) }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        // Skip our own notifications and ongoing/foreground placeholders.
        if (notification.packageName == packageName) return
        val extras = notification.notification?.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        if (title.isNullOrBlank() && text.isNullOrBlank()) return

        val pkg = notification.packageName
        if (LineBankPaymentParser.parseNotification(pkg, title, text) == null) return
        val label = runCatching {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull()

        val message = MessageEntity(
            sourceType = SourceType.NOTIFICATION,
            sourceKey = pkg,
            sourceLabel = label,
            title = title,
            text = text,
            timestamp = notification.postTime
        )
        val repo = NotificationAgentApp.from(applicationContext).repository
        scope.launch { repo.storeMessage(message) }
    }
}
