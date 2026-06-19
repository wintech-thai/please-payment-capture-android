package com.example.notification_agent.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Type of source the message originated from. */
enum class SourceType { NOTIFICATION, SMS }

/**
 * A single captured message, either a notification posted by another app
 * or an incoming SMS.
 */
@Entity(
    tableName = "messages",
    indices = [Index(value = ["dedupeKey"], unique = true)]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceType: SourceType,
    /** Package name (notifications) or sender phone number (SMS). */
    val sourceKey: String,
    /** Optional human-readable label (app name for notifications). */
    val sourceLabel: String?,
    val title: String?,
    val text: String?,
    val timestamp: Long,
    /** Stable key used to prevent duplicate persistence. */
    val dedupeKey: String = ""
)

