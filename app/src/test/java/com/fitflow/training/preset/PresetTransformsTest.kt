package com.fitflow.training.preset

import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.PlannedExercise
import com.fitflow.training.plan.WorkoutPlan
import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class PresetTransformsTest {
    private fun exercise(id: String, exerciseId: String, blockId: String) = PlannedExercise(
        id = id,
        exerciseId = exerciseId,
        customName = null,
        blocks = listOf(PlannedBlock(blockId, BigDecimal("20"), 1, 8, 60, "控制速度")),
        exerciseRestSeconds = 120,
    )

    @Test fun appendKeepsDuplicateActionsAndCreatesFreshIds() {
        val today = WorkoutPlan(LocalDate.parse("2030-06-01"), listOf(
            exercise("today-1", "existing", "today-b1"),
            exercise("today-2", "bench", "today-b2"),
        ))
        val preset = TrainingPreset(1, "胸部", listOf(exercise("preset-e", "bench", "preset-b")))
        var counter = 0
        val imported = PresetTransforms.importInto(today, preset, ImportMode.APPEND) { "new-${counter++}" }

        assertEquals(today.date, imported.date)
        assertEquals(listOf("existing", "bench", "bench"), imported.exercises.map { it.exerciseId })
        assertEquals(listOf("today-1", "today-2"), imported.exercises.take(2).map { it.id })
        assertTrue(imported.exercises.drop(2).none { it.id == "preset-e" })
        assertTrue(imported.exercises.drop(2).flatMap { it.blocks }.none { it.id == "preset-b" })
        assertEquals("控制速度", imported.exercises.last().blocks.single().note)
    }

    @Test fun replaceKeepsTargetDateAndNeverMutatesSources() {
        val today = WorkoutPlan(LocalDate.parse("2030-06-02"), listOf(exercise("old", "row", "old-b")))
        val preset = TrainingPreset(2, "背部", listOf(exercise("preset", "pullup", "preset-b")))
        var first = 0
        var second = 100
        val imported1 = PresetTransforms.importInto(today, preset, ImportMode.REPLACE) { "id-${first++}" }
        val imported2 = PresetTransforms.importInto(today, preset, ImportMode.REPLACE) { "id-${second++}" }

        assertEquals(today.date, imported1.date)
        assertEquals(listOf("pullup"), imported1.exercises.map { it.exerciseId })
        assertNotEquals(imported1.exercises.single().id, imported2.exercises.single().id)
        assertEquals("preset", preset.exercises.single().id)
        assertEquals("old", today.exercises.single().id)
    }

    @Test fun savingPlanAsPresetAlsoCreatesIndependentIds() {
        val plan = WorkoutPlan(LocalDate.parse("2030-06-03"), listOf(exercise("plan-e", "squat", "plan-b")))
        var counter = 0
        val preset = PresetTransforms.fromPlan(3, "腿部", plan) { "copy-${counter++}" }
        assertEquals(3, preset.slot)
        assertEquals("腿部", preset.name)
        assertNotEquals("plan-e", preset.exercises.single().id)
        assertNotEquals("plan-b", preset.exercises.single().blocks.single().id)
    }

    @Test fun repeatedImportsNeverReuseGeneratedIds() {
        val preset = TrainingPreset(1, "胸部", listOf(exercise("source-e", "bench", "source-b")))
        val seen = mutableSetOf<String>()
        repeat(100) {
            val imported = PresetTransforms.importInto(
                WorkoutPlan(LocalDate.parse("2030-06-04"), emptyList()),
                preset,
                ImportMode.APPEND,
            )
            imported.exercises.forEach { item ->
                assertTrue(seen.add(item.id))
                item.blocks.forEach { block -> assertTrue(seen.add(block.id)) }
            }
        }
    }

    @Test fun invalidPresetFailsBeforeChangingThePlan() {
        val today = WorkoutPlan(LocalDate.parse("2030-06-05"), listOf(exercise("today", "row", "today-b")))
        val empty = TrainingPreset(2, "空预设", emptyList())
        assertThrows(IllegalArgumentException::class.java) {
            PresetTransforms.importInto(today, empty, ImportMode.REPLACE)
        }
        assertEquals(listOf("today"), today.exercises.map { it.id })
    }
}
