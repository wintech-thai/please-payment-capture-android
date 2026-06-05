package com.example.notification_agent.data

import kotlinx.coroutines.flow.Flow

/**
 * Outcome of evaluating the user's filter rules against a captured message.
 */
data class CaptureDecision(
    /** Whether the message should be persisted at all. */
    val capture: Boolean,
    /** Whether the message should additionally be forwarded to the webhook. */
    val forward: Boolean
) {
    companion object {
        val Drop = CaptureDecision(capture = false, forward = false)
    }
}

/**
 * Single facade for capture services & UI to interact with the database.
 *
 * Filtering rule:
 *  - If user has NO rules saved for that source type, everything is allowed
 *    (but never forwarded — forwarding requires an explicit per-source opt-in).
 *  - Otherwise the message is stored only when a matching rule exists with
 *    enabled = true. SMS sender matching also accepts any rule whose
 *    sourceKey is a non-empty case-insensitive substring of the sender.
 *  - Forwarding to the webhook happens only when the matching rule has
 *    forwardToWebhook = true.
 */
class MessageRepository(
    db: AppDatabase,
    /** Sink invoked for every message that is successfully persisted. */
    private val onCaptured: suspend (MessageEntity) -> Unit = {},
    /**
     * Optional sink for messages that pass [CaptureDecision.forward]. The
     * repository only invokes this when a forwarding rule matches; the sink
     * itself decides whether the webhook is enabled / configured.
     */
    private val onForward: suspend (MessageEntity) -> Unit = {}
) {

    private val messageDao = db.messageDao()
    private val filterDao = db.filterRuleDao()

    fun observeMessages(): Flow<List<MessageEntity>> = messageDao.observeRecent()

    fun observeRules(type: SourceType): Flow<List<FilterRuleEntity>> =
        filterDao.observeByType(type)

    suspend fun upsertRule(rule: FilterRuleEntity) = filterDao.upsert(rule)

    suspend fun upsertRules(rules: List<FilterRuleEntity>) = filterDao.upsertAll(rules)

    suspend fun ensureDefaultRule(rule: FilterRuleEntity) {
        if (filterDao.countByType(rule.sourceType) == 0) {
            filterDao.upsert(rule)
        }
    }

    suspend fun deleteRule(type: SourceType, key: String) = filterDao.delete(type, key)

    suspend fun clearMessages() = messageDao.clear()

    /** Returns true if the message passes the user's current filter config. */
    suspend fun shouldCapture(type: SourceType, sourceKey: String): Boolean =
        resolveDecision(type, sourceKey).capture

    /**
     * Returns the full [CaptureDecision] for a given source. Used by the
     * capture pipeline to decide both persistence and forwarding.
     */
    suspend fun resolveDecision(type: SourceType, sourceKey: String): CaptureDecision {
        val total = filterDao.countByType(type)
        if (total == 0) {
            // Allow-all default never forwards: forwarding is opt-in per rule.
            return CaptureDecision(capture = true, forward = false)
        }
        filterDao.findRule(type, sourceKey)?.let { exact ->
            return CaptureDecision(
                capture = exact.enabled,
                forward = exact.enabled && exact.forwardToWebhook
            )
        }
        if (type == SourceType.SMS) {
            val match = filterDao.listByType(SourceType.SMS).firstOrNull { rule ->
                rule.enabled && rule.sourceKey.isNotBlank() &&
                    sourceKey.contains(rule.sourceKey, ignoreCase = true)
            } ?: return CaptureDecision.Drop
            return CaptureDecision(capture = true, forward = match.forwardToWebhook)
        }
        return CaptureDecision.Drop
    }

    suspend fun storeMessage(message: MessageEntity) {
        val decision = resolveDecision(message.sourceType, message.sourceKey)
        if (!decision.capture) return
        val id = messageDao.insert(message)
        val storedMessage = message.copy(id = id)
        onCaptured(storedMessage)
        if (decision.forward) {
            onForward(storedMessage)
        }
    }
}
