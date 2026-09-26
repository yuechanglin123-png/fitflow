package com.fitflow.training.session

import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.PlannedExercise
import com.fitflow.training.plan.WorkoutPlan
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionReducerTest {
    private val plan = WorkoutPlan(LocalDate.parse("2026-09-22"), listOf(
        PlannedExercise("a", "bench", null, listOf(PlannedBlock("b", BigDecimal("40"), 2, 8, 90, ""))),
        PlannedExercise("c", "row", null, listOf(PlannedBlock("d", BigDecimal("30"), 1, 10, 0, ""))),
    ))

    @Test fun everySetRestsAndAutomaticallyAdvances() {
        val started = SessionReducer.start(plan, "session")
        val first = SessionReducer.completeSet(started, 1000)
        assertEquals(Phase.RESTING, first.phase)
        assertEquals(1, first.completedBlockSets["b"])
        assertEquals(first, SessionReducer.completeSet(first, 2000))
        val ready = SessionReducer.reconcile(first, 91000)
        assertEquals(Phase.READY, ready.phase)
        val next = ready
        assertEquals(Phase.READY, next.phase)
        val second = SessionReducer.completeSet(next, 92000)
        assertEquals(Phase.RESTING, second.phase)
        assertEquals(RestKind.EXERCISE, second.restKind)
        val nextBlock = SessionReducer.reconcile(second, 212000)
        assertEquals("d", nextBlock.currentBlockId)
        val finalSet = SessionReducer.completeSet(nextBlock, 213000)
        assertEquals(Phase.FINISHED, finalSet.phase)
    }

    @Test fun exerciseBoundaryUsesItsOwnRestAndFinalSetFinishesImmediately() {
        val perSet = WorkoutPlan(LocalDate.parse("2026-09-22"), listOf(
            PlannedExercise("a", "bench", null, listOf(
                PlannedBlock("a1", BigDecimal("40"), 1, 8, 30, ""),
                PlannedBlock("a2", BigDecimal("45"), 1, 6, 45, ""),
            ), exerciseRestSeconds = 120),
            PlannedExercise("b", "row", null, listOf(
                PlannedBlock("b1", BigDecimal("30"), 1, 10, 90, ""),
            )),
        ))
        val first = SessionReducer.completeSet(SessionReducer.start(perSet, "new"), 1_000)
        assertEquals(RestKind.SET, first.restKind)
        assertEquals(31_000L, first.restEndsAtEpochMs)
        val second = SessionReducer.completeSet(SessionReducer.reconcile(first, 31_000), 100_000)
        assertEquals(RestKind.EXERCISE, second.restKind)
        assertEquals(220_000L, second.restEndsAtEpochMs)
        val last = SessionReducer.completeSet(SessionReducer.reconcile(second, 220_000), 230_000)
        assertEquals(Phase.FINISHED, last.phase)
        assertEquals(null, last.restEndsAtEpochMs)
    }

    @Test fun zeroRestCanBeReconciledImmediatelyAndExtendDelaysAdvance() {
        val zeroPlan = plan.copy(exercises = plan.exercises.mapIndexed { index, exercise ->
            if (index == 0) exercise.copy(blocks = exercise.blocks.map { it.copy(restSeconds = 0) }) else exercise
        })
        val first = SessionReducer.completeSet(SessionReducer.start(zeroPlan, "zero"), 1_000)
        assertEquals(Phase.READY, SessionReducer.reconcile(first, 1_000).phase)
        val extended = SessionReducer.extendRest(first, 30)
        assertEquals(Phase.RESTING, SessionReducer.reconcile(extended, 1_000).phase)
        assertEquals(Phase.READY, SessionReducer.reconcile(extended, 31_000).phase)
        assertEquals(Phase.READY, SessionReducer.skipRest(first).phase)
    }

    @Test fun zeroExerciseRestAdvancesToNextAction() {
        val twoActions = WorkoutPlan(LocalDate.parse("2026-09-22"), listOf(
            PlannedExercise("first", null, "第一个", listOf(PlannedBlock("first-set", BigDecimal.ZERO, 1, 8, 90, "")), 0),
            PlannedExercise("next", null, "第二个", listOf(PlannedBlock("next-set", BigDecimal.ZERO, 1, 8, 90, ""))),
        ))
        val rest = SessionReducer.completeSet(SessionReducer.start(twoActions, "zero-action"), 1_000)
        assertEquals(RestKind.EXERCISE, rest.restKind)
        assertEquals(1_000L, rest.restEndsAtEpochMs)
        val next = SessionReducer.reconcile(rest, 1_000)
        assertEquals(Phase.READY, next.phase)
        assertEquals("next-set", next.currentBlockId)
    }
}
