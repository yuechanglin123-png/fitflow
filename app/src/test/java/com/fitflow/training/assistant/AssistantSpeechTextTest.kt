package com.fitflow.training.assistant

import org.junit.Assert.*
import org.junit.Test

class AssistantSpeechTextTest {
    @Test fun newWakeCanPrefixAnInstruction() {
        assertEquals("完成本组", AssistantSpeechText.afterWake("铁蛋铁蛋，完成本组"))
        assertEquals("", AssistantSpeechText.afterWake("铁蛋铁蛋"))
        assertNull(AssistantSpeechText.afterWake("铁蛋，完成本组"))
        assertNull(AssistantSpeechText.afterWake("铁蛋"))
    }

    @Test fun oldOrQuotedWakeDoesNotAuthorizeAnInstruction() {
        assertNull(AssistantSpeechText.afterWake("小练小练完成本组"))
        assertNull(AssistantSpeechText.afterWake("我说铁蛋完成本组"))
        assertEquals(AssistantIntent.Command(TrainingCommand.PAUSE), AssistantIntentParser().parse("别暂停训练"))
    }
}
