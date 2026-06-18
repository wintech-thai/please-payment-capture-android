package com.example.notification_agent.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import com.example.notification_agent.NotificationAgentApp
import com.example.notification_agent.data.MessageEntity
import com.example.notification_agent.data.SourceType
import com.example.notification_agent.net.LineBankPaymentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MockLineNotificationReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MOCK_LINE_NOTIFICATION) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: LineBankPaymentParser.LINE_PACKAGE_NAME
                val sourceLabel = decodeText(intent, EXTRA_SOURCE_LABEL_B64, EXTRA_SOURCE_LABEL)
                val title = decodeText(intent, EXTRA_TITLE_B64, EXTRA_TITLE)
                val text = decodeText(intent, EXTRA_TEXT_B64, EXTRA_TEXT)

                Log.i(TAG, "Received mock notification package=$packageName sourceLabel=$sourceLabel title=$title text=$text")

                if (LineBankPaymentParser.parseNotification(packageName, title, text) == null) {
                    Log.w(TAG, "Rejected mock notification for package=$packageName")
                    return@launch
                }

                val label = runCatching {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
                }.getOrNull()

                val message = MessageEntity(
                    sourceType = SourceType.NOTIFICATION,
                    sourceKey = packageName,
                    sourceLabel = sourceLabel ?: label ?: "LINE",
                    title = title,
                    text = text,
                    timestamp = System.currentTimeMillis()
                )

                NotificationAgentApp.from(context).repository.storeMessage(message)
                Log.i(TAG, "Injected mock notification for package=$packageName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inject mock notification", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MOCK_LINE_NOTIFICATION =
            "com.example.notification_agent.action.MOCK_LINE_NOTIFICATION"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_SOURCE_LABEL_B64 = "extra_source_label_b64"
        const val EXTRA_SOURCE_LABEL = "extra_source_label"
        const val EXTRA_TITLE_B64 = "extra_title_b64"
        const val EXTRA_TEXT_B64 = "extra_text_b64"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_TEXT = "extra_text"
        private const val TAG = "MockLineNotifReceiver"
    }

    private fun decodeText(intent: Intent, encodedKey: String, plainKey: String): String? {
        intent.getStringExtra(encodedKey)
            ?.takeIf { it.isNotBlank() }
            ?.let { encoded ->
                return runCatching {
                    String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
                        .trim()
                        .takeIf { it.isNotEmpty() }
                }.getOrNull()
            }

        return intent.getStringExtra(plainKey)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }
}