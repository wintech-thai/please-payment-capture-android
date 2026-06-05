package com.example.notification_agent.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationContentExtractorTest {

    @Test
    fun prefersBigTextWhenItContainsMoreDetailThanPreviewText() {
        val result = NotificationContentExtractor.extractText(
            text = "เงินเข้า: 100.00 บาท เข้าบัญชี XX7157 เมื่อ 04/06/",
            bigText = "เงินเข้า: 100.00 บาท เข้าบัญชี XX7157 เมื่อ 04/06/2569 เวลา 11:02 น. ยอดคงเหลือ 5,500.00 บาท",
            textLines = null
        )

        assertEquals(
            "เงินเข้า: 100.00 บาท เข้าบัญชี XX7157 เมื่อ 04/06/2569 เวลา 11:02 น. ยอดคงเหลือ 5,500.00 บาท",
            result
        )
    }

    @Test
    fun prefersJoinedTextLinesOverSinglePreviewLineWhenLinesContainMoreContent() {
        val result = NotificationContentExtractor.extractText(
            text = "เงินเข้า: 100.00 บาท เข้าบัญชี XX7157 เมื่อ 04/06/",
            bigText = null,
            textLines = arrayOf(
                "เงินเข้า: 100.00 บาท",
                "เข้าบัญชี XX7157",
                "เมื่อ 04/06/2569 11:02 น."
            )
        )

        assertEquals(
            "เงินเข้า: 100.00 บาท\nเข้าบัญชี XX7157\nเมื่อ 04/06/2569 11:02 น.",
            result
        )
    }

    @Test
    fun fallsBackToPreviewTextWhenNoRicherVariantExists() {
        val result = NotificationContentExtractor.extractText(
            text = "เงินเข้า: 100.00 บาท เข้าบัญชี XX7157",
            bigText = "   ",
            textLines = emptyArray()
        )

        assertEquals("เงินเข้า: 100.00 บาท เข้าบัญชี XX7157", result)
    }

    @Test
    fun prefersBigTitleWhenAvailable() {
        val result = NotificationContentExtractor.extractTitle(
            title = "Krungthai",
            bigTitle = "Krungthai Connext"
        )

        assertEquals("Krungthai Connext", result)
    }

    @Test
    fun returnsNullWhenAllCandidatesAreBlank() {
        val result = NotificationContentExtractor.extractText(
            text = "   ",
            bigText = null,
            textLines = arrayOf(" ", "\n")
        )

        assertNull(result)
    }
}

