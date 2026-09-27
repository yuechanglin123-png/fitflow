package com.fitflow.training.session

import com.fitflow.training.plan.WorkoutPlan

object SessionReducer {
    fun start(plan: WorkoutPlan, sessionId: String): SessionSnapshot {
        require(sessionId.isNotBlank())
        val first = plan.exercises.flatMap { it.blocks }.firstOrNull()
        require(first != null) { "Workout needs at least one planned block" }
        return SessionSnapshot(sessionId, plan, emptyMap(), Phase.READY, first.id, null)
    }

    fun completeSet(snapshot: SessionSnapshot, nowMs: Long): SessionSnapshot {
        if (snapshot.phase != Phase.READY || snapshot.isPaused) return snapshot
        val exercise = snapshot.plan.exercises.firstOrNull { item -> item.blocks.any { it.id == snapshot.currentBlockId } } ?: return snapshot
        val block = exercise.blocks.first { it.id == snapshot.currentBlockId }
        val completed = (snapshot.completedBlockSets[block.id] ?: 0).coerceIn(0, block.sets)
        if (completed >= block.sets) return snapshot
        val updatedCounts = snapshot.completedBlockSets + (block.id to completed + 1)
        val nextExercise = snapshot.plan.exercises.firstOrNull { item ->
            item.blocks.any { (updatedCounts[it.id] ?: 0) < it.sets }
        }
        if (nextExercise == null) return snapshot.copy(
            completedBlockSets = updatedCounts,
            phase = Phase.FINISHED,
            currentBlockId = null,
            restEndsAtEpochMs = null,
            restKind = null,
        )
        val sameExercise = nextExercise.id == exercise.id
        val seconds = if (sameExercise) block.restSeconds else exercise.exerciseRestSeconds
        return snapshot.copy(
            completedBlockSets = updatedCounts,
            phase = Phase.RESTING,
            restEndsAtEpochMs = nowMs + seconds * 1000L,
            restReady = false,
            restKind = if (sameExercise) RestKind.SET else RestKind.EXERCISE,
            restPreparationAnnounced = false,
        )
    }

    fun reconcile(snapshot: SessionSnapshot, nowMs: Long): SessionSnapshot =
        if (!snapshot.isPaused && snapshot.phase == Phase.RESTING && nowMs >= (snapshot.restEndsAtEpochMs ?: Long.MAX_VALUE))
            advance(snapshot)
        else snapshot

    private fun advance(snapshot: SessionSnapshot): SessionSnapshot {
        if (snapshot.phase != Phase.RESTING) return snapshot
        val next = snapshot.plan.exercises.flatMap { it.blocks }
            .firstOrNull { (snapshot.completedBlockSets[it.id] ?: 0) < it.sets }
        return snapshot.copy(
            phase = if (next == null) Phase.FINISHED else Phase.READY,
            currentBlockId = next?.id,
            restEndsAtEpochMs = null,
            restReady = false,
            restKind = null,
        )
    }

    fun skipRest(snapshot: SessionSnapshot): SessionSnapshot = if (snapshot.isPaused) snapshot else advance(snapshot)

    fun extendRest(snapshot: SessionSnapshot, seconds: Int): SessionSnapshot {
        require(seconds > 0)
        if (snapshot.phase != Phase.RESTING || snapshot.isPaused) return snapshot
        return snapshot.copy(restEndsAtEpochMs = (snapshot.restEndsAtEpochMs ?: 0) + seconds * 1000L, restReady = false,
            restPreparationAnnounced = false)
    }

    fun finishEarly(snapshot: SessionSnapshot): SessionSnapshot = snapshot.copy(
        phase = Phase.FINISHED,
        currentBlockId = null,
        restEndsAtEpochMs = null,
        restReady = false,
        restKind = null,
        isPaused = false,
        pausedRestRemainingMillis = null,
        restPreparationAnnounced = false,
    )

    fun pause(snapshot: SessionSnapshot, nowMs: Long): SessionSnapshot {
        if (snapshot.phase == Phase.FINISHED || snapshot.isPaused) return snapshot
        val remaining = if (snapshot.phase == Phase.RESTING)
            ((snapshot.restEndsAtEpochMs ?: nowMs) - nowMs).coerceAtLeast(0L)
        else null
        return snapshot.copy(isPaused = true, restEndsAtEpochMs = null, pausedRestRemainingMillis = remaining)
    }

    fun resume(snapshot: SessionSnapshot, nowMs: Long): SessionSnapshot {
        if (!snapshot.isPaused) return snapshot
        val deadline = if (snapshot.phase == Phase.RESTING)
            nowMs + (snapshot.pausedRestRemainingMillis ?: 0L)
        else snapshot.restEndsAtEpochMs
        return snapshot.copy(isPaused = false, restEndsAtEpochMs = deadline, pausedRestRemainingMillis = null)
    }
}
