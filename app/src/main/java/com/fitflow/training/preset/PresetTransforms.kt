package com.fitflow.training.preset

import com.fitflow.training.plan.PlannedExercise
import com.fitflow.training.plan.WorkoutPlan
import java.util.UUID

object PresetTransforms {
    fun fromPlan(
        slot: Int,
        name: String,
        plan: WorkoutPlan,
        idFactory: () -> String = { UUID.randomUUID().toString() },
    ): TrainingPreset {
        val preset = TrainingPreset(slot, name.trim(), cloneExercises(plan.exercises, idFactory))
        require(PresetRules.validateDraft(preset).valid) { "Invalid preset" }
        return preset
    }

    fun importInto(
        plan: WorkoutPlan,
        preset: TrainingPreset,
        mode: ImportMode,
        idFactory: () -> String = { UUID.randomUUID().toString() },
    ): WorkoutPlan {
        require(PresetRules.validateImport(preset).valid) { "Preset cannot be imported" }
        val copied = cloneExercises(preset.exercises, idFactory)
        return plan.copy(exercises = if (mode == ImportMode.REPLACE) copied else plan.exercises + copied)
    }

    private fun cloneExercises(items: List<PlannedExercise>, idFactory: () -> String) = items.map { exercise ->
        exercise.copy(
            id = idFactory(),
            blocks = exercise.blocks.map { block -> block.copy(id = idFactory(), sets = 1) },
        )
    }
}
