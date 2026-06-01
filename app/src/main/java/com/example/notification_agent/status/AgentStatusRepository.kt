package com.example.notification_agent.status

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Snapshot of agent runtime state, surfaced to the Status UI. */
data class AgentStatus(
    val serviceRunning: Boolean = false,
    val serviceStartedAt: Long = 0L,

    val lastProbeAt: Long = 0L,
    val lastProbeOk: Boolean? = null,
    val lastProbeLatencyMs: Long = 0L,
    val nextProbeAt: Long = 0L,

    val webhookSentCount: Long = 0L,
    val webhookFailedCount: Long = 0L,
    val webhookQueueDepth: Int = 0,
    val lastWebhookAt: Long = 0L,
    val lastWebhookOk: Boolean? = null,
    val lastWebhookError: String? = null,

    val lastCaptureAt: Long = 0L
)

/**
 * In-process bus that aggregates runtime status for display. Single writer
 * is the agent components themselves (service / dispatcher / probe); the UI
 * is a read-only observer.
 */
class AgentStatusRepository {

    private val _state = MutableStateFlow(AgentStatus())
    val state: StateFlow<AgentStatus> = _state.asStateFlow()

    fun setServiceRunning(running: Boolean) {
        _state.update {
            if (running) it.copy(serviceRunning = true, serviceStartedAt = System.currentTimeMillis())
            else it.copy(serviceRunning = false)
        }
    }

    fun setNextProbeAt(timestampMs: Long) {
        _state.update { it.copy(nextProbeAt = timestampMs) }
    }

    fun recordProbe(ok: Boolean, latencyMs: Long) {
        _state.update {
            it.copy(
                lastProbeAt = System.currentTimeMillis(),
                lastProbeOk = ok,
                lastProbeLatencyMs = latencyMs
            )
        }
    }

    fun setWebhookQueueDepth(depth: Int) {
        _state.update { it.copy(webhookQueueDepth = depth) }
    }

    fun recordWebhook(ok: Boolean, error: String? = null) {
        _state.update {
            if (ok) it.copy(
                webhookSentCount = it.webhookSentCount + 1,
                lastWebhookAt = System.currentTimeMillis(),
                lastWebhookOk = true,
                lastWebhookError = null
            ) else it.copy(
                webhookFailedCount = it.webhookFailedCount + 1,
                lastWebhookAt = System.currentTimeMillis(),
                lastWebhookOk = false,
                lastWebhookError = error
            )
        }
    }

    fun recordCapture() {
        _state.update { it.copy(lastCaptureAt = System.currentTimeMillis()) }
    }
}

