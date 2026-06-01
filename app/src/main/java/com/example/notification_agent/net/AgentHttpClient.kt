package com.example.notification_agent.net

import com.example.notification_agent.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Shared OkHttp client. Per-call timeout is overridden by callers as needed. */
object AgentHttpClient {
    val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    /** Adds the embedded bearer token + standard headers. */
    fun Request.Builder.withAgentHeaders(): Request.Builder = this
        .header("Authorization", "Bearer ${BuildConfig.AGENT_WEBHOOK_TOKEN}")
        .header("User-Agent", "NotificationAgent/${BuildConfig.VERSION_NAME}")
        .header("Accept", "application/json")
}

