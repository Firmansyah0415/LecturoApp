package com.lecturo.lecturo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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
    version = 16,
    exportSchema = false)
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lecturo_database"
                )
                    //.fallbackToDestructiveMigration()
                    .fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}