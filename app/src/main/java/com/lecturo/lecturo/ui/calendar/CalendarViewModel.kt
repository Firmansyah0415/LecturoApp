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

    private val _calendarDays = MutableLiveData<List<CalendarDay>>()
    val calendarDays: LiveData<List<CalendarDay>> get() = _calendarDays

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val timeFilter = MutableLiveData(TimeFilter.DAILY)
    private val categoryFilter = MutableLiveData<Set<String>>(emptySet())

    private val allEntries = calendarRepository.getAllEntries()

    private val _selectedDateSchedules = MediatorLiveData<List<CalendarEntry>>()
    val selectedDateSchedules: LiveData<List<CalendarEntry>> = _selectedDateSchedules

    init {
        _selectedDateSchedules.addSource(allEntries) { updateSchedules() }
        _selectedDateSchedules.addSource(_selectedDate) { updateSchedules() }
        _selectedDateSchedules.addSource(timeFilter) { updateSchedules() }
        _selectedDateSchedules.addSource(categoryFilter) { updateSchedules() }

        selectDate(Calendar.getInstance())
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
        val newMonth = calendar.clone() as Calendar
        _currentMonth.value = newMonth
        generateCalendarDays(newMonth)
    }

    fun selectDate(calendar: Calendar) {
        _selectedDate.value = calendar
        if (_currentMonth.value?.get(Calendar.MONTH) != calendar.get(Calendar.MONTH)) {
            setCurrentMonth(calendar)
        } else {
            _currentMonth.value?.let { generateCalendarDays(it) }
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

    private fun generateCalendarDays(targetMonthCalendar: Calendar) {
        viewModelScope.launch {
            val days = mutableListOf<CalendarDay>()
            val today = Calendar.getInstance()

            // Buat salinan agar tidak mengubah kalender asli
            val cal = targetMonthCalendar.clone() as Calendar
            cal.set(Calendar.DAY_OF_MONTH, 1) // Mulai dari tanggal 1 bulan target

            // **LOGIKA BARU YANG LEBIH ROBUST**
            // Mundur ke hari pertama dalam minggu dari tanggal 1 tersebut
            val firstDayOfWeekInMonth = cal.get(Calendar.DAY_OF_WEEK)
            val daysToMoveBack = firstDayOfWeekInMonth - cal.firstDayOfWeek
            if (daysToMoveBack < 0) {
                cal.add(Calendar.DAY_OF_MONTH, - (daysToMoveBack + 7))
            } else {
                cal.add(Calendar.DAY_OF_MONTH, -daysToMoveBack)
            }

            // Buat 42 hari (6 minggu) untuk mengisi grid
            while (days.size < 42) {
                val isCurrentMonth = cal.get(Calendar.MONTH) == targetMonthCalendar.get(Calendar.MONTH)
                val isToday = isSameDay(cal, today)

                val dateString = getFormattedDate(cal)
                val categoriesForDay = allEntries.value
                    ?.filter { it.date == dateString }
                    ?.map { it.category }
                    ?.toSet() ?: emptySet()

                days.add(
                    CalendarDay(
                        date = cal.clone() as Calendar,
                        dayNumber = cal.get(Calendar.DAY_OF_MONTH).toString(),
                        isToday = isToday,
                        // isSelected tidak lagi dikelola di sini, tapi di adapter
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
