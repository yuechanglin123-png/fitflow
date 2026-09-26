package com.fitflow.training.assistant.speech

import android.content.Context
import android.media.*
import com.fitflow.training.assistant.*
import com.k2fsa.sherpa.onnx.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

class SherpaSynthesizer(private val context:Context):SpeechSynthesizerAdapter {
    private val executor=Executors.newSingleThreadExecutor { Thread(it,"assistant-synthesis") }
    private val dispatcher=executor.asCoroutineDispatcher()
    private val generation=AtomicLong()
    @Volatile private var closed=false
    @Volatile private var track:AudioTrack?=null
    private var model:OfflineTts?=null
    private var prompts=emptyMap<String,String>()
    override suspend fun initialize()=withContext(dispatcher) {
        ensureActive(); check(!closed)
        prompts=Json.parseToJsonElement(context.assets.open("assistant/tts/prompts/prompts.json").bufferedReader().use { it.readText() })
            .jsonObject.map { (key,value)->value.jsonPrimitive.content to key }.toMap()
        Unit
    }
    override suspend fun speak(text:String,voice:VoiceChoice) {
        val epoch=generation.incrementAndGet()
        withContext(dispatcher) {
            ensureActive()
            if(closed || epoch!=generation.get()) return@withContext
            val cached=prompts[text]
            val engine=if(cached==null) model ?: OfflineTts(context.assets,prepareConfig(context)).also { model=it } else null
            if(closed || epoch!=generation.get()) return@withContext
            val minimum=AudioTrack.getMinBufferSize(24000,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
            val audio=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(24000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(maxOf(minimum,24000)).setTransferMode(AudioTrack.MODE_STREAM).build()
            track=audio
            var written=0L
            var audioFailure:Throwable?=null
            fun output(samples:ShortArray):Boolean {
                var offset=0
                while(offset<samples.size && epoch==generation.get() && !closed) {
                    val count=audio.write(samples,offset,minOf(2400,samples.size-offset),AudioTrack.WRITE_BLOCKING)
                    if(count<=0) { if(epoch==generation.get()) audioFailure=IllegalStateException("Audio output failed: $count"); return false }
                    offset+=count;written+=count
                }
                return epoch==generation.get()&&!closed
            }
            try {
                if(epoch!=generation.get() || closed) return@withContext
                // Prime the audio route before the first syllable reaches the speaker.
                output(ShortArray(2400))
                audio.play()
                if(cached!=null) {
                    val path="assistant/tts/prompts/${if(voice==VoiceChoice.MALE) "male" else "female"}-$cached.pcm"
                    val bytes=context.assets.open(path).use { it.readBytes() }
                    val samples=ShortArray(bytes.size/2)
                    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(samples)
                    output(samples)
                } else {
                    // The JNI binding looks up invoke(float[]) on the concrete callback class.
                    // A Kotlin lambda only exposes invoke(Object) on some Android runtimes.
                    val callback=object:(FloatArray)->Int {
                        override fun invoke(samples:FloatArray):Int = try {
                            if(output(ShortArray(samples.size){(samples[it].coerceIn(-1f,1f)*32767).toInt().toShort()})) 1 else 0
                        } catch(error:Throwable) { audioFailure=error; 0 }
                    }
                    val spoken=if(text.endsWith('。')||text.endsWith('！')||text.endsWith('？')) text else "$text。"
                    checkNotNull(engine).generateWithCallback(spoken,if(voice==VoiceChoice.MALE) 58 else 3,0.92f,callback)
                }
                audioFailure?.let { throw it }
                if(epoch==generation.get()&&!closed) output(ShortArray(4800))
                audioFailure?.let { throw it }
                while(epoch==generation.get()&&!closed&&(audio.playbackHeadPosition.toLong() and 0xffffffffL)<written) {
                    ensureActive(); delay(10)
                }
            } finally {
                if(track===audio) track=null
                runCatching { audio.pause();audio.flush() };audio.release()
            }
        }
    }
    override fun stop() { generation.incrementAndGet(); runCatching { track?.pause();track?.flush() } }
    override fun close() {
        if(closed) return
        closed=true;stop()
        executor.execute { model?.release();model=null }
        executor.shutdown()
    }
    companion object {
        /** Only frontend dictionaries need real paths. Large weights are read directly from the APK. */
        suspend fun prepareConfig(context:Context):OfflineTtsConfig=withContext(Dispatchers.IO) {
            val assets="assistant/tts"
            val target=File(context.noBackupFilesDir,"assistant-frontend-v7")
            suspend fun copyTree(relative:String) {
                currentCoroutineContext().ensureActive()
                val children=context.assets.list("$assets/$relative").orEmpty()
                val file=File(target,relative)
                if(children.isNotEmpty()) { file.mkdirs(); children.forEach { copyTree("$relative/$it") };return }
                if(file.isFile && file.length()>0) return
                file.parentFile?.mkdirs()
                val temporary=File(file.parentFile,file.name+".tmp")
                context.assets.open("$assets/$relative").use { input -> temporary.outputStream().use { input.copyTo(it) } }
                check(temporary.renameTo(file))
            }
            copyTree("dict");copyTree("espeak-ng-data")
            OfflineTtsConfig(model=OfflineTtsModelConfig(kokoro=OfflineTtsKokoroModelConfig(
                model="$assets/model.int8.onnx",voices="$assets/voices.bin",tokens="$assets/tokens.txt",lexicon="$assets/lexicon-zh.txt",
                dataDir=File(target,"espeak-ng-data").path,dictDir=File(target,"dict").path,lang="zh"),numThreads=2),
                ruleFsts=listOf("phone-zh.fst","date-zh.fst","number-zh.fst").joinToString(","){"$assets/$it"})
        }
    }
}
