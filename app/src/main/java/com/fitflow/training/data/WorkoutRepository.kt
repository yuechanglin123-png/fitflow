package com.fitflow.training.data

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fitflow.training.checkin.CheckIn
import com.fitflow.training.checkin.CheckInRules
import com.fitflow.training.checkin.TrainingCategory
import com.fitflow.training.plan.PlanRules
import com.fitflow.training.plan.LegacyPlanNormalizer
import com.fitflow.training.session.Phase
import com.fitflow.training.plan.WorkoutPlan
import com.fitflow.training.session.SessionSnapshot
import com.fitflow.training.preset.PresetRules
import com.fitflow.training.preset.PresetSlot
import com.fitflow.training.preset.TrainingPreset
import java.time.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WorkoutRepository(private val database: WorkoutDatabase) {
    suspend fun savePlan(plan: WorkoutPlan) {
        require(PlanRules.validate(plan).valid) { "Invalid workout plan" }
        database.planDao().upsert(StoredPlan(plan.date.toString(), Json.encodeToString(plan)))
    }

    suspend fun loadPlan(date: LocalDate): WorkoutPlan? {
        val old = database.planDao().find(date.toString())?.let { Json.decodeFromString<WorkoutPlan>(it.payload) } ?: return null
        val normalized = LegacyPlanNormalizer.normalize(old)
        if (normalized != old) savePlan(normalized)
        return normalized
    }

    suspend fun saveSession(snapshot: SessionSnapshot) {
        database.sessionDao().upsert(StoredSession(snapshot.id, Json.encodeToString(snapshot)))
    }

    suspend fun loadSession(id: String): SessionSnapshot? =
        database.sessionDao().find(id)?.let { decodeSession(it.payload) }

    suspend fun latestSession(): SessionSnapshot? =
        database.sessionDao().latest()?.let { decodeSession(it.payload) }

    private suspend fun decodeSession(payload: String): SessionSnapshot {
        val old = Json.decodeFromString<SessionSnapshot>(payload)
        val normalized = LegacyPlanNormalizer.normalize(old)
        if (normalized != old) saveSession(normalized)
        return normalized
    }

    suspend fun checkIn(snapshot: SessionSnapshot): CheckIn {
        require(snapshot.phase == Phase.FINISHED) { "Workout must be finished" }
        val ids = snapshot.plan.exercises.filter { item -> item.blocks.any { (snapshot.completedBlockSets[it.id] ?: 0) > 0 } }
            .mapNotNull { it.exerciseId }
        val entry = CheckIn(snapshot.plan.date, ids, snapshot.plan, TrainingCategory.STRENGTH, snapshot.completedBlockSets)
        saveCheckIn(entry)
        return entry
    }

    suspend fun saveBackfill(entry: CheckIn, today: LocalDate = LocalDate.now()) {
        val old = database.checkInDao().find(entry.date.toString())
            ?.let { Json.decodeFromString<CheckIn>(it.payload) }
        val preserveUnknown = old != null && old.completedBlockSets == null && entry.completedBlockSets == null
        val validation = CheckInRules.validateBackfill(entry, today, allowUnknownProgress = preserveUnknown)
        require(validation.valid) { validation.errors.first() }
        saveCheckIn(entry)
    }

    suspend fun saveCheckIn(entry: CheckIn) {
        database.checkInDao().upsert(StoredCheckIn(entry.date.toString(), Json.encodeToString(entry)))
    }

    suspend fun listCheckIns(): List<CheckIn> = database.checkInDao().all().map {
        val entry = Json.decodeFromString<CheckIn>(it.payload)
        entry.copy(planSnapshot = LegacyPlanNormalizer.normalize(entry.planSnapshot))
    }

    suspend fun listPresetSlots(): List<PresetSlot> {
        val stored = database.presetDao().all().associateBy { it.slot }
        return (1..7).map { slot -> PresetSlot(slot, stored[slot]?.let(::decodePreset)) }
    }

    suspend fun loadPreset(slot: Int): TrainingPreset? {
        require(slot in 1..7)
        return database.presetDao().find(slot)?.let(::decodePreset)
    }

    suspend fun savePreset(preset: TrainingPreset) {
        val clean = preset.copy(name = preset.name.trim())
        require(PresetRules.validateDraft(clean).valid) { "Invalid preset" }
        database.presetDao().upsert(StoredPreset(clean.slot, clean.name, Json.encodeToString(clean)))
    }

    suspend fun clearPreset(slot: Int) {
        require(slot in 1..7)
        database.presetDao().delete(slot)
    }

    private fun decodePreset(stored: StoredPreset): TrainingPreset =
        Json.decodeFromString<TrainingPreset>(stored.payload).copy(slot = stored.slot, name = stored.name)

    companion object {
        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `sessions` (`id` TEXT NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`id`))")
            }
        }
        private val migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `checkins` (`date` TEXT NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`date`))")
            }
        }
        internal val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `presets` (`slot` INTEGER NOT NULL, `name` TEXT NOT NULL, `payload` TEXT NOT NULL, PRIMARY KEY(`slot`))")
            }
        }
        fun open(context: Context): WorkoutRepository = WorkoutRepository(
            Room.databaseBuilder(context.applicationContext, WorkoutDatabase::class.java, "fitflow.db")
                .addMigrations(migration1To2, migration2To3, MIGRATION_3_4).build()
        )
    }
}

