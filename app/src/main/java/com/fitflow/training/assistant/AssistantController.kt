package com.fitflow.training.assistant

import com.fitflow.training.session.Phase
import com.fitflow.training.session.SessionSnapshot
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AssistantStage(val label: String) {
    OFF("已关闭"), INITIALIZING("正在初始化"), WAITING("等待唤醒：铁蛋铁蛋"),
    LISTENING("请在 5 秒内说指令"), PROCESSING("正在处理"), SPEAKING("正在播报"), ERROR("助教暂不可用")
}
data class AssistantUiState(val enabled: Boolean=false, val stage: AssistantStage=AssistantStage.OFF,
    val heard: String="", val reply: String="", val error: String?=null)

/** All methods and callbacks run on the owning scope's single UI dispatcher. */
class AssistantController(
    private val scope: CoroutineScope,
    private val createRecognizer: ()->SpeechRecognizerAdapter,
    private val createSynthesizer: ()->SpeechSynthesizerAdapter,
    private val snapshot: ()->SessionSnapshot?,
    private val execute: suspend (TrainingCommand, CommandContext)->CommandResult,
    private val answer: suspend (AssistantIntent)->String,
    private val voice: ()->VoiceChoice,
) {
    private val mutableState=MutableStateFlow(AssistantUiState())
    val state=mutableState.asStateFlow()
    private var generation=0L
    private var nextTurn=0L
    private var lastResult=-1L
    private var parent: Job?=null
    private var timeout: Job?=null
    private var wakePrompt: Job?=null
    private var recognizer: SpeechRecognizerAdapter?=null
    private var synthesizer: SpeechSynthesizerAdapter?=null
    private var context: CommandContext?=null
    private var wakeResult: Long?=null
    private var captureEpoch=0L
    private var partialText=""
    private val parser=AssistantIntentParser()
    private fun valid(g:Long)=g==generation && state.value.enabled
    private fun stage(value:AssistantStage) { mutableState.value=state.value.copy(stage=value) }

    fun enable() {
        if (state.value.enabled || snapshot()?.phase == Phase.FINISHED || snapshot()==null) return
        val g=++generation
        lastResult=-1
        mutableState.value=AssistantUiState(true,AssistantStage.INITIALIZING)
        parent=scope.launch {
            val r=createRecognizer(); val t=createSynthesizer()
            recognizer=r; synthesizer=t
            try {
                r.initialize(); ensureActive()
                t.initialize(); ensureActive()
                if (!valid(g)) return@launch
                listen(g, AssistantStage.WAITING)
                awaitCancellation()
            } catch (cancel:CancellationException) { throw cancel }
            catch (error:Exception) { if(valid(g)) fail("无法启动离线语音，请检查麦克风权限或重新开启") }
            finally {
                withContext(NonCancellable+Dispatchers.IO) { r.close(); t.close() }
            }
        }
    }

    fun disable() {
        ++generation
        timeout?.cancel(); timeout=null
        wakePrompt?.cancel(); wakePrompt=null
        recognizer?.stop(); synthesizer?.stop()
        recognizer=null; synthesizer=null
        parent?.cancel(); parent=null; context=null; wakeResult=null
        mutableState.value=state.value.copy(enabled=false,stage=AssistantStage.OFF,error=null)
    }

    fun fail(message:String) {
        disable()
        mutableState.value=state.value.copy(stage=AssistantStage.ERROR,error=message)
    }

    fun close()=disable()

    private fun listen(g:Long, next:AssistantStage, onReady:()->Unit={}) {
        if(!valid(g)) return
        if(next==AssistantStage.WAITING) { context=null; wakeResult=null }
        else if(next==AssistantStage.LISTENING) {
            // Each retry is a new instruction, pinned before its audio is processed.
            context=snapshot()?.let { CommandContext.from(it,++nextTurn) }
        }
        partialText=""
        val capture=++captureEpoch
        stage(next)
        timeout?.cancel();timeout=null
        recognizer?.start({ result -> scope.launch { if(capture==captureEpoch) onResult(g,result) } }, { message ->
            scope.launch {
                val listening=state.value.stage in listOf(AssistantStage.WAITING,AssistantStage.LISTENING)
                if(valid(g) && capture==captureEpoch && listening) fail(message)
            }
        }, {
            scope.launch {
                if(valid(g) && capture==captureEpoch && state.value.stage==next) {
                    if(next==AssistantStage.LISTENING) startCommandTimeout(g,capture)
                    onReady()
                }
            }
        })
    }

    private fun startCommandTimeout(g:Long, capture:Long) {
        timeout=scope.launch(parent ?: return) {
            delay(5000)
            if(valid(g) && capture==captureEpoch && state.value.stage==AssistantStage.LISTENING) {
                timeout=null // Do not cancel this coroutine when speak() starts the next capture.
                stopWakePrompt()
                if(partialText.isBlank()) listen(g,AssistantStage.WAITING)
                else try {
                    speak(g,RETRY_PROMPT,AssistantStage.LISTENING)
                } catch(cancel:CancellationException) { throw cancel }
                catch(error:Exception) { if(valid(g)) fail("语音播报失败，请重新开启助教") }
            }
        }
    }

    private fun onResult(g:Long, result:SpeechResult) {
        if(!valid(g) || result.turnId <= lastResult) return
        val currentStage=state.value.stage
        if(currentStage!=AssistantStage.WAITING && currentStage!=AssistantStage.LISTENING) return
        var text=AssistantSpeechText.normalize(result.text)
        if(text.isEmpty()) return
        if(currentStage==AssistantStage.LISTENING) text=AssistantWakeReplyFilter.userText(text) ?: return
        if(currentStage==AssistantStage.LISTENING) partialText=text
        val afterWake=AssistantSpeechText.afterWake(text)
        if(currentStage==AssistantStage.WAITING && afterWake!=null && wakeResult!=result.turnId) {
            wakeResult=result.turnId
            context=snapshot()?.let { CommandContext.from(it,++nextTurn) }
        }
        if(!result.isFinal) return
        lastResult=result.turnId
        if(currentStage==AssistantStage.WAITING) {
            text=afterWake ?: return
        } else if(afterWake!=null) text=afterWake
        if(currentStage==AssistantStage.WAITING && text.isEmpty()) {
            mutableState.value=state.value.copy(heard=result.text,reply="我在，请说指令")
            listen(g,AssistantStage.LISTENING) { startWakePrompt(g) }
            return
        }
        mutableState.value=state.value.copy(heard=result.text,stage=AssistantStage.PROCESSING)
        recognizer?.stop(); timeout?.cancel(); stopWakePrompt()
        val commandContext=context
        val owner=parent ?: return
        scope.launch(owner) {
            try {
                if(text.isEmpty()) {
                    speak(g,"我在，请说指令",AssistantStage.LISTENING)
                    return@launch
                }
                val intent=parser.parse(text)
                if(intent==AssistantIntent.MultipleCommands) {
                    speak(g,"请一次只说一个指令",AssistantStage.LISTENING)
                    return@launch
                }
                if(intent==AssistantIntent.Unsupported) {
                    speak(g,RETRY_PROMPT,AssistantStage.LISTENING)
                    return@launch
                }
                val reply=when(intent) {
                    is AssistantIntent.Command -> {
                        if(commandContext==null) "尚未开始训练"
                        else execute(intent.value,commandContext).message
                    }
                    else -> answer(intent)
                }
                if(valid(g)) speak(g,reply,AssistantStage.WAITING)
                context=null
            } catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) {
                if(valid(g)) {
                    mutableState.value=state.value.copy(reply="暂时无法完成，请重试")
                    listen(g,AssistantStage.WAITING)
                }
            }
        }
    }

    private suspend fun speak(g:Long, text:String, afterwards:AssistantStage) {
        if(!valid(g)) return
        recognizer?.stop()
        mutableState.value=state.value.copy(stage=AssistantStage.SPEAKING,reply=text)
        synthesizer?.speak(text,voice())
        currentCoroutineContext().ensureActive()
        if(valid(g)) {
            if(snapshot()?.phase==Phase.FINISHED) disable() else listen(g,afterwards)
        }
    }

    fun notify(event:ReminderEvent) {
        val g=generation
        val owner=parent ?: return
        scope.launch(owner) {
            while(valid(g) && state.value.stage!=AssistantStage.WAITING && System.currentTimeMillis()<event.expiresAtMs) delay(250)
            val current=snapshot()
            if(!valid(g) || current?.id!=event.sessionId || current.isPaused || current.phase==Phase.FINISHED || System.currentTimeMillis()>=event.expiresAtMs) return@launch
            if(event.priority==2 && (current.phase!=Phase.RESTING || current.restEndsAtEpochMs!=event.expiresAtMs)) return@launch
            try { speak(g,event.text,AssistantStage.WAITING) }
            catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) { if(valid(g)) fail("语音播报失败，请重新开启助教") }
        }
    }

    private fun startWakePrompt(g:Long) {
        val owner=parent ?: return
        wakePrompt=scope.launch(owner) {
            try { synthesizer?.speak("我在，请说指令",voice()) }
            catch(cancel:CancellationException) { throw cancel }
            catch(error:Exception) { if(valid(g)) fail("语音播报失败，请重新开启助教") }
        }
    }

    private fun stopWakePrompt() {
        if(wakePrompt!=null) {
            wakePrompt?.cancel(); wakePrompt=null
            synthesizer?.stop()
        }
    }

    companion object {
        const val RETRY_PROMPT="对不起，我没有听清，请再说一次"
    }
}
