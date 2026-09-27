package com.fitflow.training.assistant.speech

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import androidx.core.content.ContextCompat
import com.fitflow.training.assistant.*
import com.k2fsa.sherpa.onnx.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/** One AudioRecord per enabled session; native stream operations stay on the capture executor. */
class SherpaRecognizer(private val context:Context):SpeechRecognizerAdapter {
    private val executor=Executors.newSingleThreadExecutor { Thread(it,"assistant-recognition") }
    private val dispatcher=executor.asCoroutineDispatcher()
    private val generation=AtomicLong()
    private class Capture(val result:(SpeechResult)->Unit,val error:(String)->Unit,val ready:()->Unit)
    private val requested=AtomicReference<Capture?>(null)
    private var captureRunning=false
    private var model:OnlineRecognizer?=null
    @Volatile private var recording:AudioRecord?=null
    @Volatile private var closed=false
    private var turn=0L

    override suspend fun initialize()=withContext(dispatcher) {
        check(!closed)
        model=OnlineRecognizer(context.assets,config())
        Unit
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    override fun start(onResult:(SpeechResult)->Unit,onError:(String)->Unit,onReady:()->Unit) {
        if(closed) return
        requested.set(Capture(onResult,onError,onReady))
        if(captureRunning) return
        captureRunning=true
        val epoch=generation.get()
        executor.execute {
            if(closed || epoch!=generation.get()) return@execute
            if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
                onError("请允许麦克风权限后重新开启助教"); return@execute
            }
            val recognizer=model ?: return@execute
            var stream:OnlineStream?=null
            var mic:AudioRecord?=null
            var echoCanceler:AcousticEchoCanceler?=null
            var errorCallback=onError
            try {
                val minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
                check(minimum>0)
                mic=AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(minimum,6400))
                check(mic.state==AudioRecord.STATE_INITIALIZED)
                if(AcousticEchoCanceler.isAvailable()) {
                    echoCanceler=runCatching { AcousticEchoCanceler.create(mic.audioSessionId)?.also { it.enabled=true } }.getOrNull()
                }
                recording=mic
                if(closed || epoch!=generation.get()) return@execute
                stream=recognizer.createStream(HOTWORDS)
                var active:Capture?=null
                var id=++turn
                var previous=""
                var frames=0
                val shorts=ShortArray(800)
                mic.startRecording()
                check(mic.recordingState==AudioRecord.RECORDSTATE_RECORDING)
                while(!closed && epoch==generation.get()) {
                    val capture=requested.get()
                    if(capture!==active) {
                        recognizer.reset(stream); id=++turn; previous=""; frames=0
                        active=capture
                        if(capture!=null) {
                            errorCallback=capture.error
                            capture.ready()
                        }
                    }
                    val count=mic.read(shorts,0,shorts.size)
                    if(count<=0) {
                        if(epoch==generation.get() && !closed) (requested.get()?.error ?: errorCallback)("麦克风不可用，请重新开启助教")
                        break
                    }
                    // Read continuously during playback, but do not decode our own replies.
                    if(capture==null || requested.get()!==capture) continue
                    stream.acceptWaveform(FloatArray(count) { shorts[it]/32768f },16000)
                    frames+=count
                    while(recognizer.isReady(stream)) recognizer.decode(stream)
                    val text=recognizer.getResult(stream).text
                    val endpoint=recognizer.isEndpoint(stream) || frames>=16000*20
                    if(epoch!=generation.get()) break
                    if(requested.get()!==capture) continue
                    if(endpoint) {
                        if(text.isNotBlank()) capture.result(SpeechResult(id,text,true))
                        recognizer.reset(stream); id=++turn; previous=""; frames=0
                    } else if(text!=previous && text.isNotBlank()) {
                        previous=text; capture.result(SpeechResult(id,text,false))
                    }
                }
            } catch(error:Exception) {
                if(!closed && epoch==generation.get()) (requested.get()?.error ?: errorCallback)("麦克风不可用或语音识别失败，请重新开启助教")
            } finally {
                if(recording===mic) recording=null
                echoCanceler?.release()
                runCatching { mic?.stop() }; mic?.release(); stream?.release()
                synchronized(this) { if(epoch==generation.get()) captureRunning=false }
            }
        }
    }
    override fun pauseRecognition() { requested.set(null) }
    @Synchronized
    override fun stop() {
        requested.set(null); generation.incrementAndGet(); captureRunning=false
        runCatching { recording?.stop() }
    }
    @Synchronized
    override fun close() {
        if(closed) return
        closed=true; stop()
        executor.execute { model?.release(); model=null }
        executor.shutdown()
    }
    companion object {
        const val HOTWORDS="你好教练/跳过休息/延长三十秒休息时间/暂停训练/完成本组/现在练什么/今天天气/北京/上海"+
            "/结束休息/延长休息三十秒/再休息三十秒/休息加三十秒/暂停一下训练/这一组做完了/现在几点/今天星期几/还剩几组/还要休息多久"
        fun config():OnlineRecognizerConfig {
            val path="assistant/asr"
            return OnlineRecognizerConfig(
                featConfig=FeatureConfig(sampleRate=16000,featureDim=80),
                modelConfig=OnlineModelConfig(transducer=OnlineTransducerModelConfig(
                    encoder="$path/encoder-epoch-99-avg-1.int8.onnx",decoder="$path/decoder-epoch-99-avg-1.onnx",joiner="$path/joiner-epoch-99-avg-1.int8.onnx"),
                    tokens="$path/tokens.txt",numThreads=2,modelingUnit="cjkchar"),
                decodingMethod="modified_beam_search",hotwordsScore=2.0f,enableEndpoint=true,
                endpointConfig=EndpointConfig(rule2=EndpointRule(true,0.45f,0f)))
        }
    }
}
