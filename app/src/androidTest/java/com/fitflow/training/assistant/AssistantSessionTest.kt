package com.fitflow.training.assistant

import androidx.test.platform.app.InstrumentationRegistry
import com.fitflow.training.data.WorkoutRuntime
import com.fitflow.training.plan.*
import com.fitflow.training.session.*
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class AssistantSessionTest {
    @Test fun automaticRestCompletionNotifiesExactlyOnceRegardlessOfCaller()=runBlocking {
        val repository=WorkoutRuntime.get(InstrumentationRegistry.getInstrumentation().targetContext).repository
        var notifications=0
        val model=SessionViewModel(repository,onRestEnded={_,_->notifications++})
        val plan=WorkoutPlan(LocalDate.now(),listOf(PlannedExercise("rest-event",null,"测试",listOf(
            PlannedBlock("r1",BigDecimal.ZERO,1,8,0,""),PlannedBlock("r2",BigDecimal.ZERO,1,8,0,"")))))
        model.start(plan);model.completeSet()
        val first=model.session.value!!
        model.reconcile()
        assertNull(model.reconcileRestAlarm(first.id,first.restEndsAtEpochMs!!))
        assertEquals(1,notifications)
        model.start(plan);model.completeSet()
        val second=model.session.value!!
        assertNotNull(model.reconcileRestAlarm(second.id,second.restEndsAtEpochMs!!))
        model.reconcile()
        assertEquals(2,notifications)
    }
    @Test fun concurrentCommandsOnlyCompleteOneSetAndPersistTime()=runBlocking {
        val runtime=WorkoutRuntime.get(InstrumentationRegistry.getInstrumentation().targetContext)
        val model=runtime.session
        model.start(WorkoutPlan(LocalDate.now(),listOf(PlannedExercise("assistant",null,"测试",listOf(
            PlannedBlock("one",BigDecimal.ZERO,1,8,60,""),PlannedBlock("two",BigDecimal.ZERO,1,8,60,""))))))
        val ctx=CommandContext.from(model.session.value!!,1)
        val results=coroutineScope { (1..2).map { async(Dispatchers.Default) { model.executeAssistant(TrainingCommand.COMPLETE_SET,ctx) } }.awaitAll() }
        assertEquals(1,results.count { it.applied })
        val saved=runtime.repository.loadSession(ctx.sessionId)!!
        assertEquals(1,saved.completedBlockSets.values.sum())
        delay(30);model.reconcile();val elapsed=model.session.value!!.activeElapsedMs
        assertTrue(elapsed>0)
        model.pause();val paused=model.session.value!!.activeElapsedMs
        delay(30);model.tickAssistant(true)
        assertEquals(paused,model.session.value!!.activeElapsedMs)
    }
}
