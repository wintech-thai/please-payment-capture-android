package com.example.notification_agent.net

import android.util.Log
import com.example.notification_agent.bank.BankConfig
import com.example.notification_agent.bank.BankConfigRepository
import com.example.notification_agent.status.AgentStatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Primary forwarding mechanism: posts a payment notification to the endpoint of
 * a specific [BankConfig].
 *
 * This supersedes the legacy single-URL `WebhookDispatcher`. Each device can
 * hold many bank configs and forward to each independently.
 *
 * The caller is responsible for parsing the captured notification into
 * [amount] / [fromAccount] and picking the right [configId] — that bridge is
 * intentionally not implemented here.
 */
class BankWebhookDispatcher(
    private val repository: BankConfigRepository,
    private val status: AgentStatusRepository
) {

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
                    status.recordWebhook(ok = true)
                } else {
                    status.recordWebhook(ok = false, error = "HTTP ${response.code}")
                }
                response.code
            }
        }.onFailure { t ->
            Log.w(TAG, "webhook delivery failed: ${t.message}")
            status.recordWebhook(ok = false, error = t.message)
        }
    }

    private fun buildJson(
        config: BankConfig,
        amount: Double,
        fromAccount: String?
    ): String {
        val json = JSONObject()
            // Always-present fields.
            .put("PaymentAmount", amount)
            .put("RemainAmount", 0.00)
            .put("TxType", "PayIn")
            .put("DestinationBankCode", DEFAULT_DEST_BANK_CODE)
            .put("DestinationAccountNo", DEFAULT_DEST_ACCOUNT_NO)

        // Optional fields: only included when present and non-blank.
        if (config.bankName.isNotBlank()) {
            json.put("SourceBankCode", config.bankName)
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
    }
}
