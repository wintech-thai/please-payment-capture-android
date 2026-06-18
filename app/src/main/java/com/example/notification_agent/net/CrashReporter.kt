package com.example.notification_agent.net

import android.content.Context
import android.util.Log

/**
 * Handles reporting of uncaught exceptions and internal errors.
 * Webhook reporting removed per user request.
 */
object CrashReporter {
    private const val TAG = "CrashReporter"
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null

    fun initialize(context: Context, deviceId: String) {
        // Setup global uncaught exception handler
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
        // Network reporting removed
    }
}
