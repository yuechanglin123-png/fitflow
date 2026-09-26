package com.fitflow.training.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "plans")
data class StoredPlan(@PrimaryKey val date: String, val payload: String)

@Entity(tableName = "sessions")
data class StoredSession(@PrimaryKey val id: String, val payload: String)

@Entity(tableName = "checkins")
data class StoredCheckIn(@PrimaryKey val date: String, val payload: String)

@Entity(tableName = "presets")
data class StoredPreset(@PrimaryKey val slot: Int, val name: String, val payload: String)

@Dao
interface CheckInDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: StoredCheckIn)
    @Query("SELECT * FROM checkins WHERE date = :date LIMIT 1")
    suspend fun find(date: String): StoredCheckIn?
    @Query("SELECT * FROM checkins ORDER BY date DESC")
    suspend fun all(): List<StoredCheckIn>
}

@Dao
interface PlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(plan: StoredPlan)

    @Query("SELECT * FROM plans WHERE date = :date LIMIT 1")
    suspend fun find(date: String): StoredPlan?
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: StoredSession)

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    suspend fun find(id: String): StoredSession?

    @Query("SELECT * FROM sessions ORDER BY rowid DESC LIMIT 1")
    suspend fun latest(): StoredSession?
}

@Dao
interface PresetDao {
    @Query("SELECT * FROM presets ORDER BY slot")
    suspend fun all(): List<StoredPreset>

    @Query("SELECT * FROM presets WHERE slot = :slot LIMIT 1")
    suspend fun find(slot: Int): StoredPreset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: StoredPreset)

    @Query("DELETE FROM presets WHERE slot = :slot")
    suspend fun delete(slot: Int)
}

@Database(
    entities = [StoredPlan::class, StoredSession::class, StoredCheckIn::class, StoredPreset::class],
    version = 4,
    exportSchema = false,
)
abstract class WorkoutDatabase : RoomDatabase() {
    abstract fun planDao(): PlanDao
    abstract fun sessionDao(): SessionDao
    abstract fun checkInDao(): CheckInDao
    abstract fun presetDao(): PresetDao
}

