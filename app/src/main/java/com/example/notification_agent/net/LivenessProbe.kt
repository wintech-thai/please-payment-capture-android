package com.example.notification_agent.net

import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.notification_agent.BuildConfig
import com.example.notification_agent.data.settings.AgentSettings
import com.example.notification_agent.data.settings.AgentSettingsRepository
import com.example.notification_agent.net.AgentHttpClient.withAgentHeaders
import com.example.notification_agent.status.AgentStatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class ProbeResult(val ok: Boolean, val httpCode: Int, val latencyMs: Long, val error: String? = null)

/** Sends a single liveness heartbeat POST. Honours the per-call timeout. */
class LivenessProbe(
    private val settings: AgentSettingsRepository,
    private val status: AgentStatusRepository,
    private val deviceId: String,
    private val statusProvider: () -> ProbePayload
) {

    data class ProbePayload(
        val uptimeSec: Long,
        val lastCaptureTs: Long,
        val queuedWebhooks: Int
    )

    suspend fun ping(): ProbeResult = withContext(Dispatchers.IO) {
        val current = settings.settings.first()
        if (current.probeUrl.isBlank()) {
            return@withContext ProbeResult(false, -1, 0, "no url")
        }
        val payload = statusProvider()
        val body = buildJson(payload).toRequestBody(JSON)
        val client = AgentHttpClient.client.newBuilder()
            .callTimeout(current.probeTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .connectTimeout(current.probeTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(current.probeTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(current.probeTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()
        val request = Request.Builder()
            .url(current.probeUrl)
            .post(body)
            .withAgentHeaders()
            .build()
        val started = SystemClock.elapsedRealtime()
        val result = runCatching {
            client.newCall(request).execute().use { response ->
                ProbeResult(
                    ok = response.isSuccessful,
                    httpCode = response.code,
                    latencyMs = SystemClock.elapsedRealtime() - started
                )
            }
        }.getOrElse { t ->
            Log.w(TAG, "probe failed: ${t.javaClass.simpleName}: ${t.message}")
            ProbeResult(false, -1, SystemClock.elapsedRealtime() - started, t.message)
        }
        status.recordProbe(result.ok, result.latencyMs)
        result
    }

    private fun buildJson(p: ProbePayload): String = """
        {"deviceId":"$deviceId","device":"${Build.MANUFACTURER} ${Build.MODEL}",
        "agentVersion":"${BuildConfig.VERSION_NAME}","ts":${System.currentTimeMillis()},
        "uptimeSec":${p.uptimeSec},"lastCaptureTs":${p.lastCaptureTs},
        "queuedWebhooks":${p.queuedWebhooks}}
    """.trimIndent().replace("\n", "")

    companion object {
        private const val TAG = "LivenessProbe"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}

