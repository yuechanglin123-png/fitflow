package com.fitflow.training.plan

import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.preset.ImportMode
import com.fitflow.training.preset.PresetRules
import com.fitflow.training.preset.PresetSlot
import com.fitflow.training.preset.PresetTransforms
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlanViewModel(private val repository: WorkoutRepository, private val date: LocalDate = LocalDate.now()) {
    private val mutablePlan = MutableStateFlow(WorkoutPlan(date, emptyList()))
    val plan = mutablePlan.asStateFlow()
    private val importMutex = Mutex()

    suspend fun load() { mutablePlan.value = repository.loadPlan(date) ?: WorkoutPlan(date, emptyList()) }

    suspend fun listPresetSlots(): List<PresetSlot> = repository.listPresetSlots()

    suspend fun saveAsPreset(slot: Int, name: String) {
        val preset = PresetTransforms.fromPlan(slot, name, plan.value)
        require(PresetRules.validateImport(preset).valid) { "Only a complete plan can be saved as a preset" }
        repository.savePreset(preset)
    }

    suspend fun importPreset(slot: Int, mode: ImportMode) = importMutex.withLock {
        val preset = requireNotNull(repository.loadPreset(slot)) { "Preset slot is empty" }
        require(PresetRules.validateImport(preset).valid) { "Preset cannot be imported" }
        update(PresetTransforms.importInto(mutablePlan.value, preset, mode))
    }

    private suspend fun update(next: WorkoutPlan) {
        require(PlanRules.validate(next).valid) { "Invalid plan" }
        repository.savePlan(next)
        mutablePlan.value = next
    }

    suspend fun addExercise(exerciseId: String): String {
        require(exerciseId.isNotBlank())
        val id = UUID.randomUUID().toString()
        update(plan.value.copy(exercises = plan.value.exercises + PlannedExercise(id, exerciseId, null, emptyList())))
        return id
    }

    suspend fun addCustomExercise(name: String) {
        val clean = name.trim()
        require(clean.isNotBlank())
        update(plan.value.copy(exercises = plan.value.exercises + PlannedExercise(UUID.randomUUID().toString(), null, clean, emptyList())))
    }

    suspend fun addBlock(parentId: String, block: PlannedBlock) = changeExercise(parentId) { exercise ->
        require(PlanRules.validate(block).valid)
        val groups = List(block.sets) { block.copy(id = UUID.randomUUID().toString(), sets = 1) }
        exercise.copy(blocks = exercise.blocks + groups)
    }
    suspend fun editBlock(parentId: String, block: PlannedBlock) = changeExercise(parentId) { parent ->
        require(block.sets == 1) { "Each card represents one set" }
        require(parent.blocks.any { it.id == block.id })
        parent.copy(blocks = parent.blocks.map { if (it.id == block.id) block else it })
    }
    suspend fun editExerciseRest(parentId: String, seconds: Int) = changeExercise(parentId) { parent ->
        require(seconds in 0..3600)
        parent.copy(exerciseRestSeconds = seconds)
    }
    suspend fun deleteBlock(parentId: String, blockId: String) = changeExercise(parentId) { it.copy(blocks = it.blocks.filterNot { b -> b.id == blockId }) }
    suspend fun deleteExercise(parentId: String) = update(PlanRules.deleteExercise(plan.value, parentId))
    suspend fun moveExercise(parentId: String, toIndex: Int) = update(PlanRules.moveExercise(plan.value, parentId, toIndex))
    fun canStart(): Boolean = plan.value.exercises.any { it.blocks.isNotEmpty() } &&
        PlanRules.validate(plan.value).valid
    fun startWorkout(): WorkoutPlan {
        check(canStart()) { "Add a valid block before starting" }
        return plan.value
    }

    private suspend fun changeExercise(id: String, transform: (PlannedExercise) -> PlannedExercise) {
        require(plan.value.exercises.any { it.id == id })
        update(plan.value.copy(exercises = plan.value.exercises.map { if (it.id == id) transform(it) else it }))
    }
}

