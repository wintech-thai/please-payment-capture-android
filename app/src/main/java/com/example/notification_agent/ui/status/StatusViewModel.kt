package com.example.notification_agent.ui.status

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notification_agent.NotificationAgentApp
import com.example.notification_agent.data.settings.AgentSettings
import com.example.notification_agent.status.AgentStatus
import com.example.notification_agent.ui.PermissionHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay

data class PermissionsStatus(
    val notificationListener: Boolean,
    val sms: Boolean,
    val postNotifications: Boolean,
    val batteryUnrestricted: Boolean
)

data class BankEndpointsStatus(
    val configuredCount: Int,
    val enabledCount: Int
)

data class StatusUiState(
    val now: Long,
    val agent: AgentStatus,
    val settings: AgentSettings,
    val permissions: PermissionsStatus,
    val bankEndpoints: BankEndpointsStatus
)

class StatusViewModel(app: Application) : AndroidViewModel(app) {

    private val agent = NotificationAgentApp.from(app)

    /** Ticks once per second so uptime / next-ping countdowns refresh smoothly. */
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    private val permissions = flow {
        while (true) {
            emit(readPermissions(getApplication()))
            delay(2_000)
        }
    }

    val state: StateFlow<StatusUiState> = combine(
        ticker,
        agent.statusRepository.state,
        agent.settingsRepository.settings,
        permissions,
        agent.bankConfigRepository.globalConfig
    ) { now, status, settings, perms, globalConfig ->
        StatusUiState(
            now = now,
            agent = status,
            settings = settings,
            permissions = perms,
            bankEndpoints = BankEndpointsStatus(
                configuredCount = if (globalConfig.endpointUrl.isNotBlank()) 1 else 0,
                enabledCount = globalConfig.enabledBanks.size
            )
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatusUiState(
            now = System.currentTimeMillis(),
            agent = agent.statusRepository.state.value,
            settings = AgentSettings(),
            permissions = readPermissions(app),
            bankEndpoints = BankEndpointsStatus(configuredCount = 0, enabledCount = 0)
        )
    )

    private fun readPermissions(ctx: Context): PermissionsStatus {
        val sms = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        val post = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
        return PermissionsStatus(
            notificationListener = PermissionHelper.isNotificationListenerEnabled(ctx),
            sms = sms,
            postNotifications = post,
            batteryUnrestricted = PermissionHelper.isIgnoringBatteryOptimizations(ctx)
        )
    }
}

