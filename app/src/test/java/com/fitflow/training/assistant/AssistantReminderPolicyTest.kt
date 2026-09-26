package com.fitflow.training.assistant

import com.fitflow.training.plan.WorkoutPlan
import com.fitflow.training.session.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AssistantReminderPolicyTest {
    private fun resting() = SessionSnapshot("s", WorkoutPlan(LocalDate.of(2026,9,23), emptyList()), emptyMap(), Phase.RESTING, "b", 20000)
    @Test fun preparesOnceAtThresholdAndNeverAtZero() {
        val s = resting()
        assertTrue(AssistantReminderPolicy.evaluate(s, 9000, true).events.isEmpty())
        val first = AssistantReminderPolicy.evaluate(s, 10000, true)
        assertEquals(1, first.events.size)
        assertTrue(AssistantReminderPolicy.evaluate(first.snapshot, 11000, true).events.isEmpty())
        assertTrue(AssistantReminderPolicy.evaluate(s, 20000, true).events.isEmpty())
    }
    @Test fun pauseAndDisabledSuppressRemindersWithoutCatchup() {
        assertTrue(AssistantReminderPolicy.evaluate(resting().copy(isPaused=true), 10000, true).events.isEmpty())
        val off = AssistantReminderPolicy.evaluate(resting().copy(activeElapsedMs=600000), 1000, false)
        assertEquals(1L, off.snapshot.encouragementBucket)
        assertTrue(AssistantReminderPolicy.evaluate(off.snapshot, 1000, true).events.isEmpty())
    }
    @Test fun preparationHasPriorityAndEncouragementExpires() {
        val result = AssistantReminderPolicy.evaluate(resting().copy(activeElapsedMs=600000), 10000, true)
        assertEquals(2, result.events.size)
        assertTrue(result.events.first().priority > result.events.last().priority)
        assertEquals(40000L, result.events.last().expiresAtMs)
    }
}
