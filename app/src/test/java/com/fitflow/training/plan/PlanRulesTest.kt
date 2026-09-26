package com.fitflow.training.plan

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanRulesTest {
    private fun block(
        weight: String = "40",
        sets: Int = 3,
        reps: Int = 8,
        rest: Int = 90,
        note: String = "",
    ) = PlannedBlock("b1", BigDecimal(weight), sets, reps, rest, note)

    @Test fun acceptsBoundaryValues() {
        assertTrue(PlanRules.validate(block(weight = "0", sets = 1, reps = 1, rest = 0)).valid)
        assertTrue(PlanRules.validate(block(weight = "1000", sets = 100, reps = 1000, rest = 3600)).valid)
    }

    @Test fun reportsEachInvalidField() {
        assertEquals(
            setOf("weightKg", "sets", "reps", "restSeconds", "note"),
            PlanRules.validate(block(weight = "-1", sets = 0, reps = 0, rest = -1, note = "x".repeat(501))).errors.keys
        )
    }

    @Test fun deletingAnExerciseKeepsOtherBlocksAndOrder() {
        val first = PlannedExercise("e1", "front_squat", null, listOf(block()))
        val second = PlannedExercise("e2", null, "自定义划船", listOf(block(weight = "20").copy(id = "b2")))
        val plan = WorkoutPlan(LocalDate.of(2026, 9, 22), listOf(first, second))
        assertEquals(listOf("e2"), PlanRules.deleteExercise(plan, "e1").exercises.map { it.id })
        assertEquals("b2", PlanRules.deleteExercise(plan, "e1").exercises.single().blocks.single().id)
        assertEquals(listOf("e2", "e1"), PlanRules.moveExercise(plan, "e2", 0).exercises.map { it.id })
    }

    @Test fun customExerciseNeedsNonblankName() {
        val custom = PlannedExercise("e1", null, "  ", listOf(block()))
        assertFalse(PlanRules.validate(custom).valid)
    }

    @Test fun exerciseRestMustBeBetweenZeroAndOneHour() {
        val exercise = PlannedExercise("e1", null, "测试", listOf(block()))
        assertFalse(PlanRules.validate(exercise.copy(exerciseRestSeconds = -1)).valid)
        assertFalse(PlanRules.validate(exercise.copy(exerciseRestSeconds = 3601)).valid)
        assertTrue(PlanRules.validate(exercise.copy(exerciseRestSeconds = 0)).valid)
    }
}
