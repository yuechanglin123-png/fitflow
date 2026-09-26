package com.fitflow.training.assistant

import org.junit.Assert.*
import org.junit.Test

class AssistantWakeReplyFilterTest {
    @Test fun ignoresOnlyTheAssistantsOwnReply() {
        assertNull(AssistantWakeReplyFilter.userText("我在，请说指令"))
        assertNull(AssistantWakeReplyFilter.userText("请说指令"))
        assertNull(AssistantWakeReplyFilter.userText("我在请说"))
        assertNull(AssistantWakeReplyFilter.userText("请说"))
    }

    @Test fun retainsUserCommandAfterAnEchoedReply() {
        assertEquals("完成本组", AssistantWakeReplyFilter.userText("我在请说指令完成本组"))
        assertEquals("跳过休息", AssistantWakeReplyFilter.userText("跳过休息"))
    }
}
