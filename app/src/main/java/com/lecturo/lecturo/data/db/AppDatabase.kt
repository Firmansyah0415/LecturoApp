package com.lecturo.lecturo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.lecturo.lecturo.data.db.dao.CalendarEntryDao
import com.lecturo.lecturo.data.db.dao.ConsultationDao
import com.lecturo.lecturo.data.db.dao.EventDao
import com.lecturo.lecturo.data.db.dao.FocusSessionDao
import com.lecturo.lecturo.data.db.dao.TasksDao
import com.lecturo.lecturo.data.db.dao.TeachingRuleDao
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.data.model.Tasks
import com.lecturo.lecturo.data.model.TeachingRule
import com.lecturo.lecturo.data.model.ConsultationPattern
import com.lecturo.lecturo.data.model.ConsultationSchedule
import com.lecturo.lecturo.data.model.FocusSession

@Database(
    entities = [
        Tasks::class,
        Event::class,
        TeachingRule::class,
        CalendarEntry::class,
        ConsultationSchedule::class,
        ConsultationPattern::class,
        FocusSession::class
    ],
    version = 18, // 🔴 PERBAIKAN 1: Versi naik ke 18
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tasksDao(): TasksDao
    abstract fun eventDao(): EventDao
    abstract fun teachingRuleDao(): TeachingRuleDao
    abstract fun calendarEntryDao(): CalendarEntryDao
    abstract fun consultationDao(): ConsultationDao
    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN endTime TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE events ADD COLUMN endTime TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 🔴 PERBAIKAN 2: Menggunakan endTime (camelCase)
                database.execSQL("ALTER TABLE calendar_entries ADD COLUMN endTime TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lecturo_database"
                )
                    // 🔴 PERBAIKAN 3: Cukup panggil satu kali saja berurutan
                    .addMigrations(MIGRATION_16_17, MIGRATION_17_18)
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}