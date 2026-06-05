package com.example.notification_agent.net

import android.os.Build
import android.util.Log
import com.example.notification_agent.BuildConfig
import com.example.notification_agent.data.MessageEntity
import com.example.notification_agent.data.settings.AgentSettingsRepository
import com.example.notification_agent.net.AgentHttpClient.withAgentHeaders
import com.example.notification_agent.service.NotificationCandidateSnapshot
import com.example.notification_agent.service.NotificationDebugInfo
import com.example.notification_agent.service.NotificationDebugRegistry
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
        val debugInfo = NotificationDebugRegistry.consume(message)
        val body = buildJson(
            m = message,
            deviceId = deviceId,
            deviceLabel = "${Build.MANUFACTURER} ${Build.MODEL}",
            agentVersion = BuildConfig.VERSION_NAME,
            notificationDebug = debugInfo
        ).toRequestBody(JSON)
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
                        } catch (_: Exception) {
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
                    is UnknownHostException ->
                        "❌ DNS Error: Cannot resolve hostname '${current.webhookUrl}'. Check URL and internet connection."
                    is ConnectException ->
                        "❌ Connection refused: Server not reachable at '${current.webhookUrl}'"
                    is MalformedURLException ->
                        "❌ Invalid URL format: ${e.message}"
                    is IOException ->
                        "❌ Network error: ${e.message ?: "Unknown IO error"}"
                    else ->
                        "❌ ${e.javaClass.simpleName}: ${e.message ?: e.toString()}"
                }

                status.recordWebhook(ok = false, error = errorMsg)
                Log.e(TAG, "webhook exception: $errorMsg", e)
                throw IllegalStateException(errorMsg, e)
            }
        }
    }

    companion object {
        private const val TAG = "WebhookDispatcher"
        private const val QUEUE_CAPACITY = 256
        private val JSON = "application/json; charset=utf-8".toMediaType()

        internal fun buildJson(
            m: MessageEntity,
            deviceId: String,
            deviceLabel: String,
            agentVersion: String,
            notificationDebug: NotificationDebugInfo? = null
        ): String {
            val fields = linkedMapOf<String, String>()
            fields["id"] = m.id.toString()
            fields["sourceType"] = jsonString(m.sourceType.name)
            fields["sourceKey"] = jsonString(m.sourceKey)
            fields["sourceLabel"] = jsonString(m.sourceLabel.orEmpty())
            fields["title"] = jsonString(m.title.orEmpty())
            fields["text"] = jsonString(m.text.orEmpty())
            fields["timestamp"] = m.timestamp.toString()
            fields["deviceId"] = jsonString(deviceId)
            fields["device"] = jsonString(deviceLabel)
            fields["agentVersion"] = jsonString(agentVersion)

            if (m.sourceType == com.example.notification_agent.data.SourceType.NOTIFICATION) {
                fields["notificationAppPackage"] = jsonString(m.sourceKey)
                if (!m.sourceLabel.isNullOrBlank()) {
                    fields["notificationAppName"] = jsonString(m.sourceLabel)
                }
                notificationDebug?.let {
                    fields["notificationDebug"] = notificationDebugToJson(it)
                }
            }

            return fields.entries.joinToString(
                prefix = "{",
                postfix = "}",
                separator = ","
            ) { (key, value) -> "\"$key\":$value" }
        }

        private fun notificationDebugToJson(debug: NotificationDebugInfo): String {
            val fields = linkedMapOf<String, String>()
            fields["selectedTitleSource"] = nullableJsonString(debug.selectedTitleSource)
            fields["selectedTextSource"] = nullableJsonString(debug.selectedTextSource)
            fields["titleCandidates"] = candidatesToJson(debug.titleCandidates)
            fields["textCandidates"] = candidatesToJson(debug.textCandidates)
            return fields.entries.joinToString(
                prefix = "{",
                postfix = "}",
                separator = ","
            ) { (key, value) -> "\"$key\":$value" }
        }

        private fun candidatesToJson(candidates: List<NotificationCandidateSnapshot>): String =
            candidates.joinToString(prefix = "[", postfix = "]", separator = ",") { candidate ->
                linkedMapOf(
                    "source" to jsonString(candidate.source),
                    "value" to jsonString(candidate.value),
                    "length" to candidate.length.toString()
                ).entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (key, value) ->
                    "\"$key\":$value"
                }
            }

        private fun nullableJsonString(value: String?): String = value?.let(::jsonString) ?: "null"

        private fun jsonString(value: String): String =
            buildString(value.length + 2) {
                append('"')
                value.forEach { ch ->
                    when (ch) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\b' -> append("\\b")
                        '\u000C' -> append("\\f")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> append(ch)
                    }
                }
                append('"')
            }
    }
}

