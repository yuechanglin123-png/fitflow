package com.fitflow.training.assistant

import com.fitflow.training.plan.WorkoutPlan
import com.fitflow.training.session.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AssistantTrainingClockTest {
    private val base=SessionSnapshot("clock",WorkoutPlan(LocalDate.now(),emptyList()),emptyMap(),Phase.READY,"b",null)
    @Test fun processRestoreCountsElapsedButDoesNotReplayEncouragement() {
        val saved=base.copy(activeElapsedMs=500_000,lastAccountedEpochMs=1000)
        val restored=AssistantTrainingClock.restore(saved,201_000)
        assertEquals(700_000,restored.activeElapsedMs)
        assertEquals(1,restored.encouragementBucket)
        assertTrue(AssistantReminderPolicy.evaluate(restored,201_000,true).events.isEmpty())
    }
    @Test fun pauseRollbackAndOldRecordsDoNotInventElapsedTime() {
        assertEquals(0,AssistantTrainingClock.restore(base,1000).activeElapsedMs)
        assertEquals(500,AssistantTrainingClock.restore(base.copy(activeElapsedMs=500,lastAccountedEpochMs=2000),1000).activeElapsedMs)
        assertEquals(500,AssistantTrainingClock.restore(base.copy(activeElapsedMs=500,lastAccountedEpochMs=1000,isPaused=true),100_000).activeElapsedMs)
    }
}
