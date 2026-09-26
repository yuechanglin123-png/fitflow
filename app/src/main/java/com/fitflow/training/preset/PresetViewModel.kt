package com.fitflow.training.preset

import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.plan.PlanRules
import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.PlannedExercise
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class PresetViewModel(private val repository: WorkoutRepository, private val slot: Int) {
    init { require(slot in 1..7) }

    private val mutablePreset = MutableStateFlow(TrainingPreset(slot, "预设 $slot", emptyList()))
    val preset = mutablePreset.asStateFlow()

    suspend fun load() {
        mutablePreset.value = repository.loadPreset(slot) ?: TrainingPreset(slot, "预设 $slot", emptyList())
    }

    suspend fun rename(name: String) = update(preset.value.copy(name = name.trim()))

    suspend fun addExercise(exerciseId: String): String {
        require(exerciseId.isNotBlank())
        val id = UUID.randomUUID().toString()
        update(preset.value.copy(exercises = preset.value.exercises + PlannedExercise(id, exerciseId, null, emptyList())))
        return id
    }

    suspend fun addCustomExercise(name: String): String {
        val clean = name.trim()
        require(clean.isNotBlank())
        val id = UUID.randomUUID().toString()
        update(preset.value.copy(exercises = preset.value.exercises + PlannedExercise(id, null, clean, emptyList())))
        return id
    }

    suspend fun addBlock(parentId: String, block: PlannedBlock) = changeExercise(parentId) { parent ->
        require(PlanRules.validate(block).valid)
        val groups = List(block.sets) { block.copy(id = UUID.randomUUID().toString(), sets = 1) }
        parent.copy(blocks = parent.blocks + groups)
    }

    suspend fun editBlock(parentId: String, block: PlannedBlock) = changeExercise(parentId) { parent ->
        require(block.sets == 1 && PlanRules.validate(block).valid)
        require(parent.blocks.any { it.id == block.id })
        parent.copy(blocks = parent.blocks.map { if (it.id == block.id) block else it })
    }

    suspend fun deleteBlock(parentId: String, blockId: String) = changeExercise(parentId) { parent ->
        parent.copy(blocks = parent.blocks.filterNot { it.id == blockId })
    }

    suspend fun editExerciseRest(parentId: String, seconds: Int) = changeExercise(parentId) { parent ->
        require(seconds in 0..3600)
        parent.copy(exerciseRestSeconds = seconds)
    }

    suspend fun deleteExercise(parentId: String) = update(
        preset.value.copy(exercises = preset.value.exercises.filterNot { it.id == parentId }),
    )

    suspend fun moveExercise(parentId: String, toIndex: Int) {
        val items = preset.value.exercises.toMutableList()
        val current = items.indexOfFirst { it.id == parentId }
        if (current < 0 || toIndex !in items.indices) return
        val item = items.removeAt(current)
        items.add(toIndex, item)
        update(preset.value.copy(exercises = items))
    }

    private suspend fun changeExercise(id: String, transform: (PlannedExercise) -> PlannedExercise) {
        require(preset.value.exercises.any { it.id == id })
        update(preset.value.copy(exercises = preset.value.exercises.map { if (it.id == id) transform(it) else it }))
    }

    private suspend fun update(next: TrainingPreset) {
        val clean = next.copy(name = next.name.trim())
        require(PresetRules.validateDraft(clean).valid) { "Invalid preset" }
        repository.savePreset(clean)
        mutablePreset.value = clean
    }
}
