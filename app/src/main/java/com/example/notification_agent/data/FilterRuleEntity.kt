package com.example.notification_agent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-configured filter rule. The captured message will be stored only if
 * a matching ENABLED rule exists for that source. If no rules of that source
 * type exist at all, everything is allowed (so the user can use the app right
 * after install without configuring anything).
 */
@Entity(tableName = "filter_rules", primaryKeys = ["sourceType", "sourceKey"])
data class FilterRuleEntity(
    val sourceType: SourceType,
    /** Package name for notifications, phone-number / keyword for SMS. */
    val sourceKey: String,
    val sourceLabel: String?,
    val enabled: Boolean,
    /**
     * When true, messages matching this rule are forwarded to the
     * configured webhook URL. The rule must also be [enabled] for capture
     * to happen at all.
     */
    val forwardToWebhook: Boolean = false
)
