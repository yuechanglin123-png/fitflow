package com.fitflow.training.assistant

import androidx.test.platform.app.InstrumentationRegistry
import com.fitflow.training.assistant.speech.*
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class NativeSpeechTest {
    @Test fun recognizesEverydayRestRequestWithAndWithoutNoise() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val engine=OnlineRecognizer(instrumentation.targetContext.assets,SherpaRecognizer.config())
        try {
            listOf("rest-extra-noisy-16k.pcm" to "休息加三十秒", "rest-extra-clean-16k.pcm" to "再休息三十秒").forEach { (file,expected) ->
                val bytes=instrumentation.context.assets.open("assistant/$file").use { it.readBytes() }
                val shorts=ShortArray(bytes.size/2)
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                val stream=engine.createStream(SherpaRecognizer.HOTWORDS)
                try {
                    stream.acceptWaveform(FloatArray(shorts.size+16000) { if(it<shorts.size) shorts[it]/32768f else 0f },16000)
                    stream.inputFinished()
                    while(engine.isReady(stream)) engine.decode(stream)
                    assertEquals(file,expected,engine.getResult(stream).text.replace(" ",""))
                } finally { stream.release() }
            }
        } finally { engine.release() }
    }
    @Test fun interruptedSpeechCanResumeAcrossRepeatedCommands() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val synthesizer=SherpaSynthesizer(context)
        try {
            synthesizer.initialize()
            repeat(4) {
                val utterance=launch(Dispatchers.Default) {
                    synthesizer.speak("现在是十点三十分，请准备下一组训练",VoiceChoice.FEMALE)
                }
                delay(150)
                synthesizer.stop()
                withTimeout(30_000) { utterance.join() }
                withTimeout(10_000) { synthesizer.speak("训练已暂停",VoiceChoice.MALE) }
            }
        } finally { synthesizer.close() }
    }
    @Test fun uncachedSpeechCompletesThroughNativeCallbackInBothVoices()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val synthesizer=SherpaSynthesizer(context)
        try {
            synthesizer.initialize()
            withTimeout(60_000) { synthesizer.speak("现在是十点三十分",VoiceChoice.MALE) }
            withTimeout(60_000) { synthesizer.speak("今天训练做得很好",VoiceChoice.FEMALE) }
        } finally { synthesizer.close() }
    }
    @Test fun bundledRecognizerDecodesIndependentChineseWave() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bytes=instrumentation.context.assets.open("assistant/wake-complete-16k.pcm").use { it.readBytes() }
        val shorts=ShortArray(bytes.size/2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        val engine=OnlineRecognizer(instrumentation.targetContext.assets,SherpaRecognizer.config())
        val stream=engine.createStream(SherpaRecognizer.HOTWORDS)
        try {
            stream.acceptWaveform(FloatArray(shorts.size+16000) { if(it<shorts.size) shorts[it]/32768f else 0f },16000)
            stream.inputFinished()
            while(engine.isReady(stream)) engine.decode(stream)
            assertEquals("铁蛋铁蛋完成本组",engine.getResult(stream).text.replace(" ",""))
        } finally { stream.release();engine.release() }
    }
    @Test fun bundledRecognizerFinishesCommandWithinOnePointThreeSecondsOfSilence() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bytes=instrumentation.context.assets.open("assistant/wake-complete-16k.pcm").use { it.readBytes() }
        val shorts=ShortArray(bytes.size/2)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
        val lastVoice=shorts.indexOfLast { kotlin.math.abs(it.toInt())>200 }
        assertTrue(lastVoice>0)
        val engine=OnlineRecognizer(instrumentation.targetContext.assets,SherpaRecognizer.config())
        val stream=engine.createStream(SherpaRecognizer.HOTWORDS)
        try {
            val maxTrailingSamples=20800
            val total=lastVoice+1+maxTrailingSamples
            for(start in 0 until total step 1600) {
                val frame=FloatArray(minOf(1600,total-start)) { position ->
                    val index=start+position
                    if(index<shorts.size) shorts[index]/32768f else 0f
                }
                stream.acceptWaveform(frame,16000)
                while(engine.isReady(stream)) engine.decode(stream)
                if(engine.isEndpoint(stream)) {
                    assertEquals("铁蛋铁蛋完成本组",engine.getResult(stream).text.replace(" ",""))
                    android.util.Log.i("AssistantValidation","endpointTrailingMs=${(start+frame.size-lastVoice)*1000/16000}")
                    assertTrue("Endpoint took over 1.3 seconds",start+frame.size-lastVoice<=maxTrailingSamples)
                    return
                }
            }
            fail("No endpoint within 1.3 seconds of silence")
        } finally { stream.release();engine.release() }
    }
    @Test fun bundledSynthesizerSupportsBothVoicesAndCache()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val config=SherpaSynthesizer.prepareConfig(context)
        val engine=OfflineTts(context.assets,config)
        try {
            assertEquals(24000,engine.sampleRate())
            assertEquals(103,engine.numSpeakers())
            val male=engine.generate("加油",58,1f).samples
            val female=engine.generate("加油",3,1f).samples
            assertTrue(male.size>2400 && male.any { kotlin.math.abs(it)>0.01f })
            assertTrue(female.size>2400 && female.any { kotlin.math.abs(it)>0.01f })
            assertFalse(male.contentEquals(female))
            listOf("male","female").forEach { voice ->
                assertTrue(context.assets.open("assistant/tts/prompts/$voice-prepare.pcm").use { it.readBytes().size }>4800)
            }
            fun size(file:java.io.File)=if(file.isDirectory) file.walkTopDown().filter { it.isFile }.sumOf { it.length() } else file.length()
            val code=size(java.io.File(context.applicationInfo.sourceDir).parentFile!!)
            val frontend=size(context.noBackupFilesDir)
            assertTrue("Installed static bytes ${code+frontend}",code+frontend<1_000_000_000)
            val storage=context.getSystemService(android.app.usage.StorageStatsManager::class.java)
                .queryStatsForUid(android.os.storage.StorageManager.UUID_DEFAULT,context.applicationInfo.uid)
            assertTrue("Installed system bytes ${storage.appBytes+storage.dataBytes}",storage.appBytes+storage.dataBytes<1_000_000_000)
            android.util.Log.i("AssistantValidation","installedCodeBytes=$code frontendBytes=$frontend systemAppBytes=${storage.appBytes} systemDataBytes=${storage.dataBytes} cacheBytes=${storage.cacheBytes} pssDuringTtsKb=${android.os.Debug.getPss()}")
            Unit
        } finally { engine.release() }
    }
    @Test fun cachedSpeechKeepsTrackOpenBeyondPcmDuration()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val bytes=context.assets.open("assistant/tts/prompts/female-pause.pcm").use { it.readBytes() }
        val durationMs=bytes.size/2*1000L/24000
        val synthesizer=SherpaSynthesizer(context)
        try {
            synthesizer.initialize()
            val start=android.os.SystemClock.elapsedRealtime()
            synthesizer.speak("训练已暂停",VoiceChoice.FEMALE)
            val elapsed=android.os.SystemClock.elapsedRealtime()-start
            android.util.Log.i("AssistantValidation","cachedSpeechMs=$elapsed sourceMs=$durationMs")
            assertTrue("AudioTrack released before tail drained: $elapsed ms",elapsed>=durationMs+150)
        } finally { synthesizer.close() }
    }
    @Test fun assistantStartupDoesNotLoadLargeDynamicVoiceModel()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val synthesizer=SherpaSynthesizer(context)
        val before=android.os.Debug.getPss()
        try {
            synthesizer.initialize()
            val increase=android.os.Debug.getPss()-before
            android.util.Log.i("AssistantValidation","assistantStartupPssDeltaKiB=$increase")
            assertTrue("Assistant startup added $increase KiB",increase<80_000)
            synthesizer.speak("训练已暂停",VoiceChoice.FEMALE)
        } finally { synthesizer.close() }
    }
    @Test fun shortCachedRepliesGiveBothVoicesTimeToFinishNaturally() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        listOf("male","female").forEach { voice ->
            val bytes=context.assets.open("assistant/tts/prompts/$voice-pause.pcm").use { it.readBytes() }
            assertTrue("$voice reply is rushed",bytes.size/2/24000f>=1.50f)
        }
    }
}
