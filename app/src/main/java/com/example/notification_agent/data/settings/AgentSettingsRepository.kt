package com.example.notification_agent.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.agentSettingsStore by preferencesDataStore("agent_settings")

/** DataStore-backed repository for [AgentSettings]. */
class AgentSettingsRepository(private val context: Context) {

    private object Keys {
        val WebhookUrl = stringPreferencesKey("webhook_url")
        val WebhookEnabled = booleanPreferencesKey("webhook_enabled")
        val WebhookBearerToken = stringPreferencesKey("webhook_bearer_token")
        val ProbeUrl = stringPreferencesKey("probe_url")
        val ProbeEnabled = booleanPreferencesKey("probe_enabled")
        val ProbeIntervalSec = intPreferencesKey("probe_interval_sec")
        val ProbeTimeoutMs = intPreferencesKey("probe_timeout_ms")
        val KeepAlive = booleanPreferencesKey("keep_alive_enabled")
    }

    val settings: Flow<AgentSettings> = context.agentSettingsStore.data.map { prefs ->
        AgentSettings(
            webhookUrl = prefs[Keys.WebhookUrl].orEmpty(),
            webhookEnabled = prefs[Keys.WebhookEnabled] ?: false,
            webhookBearerToken = prefs[Keys.WebhookBearerToken].orEmpty(),
            probeUrl = prefs[Keys.ProbeUrl].orEmpty(),
            probeEnabled = prefs[Keys.ProbeEnabled] ?: false,
            probeIntervalSec = prefs[Keys.ProbeIntervalSec]
                ?: AgentSettings.DEFAULT_PROBE_INTERVAL_SEC,
            probeTimeoutMs = prefs[Keys.ProbeTimeoutMs]
                ?: AgentSettings.DEFAULT_PROBE_TIMEOUT_MS,
            keepAliveEnabled = prefs[Keys.KeepAlive] ?: false
        )
    }

    suspend fun update(transform: (AgentSettings) -> AgentSettings) {
        context.agentSettingsStore.edit { prefs ->
            val current = AgentSettings(
                webhookUrl = prefs[Keys.WebhookUrl].orEmpty(),
                webhookEnabled = prefs[Keys.WebhookEnabled] ?: false,
                webhookBearerToken = prefs[Keys.WebhookBearerToken].orEmpty(),
                probeUrl = prefs[Keys.ProbeUrl].orEmpty(),
                probeEnabled = prefs[Keys.ProbeEnabled] ?: false,
                probeIntervalSec = prefs[Keys.ProbeIntervalSec]
                    ?: AgentSettings.DEFAULT_PROBE_INTERVAL_SEC,
                probeTimeoutMs = prefs[Keys.ProbeTimeoutMs]
                    ?: AgentSettings.DEFAULT_PROBE_TIMEOUT_MS,
                keepAliveEnabled = prefs[Keys.KeepAlive] ?: false
            )
            val next = transform(current).normalised()
            prefs[Keys.WebhookUrl] = next.webhookUrl
            prefs[Keys.WebhookEnabled] = next.webhookEnabled
            prefs[Keys.WebhookBearerToken] = next.webhookBearerToken
            prefs[Keys.ProbeUrl] = next.probeUrl
            prefs[Keys.ProbeEnabled] = next.probeEnabled
            prefs[Keys.ProbeIntervalSec] = next.probeIntervalSec
            prefs[Keys.ProbeTimeoutMs] = next.probeTimeoutMs
            prefs[Keys.KeepAlive] = next.keepAliveEnabled
        }
    }

    private fun AgentSettings.normalised(): AgentSettings = copy(
        webhookBearerToken = webhookBearerToken.trim(),
        probeIntervalSec = probeIntervalSec.coerceIn(
            AgentSettings.MIN_PROBE_INTERVAL_SEC,
            AgentSettings.MAX_PROBE_INTERVAL_SEC
        ),
        probeTimeoutMs = probeTimeoutMs.coerceIn(
            AgentSettings.MIN_PROBE_TIMEOUT_MS,
            AgentSettings.MAX_PROBE_TIMEOUT_MS
        )
    )
}

