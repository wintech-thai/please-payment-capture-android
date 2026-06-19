package com.example.notification_agent

import android.app.Application
import android.provider.Settings
import androidx.room.Room
import com.example.notification_agent.data.AppDatabase
import com.example.notification_agent.data.FilterRuleEntity
import com.example.notification_agent.data.MessageRepository
import com.example.notification_agent.data.SourceType
import com.example.notification_agent.bank.BankConfigRepository
import com.example.notification_agent.bank.SupportedBank
import com.example.notification_agent.data.settings.AgentSettingsRepository
import com.example.notification_agent.net.BankWebhookDispatcher
import com.example.notification_agent.net.LivenessProbe
import com.example.notification_agent.net.CrashReporter
import com.example.notification_agent.net.LineBankPaymentParser
import com.example.notification_agent.net.WebhookDispatcher
import com.example.notification_agent.net.SmsBankPaymentParser
import com.example.notification_agent.status.AgentStatusRepository
import com.example.notification_agent.worker.AgentWatchdogWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Process-wide DI container (manual). */
class NotificationAgentApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "notification_agent.db")
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .addMigrations(AppDatabase.MIGRATION_2_3)
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .build()
    }

    val statusRepository: AgentStatusRepository by lazy { AgentStatusRepository() }

    val settingsRepository: AgentSettingsRepository by lazy {
        AgentSettingsRepository(applicationContext)
    }

    /** Primary multi-bank forwarding config store. */
    val bankConfigRepository: BankConfigRepository by lazy {
        BankConfigRepository(applicationContext)
    }

    private val deviceId: String by lazy {
        Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
    }

    /** Primary forwarding dispatcher. Call sites supply parsed amount/account. */
    val bankWebhookDispatcher: BankWebhookDispatcher by lazy {
        BankWebhookDispatcher(bankConfigRepository, statusRepository)
    }

    @Suppress("DEPRECATION")
    val webhookDispatcher: WebhookDispatcher by lazy {
        WebhookDispatcher(settingsRepository, statusRepository, deviceId)
    }

    val livenessProbe: LivenessProbe by lazy {
        LivenessProbe(
            context = applicationContext,
            settings = settingsRepository,
            bankConfigs = bankConfigRepository,
            status = statusRepository
        )
    }

    val repository: MessageRepository by lazy {
        MessageRepository(
            db = database,
            onCaptured = { message ->
                statusRepository.recordCapture()
                appScope.launch {
                    val globalConfig = bankConfigRepository.getGlobal()

                    // Try LINE parser
                    LineBankPaymentParser.parse(message)?.let { payment ->
                        val bankCode = payment.bank.code
                        val isBankEnabled = globalConfig.enabledBanks.contains(bankCode)
                        val isLineEnabled = globalConfig.enabledLineBanks.contains(bankCode)
                        
                        android.util.Log.i("NotificationAgentApp", "Captured LINE payment for $bankCode: bankEnabled=$isBankEnabled, channelEnabled=$isLineEnabled")
                        
                        if (isBankEnabled && isLineEnabled) {
                            // 1. Always forward to bank endpoint if bank & channel are enabled
                            bankWebhookDispatcher.sendWebhookForBank(
                                bankName = bankCode,
                                message = message,
                                linePayment = payment
                            )
                            
                            // 2. Conditionally forward to legacy webhook (for debug/instrumentation)
                            val shouldForwardLegacy = globalConfig.forwardLineBanks.contains(bankCode)
                            if (shouldForwardLegacy) {
                                android.util.Log.d("NotificationAgentApp", "Forwarding to legacy webhook for $bankCode LINE")
                                webhookDispatcher.enqueue(message)
                            }
                        } else {
                            android.util.Log.w("NotificationAgentApp", "Bank $bankCode or LINE channel disabled in config")
                            statusRepository.recordBankForward(bankName = bankCode, ok = false, error = "Bank or Channel disabled")
                        }
                    }

                    // Try SMS parser
                    SmsBankPaymentParser.parse(message)?.let { payment ->
                        val bankCode = payment.bank.code
                        val isBankEnabled = globalConfig.enabledBanks.contains(bankCode)
                        val isSmsEnabled = globalConfig.enabledSmsBanks.contains(bankCode)
                        
                        android.util.Log.i("NotificationAgentApp", "Captured SMS payment for $bankCode: bankEnabled=$isBankEnabled, channelEnabled=$isSmsEnabled")
                        
                        if (isBankEnabled && isSmsEnabled) {
                            // 1. Always forward to bank endpoint if bank & channel are enabled
                            bankWebhookDispatcher.sendWebhookForBank(
                                bankName = bankCode,
                                message = message,
                                smsPayment = payment
                            )
                            
                            // 2. Conditionally forward to legacy webhook (for debug/instrumentation)
                            val shouldForwardLegacy = globalConfig.forwardSmsBanks.contains(bankCode)
                            if (shouldForwardLegacy) {
                                android.util.Log.d("NotificationAgentApp", "Forwarding to legacy webhook for $bankCode SMS")
                                webhookDispatcher.enqueue(message)
                            }
                        } else {
                            android.util.Log.w("NotificationAgentApp", "Bank $bankCode or SMS channel disabled in config")
                            statusRepository.recordBankForward(bankName = bankCode, ok = false, error = "Bank or Channel disabled")
                        }
                    }
                }
            },
            onForward = { message ->
                // The legacy single-URL webhook is still kept for backwards
                // compatibility / instrumentation tests.
                @Suppress("DEPRECATION")
                webhookDispatcher.enqueue(message)
            }
        )
    }

    override fun onCreate() {
        super.onCreate()
        com.example.notification_agent.bank.BankWebhookTester.instance = bankWebhookDispatcher
        CrashReporter.initialize(this, deviceId)

        // Eagerly initialise the legacy dispatcher so its consumer coroutine
        // starts. Kept for backwards compatibility / instrumentation tests.
        @Suppress("DEPRECATION")
        webhookDispatcher
        appScope.launch {
            runCatching {
                val pruned = repository.pruneOldMessages(retentionDays = 7)
                android.util.Log.i("NotificationAgentApp", "Startup retention prune removed=$pruned")
            }.onFailure {
                android.util.Log.w("NotificationAgentApp", "Startup retention prune failed: ${it.message}")
            }

            repository.ensureDefaultRule(
                FilterRuleEntity(
                    sourceType = SourceType.NOTIFICATION,
                    sourceKey = LineBankPaymentParser.LINE_PACKAGE_NAME,
                    sourceLabel = applicationContext.getString(
                        com.example.notification_agent.R.string.notification_source_line
                    ),
                    enabled = true,
                    forwardToWebhook = true
                )
            )

            // Sync bank SMS senders to filter rules
            bankConfigRepository.globalConfig.collect { config ->
                SupportedBank.entries.forEach { bank ->
                    val sender = bank.smsSender
                    if (bank.supportsSms && !sender.isNullOrBlank()) {
                        val isEnabled = config.enabledBanks.contains(bank.code) && 
                                        config.enabledSmsBanks.contains(bank.code)
                        
                        if (isEnabled) {
                            appScope.launch {
                                repository.upsertRule(
                                    FilterRuleEntity(
                                        sourceType = SourceType.SMS,
                                        sourceKey = sender,
                                        sourceLabel = "${bank.code} SMS",
                                        enabled = true,
                                        forwardToWebhook = false
                                    )
                                )
                            }
                        } else {
                            appScope.launch {
                                repository.deleteRule(SourceType.SMS, sender)
                            }
                        }
                    }
                }
            }
        }
        AgentWatchdogWorker.enqueue(this)
    }

    companion object {
        fun from(context: android.content.Context): NotificationAgentApp =
            context.applicationContext as NotificationAgentApp
    }
}
