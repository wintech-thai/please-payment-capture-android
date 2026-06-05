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
import org.json.JSONObject
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
     * @param amount      Payment amount, emitted as `PaymentAmount` (Float).
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

            val body = buildJson(config, amount, fromAccount).toRequestBody(JSON)
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
        config: BankConfig,
        amount: Double,
        fromAccount: String?
    ): String {
        val json = JSONObject()
            // Always-present fields.
            .put("PaymentAmount", toMoneyValue(amount))
            .put("RemainAmount", toMoneyValue(0.0))
            .put("TxType", "PayIn")
            .put("DestinationBankCode", DEFAULT_DEST_BANK_CODE)
            .put("DestinationAccountNo", DEFAULT_DEST_ACCOUNT_NO)

        // Optional fields: only included when present and non-blank.
        val sourceBankCode = SupportedBank.fromCode(config.bankName)?.code.orEmpty()
        if (sourceBankCode.isNotBlank()) {
            json.put("SourceBankCode", sourceBankCode)
        }
        if (!fromAccount.isNullOrBlank()) {
            json.put("SourceBankAccountNo", fromAccount)
        }
        return json.toString()
    }

    companion object {
        private const val TAG = "BankWebhookDispatcher"
        private const val DEFAULT_DEST_BANK_CODE = "TMB"
        private const val DEFAULT_DEST_ACCOUNT_NO = "XX-0032"
        private const val DEFAULT_APPLICATION_TYPE = "backend"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        internal fun toMoneyValue(amount: Double): BigDecimal =
            BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
    }
}
