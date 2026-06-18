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
        val testMessage = com.example.notification_agent.data.MessageEntity(
            id = 0,
            sourceType = com.example.notification_agent.data.SourceType.NOTIFICATION,
            sourceKey = "agent.test",
            sourceLabel = "Agent test",
            title = "Test from Notification Agent",
            text = "Hello from Bank Config at ${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis()
        )
        val payload = WebhookPayloadBuilder.buildPayload(
            message = testMessage,
            deviceId = config.agentId.ifBlank { "unknown" }
        )
        Log.d(TAG, "Sending test webhook to ${config.endpointUrl}")
        return sendWebhook(
            endpointUrl = config.endpointUrl,
            apiKey = config.apiKey,
            bankName = "TEST",
            rawDataJson = payload
        )
    }

    override suspend fun testHeartbeat(config: com.example.notification_agent.bank.BankGlobalConfig): Result<Int> {
        val probeUrl = if (config.endpointUrl.isNotBlank()) {
            config.endpointUrl.replace("NotifyLineMessage", "NotifyHeartbeat")
        } else ""
        
        if (probeUrl.isBlank()) return Result.failure(IllegalStateException("No endpoint URL"))
        
        val dummyRawData = """{"timestamp":${System.currentTimeMillis()},"version":"${com.example.notification_agent.BuildConfig.VERSION_NAME}","test":true}"""
        Log.d(TAG, "Sending test heartbeat to $probeUrl")
        return sendWebhook(
            endpointUrl = probeUrl,
            apiKey = config.apiKey,
            bankName = "HEARTBEAT",
            rawDataJson = dummyRawData
        )
    }

    suspend fun sendWebhookForBank(
        bankName: String,
        message: com.example.notification_agent.data.MessageEntity,
        linePayment: LineBankPayment? = null,
        smsPayment: SmsBankPayment? = null
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

        val debugInfo = com.example.notification_agent.service.NotificationDebugRegistry.consume(message)
        val payload = WebhookPayloadBuilder.buildPayload(
            message = message,
            deviceId = globalConfig.agentId.ifBlank { "unknown" },
            notificationDebug = debugInfo,
            linePayment = linePayment,
            smsPayment = smsPayment
        )

        return withRetry {
            sendWebhook(
                endpointUrl = globalConfig.endpointUrl,
                apiKey = globalConfig.apiKey,
                bankName = bank.code,
                rawDataJson = payload
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
                .header("Onix-Application-Type", "backend")
            if (apiKey.isNotBlank()) {
                builder.header("Authorization", Credentials.basic("api", apiKey))
            }

            AgentHttpClient.client.newCall(builder.build()).execute().use { response ->
                val bodyStr = try {
                    response.body?.string()?.take(500)
                } catch (_: Exception) {
                    null
                }
                Log.d(TAG, "Webhook response: code=${response.code} success=${response.isSuccessful} body=$bodyStr")
                if (response.isSuccessful) {
                    status.recordBankForward(bankName = bankName, ok = true)
                    response.code
                } else {
                    val errorMsg = "HTTP ${response.code}${if (!bodyStr.isNullOrBlank()) ": $bodyStr" else ""}"
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
        private val JSON = "application/json; charset=utf-8".toMediaType()

        internal fun buildJson(
            rawDataJson: String? = null
        ): String {
            return rawDataJson ?: "{}"
        }
    }
}
