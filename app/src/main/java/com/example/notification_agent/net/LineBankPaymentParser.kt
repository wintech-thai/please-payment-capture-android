package com.example.notification_agent.net

import com.example.notification_agent.bank.SupportedBank
import com.example.notification_agent.data.MessageEntity

data class LineBankPayment(
    val bank: SupportedBank,
    val amount: Double,
    val sourceAccount: String?
)

object LineBankPaymentParser {

    const val LINE_PACKAGE_NAME: String = "jp.naver.line.android"

    private const val INCOMING_KEYWORD = "เงินเข้า"
    private val amountRegex = Regex("เงินเข้า\\s*:?[\\s]*([0-9,]+(?:\\.[0-9]{1,2})?)")
    private val accountRegex = Regex("เข้าบัญชี\\s*([A-Za-z0-9-]+)")

    fun parse(message: MessageEntity): LineBankPayment? =
        parseNotification(
            packageName = message.sourceKey,
            title = message.title,
            text = message.text
        )

    fun parseNotification(
        packageName: String,
        title: String?,
        text: String?
    ): LineBankPayment? {
        if (!packageName.equals(LINE_PACKAGE_NAME, ignoreCase = true)) return null

        val normalizedTitle = title?.trim().orEmpty()
        val normalizedText = text?.trim().orEmpty()
        if (normalizedText.isBlank() || !normalizedText.contains(INCOMING_KEYWORD)) return null

        val bank = SupportedBank.fromLineNotification(normalizedTitle) ?: return null
        val amount = amountRegex.find(normalizedText)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(",", "")
            ?.toDoubleOrNull()
            ?: return null
        val account = accountRegex.find(normalizedText)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        return LineBankPayment(
            bank = bank,
            amount = amount,
            sourceAccount = account
        )
    }
}

