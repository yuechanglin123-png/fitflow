package com.fitflow.training.assistant

import com.fitflow.training.session.Phase
import com.fitflow.training.session.SessionSnapshot

object AssistantTrainingClock {
    /** Wall time is used only across process lifetimes; in-process timing is monotonic. */
    fun restore(saved:SessionSnapshot,nowEpochMs:Long):SessionSnapshot {
        val gap=if(saved.lastAccountedEpochMs!=null && !saved.isPaused && saved.phase!=Phase.FINISHED)
            (nowEpochMs-saved.lastAccountedEpochMs).coerceAtLeast(0) else 0
        val elapsed=saved.activeElapsedMs+gap
        return saved.copy(activeElapsedMs=elapsed,lastAccountedEpochMs=nowEpochMs,
            encouragementBucket=maxOf(saved.encouragementBucket,elapsed/600_000))
    }
}
