package com.example.notification_agent.net

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.notification_agent.BuildConfig
import com.example.notification_agent.net.AgentHttpClient.withAgentHeaders
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Handles reporting of uncaught exceptions and internal errors to a debug endpoint.
 */
object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val DEBUG_URL = "https://demo-hook.rocketlabth.com/webhook/android_debug"
    private val BEARER_TOKEN = BuildConfig.AGENT_DEBUG_TOKEN
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private var deviceId: String = "unknown"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun initialize(context: Context, deviceId: String) {
        this.deviceId = deviceId
        
        // Setup global uncaught exception handler
        if (defaultHandler == null) {
            defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                report(throwable, isFatal = true, threadName = thread.name)
                
                // Give some time to send the report (synchronously in this case since we're crashing)
                // We don't want to block forever, but long enough for the network call.
                runBlocking {
                    delay(3000)
                }
                
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun report(throwable: Throwable, isFatal: Boolean = false, threadName: String? = null) {
        val stackTrace = StringWriter().also { 
            throwable.printStackTrace(PrintWriter(it)) 
        }.toString()

        val json = buildJson(
            throwable = throwable,
            stackTrace = stackTrace,
            isFatal = isFatal,
            threadName = threadName
        )

        if (isFatal) {
            // If it's a fatal crash, we try to send it synchronously or on a separate thread 
            // that we wait for in the uncaught exception handler.
            // Using a simple thread here to avoid coroutine overhead during crash.
            Thread {
                sendSync(json)
            }.start()
        } else {
            scope.launch {
                sendSync(json)
            }
        }
    }

    private fun sendSync(json: String) {
        val request = Request.Builder()
            .url(DEBUG_URL)
            .post(json.toRequestBody(JSON))
            .withAgentHeaders(BEARER_TOKEN)
            .build()

        try {
            AgentHttpClient.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "Crash report sent successfully")
                } else {
                    Log.w(TAG, "Failed to send crash report: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending crash report", e)
        }
    }

    private fun buildJson(
        throwable: Throwable,
        stackTrace: String,
        isFatal: Boolean,
        threadName: String?
    ): String {
        val fields = linkedMapOf<String, String>()
        fields["timestamp"] = System.currentTimeMillis().toString()
        fields["deviceId"] = jsonString(deviceId)
        fields["device"] = jsonString("${Build.MANUFACTURER} ${Build.MODEL}")
        fields["osVersion"] = jsonString(Build.VERSION.RELEASE)
        fields["agentVersion"] = jsonString(BuildConfig.VERSION_NAME)
        fields["isFatal"] = isFatal.toString()
        fields["exception"] = jsonString(throwable.javaClass.name)
        fields["message"] = jsonString(throwable.message ?: "No message")
        fields["stackTrace"] = jsonString(stackTrace)
        if (threadName != null) {
            fields["thread"] = jsonString(threadName)
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
