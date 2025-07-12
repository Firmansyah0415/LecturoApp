package com.lecturo.lecturo.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lecturo.lecturo.data.db.dao.EventDao
import com.lecturo.lecturo.data.db.dao.TasksDao
import com.lecturo.lecturo.data.model.Event
import com.lecturo.lecturo.data.model.Tasks

@Database(entities = [Tasks::class, Event::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun tasksDao(): TasksDao // Kunci untuk laci Tugas
    abstract fun eventDao(): EventDao // Kunci untuk laci Event

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lecturo_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}