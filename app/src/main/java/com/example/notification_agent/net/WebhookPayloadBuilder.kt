package com.example.notification_agent.net

import android.os.Build
import com.example.notification_agent.BuildConfig
import com.example.notification_agent.data.MessageEntity
import com.example.notification_agent.data.SourceType
import com.example.notification_agent.service.NotificationCandidateSnapshot
import com.example.notification_agent.service.NotificationDebugInfo

/**
 * Shared logic for building the webhook payload JSON.
 * Consolidates fields from legacy and bank-specific forwarders.
 */
object WebhookPayloadBuilder {

    fun buildPayload(
        message: MessageEntity,
        deviceId: String,
        deviceLabel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
        agentVersion: String = BuildConfig.VERSION_NAME,
        notificationDebug: NotificationDebugInfo? = null,
        linePayment: LineBankPayment? = null,
        smsPayment: SmsBankPayment? = null
    ): String {
        val fields = linkedMapOf<String, String>()
        fields["id"] = message.id.toString()
        fields["sourceType"] = jsonString(message.sourceType.name)
        fields["sourceKey"] = jsonString(message.sourceKey)
        fields["sourceLabel"] = jsonString(message.sourceLabel.orEmpty())
        fields["title"] = jsonString(message.title.orEmpty())
        fields["text"] = jsonString(message.text.orEmpty())
        fields["timestamp"] = message.timestamp.toString()
        fields["deviceId"] = jsonString(deviceId)
        fields["device"] = jsonString(deviceLabel)
        fields["agentVersion"] = jsonString(agentVersion)

        if (message.sourceType == SourceType.NOTIFICATION) {
            fields["notificationAppPackage"] = jsonString(message.sourceKey)
            if (!message.sourceLabel.isNullOrBlank()) {
                fields["notificationAppName"] = jsonString(message.sourceLabel)
            }
            notificationDebug?.let {
                fields["notificationDebug"] = notificationDebugToJson(it)
            }
        }

        linePayment?.let {
            fields["bankCode"] = jsonString(it.bank.code)
            fields["amount"] = it.amount.toString()
            fields["account"] = jsonString(it.sourceAccount.orEmpty())
            fields["channel"] = jsonString("LINE")
        }

        smsPayment?.let {
            fields["bankCode"] = jsonString(it.bank.code)
            fields["amount"] = it.amount.toString()
            fields["account"] = jsonString(it.sourceAccount.orEmpty())
            fields["channel"] = jsonString("SMS")
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
