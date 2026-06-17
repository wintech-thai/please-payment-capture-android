package com.example.notification_agent.bank

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.bankConfigStore by preferencesDataStore("bank_configs")

/**
 * DataStore-backed store for the list of [BankConfig]s plus an optional local
 * launch PIN.
 *
 * The config list is serialised as a JSON array string under a single key. This
 * keeps the whole collection atomic and avoids pulling in a new serialization
 * dependency (uses the bundled `org.json`). All data lives on disk so it
 * survives reboots and app upgrades.
 */
class BankConfigRepository(private val context: Context) {

    private object Keys {
        val Configs = stringPreferencesKey("configs_json")
        val GlobalUrl = stringPreferencesKey("bank_global_url")
        val GlobalApiKey = stringPreferencesKey("bank_global_apikey")
        val GlobalAgentId = stringPreferencesKey("bank_global_agentid")
        val GlobalEnabledBanks = stringSetPreferencesKey("bank_global_enabled_banks")
        val GlobalEnabledLineBanks = stringSetPreferencesKey("bank_global_enabled_line_banks")
        val GlobalEnabledSmsBanks = stringSetPreferencesKey("bank_global_enabled_sms_banks")
        val GlobalForwardLineBanks = stringSetPreferencesKey("bank_global_forward_line_banks")
        val GlobalForwardSmsBanks = stringSetPreferencesKey("bank_global_forward_sms_banks")
        val Pin = stringPreferencesKey("launch_pin")
        val PinEnabled = booleanPreferencesKey("launch_pin_enabled")
    }

    /** Live list of all stored configs. */
    val configs: Flow<List<BankConfig>> = context.bankConfigStore.data.map { prefs ->
        decode(prefs[Keys.Configs])
    }

    /** Live global configuration. */
    val globalConfig: Flow<BankGlobalConfig> = context.bankConfigStore.data.map { prefs ->
        val enabledBanks = prefs[Keys.GlobalEnabledBanks] ?: emptySet()

        // Backward compatibility: if granular keys are missing, default to main enabledBanks set
        val enabledLineBanks = if (prefs.contains(Keys.GlobalEnabledLineBanks)) {
            prefs[Keys.GlobalEnabledLineBanks] ?: emptySet()
        } else {
            enabledBanks
        }
        val forwardLineBanks = if (prefs.contains(Keys.GlobalForwardLineBanks)) {
            prefs[Keys.GlobalForwardLineBanks] ?: emptySet()
        } else {
            enabledBanks
        }

        BankGlobalConfig(
            endpointUrl = prefs[Keys.GlobalUrl] ?: "",
            apiKey = prefs[Keys.GlobalApiKey] ?: "",
            agentId = prefs[Keys.GlobalAgentId] ?: "",
            enabledBanks = enabledBanks,
            enabledLineBanks = enabledLineBanks,
            enabledSmsBanks = prefs[Keys.GlobalEnabledSmsBanks] ?: emptySet(),
            forwardLineBanks = forwardLineBanks,
            forwardSmsBanks = prefs[Keys.GlobalForwardSmsBanks] ?: emptySet()
        )
    }

    /** Whether a launch PIN is configured & enabled. */
    val pinEnabled: Flow<Boolean> = context.bankConfigStore.data.map { prefs ->
        (prefs[Keys.PinEnabled] ?: false) && !prefs[Keys.Pin].isNullOrBlank()
    }

    suspend fun getAll(): List<BankConfig> = configs.first()

    suspend fun getGlobal(): BankGlobalConfig = globalConfig.first()

    suspend fun updateGlobal(config: BankGlobalConfig) {
        context.bankConfigStore.edit { prefs ->
            prefs[Keys.GlobalUrl] = config.endpointUrl
            prefs[Keys.GlobalApiKey] = config.apiKey
            prefs[Keys.GlobalAgentId] = config.agentId
            prefs[Keys.GlobalEnabledBanks] = config.enabledBanks
            prefs[Keys.GlobalEnabledLineBanks] = config.enabledLineBanks
            prefs[Keys.GlobalEnabledSmsBanks] = config.enabledSmsBanks
            prefs[Keys.GlobalForwardLineBanks] = config.forwardLineBanks
            prefs[Keys.GlobalForwardSmsBanks] = config.forwardSmsBanks
        }
    }

    suspend fun get(id: String): BankConfig? = getAll().firstOrNull { it.id == id }

    /** Insert a new config (or replace one with the same id). */
    suspend fun add(config: BankConfig) = mutate { list ->
        list.filterNot { it.id == config.id } + config
    }

    /** Replace the config sharing [config].id. No-op if it does not exist. */
    suspend fun update(config: BankConfig) = mutate { list ->
        list.map { if (it.id == config.id) config else it }
    }

    suspend fun delete(id: String) = mutate { list ->
        list.filterNot { it.id == id }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = mutate { list ->
        list.map { if (it.id == id) it.copy(isEnabled = enabled) else it }
    }

    // --- Launch PIN ---------------------------------------------------------

    /** Returns true when [pin] matches the stored PIN. */
    suspend fun verifyPin(pin: String): Boolean {
        val stored = context.bankConfigStore.data.first()[Keys.Pin]
        return !stored.isNullOrBlank() && stored == pin
    }

    /** Enable the launch PIN and store its value. */
    suspend fun setPin(pin: String) {
        context.bankConfigStore.edit { prefs ->
            prefs[Keys.Pin] = pin
            prefs[Keys.PinEnabled] = true
        }
    }

    /** Disable & clear the launch PIN. */
    suspend fun clearPin() {
        context.bankConfigStore.edit { prefs ->
            prefs.remove(Keys.Pin)
            prefs[Keys.PinEnabled] = false
        }
    }

    private suspend fun mutate(transform: (List<BankConfig>) -> List<BankConfig>) {
        context.bankConfigStore.edit { prefs ->
            val next = transform(decode(prefs[Keys.Configs]))
            prefs[Keys.Configs] = encode(next)
        }
    }

    private fun decode(json: String?): List<BankConfig> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                BankConfig(
                    id = o.optString("id"),
                    bankName = o.optString("bankName"),
                    endpointUrl = o.optString("endpointUrl"),
                    apiKey = decodeApiKey(o),
                    isEnabled = o.optBoolean("isEnabled", true)
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encode(list: List<BankConfig>): String {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("bankName", c.bankName)
                    .put("endpointUrl", c.endpointUrl)
                        .put("apiKeyCiphertext", DeviceBoundSecretCipher.encrypt(context, c.apiKey))
                    .put("isEnabled", c.isEnabled)
            )
        }
        return arr.toString()
    }

    private fun decodeApiKey(config: JSONObject): String {
        val encrypted = config.optString("apiKeyCiphertext")
        if (encrypted.isNotBlank()) {
            return DeviceBoundSecretCipher.decrypt(context, encrypted).orEmpty()
        }
        return config.optString("apiKey")
    }
}
