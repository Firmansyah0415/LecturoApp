package com.lecturo.lecturo.utils

import java.util.regex.Pattern

/**
 * Object utilitas untuk mem-parsing teks mentah dari OCR menjadi
 * informasi jadwal yang terstruktur (EventInfo).
 */

object ScheduleParser {

    // Pola Regex yang sudah ditingkatkan
    private val DATE_PATTERN: Pattern = Pattern.compile(
        "\\b(\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4}|\\d{1,2}\\s+(Januari|Februari|Maret|April|Mei|Juni|Juli|Agustus|September|Oktober|November|Desember|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\\s+\\d{2,4})\\b",
        Pattern.CASE_INSENSITIVE
    )
    private val TIME_PATTERN: Pattern = Pattern.compile(
        "\\b(\\d{1,2}[:.]\\d{2})(\\s*-\\s*\\d{1,2}[:.]\\d{2})?(\\s*(WIB|WITA|WIT|AM|PM))?\\b",
        Pattern.CASE_INSENSITIVE
    )

    // Kata kunci untuk membantu identifikasi
    private val LOCATION_KEYWORDS = listOf("tempat", "lokasi", "di", "ruang", "gedung", "lantai", "aula", "zoom", "gmeet", "online")
    private val IGNORED_KEYWORDS_FOR_TITLE = listOf("undangan", "yth", "dengan hormat", "kepada")

    fun parse(text: String): EventInfo {
        val lines = text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val usedLines = BooleanArray(lines.size) { false }

        var date: String? = null
        var time: String? = null
        var location: String? = null

        // Iterasi pertama: Cari entitas yang paling pasti (Tanggal, Waktu, Lokasi)
        lines.forEachIndexed { index, line ->
            // Cari Tanggal
            val dateMatcher = DATE_PATTERN.matcher(line)
            if (date == null && dateMatcher.find()) {
                date = normalizeDate(dateMatcher.group())
                usedLines[index] = true
            }

            // Cari Waktu
            val timeMatcher = TIME_PATTERN.matcher(line)
            if (time == null && timeMatcher.find()) {
                time = timeMatcher.group().replace(".", ":")
                usedLines[index] = true
            }

            // Cari Lokasi (lebih pintar, cari yang diawali kata kunci)
            val lowerLine = line.lowercase()
            LOCATION_KEYWORDS.forEach { keyword ->
                if (location == null && lowerLine.contains(keyword)) {
                    // Prioritaskan baris yang diawali dengan "Tempat:" atau "Lokasi:"
                    val prefix = "$keyword:"
                    if (lowerLine.startsWith(prefix)) {
                        location = line.substringAfter(prefix).trim()
                    } else {
                        location = line
                    }
                    usedLines[index] = true
                }
            }
        }

        // Iterasi kedua: Cari Judul dari baris yang tersisa
        val title = lines.firstOrNull { line ->
            val lowerLine = line.lowercase()
            !usedLines[lines.indexOf(line)] && IGNORED_KEYWORDS_FOR_TITLE.none { lowerLine.contains(it) }
        }

        return EventInfo(
            title = title,
            date = date,
            time = time,
            location = location
        )
    }

    /**
     * Mengubah berbagai format tanggal menjadi format standar "dd/MM/yyyy".
     */
    private fun normalizeDate(dateString: String): String {
        // Contoh sederhana, bisa dikembangkan lebih lanjut
        // Misal: "19 Juli 2025" -> "19/07/2025"
        val monthMap = mapOf(
            "januari" to "01", "februari" to "02", "maret" to "03", "april" to "04", "mei" to "05", "juni" to "06",
            "juli" to "07", "agustus" to "08", "september" to "09", "oktober" to "10", "november" to "11", "desember" to "12",
            "jan" to "01", "feb" to "02", "mar" to "03", "apr" to "04", "may" to "05", "jun" to "06",
            "jul" to "07", "aug" to "08", "sep" to "09", "oct" to "10", "nov" to "11", "dec" to "12"
        )
        val parts = dateString.split(Regex("[\\s/-]"))
        if (parts.size == 3) {
            val day = parts[0].padStart(2, '0')
            val month = monthMap[parts[1].lowercase()] ?: parts[1].padStart(2, '0')
            val year = if (parts[2].length == 2) "20${parts[2]}" else parts[2]
            return "$day/$month/$year"
        }
        return dateString // Kembalikan asli jika format tidak dikenali
    }
}
