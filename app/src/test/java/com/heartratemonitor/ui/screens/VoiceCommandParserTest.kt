package com.heartratemonitor.ui.screens

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VoiceCommandParserTest {

    @Test
    fun parseAlarm_parsesExampleWithoutApiKey() = runBlocking {
        val result = VoiceCommandParser.parseAlarm(
            apiKey = "",
            text = "帮我订一个3:30的闹钟 我要去赶飞机"
        )

        assertNotNull(result)
        assertEquals("赶飞机", result!!.eventName)
        assertEquals(3, result.hour)
        assertEquals(30, result.minute)
        assertEquals(0, result.dateOffsetDays)
    }

    @Test
    fun parseAlarm_parsesTomorrowAfternoonHalfHour() = runBlocking {
        val result = VoiceCommandParser.parseAlarm(
            apiKey = "",
            text = "明天下午3点半提醒我开会"
        )

        assertNotNull(result)
        assertEquals("开会", result!!.eventName)
        assertEquals(15, result.hour)
        assertEquals(30, result.minute)
        assertEquals(1, result.dateOffsetDays)
    }
}
