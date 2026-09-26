package com.fitflow.training.plan

import com.fitflow.training.session.Phase
import com.fitflow.training.session.SessionSnapshot

object LegacyPlanNormalizer {
    fun normalize(plan: WorkoutPlan): WorkoutPlan = plan.copy(exercises = plan.exercises.map { exercise ->
        exercise.copy(blocks = exercise.blocks.flatMap { block ->
            if (block.sets == 1) listOf(block)
            else (1..block.sets).map { index -> block.copy(id = "${block.id}:$index", sets = 1) }
        })
    })

    fun normalize(snapshot: SessionSnapshot): SessionSnapshot {
        val oldBlocks = snapshot.plan.exercises.flatMap { it.blocks }
        val converted = normalize(snapshot.plan)
        if (converted == snapshot.plan) return snapshot
        val completed = buildMap {
            oldBlocks.forEach { block ->
                val count = (snapshot.completedBlockSets[block.id] ?: 0).coerceIn(0, block.sets)
                if (block.sets == 1) {
                    if (count > 0) put(block.id, 1)
                } else {
                    (1..count).forEach { index -> put("${block.id}:$index", 1) }
                }
            }
        }
        val oldCurrent = oldBlocks.firstOrNull { it.id == snapshot.currentBlockId }
        val currentId = when {
            oldCurrent == null -> snapshot.currentBlockId
            oldCurrent.sets == 1 -> oldCurrent.id
            snapshot.phase == Phase.RESTING -> {
                val index = (snapshot.completedBlockSets[oldCurrent.id] ?: 1).coerceIn(1, oldCurrent.sets)
                "${oldCurrent.id}:$index"
            }
            else -> {
                val index = ((snapshot.completedBlockSets[oldCurrent.id] ?: 0) + 1).coerceIn(1, oldCurrent.sets)
                "${oldCurrent.id}:$index"
            }
        }
        return snapshot.copy(plan = converted, completedBlockSets = completed, currentBlockId = currentId)
    }
}
