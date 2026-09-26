package com.fitflow.training.plan

import com.fitflow.training.session.Phase
import com.fitflow.training.session.SessionSnapshot
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyPlanNormalizerTest {
    private val oldPlan = WorkoutPlan(LocalDate.parse("2026-09-22"), listOf(
        PlannedExercise("action", null, "测试动作", listOf(
            PlannedBlock("warm", BigDecimal("20"), 2, 8, 30, "热身"),
            PlannedBlock("heavy", BigDecimal("40"), 3, 5, 90, "正式"),
        ))
    ))

    @Test fun expandsLegacyBlocksInOrderAndIsIdempotent() {
        val converted = LegacyPlanNormalizer.normalize(oldPlan)
        assertEquals(listOf("warm:1", "warm:2", "heavy:1", "heavy:2", "heavy:3"),
            converted.exercises.single().blocks.map { it.id })
        assertEquals(listOf("热身", "热身", "正式", "正式", "正式"),
            converted.exercises.single().blocks.map { it.note })
        assertEquals(listOf(1, 1, 1, 1, 1), converted.exercises.single().blocks.map { it.sets })
        assertEquals(converted, LegacyPlanNormalizer.normalize(converted))
    }

    @Test fun preservesPartialProgressAndRestDeadline() {
        val old = SessionSnapshot("s", oldPlan, mapOf("warm" to 1), Phase.RESTING, "warm", 123456L)
        val converted = LegacyPlanNormalizer.normalize(old)
        assertEquals(1, converted.completedBlockSets["warm:1"])
        assertEquals(null, converted.completedBlockSets["warm:2"])
        assertEquals("warm:1", converted.currentBlockId)
        assertEquals(123456L, converted.restEndsAtEpochMs)
        assertEquals(converted, LegacyPlanNormalizer.normalize(converted))
    }
}
