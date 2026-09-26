package com.fitflow.training.assistant.speech

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import androidx.core.content.ContextCompat
import com.fitflow.training.assistant.*
import com.k2fsa.sherpa.onnx.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

/** Native recognizer and streams are confined to one executor. stop() only cancels capture. */
class SherpaRecognizer(private val context:Context):SpeechRecognizerAdapter {
    private val executor=Executors.newSingleThreadExecutor { Thread(it,"assistant-recognition") }
    private val dispatcher=executor.asCoroutineDispatcher()
    private val generation=AtomicLong()
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
    override fun start(onResult:(SpeechResult)->Unit,onError:(String)->Unit) {
        stop()
        if(closed) return
        val epoch=generation.get()
        executor.execute {
            if(closed || epoch!=generation.get()) return@execute
            if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) {
                onError("请允许麦克风权限后重新开启助教"); return@execute
            }
            val recognizer=model ?: return@execute
            var stream:OnlineStream?=null
            var mic:AudioRecord?=null
            try {
                val minimum=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
                check(minimum>0)
                mic=AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,maxOf(minimum,6400))
                check(mic.state==AudioRecord.STATE_INITIALIZED)
                recording=mic
                if(closed || epoch!=generation.get()) return@execute
                stream=recognizer.createStream(HOTWORDS)
                var id=++turn
                var previous=""
                var frames=0
                val shorts=ShortArray(1600)
                mic.startRecording()
                check(mic.recordingState==AudioRecord.RECORDSTATE_RECORDING)
                while(!closed && epoch==generation.get()) {
                    val count=mic.read(shorts,0,shorts.size)
                    if(count<=0) { if(epoch==generation.get()) error("Microphone unavailable"); break }
                    stream.acceptWaveform(FloatArray(count) { shorts[it]/32768f },16000)
                    frames+=count
                    while(recognizer.isReady(stream)) recognizer.decode(stream)
                    val text=recognizer.getResult(stream).text
                    val endpoint=recognizer.isEndpoint(stream) || frames>=16000*20
                    if(epoch!=generation.get()) break
                    if(endpoint) {
                        if(text.isNotBlank()) onResult(SpeechResult(id,text,true))
                        recognizer.reset(stream); id=++turn; previous=""; frames=0
                    } else if(text!=previous && text.isNotBlank()) {
                        previous=text; onResult(SpeechResult(id,text,false))
                    }
                }
            } catch(error:Exception) {
                if(!closed && epoch==generation.get()) onError("麦克风不可用或语音识别失败，请重新开启助教")
            } finally {
                if(recording===mic) recording=null
                runCatching { mic?.stop() }; mic?.release(); stream?.release()
            }
        }
    }
    override fun stop() { generation.incrementAndGet(); runCatching { recording?.stop() } }
    override fun close() {
        if(closed) return
        closed=true; stop()
        executor.execute { model?.release(); model=null }
        executor.shutdown()
    }
    companion object {
        const val HOTWORDS="小练小练/跳过休息/延长三十秒休息时间/暂停训练/完成本组/现在练什么/今天天气/北京/上海"+
            "/结束休息/延长休息三十秒/再休息三十秒/休息加三十秒/暂停一下训练/这一组做完了/现在几点/今天星期几/还剩几组/还要休息多久"
        fun config():OnlineRecognizerConfig {
            val path="assistant/asr"
            return OnlineRecognizerConfig(
                featConfig=FeatureConfig(sampleRate=16000,featureDim=80),
                modelConfig=OnlineModelConfig(transducer=OnlineTransducerModelConfig(
                    encoder="$path/encoder-epoch-99-avg-1.int8.onnx",decoder="$path/decoder-epoch-99-avg-1.onnx",joiner="$path/joiner-epoch-99-avg-1.int8.onnx"),
                    tokens="$path/tokens.txt",numThreads=2,modelingUnit="cjkchar"),
                decodingMethod="modified_beam_search",hotwordsScore=2.0f,enableEndpoint=true,
                endpointConfig=EndpointConfig(rule2=EndpointRule(true,0.65f,0f)))
        }
    }
}
