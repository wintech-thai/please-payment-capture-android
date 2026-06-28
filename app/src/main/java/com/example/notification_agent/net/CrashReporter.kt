package com.example.notification_agent.net

import android.content.Context
import android.util.Log
import com.example.notification_agent.BuildConfig
import com.example.notification_agent.data.CrashLogDao
import com.example.notification_agent.data.CrashLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val MAX_STACK_TRACE_LENGTH = 4000

    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var crashLogDao: CrashLogDao? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun initialize(context: Context, deviceId: String, dao: CrashLogDao) {
        crashLogDao = dao
        if (defaultHandler == null) {
            defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                report(throwable, isFatal = true, threadName = thread.name)
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun report(throwable: Throwable, isFatal: Boolean = false, threadName: String? = null) {
        if (isFatal) {
            Log.e(TAG, "Fatal crash on thread $threadName", throwable)
        } else {
            Log.w(TAG, "Internal error reported", throwable)
        }
        persistCrash(throwable, isFatal, threadName)
    }

    private fun persistCrash(throwable: Throwable, isFatal: Boolean, threadName: String?) {
        val dao = crashLogDao ?: return
        val entry = CrashLogEntity(
            level = if (isFatal) "FATAL" else "ERROR",
            tag = TAG,
            thread = threadName ?: Thread.currentThread().name,
            exceptionClass = throwable.javaClass.name,
            message = throwable.message,
            stackTrace = throwable.stackTraceToString().take(MAX_STACK_TRACE_LENGTH),
            occurredAt = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME
        )
        if (isFatal) {
            // Process is about to die; persist synchronously before the default handler kills it
            runBlocking { dao.insert(entry) }
        } else {
            scope.launch { dao.insert(entry) }
        }
    }
}
