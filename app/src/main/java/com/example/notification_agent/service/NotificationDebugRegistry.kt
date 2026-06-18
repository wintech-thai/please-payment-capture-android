package com.example.notification_agent.service

import com.example.notification_agent.data.MessageEntity
import com.example.notification_agent.data.SourceType

data class NotificationDebugInfo(
    val selectedTitleSource: String?,
    val selectedTextSource: String?,
    val titleCandidates: List<NotificationCandidateSnapshot>,
    val textCandidates: List<NotificationCandidateSnapshot>
)

internal object NotificationDebugRegistry {
    private const val MAX_ENTRIES = 128

    private val entries = object : LinkedHashMap<NotificationDebugKey, NotificationDebugInfo>(
        MAX_ENTRIES,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<NotificationDebugKey, NotificationDebugInfo>?
        ): Boolean = size > MAX_ENTRIES
    }

    fun register(
        message: MessageEntity,
        debugInfo: NotificationDebugInfo
    ) {
        if (message.sourceType != SourceType.NOTIFICATION) return
        synchronized(entries) {
            entries[keyOf(message)] = debugInfo
        }
    }

    @Suppress("unused")
    fun consume(message: MessageEntity): NotificationDebugInfo? {
        if (message.sourceType != SourceType.NOTIFICATION) return null
        return synchronized(entries) {
            entries.remove(keyOf(message))
        }
    }

    private fun keyOf(message: MessageEntity): NotificationDebugKey = NotificationDebugKey(
        sourceType = message.sourceType,
        sourceKey = message.sourceKey,
        title = message.title,
        text = message.text,
        timestamp = message.timestamp
    )
}

private data class NotificationDebugKey(
    val sourceType: SourceType,
    val sourceKey: String,
    val title: String?,
    val text: String?,
    val timestamp: Long
)


