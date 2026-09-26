package com.fitflow.training.assistant

import com.fitflow.training.plan.*
import com.fitflow.training.session.*
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class AssistantCommandDispatcherTest {
    private fun started() = SessionReducer.start(WorkoutPlan(LocalDate.of(2026,9,23), listOf(
        PlannedExercise("e", null, "深蹲", listOf(
            PlannedBlock("b1", BigDecimal.TEN, 1, 10, 60, ""),
            PlannedBlock("b2", BigDecimal.TEN, 1, 10, 60, ""),
        )))), "s")

    @Test fun sameContextCannotCompleteNextSet() {
        val first = started()
        val context = CommandContext.from(first, 1)
        val completed = AssistantCommandDispatcher.apply(first, TrainingCommand.COMPLETE_SET, context, 1000)
        assertTrue(completed.result.applied)
        assertEquals(1, completed.snapshot.completedBlockSets["b1"])
        val next = SessionReducer.skipRest(completed.snapshot)
        assertFalse(AssistantCommandDispatcher.apply(next, TrainingCommand.COMPLETE_SET, context, 2000).result.applied)
    }

    @Test fun rejectsWrongPhasePausedAndDifferentSession() {
        val state = started()
        assertFalse(AssistantCommandDispatcher.apply(state, TrainingCommand.SKIP_REST, CommandContext.from(state, 1), 1000).result.applied)
        val paused = SessionReducer.pause(state, 1000)
        assertFalse(AssistantCommandDispatcher.apply(paused, TrainingCommand.COMPLETE_SET, CommandContext.from(paused, 2), 2000).result.applied)
        assertFalse(AssistantCommandDispatcher.apply(state.copy(id="other"), TrainingCommand.PAUSE, CommandContext.from(state, 3), 1000).result.applied)
    }

    @Test fun extensionChangesOnlyActiveRestAndDuplicateIsRejected() {
        val resting = SessionReducer.completeSet(started(), 1000)
        val context = CommandContext.from(resting, 4)
        val extended = AssistantCommandDispatcher.apply(resting, TrainingCommand.EXTEND_REST_30, context, 2000)
        assertEquals(91000L, extended.snapshot.restEndsAtEpochMs)
        assertFalse(AssistantCommandDispatcher.apply(extended.snapshot, TrainingCommand.EXTEND_REST_30, context, 2000).result.applied)
    }
}
