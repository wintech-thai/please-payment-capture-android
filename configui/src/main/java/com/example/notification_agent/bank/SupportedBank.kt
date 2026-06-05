package com.example.notification_agent.bank

enum class SupportedBank(
    val code: String,
    private val lineTitleKeywords: Set<String>
) {
    SCB(
        code = "SCB",
        lineTitleKeywords = setOf("SCB Connect", "SCB")
    ),
    KTB(
        code = "KTB",
        lineTitleKeywords = setOf("Krungthai Connext", "Krungthai")
    );

    fun matchesLineTitle(title: String): Boolean =
        lineTitleKeywords.any { keyword -> title.contains(keyword, ignoreCase = true) }

    companion object {
        fun fromCode(value: String?): SupportedBank? {
            val normalized = value?.trim().orEmpty()
            return entries.firstOrNull { bank -> bank.code.equals(normalized, ignoreCase = true) }
        }

        fun fromLineNotification(title: String?): SupportedBank? {
            val normalizedTitle = title?.trim().orEmpty()
            if (normalizedTitle.isBlank()) return null
            return entries.firstOrNull { bank -> bank.matchesLineTitle(normalizedTitle) }
        }
    }
}

