package com.example.notification_agent.service

import android.app.Notification
import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.notification_agent.NotificationAgentApp
import com.example.notification_agent.data.MessageEntity
import com.example.notification_agent.data.SourceType
import com.example.notification_agent.net.LineBankPaymentParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Reads notifications posted by other apps (requires the user to grant
 * notification access in system Settings).
 */
class NotificationCaptureService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processedCache = object : LinkedHashMap<String, Long>(20, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > 50
        }
    }

    override fun onListenerDisconnected() {
        // Ask the system to rebind us as soon as it can.
        runCatching { requestRebind(ComponentName(this, NotificationCaptureService::class.java)) }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        // Skip our own notifications and ongoing/foreground placeholders.
        if (notification.packageName == packageName) return
        val extras = notification.notification?.extras ?: return

        val titleSelection = NotificationContentExtractor.selectTitle(
            title = extras.getCharSequence(Notification.EXTRA_TITLE),
            bigTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)
        )
        val textSelection = NotificationContentExtractor.selectText(
            text = extras.getCharSequence(Notification.EXTRA_TEXT),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
            textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        )
        val title = titleSelection.value
        val text = textSelection.value
        if (title.isNullOrBlank() && text.isNullOrBlank()) return

        val pkg = notification.packageName
        if (LineBankPaymentParser.parseNotification(pkg, title, text) == null) return

        val contentKey = "${notification.key}:$title:$text"
        val now = System.currentTimeMillis()
        synchronized(processedCache) {
            val lastSeen = processedCache[contentKey]
            if (lastSeen != null && (now - lastSeen) < 10_000) {
                return
            }
            processedCache[contentKey] = now
        }

        val label = runCatching {
            val pm = packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrNull()

        val message = MessageEntity(
            sourceType = SourceType.NOTIFICATION,
            sourceKey = pkg,
            sourceLabel = label,
            title = title,
            text = text,
            timestamp = notification.postTime
        )
        val repo = NotificationAgentApp.from(applicationContext).repository
        NotificationDebugRegistry.register(
            message = message,
            debugInfo = NotificationDebugInfo(
                selectedTitleSource = titleSelection.source,
                selectedTextSource = textSelection.source,
                titleCandidates = titleSelection.candidates,
                textCandidates = textSelection.candidates
            )
        )
        scope.launch { repo.storeMessage(message) }
    }
}

internal object NotificationContentExtractor {

    fun selectTitle(
        title: CharSequence?,
        bigTitle: CharSequence?
    ): NotificationSelection = chooseBest(
        NotificationCandidate(source = "bigTitle", value = normalize(bigTitle), priority = 0),
        NotificationCandidate(source = "title", value = normalize(title), priority = 1)
    )

    fun extractTitle(
        title: CharSequence?,
        bigTitle: CharSequence?
    ): String? = selectTitle(title = title, bigTitle = bigTitle).value

    fun selectText(
        text: CharSequence?,
        bigText: CharSequence?,
        textLines: Array<CharSequence>?
    ): NotificationSelection = chooseBest(
        NotificationCandidate(source = "bigText", value = normalize(bigText), priority = 0),
        NotificationCandidate(source = "textLines", value = normalizeLines(textLines), priority = 1),
        NotificationCandidate(source = "text", value = normalize(text), priority = 2)
    )

    fun extractText(
        text: CharSequence?,
        bigText: CharSequence?,
        textLines: Array<CharSequence>?
    ): String? = selectText(text = text, bigText = bigText, textLines = textLines).value

    private fun chooseBest(vararg candidates: NotificationCandidate): NotificationSelection {
        val normalized = candidates
            .asSequence()
            .filter { !it.value.isNullOrBlank() }
            .distinctBy { it.value }
            .sortedWith(
                compareByDescending<NotificationCandidate> { requireNotNull(it.value).length }
                    .thenBy { it.priority }
            )
            .toList()

        return NotificationSelection(
            value = normalized.firstOrNull()?.value,
            source = normalized.firstOrNull()?.source,
            candidates = normalized.map {
                NotificationCandidateSnapshot(
                    source = it.source,
                    value = requireNotNull(it.value),
                    length = it.value.length
                )
            }
        )
    }

    private fun normalize(value: CharSequence?): String? = value
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    private fun normalizeLines(lines: Array<CharSequence>?): String? = lines
        ?.mapNotNull(::normalize)
        ?.takeIf { it.isNotEmpty() }
        ?.joinToString(separator = "\n")

    private data class NotificationCandidate(
        val source: String,
        val value: String?,
        val priority: Int
    )
}

internal data class NotificationSelection(
    val value: String?,
    val source: String?,
    val candidates: List<NotificationCandidateSnapshot>
)

data class NotificationCandidateSnapshot(
    val source: String,
    val value: String,
    val length: Int
)

