package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.CoachMasterRepository
import com.example.util.FeeCalculator
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CoachMasterRepository.getInstance(application)

    private val _selectedStudentForDossier = MutableStateFlow<Student?>(null)
    val selectedStudentForDossier: StateFlow<Student?> = _selectedStudentForDossier.asStateFlow()

    val isSyncing: StateFlow<Boolean> = repository.isSyncing
    val isCloudConnected: StateFlow<Boolean> = repository.isCloudConnected

    val students: StateFlow<List<Student>> = repository.allStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeStudents: StateFlow<List<Student>> = repository.activeStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val batches: StateFlow<List<Batch>> = repository.allBatches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val attendance: StateFlow<List<AttendanceRecord>> = repository.allAttendance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payments: StateFlow<List<PaymentRecord>> = repository.allPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<ExpenseRecord>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exams: StateFlow<List<Exam>> = repository.allExams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val config: StateFlow<InstituteConfig> = repository.config
        .map { it ?: InstituteConfig() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InstituteConfig())

    init {
        repository.ensureInitialized()
    }

    fun openStudentDossier(student: Student) {
        _selectedStudentForDossier.value = student
    }

    fun closeStudentDossier() {
        _selectedStudentForDossier.value = null
    }

    fun triggerCloudSync(pullFirst: Boolean = false) {
        viewModelScope.launch {
            val result = repository.syncWithCloud(pullFirst = pullFirst)
            repository.postStatusMessage(
                if (result.isSuccess) result.getOrNull() else "Offline mode active (Local changes saved safely)"
            )
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
}
