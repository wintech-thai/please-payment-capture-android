package com.example.notification_agent.net

import android.util.Log
import com.example.notification_agent.bank.BankConfigRepository
import com.example.notification_agent.bank.SupportedBank
import com.example.notification_agent.status.AgentStatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Primary forwarding mechanism: posts a payment notification to the consolidated
 * global bank endpoint.
 */
class BankWebhookDispatcher(
    private val repository: BankConfigRepository,
    private val status: AgentStatusRepository
) : com.example.notification_agent.bank.BankWebhookTester {

    override suspend fun testWebhook(config: com.example.notification_agent.bank.BankGlobalConfig): Result<Int> {
        val dummyRawData = """{"test":true,"timestamp":${System.currentTimeMillis()},"message":"Test from NotificationAgent"}"""
        Log.d(TAG, "Sending test webhook to ${config.endpointUrl}")
        return sendWebhook(
            endpointUrl = config.endpointUrl,
            apiKey = config.apiKey,
            bankName = "TEST",
            rawDataJson = dummyRawData
        )
    }

    suspend fun sendWebhookForBank(
        bankName: String,
        rawDataJson: String? = null
    ): Result<Int> {
        val bank = SupportedBank.fromCode(bankName) ?: run {
            status.recordBankForward(bankName = bankName, ok = false, error = "unsupported bank")
            return Result.failure(IllegalArgumentException("unsupported bank '$bankName'"))
        }

        val globalConfig = repository.getGlobal()
        if (!globalConfig.enabledBanks.contains(bank.code)) {
            status.recordBankForward(
                bankName = bank.code,
                ok = false,
                error = "bank is not enabled in global config"
            )
            return Result.failure(
                IllegalStateException("bank '${bank.code}' is not enabled in global config")
            )
        }

        if (globalConfig.endpointUrl.isBlank()) {
            status.recordBankForward(
                bankName = bank.code,
                ok = false,
                error = "global endpoint url is not configured"
            )
            return Result.failure(
                IllegalStateException("global endpoint url is not configured")
            )
        }

        return withRetry {
            sendWebhook(
                endpointUrl = globalConfig.endpointUrl,
                apiKey = globalConfig.apiKey,
                bankName = bank.code,
                rawDataJson = rawDataJson
            )
        }
    }

    private suspend fun <T> withRetry(
        maxRetries: Int = 3,
        initialDelay: Long = 1000,
        block: suspend () -> Result<T>
    ): Result<T> {
        var currentDelay = initialDelay
        repeat(maxRetries) { attempt ->
            val result = block()
            if (result.isSuccess) return result
            
            val error = result.exceptionOrNull()
            val shouldRetry = when {
                error is IOException -> true
                error is IllegalStateException && error.message?.startsWith("HTTP 5") == true -> true
                else -> false
            }
            
            if (!shouldRetry || attempt == maxRetries - 1) return result
            
            Log.w(TAG, "Attempt ${attempt + 1} failed, retrying in ${currentDelay}ms: ${error?.message}")
            delay(currentDelay)
            currentDelay *= 2
        }
        return block() // Should not be reached due to repeat loop logic
    }

    /**
     * Forward a payment event to the global endpoint.
     */
    suspend fun sendWebhook(
        endpointUrl: String,
        apiKey: String,
        bankName: String,
        rawDataJson: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        Log.d(TAG, "Preparing to send webhook to $endpointUrl (bank=$bankName)")
        runCatching {
            val bodyJson = buildJson(rawDataJson)
            Log.d(TAG, "Webhook body: $bodyJson")
            val body = bodyJson.toRequestBody(JSON)
            val builder = Request.Builder()
                .url(endpointUrl)
                .post(body)
                .header("Accept", "application/json")
                .header("Onix-Application-Type", DEFAULT_APPLICATION_TYPE)
            if (apiKey.isNotBlank()) {
                builder.header("Authorization", Credentials.basic("api", apiKey))
            }

            AgentHttpClient.client.newCall(builder.build()).execute().use { response ->
                Log.d(TAG, "Webhook response: code=${response.code} success=${response.isSuccessful}")
                if (response.isSuccessful) {
                    status.recordBankForward(bankName = bankName, ok = true)
                    response.code
                } else {
                    val errorMsg = "HTTP ${response.code}"
                    status.recordBankForward(
                        bankName = bankName,
                        ok = false,
                        error = errorMsg
                    )
                    error(errorMsg)
                }
            }
        }.onFailure { t ->
            Log.e(TAG, "webhook delivery failed: ${t.message}", t)
            status.recordBankForward(
                bankName = bankName,
                ok = false,
                error = t.message
            )
        }
    }

    private fun buildJson(
        rawDataJson: String?
    ): String = Companion.buildJson(rawDataJson)

    companion object {
        private const val TAG = "BankWebhookDispatcher"
        private const val DEFAULT_APPLICATION_TYPE = "backend"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        internal fun buildJson(
            rawDataJson: String? = null
        ): String {
            return rawDataJson ?: "{}"
        }

        internal fun buildRawDataJson(message: com.example.notification_agent.data.MessageEntity): String {
            val fields = linkedMapOf<String, String>()
            fields["id"] = message.id.toString()
            fields["sourceType"] = jsonString(message.sourceType.name)
            fields["sourceKey"] = jsonString(message.sourceKey)
            fields["sourceLabel"] = jsonString(message.sourceLabel.orEmpty())
            fields["title"] = jsonString(message.title.orEmpty())
            fields["text"] = jsonString(message.text.orEmpty())
            fields["timestamp"] = message.timestamp.toString()
            return fields.entries.joinToString(
                prefix = "{",
                postfix = "}",
                separator = ","
            ) { (key, value) -> "\"$key\":$value" }
        }

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
