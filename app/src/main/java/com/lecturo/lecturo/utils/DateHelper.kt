package com.lecturo.lecturo.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateHelper {

    // Mengubah Calendar.MONDAY (2) menjadi "Monday"
    fun getDayName(dayOfWeek: Int): String {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_WEEK, dayOfWeek)
        return SimpleDateFormat("EEEE", Locale.getDefault()).format(calendar.time)
    }

    // Mencari tanggal terdekat berdasarkan hari yang diminta
    // Contoh: Hari ini Rabu, user minta "Selasa". Maka return tanggal Selasa depan.
    fun getNextDateForDay(targetDayOfWeek: Int): String {
        val calendar = Calendar.getInstance()

        // Jika hari target < hari ini (misal target Senin, skrg Rabu), tambah 1 minggu dulu?
        // Logic sederhana: Loop tambah 1 hari sampai ketemu hari yang cocok
        while (calendar.get(Calendar.DAY_OF_WEEK) != targetDayOfWeek) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        // Return format yyyy-MM-dd untuk Database/EditText
        return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
    }
}