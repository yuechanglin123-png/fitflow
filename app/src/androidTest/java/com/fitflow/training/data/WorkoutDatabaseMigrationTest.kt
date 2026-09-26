package com.fitflow.training.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutDatabaseMigrationTest {
    @Test fun migrationThreeToFourKeepsExistingRowsAndCreatesPresets() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "migration-${UUID.randomUUID()}.db"
        try {
            createVersionThreeDatabase(context, name)
            val db = Room.databaseBuilder(context, WorkoutDatabase::class.java, name)
                .addMigrations(WorkoutRepository.MIGRATION_3_4)
                .build()
            try {
                val sql = db.openHelper.writableDatabase
                assertEquals(1, count(sql, "plans"))
                assertEquals(1, count(sql, "sessions"))
                assertEquals(1, count(sql, "checkins"))
                sql.query("SELECT name FROM sqlite_master WHERE type='table' AND name='presets'").use {
                    assertEquals(true, it.moveToFirst())
                }
            } finally {
                db.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun createVersionThreeDatabase(context: Context, name: String) {
        val db = context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null)
        db.execSQL("CREATE TABLE plans (date TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE sessions (id TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL)")
        db.execSQL("CREATE TABLE checkins (date TEXT NOT NULL PRIMARY KEY, payload TEXT NOT NULL)")
        db.execSQL("INSERT INTO plans VALUES ('2030-01-01', 'plan-marker')")
        db.execSQL("INSERT INTO sessions VALUES ('session-marker', 'session-marker')")
        db.execSQL("INSERT INTO checkins VALUES ('2030-01-01', 'checkin-marker')")
        db.version = 3
        db.close()
    }

    private fun count(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }
}
