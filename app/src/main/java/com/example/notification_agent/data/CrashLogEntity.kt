package com.example.notification_agent.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "crash_logs")
data class CrashLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: String,
    val tag: String,
    val thread: String,
    val exceptionClass: String,
    val message: String?,
    val stackTrace: String,
    val occurredAt: Long,
    val appVersion: String,
    val sent: Boolean = false
)
