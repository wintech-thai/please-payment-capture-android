package com.example.notification_agent.net

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

    /** Adds standard headers plus an optional bearer token. */
    fun Request.Builder.withAgentHeaders(bearerToken: String? = null): Request.Builder {
        if (!bearerToken.isNullOrBlank()) {
            header("Authorization", "Bearer $bearerToken")
        }
        return header("User-Agent", "NotificationAgent/${com.example.notification_agent.BuildConfig.VERSION_NAME}")
            .header("Accept", "application/json")
    }
}

