package com.fitflow.training.session

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.PlannedExercise
import com.fitflow.training.plan.WorkoutPlan
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionPersistenceTest {
    @Test fun pausedRestFreezesAndResumesRemainingTime() {
        val plan = WorkoutPlan(LocalDate.parse("2030-05-01"), listOf(PlannedExercise("a", null, "测试", listOf(
            PlannedBlock("one", BigDecimal.ZERO, 1, 8, 60, ""),
            PlannedBlock("two", BigDecimal.ZERO, 1, 8, 60, ""),
        ))))
        val resting = SessionReducer.completeSet(SessionReducer.start(plan, "pause-test"), 1_000L)
        val paused = SessionReducer.pause(resting, 11_000L)
        assertEquals(true, paused.isPaused)
        assertEquals(50_000L, paused.pausedRestRemainingMillis)
        assertEquals(paused, SessionReducer.reconcile(paused, 1_000_000L))
        val resumed = SessionReducer.resume(paused, 21_000L)
        assertEquals(false, resumed.isPaused)
        assertEquals(71_000L, resumed.restEndsAtEpochMs)
    }

    @Test fun pausedWorkoutAllowsEditingUnfinishedSet() = runBlocking {
        val model = SessionViewModel(WorkoutRepository.open(ApplicationProvider.getApplicationContext<Context>()))
        val block = PlannedBlock("paused-block", BigDecimal.ZERO, 1, 8, 60, "")
        model.start(WorkoutPlan(LocalDate.parse("2030-05-02"), listOf(
            PlannedExercise("paused-parent", null, "测试", listOf(block)),
        )))
        model.pause()
        model.editUnfinishedBlock(block.copy(reps = 10, note = "暂停时调整"))
        assertEquals(10, model.session.value?.plan?.exercises?.single()?.blocks?.single()?.reps)
        assertEquals(true, model.session.value?.isPaused)
    }

    @Test fun perSetEditDuringExerciseRestPreservesDeadlineAndCompletedSet() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = SessionViewModel(WorkoutRepository.open(context))
        val plan = WorkoutPlan(LocalDate.parse("2030-02-01"), listOf(
            PlannedExercise("one", null, "第一动作", listOf(PlannedBlock("one-set", BigDecimal("20"), 1, 8, 75, "")), 180),
            PlannedExercise("two", null, "第二动作", listOf(PlannedBlock("two-set", BigDecimal("30"), 1, 8, 60, ""))),
        ))
        model.start(plan)
        model.completeSet()
        val resting = model.session.value!!
        assertEquals(RestKind.EXERCISE, resting.restKind)
        model.editUnfinishedBlock(PlannedBlock("two-set", BigDecimal("35"), 1, 6, 45, "加重"))
        val saved = WorkoutRepository.open(context).loadSession(resting.id)!!
        assertEquals(1, saved.completedBlockSets["one-set"])
        assertEquals(resting.restEndsAtEpochMs, saved.restEndsAtEpochMs)
        assertEquals(BigDecimal("35"), saved.plan.exercises[1].blocks.single().weightKg)
        try {
            model.deleteUnfinishedBlock("two-set")
            throw AssertionError("Active rest target must not be deleted")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun completedSetSurvivesReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = WorkoutRepository.open(context)
        val plan = WorkoutPlan(LocalDate.parse("2026-09-22"), listOf(PlannedExercise("a", null, "测试", listOf(
            PlannedBlock("b", BigDecimal.ZERO, 1, 8, 90, ""),
            PlannedBlock("b-next", BigDecimal.ZERO, 1, 8, 90, ""),
        ))))
        val session = SessionReducer.completeSet(SessionReducer.start(plan, "restore-test"), 1000)
        repository.saveSession(session)
        assertEquals(1, WorkoutRepository.open(context).loadSession("restore-test")?.completedBlockSets?.get("b"))
    }

    @Test fun editingRemainingSetsPreservesCompletedCount() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = SessionViewModel(WorkoutRepository.open(context))
        val plan = WorkoutPlan(LocalDate.parse("2030-01-01"), listOf(
            PlannedExercise("edit-parent", null, "测试", listOf(
                PlannedBlock("edit-done", BigDecimal("20"), 1, 8, 0, ""),
                PlannedBlock("edit-pending", BigDecimal("20"), 1, 8, 0, ""),
            ))
        ))
        model.start(plan)
        model.completeSet()
        model.reconcile()
        try {
            model.editUnfinishedBlock(PlannedBlock("edit-done", BigDecimal("25"), 1, 10, 30, "已完成组"))
            throw AssertionError("Expected rejection")
        } catch (_: IllegalArgumentException) { }
        model.editUnfinishedBlock(PlannedBlock("edit-pending", BigDecimal("25"), 1, 10, 30, "下一组"))
        val saved = WorkoutRepository.open(context).loadSession(model.session.value!!.id)!!
        assertEquals(1, saved.completedBlockSets["edit-done"])
        assertEquals(BigDecimal("25"), saved.plan.exercises.single().blocks[1].weightKg)
        assertEquals(Phase.READY, saved.phase)
    }

    @Test fun deletingNextBlockKeepsFinishedSet() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = SessionViewModel(WorkoutRepository.open(context))
        val plan = WorkoutPlan(LocalDate.parse("2030-01-02"), listOf(
            PlannedExercise("delete-parent", null, "测试", listOf(
                PlannedBlock("done-block", BigDecimal.ZERO, 1, 8, 0, ""),
                PlannedBlock("pending-block", BigDecimal.ZERO, 1, 8, 0, ""),
            ))
        ))
        model.start(plan)
        model.completeSet()
        model.reconcile()
        model.deleteUnfinishedBlock("pending-block")
        val saved = WorkoutRepository.open(context).loadSession(model.session.value!!.id)!!
        assertEquals(1, saved.completedBlockSets["done-block"])
        assertEquals(Phase.FINISHED, saved.phase)
    }

    @Test fun reorderingFutureActionDuringRestKeepsCurrentCursor() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = SessionViewModel(WorkoutRepository.open(context))
        val plan = WorkoutPlan(LocalDate.parse("2030-01-03"), listOf(
            PlannedExercise("first", null, "第一", listOf(
                PlannedBlock("first-block", BigDecimal.ZERO, 1, 8, 0, ""),
                PlannedBlock("first-second", BigDecimal.ZERO, 1, 8, 0, ""),
            )),
            PlannedExercise("second", null, "第二", listOf(PlannedBlock("second-block", BigDecimal.ZERO, 1, 8, 0, ""))),
            PlannedExercise("third", null, "第三", listOf(PlannedBlock("third-block", BigDecimal.ZERO, 1, 8, 0, ""))),
        ))
        model.start(plan)
        model.completeSet()
        model.moveUnfinishedExercise("third", 1)
        assertEquals("first-block", model.session.value?.currentBlockId)
        assertEquals(listOf("first", "third", "second"), model.session.value?.plan?.exercises?.map { it.id })
        model.reconcile()
        model.completeSet()
        model.skipRest()
        assertEquals("third-block", model.session.value?.currentBlockId)
        assertEquals(1, model.session.value?.completedBlockSets?.get("first-block"))
        assertEquals(1, model.session.value?.completedBlockSets?.get("first-second"))
    }

    @Test fun cannotMoveUnstartedActionAheadOfPartiallyCompletedAction() = runBlocking {
        val model = SessionViewModel(WorkoutRepository.open(ApplicationProvider.getApplicationContext<Context>()))
        val plan = WorkoutPlan(LocalDate.parse("2030-03-02"), listOf(
            PlannedExercise("active", null, "正在训练", listOf(
                PlannedBlock("active-1", BigDecimal.ZERO, 1, 8, 60, ""),
                PlannedBlock("active-2", BigDecimal.ZERO, 1, 8, 60, ""),
            )),
            PlannedExercise("future", null, "待练", listOf(
                PlannedBlock("future-1", BigDecimal.ZERO, 1, 8, 60, ""),
            )),
        ))
        model.start(plan)
        model.completeSet()
        try {
            model.moveUnfinishedExercise("future", 0)
            throw AssertionError("Future action should not move ahead of started action")
        } catch (_: IllegalArgumentException) { }
        model.skipRest()
        assertEquals("active-2", model.session.value?.currentBlockId)
    }
}
