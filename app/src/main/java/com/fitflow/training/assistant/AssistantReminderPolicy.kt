package com.fitflow.training.assistant

import com.fitflow.training.session.Phase
import com.fitflow.training.session.SessionSnapshot

data class ReminderEvent(val id: String, val sessionId: String, val text: String, val priority: Int, val expiresAtMs: Long)
data class ReminderEvaluation(val snapshot: SessionSnapshot, val events: List<ReminderEvent>)

object AssistantReminderPolicy {
    fun evaluate(snapshot: SessionSnapshot, nowMs: Long, enabled: Boolean): ReminderEvaluation {
        val events = mutableListOf<ReminderEvent>()
        var next = snapshot
        if (snapshot.phase == Phase.FINISHED) return ReminderEvaluation(next, events)
        val bucket = snapshot.activeElapsedMs / 600_000
        val encourage = bucket > snapshot.encouragementBucket
        if (encourage) next = next.copy(encouragementBucket = bucket)
        if (!enabled || snapshot.isPaused) return ReminderEvaluation(next, events)
        val remaining = (snapshot.restEndsAtEpochMs ?: Long.MIN_VALUE).let {
            if (it == Long.MIN_VALUE) 0 else it - nowMs
        }
        if (snapshot.phase == Phase.RESTING && remaining in 1..10_000 && !snapshot.restPreparationAnnounced) {
            next = next.copy(restPreparationAnnounced = true)
            events += ReminderEvent("rest:${snapshot.id}:${snapshot.restEndsAtEpochMs}", snapshot.id,
                "休息马上结束，请进入准备状态", 2, snapshot.restEndsAtEpochMs!!)
        }
        if (encourage) events += ReminderEvent("encourage:${snapshot.id}:$bucket", snapshot.id,
            "保持自己的节奏，你做得很好，继续加油", 1, nowMs + 30_000)
        return ReminderEvaluation(next, events)
    }
}
