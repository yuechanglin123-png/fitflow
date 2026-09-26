package com.fitflow.training.session

import com.fitflow.training.data.WorkoutRepository
import com.fitflow.training.reminder.RestReminder
import com.fitflow.training.plan.PlannedBlock
import com.fitflow.training.plan.PlanRules
import com.fitflow.training.plan.PlannedExercise
import com.fitflow.training.assistant.*
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionViewModel(private val repository: WorkoutRepository, private val reminder: RestReminder? = null,
    private val onRestEnded: ((SessionSnapshot, RestKind?) -> Unit)? = null,
    private val nanoTime:()->Long = { android.os.SystemClock.elapsedRealtimeNanos() },
    private val epochTime:()->Long = { System.currentTimeMillis() }) {
    private val lock = Mutex()
    private val mutableSession = MutableStateFlow<SessionSnapshot?>(null)
    val session = mutableSession.asStateFlow()
    private var lastTickNanos = nanoTime()

    private fun accountTime(current: SessionSnapshot): SessionSnapshot {
        val now = nanoTime()
        val delta = ((now - lastTickNanos) / 1_000_000).coerceAtLeast(0)
        lastTickNanos = now
        return current.copy(lastAccountedEpochMs=epochTime(),activeElapsedMs=current.activeElapsedMs+
            if (!current.isPaused && current.phase != Phase.FINISHED) delta else 0)
    }

    suspend fun tickAssistant(enabled: Boolean): List<ReminderEvent> = lock.withLock {
        val current = mutableSession.value ?: return@withLock emptyList()
        val timed = accountTime(current)
        val evaluation = AssistantReminderPolicy.evaluate(timed, epochTime(), enabled)
        if (evaluation.snapshot != current) {
            repository.saveSession(evaluation.snapshot)
            mutableSession.value = evaluation.snapshot
        }
        evaluation.events
    }

    suspend fun reconcileRestAlarm(id: String, deadline: Long): Pair<SessionSnapshot, RestKind?>? = lock.withLock {
        val current = mutableSession.value ?: repository.latestSession()?.let { restore(it) }
            ?: return@withLock null
        if (current.id != id || current.phase != Phase.RESTING || current.isPaused || current.restEndsAtEpochMs != deadline)
            return@withLock null
        reconcileLocked(current)
    }

    private suspend fun reconcileLocked(current:SessionSnapshot):Pair<SessionSnapshot,RestKind?>? {
        val timed=accountTime(current)
        val next = SessionReducer.reconcile(timed, epochTime())
        if (next == timed) {
            if(timed!=current) { repository.saveSession(timed);mutableSession.value=timed }
            return null
        }
        val updated = next.copy(revision = current.revision + 1)
        repository.saveSession(updated)
        mutableSession.value = updated
        syncReminder(updated)
        if(onRestEnded!=null) onRestEnded.invoke(updated,current.restKind) else reminder?.notifyRestEnd(updated,current.restKind)
        return updated to current.restKind
    }

    suspend fun resume(): Unit = lock.withLock {
        val stored = repository.latestSession()
        if (mutableSession.value?.id == stored?.id) return@withLock
        mutableSession.value = stored?.let { restore(it) }
        mutableSession.value?.let { if(reconcileLocked(it)==null) syncReminder(it) }
    }

    private suspend fun restore(stored:SessionSnapshot):SessionSnapshot {
        lastTickNanos=nanoTime()
        val recovered=AssistantTrainingClock.restore(stored,epochTime())
        repository.saveSession(recovered)
        mutableSession.value=recovered
        return recovered
    }

    suspend fun start(plan: com.fitflow.training.plan.WorkoutPlan) = lock.withLock {
        lastTickNanos = nanoTime()
        repository.latestSession()?.let { reminder?.cancel(it.id) }
        val next = SessionReducer.start(plan, UUID.randomUUID().toString()).copy(lastAccountedEpochMs=epochTime())
        repository.saveSession(next)
        mutableSession.value = next
        syncReminder(next)
    }

    suspend fun completeSet() = mutate { SessionReducer.completeSet(it, epochTime()) }
    suspend fun skipRest() = mutate { SessionReducer.skipRest(it) }
    suspend fun extendRest(seconds: Int) = mutate { SessionReducer.extendRest(it, seconds) }
    suspend fun reconcile() = lock.withLock { mutableSession.value?.let { reconcileLocked(it) }; Unit }
    suspend fun pause() = mutate { SessionReducer.pause(it, epochTime()) }
    suspend fun resumeTraining() = mutate { SessionReducer.resume(it, epochTime()) }

    suspend fun executeAssistant(command: TrainingCommand, context: CommandContext): CommandResult = lock.withLock {
        val original = mutableSession.value ?: return@withLock CommandResult(false, "尚未开始训练")
        val current = accountTime(original)
        val transition = AssistantCommandDispatcher.apply(current, command, context, epochTime())
        if (transition.snapshot != original) {
            repository.saveSession(transition.snapshot)
            mutableSession.value = transition.snapshot
            if (transition.result.applied) syncReminder(transition.snapshot)
        }
        transition.result
    }

    suspend fun editUnfinishedBlock(block: PlannedBlock) = mutate { snapshot ->
        val original = snapshot.plan.exercises.flatMap { it.blocks }.firstOrNull { it.id == block.id } ?: error("Block missing")
        val completed = snapshot.completedBlockSets[block.id] ?: 0
        require(completed == 0 && original.sets == 1 && block.sets == 1) { "Completed set cannot be edited" }
        require(PlanRules.validate(block).valid) { "Invalid block" }
        val updated = snapshot.plan.copy(exercises = snapshot.plan.exercises.map { exercise ->
            exercise.copy(blocks = exercise.blocks.map { if (it.id == block.id) block else it })
        })
        snapshot.copy(plan = updated)
    }

    suspend fun editExerciseRest(parentId: String, seconds: Int) = mutate { snapshot ->
        require(!snapshot.isPaused) { "Paused workout cannot be edited" }
        require(seconds in 0..3600) { "Exercise rest must be between 0 and 3600 seconds" }
        val exercise = snapshot.plan.exercises.firstOrNull { it.id == parentId } ?: error("Exercise missing")
        require(exercise.blocks.any { (snapshot.completedBlockSets[it.id] ?: 0) == 0 }) {
            "Completed exercise rest cannot be edited"
        }
        snapshot.copy(plan = snapshot.plan.copy(exercises = snapshot.plan.exercises.map {
            if (it.id == parentId) it.copy(exerciseRestSeconds = seconds) else it
        }))
    }

    suspend fun moveUnfinishedExercise(parentId: String, toIndex: Int) = mutate { snapshot ->
        require(!snapshot.isPaused) { "Paused workout cannot be edited" }
        val exercise = snapshot.plan.exercises.firstOrNull { it.id == parentId } ?: error("Exercise missing")
        require(exercise.blocks.all { (snapshot.completedBlockSets[it.id] ?: 0) == 0 }) { "Completed exercise cannot be moved" }
        val lastStartedIndex = snapshot.plan.exercises.indexOfLast { item ->
            item.blocks.any { (snapshot.completedBlockSets[it.id] ?: 0) > 0 }
        }
        require(toIndex > lastStartedIndex) { "Future exercise cannot move ahead of started exercise" }
        snapshot.copy(plan = PlanRules.moveExercise(snapshot.plan, parentId, toIndex))
    }

    suspend fun deleteUnfinishedBlock(id: String) = mutate { snapshot ->
        require(!snapshot.isPaused) { "Paused workout cannot be edited" }
        require((snapshot.completedBlockSets[id] ?: 0) == 0) { "Completed sets cannot be deleted" }
        val nextRestTarget = if (snapshot.phase == Phase.RESTING) snapshot.plan.exercises
            .flatMap { it.blocks }.firstOrNull { (snapshot.completedBlockSets[it.id] ?: 0) == 0 }?.id else null
        require(id != nextRestTarget) { "Cannot remove active rest target" }
        val updated = snapshot.plan.copy(exercises = snapshot.plan.exercises.map { exercise ->
            exercise.copy(blocks = exercise.blocks.filterNot { it.id == id })
        })
        val next = updated.exercises.flatMap { it.blocks }.firstOrNull { (snapshot.completedBlockSets[it.id] ?: 0) < it.sets }
        snapshot.copy(plan = updated, currentBlockId = next?.id, phase = if (next == null) Phase.FINISHED else snapshot.phase)
    }

    private fun syncReminder(snapshot: SessionSnapshot) {
        reminder ?: return
        reminder.cancel(snapshot.id)
        if (snapshot.phase == Phase.RESTING && !snapshot.isPaused) {
            snapshot.restEndsAtEpochMs?.let { reminder.schedule(it, snapshot.id) }
        }
    }

    private suspend fun mutate(transform: (SessionSnapshot) -> SessionSnapshot) = lock.withLock {
        val original = mutableSession.value ?: return@withLock
        val current = accountTime(original)
        val transformed = transform(current)
        if (transformed != original) {
            val changed = transformed != current
            val next = if (changed) transformed.copy(revision = current.revision + 1) else transformed
            repository.saveSession(next)
            mutableSession.value = next
            if (changed) syncReminder(next)
        }
    }
}
