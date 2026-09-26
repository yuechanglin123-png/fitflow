package com.fitflow.training.checkin

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.plan.WorkoutPlan
import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.PlannedExercise
import com.fitflow.training.session.Phase
import com.fitflow.training.session.SessionSnapshot
import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CheckInTest {
    @Test fun oneDateHasOneRecord() = runBlocking {
        val repo = WorkoutRepository.open(ApplicationProvider.getApplicationContext<Context>())
        val date = LocalDate.parse("2026-09-22")
        val entry = CheckIn(date, listOf("bench"), WorkoutPlan(date, emptyList()))
        repo.saveCheckIn(entry)
        repo.saveCheckIn(entry)
        assertEquals(1, repo.listCheckIns().count { it.date == date })
    }

    @Test fun finishedSessionSavesCategoryAndCompletedGroups() = runBlocking {
        val repo = WorkoutRepository.open(ApplicationProvider.getApplicationContext<Context>())
        val date = LocalDate.parse("2026-09-21")
        val plan = WorkoutPlan(date, listOf(PlannedExercise("squat", null, "深蹲", listOf(
            PlannedBlock("set-a", BigDecimal("60"), 1, 8, 90, ""),
            PlannedBlock("set-b", BigDecimal("60"), 1, 8, 90, ""),
        ))))
        val session = SessionSnapshot("checkin-test", plan, mapOf("set-a" to 1), Phase.FINISHED, null, null)
        repo.checkIn(session)
        val saved = repo.listCheckIns().first { it.date == date }
        assertEquals(TrainingCategory.STRENGTH, saved.category)
        assertEquals(1, CheckInRules.completedSets(saved))
        assertEquals(50, CheckInRules.completionPercent(saved))
    }

    @Test fun historicalBackfillPersistsBodyweightPlanAndProgress() = runBlocking {
        val repo = WorkoutRepository.open(ApplicationProvider.getApplicationContext<Context>())
        val date = LocalDate.parse("2026-09-18")
        val plan = WorkoutPlan(date, listOf(PlannedExercise("pullup", null, "引体向上", listOf(
            PlannedBlock("set-1", BigDecimal.ZERO, 1, 6, 60, "慢速下放"),
        ))))
        repo.saveBackfill(CheckIn(date, emptyList(), plan, TrainingCategory.BODYWEIGHT, mapOf("set-1" to 1)))
        val saved = repo.listCheckIns().first { it.date == date }
        assertEquals(TrainingCategory.BODYWEIGHT, saved.category)
        assertEquals("慢速下放", saved.planSnapshot.exercises.single().blocks.single().note)
        assertEquals(100, CheckInRules.completionPercent(saved))
    }
}
