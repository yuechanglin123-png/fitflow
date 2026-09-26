package com.fitflow.training.session

import com.fitflow.training.plan.WorkoutPlan
import kotlinx.serialization.Serializable

@Serializable
enum class Phase { READY, RESTING, FINISHED }

@Serializable
enum class RestKind { SET, EXERCISE }

@Serializable
data class SessionSnapshot(
    val id: String,
    val plan: WorkoutPlan,
    val completedBlockSets: Map<String, Int>,
    val phase: Phase,
    val currentBlockId: String?,
    val restEndsAtEpochMs: Long?,
    val restReady: Boolean = false,
    val restKind: RestKind? = null,
    val isPaused: Boolean = false,
    val pausedRestRemainingMillis: Long? = null,
    val revision: Long = 0,
    val activeElapsedMs: Long = 0,
    val lastAccountedEpochMs: Long? = null,
    val encouragementBucket: Long = 0,
    val restPreparationAnnounced: Boolean = false,
)
