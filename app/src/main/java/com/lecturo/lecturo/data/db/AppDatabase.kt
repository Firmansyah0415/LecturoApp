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
import com.lecturo.lecturo.data.db.dao.TeachingScheduleDao
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.data.model.Tasks
import com.lecturo.lecturo.data.model.TeachingSchedule
import com.lecturo.lecturo.data.model.ConsultationPattern
import com.lecturo.lecturo.data.model.ConsultationSchedule
import com.lecturo.lecturo.data.model.FocusSession

@Database(
    entities = [
        Tasks::class,
        Event::class,
        TeachingSchedule::class,
        CalendarEntry::class,
        ConsultationSchedule::class,
        ConsultationPattern::class,
        FocusSession::class
    ],
    version = 21, // 🔴 PERBAIKAN: NAIKKAN KE VERSI 21
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tasksDao(): TasksDao
    abstract fun eventDao(): EventDao
    abstract fun teachingScheduleDao(): TeachingScheduleDao
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

        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE calendar_entries ADD COLUMN endTime TEXT NOT NULL DEFAULT ''")
            }
        }

        // MIGRATION 18 ke 19: Khusus untuk merombak Jadwal Mengajar
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS `teaching_schedules` (
                        `localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `firestoreId` TEXT, 
                        `is_synced` INTEGER NOT NULL, 
                        `is_deleted` INTEGER NOT NULL, 
                        `userId` TEXT, 
                        `courseName` TEXT NOT NULL, 
                        `classCode` TEXT NOT NULL, 
                        `classroom` TEXT NOT NULL, 
                        `dayOfWeek` TEXT NOT NULL, 
                        `date` TEXT NOT NULL, 
                        `startTime` TEXT NOT NULL, 
                        `endTime` TEXT NOT NULL, 
                        `priority` TEXT, 
                        `studentCount` INTEGER NOT NULL, 
                        `meeting_number` INTEGER NOT NULL, 
                        `is_completed` INTEGER NOT NULL, 
                        `notificationMinutes` INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("DROP TABLE IF EXISTS `teaching_rules`")
                database.execSQL("DELETE FROM calendar_entries WHERE sourceFeatureType = 'TEACHING_RULE'")
            }
        }

        // 🔴 PERBAIKAN: MIGRATION 19 ke 20 (Khusus menambahkan isCompleted di Kalender)
        private val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE calendar_entries ADD COLUMN isCompleted INTEGER NOT NULL DEFAULT 0")
            }
        }

        // 🔴 MIGRATION 20_21: Tambahkan input_source ke teaching_schedules
        private val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE teaching_schedules ADD COLUMN input_source TEXT NOT NULL DEFAULT 'MANUAL'")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lecturo_database"
                )
                    // Daftarkan MIGRATION_19_20 di sini
                    .addMigrations(MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}