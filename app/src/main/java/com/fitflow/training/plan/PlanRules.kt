package com.fitflow.training.plan

import java.math.BigDecimal

data class ValidationResult(val errors: Map<String, String>) {
    val valid: Boolean get() = errors.isEmpty()
}

object PlanRules {
    fun validate(block: PlannedBlock): ValidationResult {
        val errors = mutableMapOf<String, String>()
        if (block.id.isBlank()) errors["id"] = "缺少卡片编号"
        if (block.weightKg < BigDecimal.ZERO || block.weightKg > BigDecimal("1000"))
            errors["weightKg"] = "重量需在 0–1000 kg 之间"
        if (block.sets !in 1..100) errors["sets"] = "组数需在 1–100 之间"
        if (block.reps !in 1..1000) errors["reps"] = "次数需在 1–1000 之间"
        if (block.restSeconds !in 0..3600) errors["restSeconds"] = "休息需在 0–3600 秒之间"
        if (block.note.length > 500) errors["note"] = "备注最多 500 字"
        return ValidationResult(errors)
    }

    fun validate(exercise: PlannedExercise): ValidationResult {
        val errors = mutableMapOf<String, String>()
        if (exercise.id.isBlank()) errors["id"] = "缺少动作编号"
        if (exercise.exerciseId.isNullOrBlank() && exercise.customName.isNullOrBlank())
            errors["customName"] = "请输入自定义动作名称"
        if (exercise.exerciseId != null && exercise.customName != null)
            errors["exerciseId"] = "动作只能来自库或自定义名称"
        if (exercise.blocks.map { it.id }.distinct().size != exercise.blocks.size)
            errors["blocks"] = "卡片编号重复"
        if (exercise.exerciseRestSeconds !in 0..3600)
            errors["exerciseRestSeconds"] = "动作间休息需在 0–3600 秒之间"
        exercise.blocks.forEach { errors.putAll(validate(it).errors) }
        return ValidationResult(errors)
    }

    fun validate(plan: WorkoutPlan): ValidationResult {
        val errors = mutableMapOf<String, String>()
        if (plan.exercises.map { it.id }.distinct().size != plan.exercises.size)
            errors["exercises"] = "动作编号重复"
        plan.exercises.forEach { errors.putAll(validate(it).errors) }
        return ValidationResult(errors)
    }

    fun deleteExercise(plan: WorkoutPlan, id: String): WorkoutPlan =
        plan.copy(exercises = plan.exercises.filterNot { it.id == id })

    fun moveExercise(plan: WorkoutPlan, id: String, toIndex: Int): WorkoutPlan {
        val current = plan.exercises.indexOfFirst { it.id == id }
        if (current < 0 || toIndex !in plan.exercises.indices) return plan
        val reordered = plan.exercises.toMutableList()
        val selected = reordered.removeAt(current)
        reordered.add(toIndex, selected)
        return plan.copy(exercises = reordered)
    }
}
