package com.example.notification_agent.net

import android.util.Log
import com.example.notification_agent.bank.BankConfig
import com.example.notification_agent.bank.BankConfigRepository
import com.example.notification_agent.bank.SupportedBank
import com.example.notification_agent.status.AgentStatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Primary forwarding mechanism: posts a payment notification to the endpoint of
 * a specific [BankConfig].
 *
 * This supersedes the legacy single-URL `WebhookDispatcher`. Each device can
 * hold many bank configs and forward to each independently.
 *
 * The caller is responsible for parsing the captured notification into a bank,
 * amount, and source account before calling this dispatcher.
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
        val configId = repository.getAll()
            .firstOrNull { config ->
                config.isEnabled && SupportedBank.fromCode(config.bankName) == bank
            }
            ?.id ?: run {
            status.recordBankForward(
                bankName = bank.code,
                ok = false,
                error = "no enabled bank endpoint configured"
            )
            return Result.failure(
                IllegalStateException("no enabled bank config for bank '${bank.code}'")
            )
        }
        return sendWebhook(configId = configId, amount = amount, fromAccount = fromAccount)
    }

    /**
     * Forward a payment event to the bank identified by [configId].
     *
     * @param amount      Payment amount, emitted as numeric `PaymentAmount`.
     * @param fromAccount Optional source account number. When non-null/non-blank
     *                    it is emitted as `SourceBankAccountNo`.
     * @return [Result] with the HTTP status code on success.
     */
    suspend fun sendWebhook(
        configId: String,
        amount: Double,
        fromAccount: String?
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val config = repository.get(configId)
                ?: error("no bank config for id=$configId")
            require(config.isEnabled) { "bank config '${config.bankName}' is disabled" }
            require(config.endpointUrl.isNotBlank()) { "endpoint url is blank" }

            val body = buildJson(
                bankName = config.bankName,
                amount = amount,
                fromAccount = fromAccount
            ).toRequestBody(JSON)
            val builder = Request.Builder()
                .url(config.endpointUrl)
                .post(body)
                .header("Accept", "application/json")
                .header("Onix-Application-Type", DEFAULT_APPLICATION_TYPE)
            if (config.apiKey.isNotBlank()) {
                builder.header("Authorization", Credentials.basic("api", config.apiKey))
            }

            AgentHttpClient.client.newCall(builder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    status.recordBankForward(bankName = config.bankName, ok = true)
                } else {
                    status.recordBankForward(
                        bankName = config.bankName,
                        ok = false,
                        error = "HTTP ${response.code}"
                    )
                }
                response.code
            }
        }.onFailure { t ->
            Log.w(TAG, "webhook delivery failed: ${t.message}")
            status.recordBankForward(
                bankName = repository.get(configId)?.bankName ?: configId,
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
        internal const val HTTP_METHOD = "POST"
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
