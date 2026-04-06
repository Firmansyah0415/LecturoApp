package com.lecturo.lecturo.utils

import java.text.SimpleDateFormat
import java.util.*

fun String.toReadableDate(): String {
    // 'this' merujuk pada objek String itu sendiri yang memanggil fungsi ini.
    val dateString = this
    return try {
        // Tentukan format input (sesuai dengan data yang disimpan)
        val inputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        // Tentukan format output yang diinginkan dengan Locale Bahasa Indonesia
        val outputFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale("id", "ID"))

        // Parse string input menjadi objek Date
        val date = inputFormat.parse(dateString)

        // Format objek Date menjadi string output
        if (date != null) {
            outputFormat.format(date)
        } else {
            dateString // Kembalikan string asli jika parsing gagal
        }
    } catch (e: Exception) {
        e.printStackTrace()
        dateString // Kembalikan string asli jika terjadi error
    }
}
