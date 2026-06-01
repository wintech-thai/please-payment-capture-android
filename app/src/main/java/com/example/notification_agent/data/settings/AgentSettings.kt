package com.example.notification_agent.data.settings

/** All user-tunable agent settings, persisted via DataStore. */
data class AgentSettings(
    val webhookUrl: String = "",
    val webhookEnabled: Boolean = false,
    val probeUrl: String = "",
    val probeEnabled: Boolean = false,
    /** Liveness probe period. Default 15 seconds. */
    val probeIntervalSec: Int = DEFAULT_PROBE_INTERVAL_SEC,
    /** Per-request HTTP timeout for the liveness probe. Default 3 seconds. */
    val probeTimeoutMs: Int = DEFAULT_PROBE_TIMEOUT_MS,
    /**
     * When true the agent runs an ongoing foreground service so that the
     * 15-second probe cadence survives Doze / app being swiped away.
     * Off by default because it adds a persistent notification.
     */
    val keepAliveEnabled: Boolean = false
) {
    companion object {
        const val DEFAULT_PROBE_INTERVAL_SEC = 15
        const val DEFAULT_PROBE_TIMEOUT_MS = 3_000
        const val MIN_PROBE_INTERVAL_SEC = 5
        const val MAX_PROBE_INTERVAL_SEC = 3_600
        const val MIN_PROBE_TIMEOUT_MS = 500
        const val MAX_PROBE_TIMEOUT_MS = 60_000
    }
}

