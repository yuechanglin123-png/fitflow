package com.fitflow.training.preset

import com.fitflow.training.plan.PlannedExercise
import kotlinx.serialization.Serializable

@Serializable
data class TrainingPreset(
    val slot: Int,
    val name: String,
    val exercises: List<PlannedExercise>,
)

data class PresetSlot(val slot: Int, val preset: TrainingPreset?)

enum class ImportMode { REPLACE, APPEND }
