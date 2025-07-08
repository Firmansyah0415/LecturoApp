package com.lecturo.lecturo

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "schedule_db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_SCHEDULES = "schedules"

        private const val COLUMN_ID = "id"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_DATE = "date"
        private const val COLUMN_TIME = "time"
        private const val COLUMN_LOCATION = "location"
        private const val COLUMN_DESCRIPTION = "description"
        private const val COLUMN_CREATED_AT = "created_at"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_SCHEDULES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_DATE TEXT NOT NULL,
                $COLUMN_TIME TEXT NOT NULL,
                $COLUMN_LOCATION TEXT NOT NULL,
                $COLUMN_DESCRIPTION TEXT,
                $COLUMN_CREATED_AT INTEGER
            )
        """.trimIndent()

        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SCHEDULES")
        onCreate(db)
    }

    fun insertSchedule(schedule: Schedule): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TITLE, schedule.title)
            put(COLUMN_DATE, schedule.date)
            put(COLUMN_TIME, schedule.time)
            put(COLUMN_LOCATION, schedule.location)
            put(COLUMN_DESCRIPTION, schedule.description)
            put(COLUMN_CREATED_AT, schedule.createdAt)
        }

        val id = db.insert(TABLE_SCHEDULES, null, values)
        db.close()
        return id
    }

    fun getAllSchedules(): List<Schedule> {
        val schedules = mutableListOf<Schedule>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SCHEDULES,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_DATE ASC, $COLUMN_TIME ASC"
        )

        with(cursor) {
            while (moveToNext()) {
                val schedule = Schedule(
                    id = getLong(getColumnIndexOrThrow(COLUMN_ID)),
                    title = getString(getColumnIndexOrThrow(COLUMN_TITLE)),
                    date = getString(getColumnIndexOrThrow(COLUMN_DATE)),
                    time = getString(getColumnIndexOrThrow(COLUMN_TIME)),
                    location = getString(getColumnIndexOrThrow(COLUMN_LOCATION)),
                    description = getString(getColumnIndexOrThrow(COLUMN_DESCRIPTION)),
                    createdAt = getLong(getColumnIndexOrThrow(COLUMN_CREATED_AT))
                )
                schedules.add(schedule)
            }
        }
        cursor.close()
        db.close()
        return schedules
    }

    fun getScheduleById(id: Long): Schedule? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_SCHEDULES,
            null,
            "$COLUMN_ID = ?",
            arrayOf(id.toString()),
            null,
            null,
            null
        )

        var schedule: Schedule? = null
        if (cursor.moveToFirst()) {
            schedule = Schedule(
                id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TITLE)),
                date = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DATE)),
                time = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIME)),
                location = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LOCATION)),
                description = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DESCRIPTION)),
                createdAt = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_CREATED_AT))
            )
        }
        cursor.close()
        db.close()
        return schedule
    }

    fun updateSchedule(schedule: Schedule): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TITLE, schedule.title)
            put(COLUMN_DATE, schedule.date)
            put(COLUMN_TIME, schedule.time)
            put(COLUMN_LOCATION, schedule.location)
            put(COLUMN_DESCRIPTION, schedule.description)
        }

        val rowsAffected = db.update(
            TABLE_SCHEDULES,
            values,
            "$COLUMN_ID = ?",
            arrayOf(schedule.id.toString())
        )
        db.close()
        return rowsAffected
    }

    fun deleteSchedule(id: Long): Int {
        val db = writableDatabase
        val rowsAffected = db.delete(
            TABLE_SCHEDULES,
            "$COLUMN_ID = ?",
            arrayOf(id.toString())
        )
        db.close()
        return rowsAffected
    }
}