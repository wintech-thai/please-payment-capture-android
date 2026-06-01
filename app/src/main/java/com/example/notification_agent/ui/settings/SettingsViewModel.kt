package com.example.notification_agent.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notification_agent.NotificationAgentApp
import com.example.notification_agent.data.settings.AgentSettings
import com.example.notification_agent.data.settings.AgentSettingsRepository
import com.example.notification_agent.net.LivenessProbe
import com.example.notification_agent.net.WebhookDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class TestResult {
    data class Success(val message: String) : TestResult()
    data class Failure(val message: String) : TestResult()
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val agent = NotificationAgentApp.from(app)
    private val settingsRepo: AgentSettingsRepository = agent.settingsRepository
    @Suppress("DEPRECATION")
    private val dispatcher: WebhookDispatcher = agent.webhookDispatcher
    private val probe: LivenessProbe = agent.livenessProbe

    val settings: StateFlow<AgentSettings> = settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AgentSettings())

    private val _testEvents = MutableSharedFlow<TestResult>(extraBufferCapacity = 4)
    val testEvents: SharedFlow<TestResult> = _testEvents.asSharedFlow()

    fun update(transform: (AgentSettings) -> AgentSettings) {
        viewModelScope.launch { settingsRepo.update(transform) }
    }

    fun sendTestWebhook() {
        viewModelScope.launch {
            val result = dispatcher.sendTest()
            result.fold(
                onSuccess = { code ->
                    _testEvents.emit(
                        if (code in 200..299) TestResult.Success("HTTP $code")
                        else TestResult.Failure("HTTP $code")
                    )
                },
                onFailure = { _testEvents.emit(TestResult.Failure(it.message ?: "error")) }
            )
        }
    }

    fun sendTestProbe() {
        viewModelScope.launch {
            val r = probe.ping()
            _testEvents.emit(
                if (r.ok) TestResult.Success("HTTP ${r.httpCode} • ${r.latencyMs} ms")
                else TestResult.Failure(r.error ?: "HTTP ${r.httpCode}")
            )
        }
    }
}

