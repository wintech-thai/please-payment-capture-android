package com.example.notification_agent.ui

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.notification_agent.NotificationAgentApp
import com.example.notification_agent.data.FilterRuleEntity
import com.example.notification_agent.data.MessageEntity
import com.example.notification_agent.data.MessageRepository
import com.example.notification_agent.data.SourceType
import com.example.notification_agent.net.LineBankPaymentParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val isSystem: Boolean
)

data class AppFilterUiItem(
    val info: InstalledAppInfo,
    val enabled: Boolean,
    val forwardToWebhook: Boolean,
    val isForwardManagedBySystem: Boolean
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: MessageRepository = NotificationAgentApp.from(app).repository

    val messages: StateFlow<List<MessageEntity>> = repo.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val smsRules: StateFlow<List<FilterRuleEntity>> = repo.observeRules(SourceType.SMS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())
    private val showSystem = MutableStateFlow(false)
    private val query = MutableStateFlow("")

    val notificationFilters: StateFlow<List<AppFilterUiItem>> = combine(
        installedApps, repo.observeRules(SourceType.NOTIFICATION), showSystem, query
    ) { apps, rules, system, q ->
        val ruleMap = rules.associateBy { it.sourceKey }
        apps.asSequence()
            .filter { system || !it.isSystem }
            .filter { q.isBlank() || it.label.contains(q, true) || it.packageName.contains(q, true) }
            .map { app ->
                val rule = ruleMap[app.packageName]
                val isSystemManagedLine = app.packageName == LineBankPaymentParser.LINE_PACKAGE_NAME
                AppFilterUiItem(
                    info = app,
                    enabled = rule?.enabled == true,
                    forwardToWebhook = isSystemManagedLine || rule?.forwardToWebhook == true,
                    isForwardManagedBySystem = isSystemManagedLine
                )
            }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { loadInstalledApps() }

    fun setShowSystem(show: Boolean) { showSystem.value = show }
    fun setQuery(q: String) { query.value = q }

    fun toggleNotificationFilter(item: AppFilterUiItem, enabled: Boolean) {
        viewModelScope.launch {
            repo.upsertRule(
                FilterRuleEntity(
                    sourceType = SourceType.NOTIFICATION,
                    sourceKey = item.info.packageName,
                    sourceLabel = item.info.label,
                    enabled = enabled,
                    forwardToWebhook = if (!enabled) {
                        false
                    } else if (item.isForwardManagedBySystem) {
                        true
                    } else {
                        item.forwardToWebhook
                    }
                )
            )
        }
    }

    fun toggleNotificationForward(item: AppFilterUiItem, forward: Boolean) {
        if (item.isForwardManagedBySystem) return
        viewModelScope.launch {
            repo.upsertRule(
                FilterRuleEntity(
                    sourceType = SourceType.NOTIFICATION,
                    sourceKey = item.info.packageName,
                    sourceLabel = item.info.label,
                    enabled = true,
                    forwardToWebhook = forward
                )
            )
        }
    }

    fun addSmsRule(key: String, enabled: Boolean = true) {
        val trimmed = key.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repo.upsertRule(
                FilterRuleEntity(
                    sourceType = SourceType.SMS,
                    sourceKey = trimmed,
                    sourceLabel = trimmed,
                    enabled = enabled,
                    forwardToWebhook = false
                )
            )
        }
    }

    fun toggleSmsRule(rule: FilterRuleEntity, enabled: Boolean) {
        viewModelScope.launch {
            repo.upsertRule(
                rule.copy(
                    enabled = enabled,
                    forwardToWebhook = if (enabled) rule.forwardToWebhook else false
                )
            )
        }
    }

    fun toggleSmsForward(rule: FilterRuleEntity, forward: Boolean) {
        viewModelScope.launch {
            repo.upsertRule(rule.copy(forwardToWebhook = forward))
        }
    }

    fun deleteSmsRule(rule: FilterRuleEntity) {
        viewModelScope.launch { repo.deleteRule(rule.sourceType, rule.sourceKey) }
    }

    fun clearMessages() { viewModelScope.launch { repo.clearMessages() } }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            val pm = getApplication<Application>().packageManager
            val list = withContext(Dispatchers.IO) {
                pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .map { ai ->
                        InstalledAppInfo(
                            packageName = ai.packageName,
                            label = pm.getApplicationLabel(ai).toString(),
                            isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        )
                    }
                    .sortedBy { it.label.lowercase() }
            }
            installedApps.value = list
        }
    }
}
