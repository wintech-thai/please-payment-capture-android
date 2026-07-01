package com.example.notification_agent.service

/**
 * A notification that was suppressed by the in-memory dedup window in
 * [NotificationCaptureService] (same `notification.key` + identical content
 * seen again within the dedup window).
 *
 * These are kept purely for debugging: they are flushed to the backend as a
 * `duplicates` array on the next [com.example.notification_agent.net.LivenessProbe]
 * heartbeat so we can spot cases where legitimate messages might be getting
 * dropped and tune the logic.
 */
data class DuplicateEvent(
    /** Local-only id used to remove the event once it has been sent. */
    val id: Long,
    val notificationKey: String,
    /** Package name of the originating app. */
    val sourceKey: String,
    val title: String?,
    val text: String?,
    /** When the original (forwarded) notification was first seen. */
    val firstSeenAt: Long,
    /** When this suppressed duplicate arrived. */
    val duplicateAt: Long
)

/**
 * In-memory ring buffer of suppressed duplicate notifications. Lives in the
 * single app process and is shared between the capture service (producer) and
 * the liveness probe (consumer). Not persisted — duplicates are low-value debug
 * data and the process is always alive when one is recorded.
 */
internal object DuplicateEventRegistry {
    private const val MAX_ENTRIES = 100

    private var nextId = 0L
    private val events = ArrayDeque<DuplicateEvent>()

    fun record(
        notificationKey: String,
        sourceKey: String,
        title: String?,
        text: String?,
        firstSeenAt: Long,
        duplicateAt: Long
    ) {
        synchronized(events) {
            events.addLast(
                DuplicateEvent(
                    id = nextId++,
                    notificationKey = notificationKey,
                    sourceKey = sourceKey,
                    title = title,
                    text = text,
                    firstSeenAt = firstSeenAt,
                    duplicateAt = duplicateAt
                )
            )
            while (events.size > MAX_ENTRIES) events.removeFirst()
        }
    }

    /** Returns a copy of the currently buffered duplicates without removing them. */
    fun snapshot(): List<DuplicateEvent> = synchronized(events) { events.toList() }

    /** Removes the given events (by id) after they have been successfully sent. */
    fun remove(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        val idSet = ids.toHashSet()
        synchronized(events) { events.removeAll { it.id in idSet } }
    }
}
