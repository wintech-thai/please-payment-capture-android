package com.example.notification_agent.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.notification_agent.NotificationAgentApp
import com.example.notification_agent.data.MessageEntity
import com.example.notification_agent.data.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Captures incoming SMS without becoming the default SMS app. Works on
 * Android 11+ provided the user has granted RECEIVE_SMS / READ_SMS
 * permissions.
 */
class SmsCaptureReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // Group multipart segments by sender.
        val grouped = messages.groupBy { it.originatingAddress.orEmpty() }
        val pending = goAsync()
        val repo = NotificationAgentApp.from(context.applicationContext).repository
        scope.launch {
            try {
                for ((sender, parts) in grouped) {
                    val body = parts.joinToString(separator = "") { it.messageBody.orEmpty() }
                    val timestamp = parts.maxOf { it.timestampMillis }
                    val message = MessageEntity(
                        sourceType = SourceType.SMS,
                        sourceKey = sender,
                        sourceLabel = sender,
                        title = sender.ifBlank { "Unknown" },
                        text = body,
                        timestamp = timestamp
                    )
                    repo.storeMessage(message)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

