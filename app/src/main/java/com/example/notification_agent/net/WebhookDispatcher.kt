package com.example.notification_agent.net

import android.os.Build
import android.util.Log
import com.example.notification_agent.BuildConfig
import com.example.notification_agent.data.MessageEntity
import com.example.notification_agent.data.settings.AgentSettingsRepository
import com.example.notification_agent.net.AgentHttpClient.withAgentHeaders
import com.example.notification_agent.status.AgentStatusRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.MalformedURLException
import java.net.UnknownHostException

/**
 * Fire-and-forget webhook sink. Captured messages are pushed onto a bounded
 * channel; a single consumer coroutine on [Dispatchers.IO] drains it and
 * issues an HTTP POST per item. No persistence, no retry — overflow drops
 * the oldest queued payload to keep memory bounded.
 *
 * @deprecated Legacy single-URL forwarder. Superseded by
 * [com.example.notification_agent.net.BankWebhookDispatcher], which supports
 * multiple per-bank endpoints. Retained for backwards compatibility and
 * instrumentation tests.
 */
@Deprecated("Use BankWebhookDispatcher (multi-bank) instead.")
class WebhookDispatcher(
    private val settings: AgentSettingsRepository,
    private val status: AgentStatusRepository,
    private val deviceId: String
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<MessageEntity>(
        capacity = QUEUE_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private var pending = 0

    init {
        scope.launch {
            for (message in queue) {
                synchronized(this@WebhookDispatcher) {
                    pending = (pending - 1).coerceAtLeast(0)
                    status.setWebhookQueueDepth(pending)
                }
                runCatching { send(message) }
                    .onFailure { t ->
                        Log.w(TAG, "webhook delivery failed: ${t.javaClass.simpleName}")
                        status.recordWebhook(ok = false, error = t.message)
                    }
            }
        }
    }

    /** Schedule the message for delivery. Never blocks the caller. */
    fun enqueue(message: MessageEntity) {
        synchronized(this) {
            pending++
            status.setWebhookQueueDepth(pending)
        }
        val accepted = queue.trySend(message).isSuccess
        if (!accepted) {
            // Channel is closed; should not happen.
            synchronized(this) {
                pending = (pending - 1).coerceAtLeast(0)
                status.setWebhookQueueDepth(pending)
            }
        }
    }

    /** Send a one-off test payload using the current settings. */
    suspend fun sendTest(): Result<Int> = runCatching {
        val current = settings.settings.first()
        require(current.webhookEnabled) { "❌ Webhook is disabled. Please enable the webhook toggle in settings." }
        require(current.webhookUrl.isNotBlank()) { "❌ Webhook URL is empty. Please enter: https://demo-hook.rocketlabth.com/webhook" }
        // Additional URL validation
        require(current.webhookUrl.startsWith("http://") || current.webhookUrl.startsWith("https://")) {
            "❌ Invalid URL format. Must start with http:// or https://"
        }
        require(current.webhookBearerToken.isNotBlank()) {
            "⚠️  Bearer token is empty. Webhook may fail on protected endpoints."
        }
        val testMessage = MessageEntity(
            id = 0,
            sourceType = com.example.notification_agent.data.SourceType.NOTIFICATION,
            sourceKey = "agent.test",
            sourceLabel = "Agent test",
            title = "Test from Notification Agent",
            text = "Hello from $deviceId at ${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis()
        )
        send(testMessage, isTest = true)
    }

    private suspend fun send(message: MessageEntity, isTest: Boolean = false): Int {
        val current = settings.settings.first()
        if (!isTest && !current.webhookEnabled) return -1
        val url = current.webhookUrl
        if (url.isBlank()) return -1
        val body = buildJson(message).toRequestBody(JSON)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .withAgentHeaders(current.webhookBearerToken)
            .build()

        return withContext(Dispatchers.IO) {
            try {
                AgentHttpClient.client.newCall(request).execute().use { response ->
                    val ok = response.isSuccessful
                    val code = response.code

                    if (ok) {
                        status.recordWebhook(ok = true)
                        Log.i(TAG, "webhook success: HTTP $code")
                    } else {
                        val bodyStr = try {
                            response.body?.string()?.take(200)
                        } catch (e: Exception) {
                            null
                        }
                        val errorMsg = "HTTP $code${if (!bodyStr.isNullOrBlank()) ": $bodyStr" else ""}"
                        status.recordWebhook(ok = false, error = errorMsg)
                        Log.w(TAG, "webhook failure: $errorMsg")
                    }
                    return@withContext code
                }
            } catch (e: Exception) {
                val errorMsg = when (e) {
                    is java.net.UnknownHostException ->
                        "❌ DNS Error: Cannot resolve hostname '${current.webhookUrl}'. Check URL and internet connection."
                    is java.net.ConnectException ->
                        "❌ Connection refused: Server not reachable at '${current.webhookUrl}'"
                    is java.io.IOException ->
                        "❌ Network error: ${e.message ?: "Unknown IO error"}"
                    is java.net.MalformedURLException ->
                        "❌ Invalid URL format: ${e.message}"
                    else ->
                        "❌ ${e.javaClass.simpleName}: ${e.message ?: e.toString()}"
                }

                status.recordWebhook(ok = false, error = errorMsg)
                Log.e(TAG, "webhook exception: $errorMsg", e)
                throw IllegalStateException(errorMsg, e)
            }
        }
    }

    private fun buildJson(m: MessageEntity): String {
        val json = JSONObject()
            .put("id", m.id)
            .put("sourceType", m.sourceType.name)
            .put("sourceKey", m.sourceKey)
            .put("sourceLabel", m.sourceLabel.orEmpty())
            .put("title", m.title.orEmpty())
            .put("text", m.text.orEmpty())
            .put("timestamp", m.timestamp)
            .put("deviceId", deviceId)
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("agentVersion", BuildConfig.VERSION_NAME)

        if (m.sourceType == com.example.notification_agent.data.SourceType.NOTIFICATION) {
            json.put("notificationAppPackage", m.sourceKey)
            if (!m.sourceLabel.isNullOrBlank()) {
                json.put("notificationAppName", m.sourceLabel)
            }
        }

        return json.toString()
    }

    companion object {
        private const val TAG = "WebhookDispatcher"
        private const val QUEUE_CAPACITY = 256
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

