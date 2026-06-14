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
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Primary forwarding mechanism: posts a payment notification to the consolidated
 * global bank endpoint.
 */
class BankWebhookDispatcher(
    private val repository: BankConfigRepository,
    private val status: AgentStatusRepository
) {

    suspend fun sendWebhookForBank(
        bankName: String,
        amount: Double,
        fromAccount: String?
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
                amount = amount,
                fromAccount = fromAccount
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
        amount: Double,
        fromAccount: String?
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val body = buildJson(
                bankName = bankName,
                amount = amount,
                fromAccount = fromAccount
            ).toRequestBody(JSON)
            val builder = Request.Builder()
                .url(endpointUrl)
                .post(body)
                .header("Accept", "application/json")
                .header("Onix-Application-Type", DEFAULT_APPLICATION_TYPE)
            if (apiKey.isNotBlank()) {
                builder.header("Authorization", Credentials.basic("api", apiKey))
            }

            AgentHttpClient.client.newCall(builder.build()).execute().use { response ->
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
            Log.w(TAG, "webhook delivery failed: ${t.message}")
            status.recordBankForward(
                bankName = bankName,
                ok = false,
                error = t.message
            )
        }
    }

    private fun buildJson(
        bankName: String,
        amount: Double,
        fromAccount: String?
    ): String = Companion.buildJson(bankName = bankName, amount = amount, fromAccount = fromAccount)

    companion object {
        private const val TAG = "BankWebhookDispatcher"
        private const val DEFAULT_APPLICATION_TYPE = "backend"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        internal fun toMoneyValue(amount: Double): BigDecimal =
            BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)

        internal fun buildJson(
            bankName: String,
            amount: Double,
            fromAccount: String?
        ): String {
            val fields = linkedMapOf<String, String>()
            fields["PaymentAmount"] = toMoneyValue(amount).toPlainString()
            fields["RemainAmount"] = toMoneyValue(0.0).toPlainString()
            fields["TxType"] = jsonString("PayIn")

            val sourceBankCode = SupportedBank.fromCode(bankName)?.code.orEmpty()
            if (sourceBankCode.isNotBlank()) {
                fields["SourceBankCode"] = jsonString(sourceBankCode)
            }
            if (!fromAccount.isNullOrBlank()) {
                fields["SourceBankAccountNo"] = jsonString(fromAccount)
            }
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
