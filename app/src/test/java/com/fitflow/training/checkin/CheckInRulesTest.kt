package com.fitflow.training.checkin

import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.PlannedExercise
import com.fitflow.training.plan.WorkoutPlan
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckInRulesTest {
    private val date = LocalDate.parse("2026-09-20")
    private val plan = WorkoutPlan(date, listOf(PlannedExercise(
        id = "squat", exerciseId = null, customName = "深蹲",
        blocks = listOf(
            PlannedBlock("first", BigDecimal("50"), 1, 8, 90, "稳住核心"),
            PlannedBlock("second", BigDecimal("55"), 1, 6, 120, ""),
        ),
    )))

    @Test fun progressCountsCompletedGroupsAgainstPlannedGroups() {
        val entry = CheckIn(date, emptyList(), plan, TrainingCategory.STRENGTH, mapOf("first" to 1, "second" to 0))
        assertEquals(1, CheckInRules.completedSets(entry))
        assertEquals(2, CheckInRules.plannedSets(entry))
        assertEquals(50, CheckInRules.completionPercent(entry))
    }

    @Test fun backfillRejectsFutureDatesAndProgressBeyondPlan() {
        val valid = CheckIn(date, emptyList(), plan, TrainingCategory.BODYWEIGHT, mapOf("first" to 1))
        assertTrue(CheckInRules.validateBackfill(valid, LocalDate.parse("2026-09-25")).valid)
        assertFalse(CheckInRules.validateBackfill(valid, LocalDate.parse("2026-09-19")).valid)
        assertFalse(CheckInRules.validateBackfill(valid.copy(completedBlockSets = mapOf("first" to 2)), LocalDate.parse("2026-09-25")).valid)
    }

    @Test fun oldRecordDoesNotInventCompletedGroups() {
        val legacy = CheckIn(date, listOf("squat"), plan)
        assertEquals(null, CheckInRules.completionPercent(legacy))
    }

    @Test fun completedThroughGroupIsStoredPerSmallCard() {
        val groups = CheckInRules.progressFromCompletedGroups(plan, mapOf("squat" to 1))
        assertEquals(mapOf("first" to 1, "second" to 0), groups)
    }
}
