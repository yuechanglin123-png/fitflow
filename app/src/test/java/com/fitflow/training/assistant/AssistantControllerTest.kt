package com.fitflow.training.assistant

import com.fitflow.training.plan.WorkoutPlan
import com.fitflow.training.session.*
import java.time.LocalDate
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AssistantControllerTest {
    private class Recognizer(val initialization:CompletableDeferred<Unit>?=null) : SpeechRecognizerAdapter {
        var callback: ((SpeechResult)->Unit)? = null
        var errorCallback: ((String)->Unit)? = null
        var stopped=false
        override suspend fun initialize() { initialization?.await() }
        override fun start(onResult: (SpeechResult)->Unit, onError: (String)->Unit) { callback=onResult;errorCallback=onError; stopped=false }
        override fun stop() { stopped=true }
        override fun close() { stopped=true }
        fun emit(id:Long, text:String, final:Boolean=true) { callback?.invoke(SpeechResult(id,text,final)) }
    }
    private class Synth(val completion:CompletableDeferred<Unit>?=null, val retryCompletion:CompletableDeferred<Unit>?=null) : SpeechSynthesizerAdapter {
        val spoken=mutableListOf<String>()
        override suspend fun initialize() {}
        override suspend fun speak(text:String, voice:VoiceChoice) {
            spoken+=text; completion?.await()
            if(text=="对不起，我没有听清，请再说一次") retryCompletion?.await()
        }
        override fun stop() {}
        override fun close() {}
    }
    private val snapshot = SessionSnapshot("s", WorkoutPlan(LocalDate.of(2026,9,23), emptyList()), emptyMap(), Phase.READY,"b",null)
    @Test fun delayedErrorFromStoppedRecordingDoesNotAbortReply()=runBlocking {
        val gate=CompletableDeferred<Unit>(); val r=Recognizer(); val t=Synth(gate)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val c=AssistantController(scope,{r},{t},{snapshot},{_,_->error("No command")},{"回答"},{VoiceChoice.FEMALE})
        try {
            c.enable();val oldError=r.errorCallback!!
            r.emit(1,"小练小练")
            assertEquals(AssistantStage.SPEAKING,c.state.value.stage)
            oldError("录音已关闭")
            assertEquals(AssistantStage.SPEAKING,c.state.value.stage)
            gate.complete(Unit);yield()
            assertEquals(AssistantStage.LISTENING,c.state.value.stage)
        } finally { c.close();scope.cancel() }
    }
    @Test fun noInstructionForFiveSecondsReturnsToWakeOnly()=runBlocking {
        val r=Recognizer(); val t=Synth(); var count=0
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val c=AssistantController(scope,{r},{t},{snapshot},{_,_->count++;CommandResult(true,"完成")},{"回答"},{VoiceChoice.FEMALE})
        try {
            c.enable(); r.emit(1,"小练小练")
            assertEquals(AssistantStage.LISTENING,c.state.value.stage)
            delay(4500)
            assertEquals(AssistantStage.LISTENING,c.state.value.stage)
            delay(700)
            assertEquals(AssistantStage.WAITING,c.state.value.stage)
            r.emit(2,"完成本组")
            assertEquals(0,count)
            assertEquals(listOf("我在，请说指令"),t.spoken)
        } finally { c.close();scope.cancel() }
    }
    @Test fun unclearInstructionPromptsAndAcceptsRetryWithoutAnotherWake()=runBlocking {
        val r=Recognizer(); val t=Synth(); var count=0
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val c=AssistantController(scope,{r},{t},{snapshot},{_,_->count++;CommandResult(true,"完成")},{"回答"},{VoiceChoice.FEMALE})
        try {
            c.enable();r.emit(1,"小练小练");r.emit(2,"帮我那个一下")
            assertEquals("对不起，我没有听清，请再说一次",c.state.value.reply)
            assertEquals(AssistantStage.LISTENING,c.state.value.stage)
            r.emit(3,"完成本组")
            assertEquals(1,count)
            assertEquals(AssistantStage.WAITING,c.state.value.stage)
        } finally { c.close();scope.cancel() }
    }
    @Test fun retryWindowBeginsAfterPromptAndExpiresWithoutInstruction()=runBlocking {
        val gate=CompletableDeferred<Unit>(); val r=Recognizer(); val t=Synth(retryCompletion=gate)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val c=AssistantController(scope,{r},{t},{snapshot},{_,_->error("No command")},{"回答"},{VoiceChoice.FEMALE})
        try {
            c.enable();r.emit(1,"小练小练");r.emit(2,"没那个")
            delay(5200)
            assertEquals(AssistantStage.SPEAKING,c.state.value.stage)
            gate.complete(Unit);yield()
            assertEquals(AssistantStage.LISTENING,c.state.value.stage)
            delay(5200)
            assertEquals(AssistantStage.WAITING,c.state.value.stage)
        } finally { c.close();scope.cancel() }
    }
    @Test fun incompleteSpeechAtDeadlineRequestsRetryWithoutExecutingPartialCommand()=runBlocking {
        val r=Recognizer(); val t=Synth(); var count=0
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val c=AssistantController(scope,{r},{t},{snapshot},{_,_->count++;CommandResult(true,"完成")},{"回答"},{VoiceChoice.FEMALE})
        try {
            c.enable();r.emit(1,"小练小练");r.emit(2,"完成本组",false)
            delay(5200)
            assertEquals(0,count)
            assertEquals("对不起，我没有听清，请再说一次",c.state.value.reply)
            assertEquals(AssistantStage.LISTENING,c.state.value.stage)
            r.emit(3,"完成本组")
            assertEquals(1,count)
        } finally { c.close();scope.cancel() }
    }
    @Test fun closingWhileRetryPromptPlaysDoesNotRestartListening()=runBlocking {
        val gate=CompletableDeferred<Unit>(); val r=Recognizer(); val t=Synth(retryCompletion=gate)
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val c=AssistantController(scope,{r},{t},{snapshot},{_,_->error("No command")},{"回答"},{VoiceChoice.FEMALE})
        try {
            c.enable();r.emit(1,"小练小练");r.emit(2,"没那个")
            c.disable();gate.complete(Unit);yield()
            assertEquals(AssistantStage.OFF,c.state.value.stage)
            assertTrue(r.stopped)
        } finally { c.close();scope.cancel() }
    }
    @Test fun retryPinsFreshTrainingContextAndRejectsOldCaptureCallbacks()=runBlocking {
        val r=Recognizer(); val t=Synth(); var current=snapshot; var captured:CommandContext?=null
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val c=AssistantController(scope,{r},{t},{current},{_,ctx->captured=ctx;CommandResult(true,"完成")},{"回答"},{VoiceChoice.FEMALE})
        try {
            c.enable();r.emit(1,"小练小练")
            val oldCapture=r.callback!!
            current=current.copy(currentBlockId="next",revision=1)
            r.emit(2,"不清楚")
            oldCapture(SpeechResult(99,"完成本组",true))
            assertNull(captured)
            r.emit(3,"完成本组")
            assertEquals("next",captured?.blockId)
            assertEquals(1L,captured?.revision)
        } finally { c.close();scope.cancel() }
    }
    @Test fun wakeHomophonesWorkButSingleNameAndQuotedWakeDoNot()=runBlocking {
        val r=Recognizer(); val t=Synth(); var count=0
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val c=AssistantController(scope,{r},{t},{snapshot},{_,_->count++;CommandResult(true,"完成")},{"回答"},{VoiceChoice.FEMALE})
        try {
            c.enable();r.emit(1,"小练完成本组");r.emit(2,"我说小练小练完成本组")
            assertEquals(0,count)
            r.emit(3,"小炼小练，完成本组")
            assertEquals(1,count)
        } finally { c.close();scope.cancel() }
    }
    @Test fun requiresWakeAndFinalResultAndExecutesOnlyOnce() = runBlocking {
        val r=Recognizer(); val t=Synth(); var count=0
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val c=AssistantController(scope, {r}, {t}, {snapshot}, {_,_-> count++; CommandResult(true,"完成")}, {"回答"}, {VoiceChoice.FEMALE})
        c.enable(); yield()
        r.emit(1,"完成本组")
        r.emit(2,"小练小练完成本组",false)
        assertEquals(0,count)
        r.emit(3,"小练小练完成本组")
        r.emit(3,"小练小练完成本组")
        assertEquals(1,count)
        c.disable(); scope.cancel()
    }
    @Test fun disablingRejectsLateRecognitionAndStopsAudio() = runBlocking {
        val r=Recognizer(); val t=Synth(); var count=0
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val c=AssistantController(scope, {r}, {t}, {snapshot}, {_,_-> count++; CommandResult(true,"完成")}, {"回答"}, {VoiceChoice.MALE})
        c.enable(); yield(); c.disable()
        r.emit(1,"小练小练完成本组")
        assertEquals(0,count)
        assertTrue(r.stopped)
        assertFalse(c.state.value.enabled)
        scope.cancel()
    }
    @Test fun partialWakePinsContextBeforeButtonChangesSet()=runBlocking {
        val r=Recognizer(); val t=Synth(); var current=snapshot; var captured:CommandContext?=null
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val c=AssistantController(scope,{r},{t},{current},{_,ctx->captured=ctx;CommandResult(false,"已变化")},{"回答"},{VoiceChoice.FEMALE})
        c.enable(); yield()
        r.emit(1,"小练小练",false)
        current=current.copy(currentBlockId="next",revision=1)
        r.emit(1,"小练小练完成本组")
        assertEquals("b",captured?.blockId)
        c.close(); scope.cancel()
    }
    @Test fun offDuringInitializationNeverStartsListening()=runBlocking {
        val gate=CompletableDeferred<Unit>(); val r=Recognizer(gate); val t=Synth()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val c=AssistantController(scope,{r},{t},{snapshot},{_,_->error("No action")},{"回答"},{VoiceChoice.FEMALE})
        c.enable(); c.disable(); gate.complete(Unit); yield()
        assertNull(r.callback); assertFalse(c.state.value.enabled)
        scope.cancel()
    }
    @Test fun offCancelsPendingWeatherAndDoesNotSpeakOrRelisten()=runBlocking {
        val gate=CompletableDeferred<String>(); val r=Recognizer(); val t=Synth()
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val c=AssistantController(scope,{r},{t},{snapshot},{_,_->error("No action")},{gate.await()},{VoiceChoice.FEMALE})
        c.enable(); r.emit(1,"小练小练今天天气")
        c.disable(); gate.complete("晴"); yield()
        assertTrue(t.spoken.isEmpty()); assertTrue(r.stopped); assertFalse(c.state.value.enabled)
        scope.cancel()
    }
    @Test fun lastSetSpeaksBeforeClosingAndNeverRelistens()=runBlocking {
        val gate=CompletableDeferred<Unit>(); val r=Recognizer(); val t=Synth(gate); var current=snapshot
        val scope=CoroutineScope(SupervisorJob()+Dispatchers.Unconfined)
        val c=AssistantController(scope,{r},{t},{current},{_,_->current=current.copy(phase=Phase.FINISHED);CommandResult(true,"训练已完成，辛苦了")},{"回答"},{VoiceChoice.FEMALE})
        c.enable();r.emit(1,"小练小练完成本组")
        assertTrue(c.state.value.enabled)
        assertEquals(AssistantStage.SPEAKING,c.state.value.stage)
        gate.complete(Unit);yield()
        assertEquals(listOf("训练已完成，辛苦了"),t.spoken)
        assertFalse(c.state.value.enabled)
        assertTrue(r.stopped)
        scope.cancel()
    }
}
