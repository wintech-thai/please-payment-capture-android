package com.example.notification_agent.net

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import com.example.notification_agent.BuildConfig
import com.example.notification_agent.bank.BankConfigRepository
import com.example.notification_agent.data.CrashLogDao
import com.example.notification_agent.data.CrashLogEntity
import com.example.notification_agent.data.settings.AgentSettingsRepository
import com.example.notification_agent.service.DuplicateEvent
import com.example.notification_agent.service.DuplicateEventRegistry
import com.example.notification_agent.status.AgentStatusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.io.RandomAccessFile

data class ProbeResult(val ok: Boolean, val httpCode: Int, val latencyMs: Long, val error: String? = null)

/** Sends a single liveness heartbeat POST. */
class LivenessProbe(
    private val context: Context,
    private val settings: AgentSettingsRepository,
    private val bankConfigs: BankConfigRepository,
    private val status: AgentStatusRepository,
    private val crashLogDao: CrashLogDao
) {

    suspend fun ping(): ProbeResult = withContext(Dispatchers.IO) {
        val current = settings.settings.first()
        val global = bankConfigs.getGlobal()

        val probeUrl = current.probeUrl.ifBlank {
            if (global.endpointUrl.isNotBlank()) {
                global.endpointUrl.replace("NotifyLineMessage", "NotifyHeartbeat")
            } else ""
        }

        if (probeUrl.isBlank()) {
            val result = ProbeResult(false, -1, 0, "no url")
            status.recordProbe(result.ok, result.latencyMs)
            return@withContext result
        }

        val apiKey = global.apiKey
        val pendingCrashes = crashLogDao.pendingLogs()
        val pendingDuplicates = DuplicateEventRegistry.snapshot()

        val body = buildJson(pendingCrashes, pendingDuplicates).toRequestBody(JSON)
        val client = AgentHttpClient.client.newBuilder()
            .callTimeout(current.probeTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .connectTimeout(current.probeTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(current.probeTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(current.probeTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url(probeUrl)
            .post(body)
            .header("Accept", "application/json")
            .header("User-Agent", "NotificationAgent/${BuildConfig.VERSION_NAME}")

        if (apiKey.isNotBlank()) {
            request.header("Authorization", Credentials.basic("api", apiKey))
        }

        val started = SystemClock.elapsedRealtime()
        val result = runCatching {
            client.newCall(request.build()).execute().use { response ->
                ProbeResult(
                    ok = response.isSuccessful,
                    httpCode = response.code,
                    latencyMs = SystemClock.elapsedRealtime() - started
                )
            }
        }.getOrElse { t ->
            Log.w(TAG, "heartbeat failed: ${t.javaClass.simpleName}: ${t.message}")
            ProbeResult(false, -1, SystemClock.elapsedRealtime() - started, t.message)
        }

        if (result.ok) {
            if (pendingCrashes.isNotEmpty()) {
                crashLogDao.markSent(pendingCrashes.map { it.id })
                crashLogDao.deleteSentBefore(System.currentTimeMillis() - 48 * 3600 * 1000L)
            }
            if (pendingDuplicates.isNotEmpty()) {
                DuplicateEventRegistry.remove(pendingDuplicates.map { it.id })
            }
        }

        status.recordProbe(result.ok, result.latencyMs)
        result
    }

    private fun buildJson(
        pendingCrashes: List<CrashLogEntity>,
        pendingDuplicates: List<DuplicateEvent>
    ): String {
        val cpu = Runtime.getRuntime().availableProcessors().toString()
        val memory = getMemoryInfo()
        val osVersion = Build.VERSION.RELEASE
        val appVersion = BuildConfig.VERSION_NAME
        val battery = getBatteryLevel().toString()
        val model = "${Build.MANUFACTURER} ${Build.MODEL}"
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        val storage = getStorageInfo()
        val network = getNetworkType()
        val uptime = (SystemClock.elapsedRealtime() / 3600000L).toString()
        val crashesJson = buildCrashesJson(pendingCrashes)
        val duplicatesJson = buildDuplicatesJson(pendingDuplicates)

        return """{"CPU":"$cpu","Memory":"$memory","OsVersion":"$osVersion","AppVersion":"$appVersion","Battery":"$battery","Model":"$model","DeviceId":"$deviceId","Storage":"$storage","Network":"$network","Uptime":"$uptime","crashes":$crashesJson,"duplicates":$duplicatesJson}"""
    }

    private fun buildDuplicatesJson(duplicates: List<DuplicateEvent>): String {
        if (duplicates.isEmpty()) return "[]"
        val sb = StringBuilder("[")
        duplicates.forEachIndexed { i, d ->
            if (i > 0) sb.append(",")
            sb.append("{")
            sb.append("\"notificationKey\":\"${escape(d.notificationKey)}\",")
            sb.append("\"sourceKey\":\"${escape(d.sourceKey)}\",")
            if (d.title != null) sb.append("\"title\":\"${escape(d.title)}\",") else sb.append("\"title\":null,")
            if (d.text != null) sb.append("\"text\":\"${escape(d.text)}\",") else sb.append("\"text\":null,")
            sb.append("\"firstSeenAt\":${d.firstSeenAt},")
            sb.append("\"duplicateAt\":${d.duplicateAt}")
            sb.append("}")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun buildCrashesJson(crashes: List<CrashLogEntity>): String {
        if (crashes.isEmpty()) return "[]"
        val sb = StringBuilder("[")
        crashes.forEachIndexed { i, c ->
            if (i > 0) sb.append(",")
            sb.append("{")
            sb.append("\"id\":${c.id},")
            sb.append("\"level\":\"${escape(c.level)}\",")
            sb.append("\"tag\":\"${escape(c.tag)}\",")
            sb.append("\"thread\":\"${escape(c.thread)}\",")
            sb.append("\"exception\":\"${escape(c.exceptionClass)}\",")
            if (c.message != null) sb.append("\"message\":\"${escape(c.message)}\",") else sb.append("\"message\":null,")
            sb.append("\"stackTrace\":\"${escape(c.stackTrace)}\",")
            sb.append("\"occurredAt\":${c.occurredAt},")
            sb.append("\"appVersion\":\"${escape(c.appVersion)}\"")
            sb.append("}")
        }
        sb.append("]")
        return sb.toString()
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")

    private fun getStorageInfo(): String {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val available = stat.availableBytes / (1024 * 1024 * 1024)
            val total = stat.totalBytes / (1024 * 1024 * 1024)
            "$available/$total GB"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getNetworkType(): String {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "none"
        val nw = cm.activeNetwork ?: return "none"
        val actNw = cm.getNetworkCapabilities(nw) ?: return "none"
        return when {
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
    }

    private fun getMemoryInfo(): String {
        return try {
            val reader = RandomAccessFile("/proc/meminfo", "r")
            val load = reader.readLine() ?: ""
            reader.close()
            val match = Regex("(\\d+)").find(load)
            val kb = match?.value?.toLong() ?: 0L
            (kb / 1024 / 1024).toString()
        } catch (e: Exception) {
            "0"
        }
    }

    private fun getBatteryLevel(): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) (level * 100 / scale) else -1
    }

    companion object {
        private const val TAG = "LivenessProbe"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
