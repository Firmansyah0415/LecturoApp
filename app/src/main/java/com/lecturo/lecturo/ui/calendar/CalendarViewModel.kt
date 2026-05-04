package com.lecturo.lecturo.ui.calendar

import androidx.lifecycle.*
import com.lecturo.lecturo.data.model.CalendarDay
import com.lecturo.lecturo.data.model.CalendarEntry
import com.lecturo.lecturo.data.repository.CalendarRepository
import com.lecturo.lecturo.data.repository.EventRepository
import com.lecturo.lecturo.data.repository.TasksRepository
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CalendarViewModel(
    private val calendarRepository: CalendarRepository,
    private val eventRepository: EventRepository,
    private val tasksRepository: TasksRepository
) : ViewModel() {

    enum class TimeFilter { DAILY, WEEKLY, MONTHLY }

    private val _currentMonth = MutableLiveData<Calendar>()
    val currentMonth: LiveData<Calendar> get() = _currentMonth

    private val _selectedDate = MutableLiveData<Calendar>()
    val selectedDate: LiveData<Calendar> get() = _selectedDate

    // 1. UBAH KE MEDIATOR LIVEDATA AGAR REAKTIF
    private val _calendarDays = MediatorLiveData<List<CalendarDay>>()
    val calendarDays: LiveData<List<CalendarDay>> get() = _calendarDays

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val timeFilter = MutableLiveData(TimeFilter.DAILY)
    private val categoryFilter = MutableLiveData<Set<String>>(emptySet())

    private val allEntries = calendarRepository.getAllEntries()

    private val _selectedDateSchedules = MediatorLiveData<List<CalendarEntry>>()
    val selectedDateSchedules: LiveData<List<CalendarEntry>> = _selectedDateSchedules

    init {
        // Pantau filter untuk daftar jadwal di bawah
        _selectedDateSchedules.addSource(allEntries) { updateSchedules() }
        _selectedDateSchedules.addSource(_selectedDate) { updateSchedules() }
        _selectedDateSchedules.addSource(timeFilter) { updateSchedules() }
        _selectedDateSchedules.addSource(categoryFilter) { updateSchedules() }

        // 2. KUNCI PERBAIKAN: Pantau data bulan & database untuk kalender grid!
        _calendarDays.addSource(_currentMonth) { generateCalendarDays() }
        _calendarDays.addSource(allEntries) { generateCalendarDays() }

        // Set awal bulan dan tanggal hari ini
        val today = Calendar.getInstance()
        _currentMonth.value = today.clone() as Calendar
        selectDate(today)
    }

    private fun updateSchedules() {
        viewModelScope.launch {
            _isLoading.value = true
            val entries = allEntries.value ?: emptyList()
            val date = _selectedDate.value ?: return@launch
            val categories = categoryFilter.value ?: emptySet()
            val currentFilterType = timeFilter.value ?: TimeFilter.DAILY

            val filtered = entries.filter { entry ->
                val dateMatch = when (currentFilterType) {
                    TimeFilter.DAILY -> entry.date == getFormattedDate(date)
                    else -> entry.date == getFormattedDate(date)
                }
                val categoryMatch = categories.isEmpty() || categories.contains(entry.category)
                dateMatch && categoryMatch
            }.sortedBy { it.time }

            _selectedDateSchedules.postValue(filtered)
            _isLoading.value = false
        }
    }

    fun setCurrentMonth(calendar: Calendar) {
        // Fungsi ini sekarang HANYA mengubah bulan,
        // generateCalendarDays() akan otomatis terpanggil berkat MediatorLiveData
        _currentMonth.value = calendar.clone() as Calendar
    }

    fun selectDate(calendar: Calendar) {
        _selectedDate.value = calendar
        val current = _currentMonth.value
        // Pindah bulan jika tanggal yang diklik berbeda bulan
        if (current == null || current.get(Calendar.MONTH) != calendar.get(Calendar.MONTH)) {
            setCurrentMonth(calendar)
        }
    }

    fun navigateToNextMonth() {
        _currentMonth.value?.let {
            val nextMonth = it.clone() as Calendar
            nextMonth.add(Calendar.MONTH, 1)
            setCurrentMonth(nextMonth)
        }
    }

    fun navigateToPreviousMonth() {
        _currentMonth.value?.let {
            val prevMonth = it.clone() as Calendar
            prevMonth.add(Calendar.MONTH, -1)
            setCurrentMonth(prevMonth)
        }
    }

    fun setTimeFilter(filter: TimeFilter) {
        timeFilter.value = filter
    }

    fun setCategoryFilter(categories: Set<String>) {
        categoryFilter.value = categories
    }

    // 3. FUNGSI YANG DIPERBARUI: Tidak perlu menerima parameter targetMonth lagi
    private fun generateCalendarDays() {
        val targetMonthCalendar = _currentMonth.value ?: return
        val entries = allEntries.value // Akan mengambil data yang sudah siap dari LiveData

        viewModelScope.launch {
            val days = mutableListOf<CalendarDay>()
            val today = Calendar.getInstance()

            val cal = targetMonthCalendar.clone() as Calendar
            cal.set(Calendar.DAY_OF_MONTH, 1)

            val firstDayOfWeekInMonth = cal.get(Calendar.DAY_OF_WEEK)
            val daysToMoveBack = firstDayOfWeekInMonth - cal.firstDayOfWeek
            if (daysToMoveBack < 0) {
                cal.add(Calendar.DAY_OF_MONTH, -(daysToMoveBack + 7))
            } else {
                cal.add(Calendar.DAY_OF_MONTH, -daysToMoveBack)
            }

            while (days.size < 42) {
                val isCurrentMonth = cal.get(Calendar.MONTH) == targetMonthCalendar.get(Calendar.MONTH)
                val isToday = isSameDay(cal, today)
                val dateString = getFormattedDate(cal)

                // Karena kita mengamati allEntries, variabel 'entries' dijamin update
                val categoriesForDay = entries
                    ?.filter { it.date == dateString }
                    ?.map { it.category }
                    ?.toSet() ?: emptySet()

                days.add(
                    CalendarDay(
                        date = cal.clone() as Calendar,
                        dayNumber = cal.get(Calendar.DAY_OF_MONTH).toString(),
                        isToday = isToday,
                        isCurrentMonth = isCurrentMonth,
                        scheduleCategories = categoriesForDay
                    )
                )
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
            _calendarDays.postValue(days)
        }
    }

    private fun getFormattedDate(calendar: Calendar): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        return sdf.format(calendar.time)
    }

    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }
}