package com.fitflow.training.checkin

import com.fitflow.training.plan.PlanRules
import com.fitflow.training.plan.WorkoutPlan
import java.time.LocalDate

data class CheckInValidation(val errors: List<String>) {
    val valid: Boolean get() = errors.isEmpty()
}

object CheckInRules {
    fun progressFromCompletedGroups(plan: WorkoutPlan, completedGroups: Map<String, Int>): Map<String, Int> =
        plan.exercises.flatMap { exercise ->
            var remaining = (completedGroups[exercise.id] ?: 0).coerceAtLeast(0)
            exercise.blocks.map { block ->
                val completed = remaining.coerceAtMost(block.sets)
                remaining -= completed
                block.id to completed
            }
        }.toMap()

    fun plannedSets(entry: CheckIn): Int = entry.planSnapshot.exercises.sumOf { exercise ->
        exercise.blocks.sumOf { it.sets }
    }

    fun completedSets(entry: CheckIn): Int = entry.completedBlockSets?.values?.sum() ?: 0

    fun completionPercent(entry: CheckIn): Int? {
        if (entry.completedBlockSets == null) return null
        val total = plannedSets(entry)
        return if (total == 0) null else completedSets(entry) * 100 / total
    }

    fun validateBackfill(
        entry: CheckIn,
        today: LocalDate = LocalDate.now(),
        allowUnknownProgress: Boolean = false,
    ): CheckInValidation {
        val errors = mutableListOf<String>()
        if (entry.date.isAfter(today)) errors += "不能补卡未来日期"
        if (entry.planSnapshot.date != entry.date) errors += "计划日期与打卡日期不一致"
        if (entry.planSnapshot.exercises.isEmpty() || entry.planSnapshot.exercises.any { it.blocks.isEmpty() })
            errors += "请添加训练动作与训练小卡"
        if (!PlanRules.validate(entry.planSnapshot).valid) errors += "训练小卡参数无效"
        val progress = entry.completedBlockSets
        if (progress == null && !allowUnknownProgress) errors += "请填写完成组数" else if (progress != null) {
            val planned = entry.planSnapshot.exercises.flatMap { it.blocks }.associate { it.id to it.sets }
            if (progress.any { (id, count) -> id !in planned || count !in 0..(planned[id] ?: 0) })
                errors += "完成组数不能超过计划组数"
        }
        return CheckInValidation(errors)
    }
}
