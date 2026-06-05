package com.example.notification_agent.net

import com.example.notification_agent.data.MessageEntity
import com.example.notification_agent.data.SourceType
import com.example.notification_agent.service.NotificationCandidateSnapshot
import com.example.notification_agent.service.NotificationDebugInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress("DEPRECATION")
class WebhookDispatcherTest {

    @Test
    fun buildJsonIncludesNotificationDebugWhenProvided() {
        val payload = WebhookDispatcher.buildJson(
            m = MessageEntity(
                id = 13,
                sourceType = SourceType.NOTIFICATION,
                sourceKey = "jp.naver.line.android",
                sourceLabel = "LINE",
                title = "Krungthai Connext",
                text = "เงินเข้า: 100.00 บาท เข้าบัญชี XX7157 เมื่อ 04/06/2569 11:02 น.",
                timestamp = 1780570950423
            ),
            deviceId = "441c54094af6c4ce",
            deviceLabel = "samsung SM-A166P",
            agentVersion = "1.0.9-test",
            notificationDebug = NotificationDebugInfo(
                selectedTitleSource = "bigTitle",
                selectedTextSource = "bigText",
                titleCandidates = listOf(
                    NotificationCandidateSnapshot(
                        source = "bigTitle",
                        value = "Krungthai Connext",
                        length = 18
                    ),
                    NotificationCandidateSnapshot(
                        source = "title",
                        value = "Krungthai",
                        length = 10
                    )
                ),
                textCandidates = listOf(
                    NotificationCandidateSnapshot(
                        source = "bigText",
                        value = "เงินเข้า: 100.00 บาท เข้าบัญชี XX7157 เมื่อ 04/06/2569 11:02 น.",
                        length = 60
                    ),
                    NotificationCandidateSnapshot(
                        source = "text",
                        value = "เงินเข้า: 100.00 บาท เข้าบัญชี XX7157 เมื่อ 04/06/",
                        length = 44
                    )
                )
            )
        )

        assertTrue(payload.contains("\"notificationDebug\":"))
        assertTrue(payload.contains("\"selectedTextSource\":\"bigText\""))
        assertTrue(payload.contains("\"selectedTitleSource\":\"bigTitle\""))
        assertTrue(payload.contains("\"textCandidates\":[{"))
        assertTrue(payload.contains("\"value\":\"เงินเข้า: 100.00 บาท เข้าบัญชี XX7157 เมื่อ 04/06/\""))
    }

    @Test
    fun buildJsonOmitsNotificationDebugWhenUnavailable() {
        val payload = WebhookDispatcher.buildJson(
            m = MessageEntity(
                id = 1,
                sourceType = SourceType.SMS,
                sourceKey = "+66800000000",
                sourceLabel = "Sender",
                title = null,
                text = "otp 1234",
                timestamp = 1000L
            ),
            deviceId = "device-1",
            deviceLabel = "Pixel",
            agentVersion = "1.0.0"
        )

        assertFalse(payload.contains("\"notificationDebug\":"))
        assertTrue(payload.contains("\"sourceType\":\"SMS\""))
        assertTrue(payload.contains("\"text\":\"otp 1234\""))
    }
}




