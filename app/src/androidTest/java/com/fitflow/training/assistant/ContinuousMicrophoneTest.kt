package com.fitflow.training.assistant

import android.Manifest
import android.media.AudioManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.fitflow.training.assistant.speech.SherpaRecognizer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ContinuousMicrophoneTest {
    @get:Rule val activity=createComposeRule()

    @Test fun logicalCapturesReuseOneMicrophoneSessionAndCloseReleasesIt()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.RECORD_AUDIO)
        val audio=context.getSystemService(AudioManager::class.java)
        val initial=audio.activeRecordingConfigurations.map { it.clientAudioSessionId }.toSet()
        val recognizer=SherpaRecognizer(context)
        val errors=java.util.concurrent.CopyOnWriteArrayList<String>()
        suspend fun startCapture() {
            val ready=CompletableDeferred<Unit>()
            recognizer.start({}, { errors+=it;ready.completeExceptionally(IllegalStateException(it)) }, {ready.complete(Unit)})
            withTimeout(10_000) { ready.await() }
        }
        try {
            recognizer.initialize()
            startCapture()
            val id=withTimeout(5000) {
                var session:Int?=null
                while(session==null) {
                    session=audio.activeRecordingConfigurations.firstOrNull { it.clientAudioSessionId !in initial }?.clientAudioSessionId
                    if(session==null) delay(50)
                }
                session
            }
            repeat(6) {
                recognizer.pauseRecognition()
                delay(150)
                assertTrue("Microphone closed during playback",audio.activeRecordingConfigurations.any { it.clientAudioSessionId==id })
                startCapture()
                assertTrue("Logical capture restarted microphone",audio.activeRecordingConfigurations.any { it.clientAudioSessionId==id })
            }
            assertTrue(errors.toString(),errors.isEmpty())
            recognizer.close()
            withTimeout(5000) {
                while(audio.activeRecordingConfigurations.any { it.clientAudioSessionId==id }) delay(50)
            }
        } finally { recognizer.close() }
    }
}
