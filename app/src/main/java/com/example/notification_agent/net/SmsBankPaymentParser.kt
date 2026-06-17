package com.example.notification_agent.net

import com.example.notification_agent.bank.SupportedBank
import com.example.notification_agent.data.MessageEntity
import com.example.notification_agent.data.SourceType

data class SmsBankPayment(
    val bank: SupportedBank,
    val amount: Double,
    val sourceAccount: String?
)

object SmsBankPaymentParser {

    private val amountRegex = Regex("(?:ได้รับเงิน|ยอดเงินเข้า|เข้าบัญชี)\\s*:?[\\s]*([0-9,]+(?:\\.[0-9]{1,2})?)")
    private val accountRegex = Regex("(?:เข้าบัญชี|บ/ช)\\s*([A-Za-z0-9-]+)")

    fun parse(message: MessageEntity): SmsBankPayment? {
        if (message.sourceType != SourceType.SMS) return null
        
        val sender = message.sourceKey.trim()
        val bank = SupportedBank.entries.find { it.supportsSms && it.smsSender == sender } ?: return null
        
        val text = message.text.orEmpty()
        val amount = amountRegex.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(",", "")
            ?.toDoubleOrNull() ?: return null
            
        val account = accountRegex.find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            
        return SmsBankPayment(
            bank = bank,
            amount = amount,
            sourceAccount = account
        )
    }
}
