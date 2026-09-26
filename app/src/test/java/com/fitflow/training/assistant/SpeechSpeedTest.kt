package com.fitflow.training.assistant

import com.fitflow.training.assistant.speech.SherpaSynthesizer
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechSpeedTest {
    @Test fun dynamicSpeechUsesSlightlyFasterNaturalSpeed() {
        assertEquals(1.0f, SherpaSynthesizer.SPEECH_SPEED)
    }
}
