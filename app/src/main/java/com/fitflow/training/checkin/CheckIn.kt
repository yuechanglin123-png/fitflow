package com.fitflow.training.checkin

import com.fitflow.training.plan.DateSerializer
import com.fitflow.training.plan.WorkoutPlan
import java.time.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class CheckIn(
    @Serializable(with = DateSerializer::class) val date: LocalDate,
    val completedExerciseIds: List<String>,
    val planSnapshot: WorkoutPlan,
    val category: TrainingCategory = TrainingCategory.STRENGTH,
    val completedBlockSets: Map<String, Int>? = null,
)

@Serializable
enum class TrainingCategory(val label: String) {
    STRENGTH("力量训练"),
    BODYWEIGHT("徒手训练"),
    AEROBIC("有氧运动"),
}
