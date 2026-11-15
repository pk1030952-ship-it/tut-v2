package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AttendanceRecord
import com.example.data.model.Batch
import com.example.data.model.InstituteConfig
import com.example.data.model.Student
import com.example.data.repository.CoachMasterRepository
import com.example.ui.components.MonthlyAttendanceData
import com.example.util.FeeCalculator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class AttendanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CoachMasterRepository.getInstance(application)

    private val _selectedDate = MutableStateFlow(FeeCalculator.getTodayStr())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    val students: StateFlow<List<Student>> = repository.allStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeStudents: StateFlow<List<Student>> = repository.activeStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val batches: StateFlow<List<Batch>> = repository.allBatches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val attendance: StateFlow<List<AttendanceRecord>> = repository.allAttendance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val config: StateFlow<InstituteConfig> = repository.config
        .map { it ?: InstituteConfig() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InstituteConfig())

    fun setSelectedDate(date: String) {
        _selectedDate.value = date
    }

    fun markAttendance(
        date: String,
        attendanceMap: Map<String, String>,
        batchId: String?,
        reasonMap: Map<String, String> = emptyMap()
    ) {
        viewModelScope.launch {
            val studentLookup = students.value.associateBy { it.id }
            val records = attendanceMap.map { (studentId, status) ->
                AttendanceRecord(
                    id = "${date}_${studentId}",
                    date = date,
                    studentId = studentId,
                    batchId = batchId ?: studentLookup[studentId]?.batchId,
                    status = status,
                    reason = reasonMap[studentId] ?: ""
                )
            }
            repository.recordAttendanceBatch(date, records)
            repository.postStatusMessage("Attendance saved for ${FeeCalculator.formatDate(date)}")
        }
    }

    fun markTuitionHolidayRange(
        startDate: String,
        endDate: String,
        reason: String,
        batchId: String? = null
    ) {
        viewModelScope.launch {
            val targetStudents = if (batchId == null) {
                activeStudents.value
            } else {
                activeStudents.value.filter { it.batchId == batchId }
            }
            val dates = FeeCalculator.generateDateRange(startDate, endDate)
            val cleanReason = reason.trim().ifBlank { "Tuition Holiday" }
            val allHolidayRecords = mutableListOf<AttendanceRecord>()

            dates.forEach { d ->
                targetStudents.forEach { stu ->
                    allHolidayRecords.add(
                        AttendanceRecord(
                            id = "${d}_${stu.id}",
                            date = d,
                            studentId = stu.id,
                            batchId = stu.batchId,
                            status = "holiday",
                            reason = cleanReason
                        )
                    )
                }
            }
            if (allHolidayRecords.isNotEmpty()) {
                repository.recordAttendanceBatch(startDate, allHolidayRecords)
            }
            val label = if (dates.size == 1) {
                FeeCalculator.formatDate(startDate)
            } else {
                "${FeeCalculator.formatDate(startDate)} - ${FeeCalculator.formatDate(endDate)} (${dates.size} days)"
            }
            repository.postStatusMessage("🏖️ Marked Tuition Holiday for $label (Fee Frozen)")
        }
    }

    fun markPaidWeeklyOff(date: String, reason: String = "Paid Sunday / Weekly Off") {
        viewModelScope.launch {
            val records = activeStudents.value.map { stu ->
                AttendanceRecord(
                    id = "${date}_${stu.id}",
                    date = date,
                    studentId = stu.id,
                    batchId = stu.batchId,
                    status = "present",
                    reason = reason
                )
            }
            if (records.isNotEmpty()) {
                repository.recordAttendanceBatch(date, records)
            }
            repository.postStatusMessage("✅ Marked ${FeeCalculator.formatDate(date)} as Paid Day (+1 Working Day)")
        }
    }

    fun getUnmarkedPastDates(
        allAttendance: List<AttendanceRecord> = attendance.value,
        activeStudentList: List<Student> = activeStudents.value
    ): List<String> {
        if (activeStudentList.isEmpty()) return emptyList()
        val earliestJoining = activeStudentList.minOfOrNull { it.joiningDate.substringBefore("T") } ?: return emptyList()
        val markedDates = allAttendance.map { it.date }.toSet()

        val unmarked = mutableListOf<String>()
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()

        for (daysBack in 1..7) {
            val checkCal = cal.clone() as Calendar
            checkCal.add(Calendar.DAY_OF_YEAR, -daysBack)
            val dateStr = fmt.format(checkCal.time)
            if (dateStr >= earliestJoining && !markedDates.contains(dateStr)) {
                unmarked.add(dateStr)
            }
        }
        return unmarked
    }

    fun getMonthlyAttendanceTrend(
        allAttendance: List<AttendanceRecord> = attendance.value
    ): List<MonthlyAttendanceData> {
        val result = mutableListOf<MonthlyAttendanceData>()
        val cal = Calendar.getInstance()
        val monthFmt = SimpleDateFormat("MMM yy", Locale.forLanguageTag("en-IN"))
        val isoMonthFmt = SimpleDateFormat("yyyy-MM", Locale.US)

        for (i in 5 downTo 0) {
            val c = cal.clone() as Calendar
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.add(Calendar.MONTH, -i)
            val monthLabel = monthFmt.format(c.time)
            val isoMonth = isoMonthFmt.format(c.time)

            val monthRecords = allAttendance.filter {
                it.date.startsWith(isoMonth) && (it.status == "present" || it.status == "absent")
            }
            val pct = if (monthRecords.isNotEmpty()) {
                val present = monthRecords.count { it.status == "present" }
                Math.round((present.toFloat() / monthRecords.size) * 100f)
            } else {
                0
            }
            result.add(MonthlyAttendanceData(monthLabel, pct))
        }
        return result
    }
}
