package com.fitflow.training.preset

import com.fitflow.training.plan.PlanRules
import com.fitflow.training.plan.ValidationResult

object PresetRules {
    fun validateDraft(preset: TrainingPreset): ValidationResult {
        val errors = mutableMapOf<String, String>()
        if (preset.slot !in 1..7) errors["slot"] = "预设槽位需在 1–7 之间"
        val cleanName = preset.name.trim()
        if (cleanName.isBlank()) errors["name"] = "请输入预设名称"
        if (cleanName.length > 30) errors["name"] = "预设名称最多 30 字"
        if (preset.exercises.map { it.id }.distinct().size != preset.exercises.size)
            errors["exercises"] = "动作编号重复"
        preset.exercises.forEach { exercise -> errors.putAll(PlanRules.validate(exercise).errors) }
        return ValidationResult(errors)
    }

    fun validateImport(preset: TrainingPreset): ValidationResult {
        val errors = validateDraft(preset).errors.toMutableMap()
        if (preset.exercises.isEmpty()) errors["exercises"] = "预设至少需要一个动作"
        if (preset.exercises.any { it.blocks.isEmpty() }) errors["blocks"] = "每个动作至少需要一组"
        return ValidationResult(errors)
    }
}
