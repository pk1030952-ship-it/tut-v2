package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.CoachMasterRepository
import com.example.ui.components.MonthlyAttendanceData
import com.example.ui.components.MonthlyFinanceData
import com.example.util.FeeCalculator
import com.example.util.ShareHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class AppNavDestination(val label: String) {
    DASHBOARD("Dashboard"),
    STUDENTS("Students"),
    BATCHES("Batches"),
    ATTENDANCE("Attendance"),
    FINANCE("Fee Ledger"),
    EXAMS("Exams"),
    EXPENSES("Expenses"),
    STAFF("Staff"),
    TIMETABLE("Timetable"),
    REPORTS("Reports"),
    SETTINGS("Settings")
}

data class UiState(
    val currentTab: AppNavDestination = AppNavDestination.DASHBOARD,
    val isUnlocked: Boolean = false,
    val isAdmin: Boolean = false,
    val selectedDate: String = FeeCalculator.getTodayStr(),
    val isSyncing: Boolean = false,
    val isCloudConnected: Boolean = true,
    val statusMessage: String? = null,
    val selectedStudentForPayment: Student? = null,
    val showGlobalSearchModal: Boolean = false
)

/**
 * App-shell coordinator ViewModel handling navigation, lock/unlock session state,
 * staff/exam management, and system settings.
 * Core domain operations are modularized into:
 * - [DashboardViewModel]
 * - [StudentViewModel]
 * - [AttendanceViewModel]
 * - [FinanceViewModel]
 */
class CoachMasterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CoachMasterRepository.getInstance(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    val students: StateFlow<List<Student>> = repository.allStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeStudents: StateFlow<List<Student>> = repository.activeStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val batches: StateFlow<List<Batch>> = repository.allBatches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val staff: StateFlow<List<Staff>> = repository.allStaff
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val attendance: StateFlow<List<AttendanceRecord>> = repository.allAttendance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payments: StateFlow<List<PaymentRecord>> = repository.allPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<ExpenseRecord>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val exams: StateFlow<List<Exam>> = repository.allExams
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val examResults: StateFlow<List<ExamResult>> = repository.allExamResults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val config: StateFlow<InstituteConfig> = repository.config
        .map { it ?: InstituteConfig() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InstituteConfig())

    init {
        repository.ensureInitialized()

        viewModelScope.launch {
            repository.isSyncing.collect { syncing ->
                _uiState.update { it.copy(isSyncing = syncing) }
            }
        }
        viewModelScope.launch {
            repository.isCloudConnected.collect { connected ->
                _uiState.update { it.copy(isCloudConnected = connected) }
            }
        }
        viewModelScope.launch {
            repository.statusMessage.collect { msg ->
                if (msg != null) {
                    _uiState.update { it.copy(statusMessage = msg) }
                }
            }
        }
    }

    fun unlockAsAdmin() {
        _uiState.update { it.copy(isUnlocked = true, isAdmin = true, statusMessage = "Welcome back, Admin!") }
    }

    fun loginAsGuest() {
        _uiState.update { it.copy(isUnlocked = true, isAdmin = false, statusMessage = "Logged in as Guest (Read-Only)") }
    }

    fun lockApp() {
        _uiState.update { it.copy(isUnlocked = false, isAdmin = false, statusMessage = "Logged out successfully") }
    }

    fun selectTab(tab: AppNavDestination) {
        if (!_uiState.value.isAdmin && tab == AppNavDestination.SETTINGS) {
            _uiState.update { it.copy(statusMessage = "Admin access required for Settings") }
            return
        }
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun navigateToAttendanceForDate(date: String) {
        _uiState.update { it.copy(selectedDate = date, currentTab = AppNavDestination.ATTENDANCE) }
    }

    fun clearStatusMessage() {
        repository.clearStatusMessage()
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun openGlobalSearch() {
        _uiState.update { it.copy(showGlobalSearchModal = true) }
    }

    fun closeGlobalSearch() {
        _uiState.update { it.copy(showGlobalSearchModal = false) }
    }

    fun selectStudentForPayment(student: Student?) {
        _uiState.update { it.copy(selectedStudentForPayment = student, currentTab = AppNavDestination.FINANCE) }
    }

    // Batch CRUD
    fun saveBatch(batch: Batch) {
        viewModelScope.launch {
            repository.saveBatch(batch)
            repository.postStatusMessage("Batch saved: ${batch.name}")
        }
    }

    fun deleteBatch(batch: Batch) {
        viewModelScope.launch {
            repository.deleteBatch(batch)
            repository.postStatusMessage("Batch deleted")
        }
    }

    // Staff CRUD
    fun saveStaff(staffMember: Staff) {
        viewModelScope.launch {
            repository.saveStaff(staffMember)
            repository.postStatusMessage("Staff member saved: ${staffMember.name}")
        }
    }

    fun deleteStaff(staffMember: Staff) {
        viewModelScope.launch {
            repository.deleteStaff(staffMember)
            repository.postStatusMessage("Staff member removed")
        }
    }

    // Expenses
    fun recordExpense(date: String, category: String, description: String, amount: Double) {
        if (amount <= 0.0) {
            repository.postStatusMessage("Expense amount must be greater than 0")
            return
        }
        viewModelScope.launch {
            val expense = ExpenseRecord(
                date = date,
                category = category,
                description = description,
                amount = amount
            )
            repository.recordExpense(expense)
            repository.postStatusMessage("Expense logged")
        }
    }

    fun deleteExpense(expense: ExpenseRecord) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            repository.postStatusMessage("Expense deleted")
        }
    }

    // Exams
    fun saveExam(exam: Exam) {
        viewModelScope.launch {
            repository.saveExam(exam)
            repository.postStatusMessage("Exam scheduled")
        }
    }

    fun deleteExam(exam: Exam) {
        viewModelScope.launch {
            repository.deleteExam(exam)
            repository.postStatusMessage("Exam deleted")
        }
    }

    fun saveExamMarks(examId: String, marksMap: Map<String, Double>) {
        viewModelScope.launch {
            val results = marksMap.map { (studentId, marks) ->
                ExamResult(
                    id = "${examId}_${studentId}",
                    examId = examId,
                    studentId = studentId,
                    marksObtained = marks
                )
            }
            repository.saveExamResults(examId, results)
            repository.postStatusMessage("Marks saved successfully")
        }
    }

    // Settings & Dark Mode
    fun updateConfig(newConfig: InstituteConfig) {
        viewModelScope.launch {
            repository.saveConfig(newConfig)
            repository.postStatusMessage("Institute Profile & Security Saved")
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            val current = config.value
            repository.saveConfig(current.copy(darkMode = !current.darkMode))
        }
    }

    fun factoryReset() {
        viewModelScope.launch {
            repository.factoryReset()
            repository.postStatusMessage("Factory reset complete.")
        }
    }

    fun seedDemoData() {
        viewModelScope.launch {
            repository.seedDemoData()
            _uiState.update { it.copy(currentTab = AppNavDestination.DASHBOARD) }
            repository.postStatusMessage("Demo data loaded")
        }
    }

    fun exportBackupJson(context: Context) {
        viewModelScope.launch {
            val json = repository.exportBackupJson()
            ShareHelper.shareBackupJsonFile(context, json)
            repository.postStatusMessage("Backup JSON generated")
        }
    }

    fun runEncryptedBackupNow(context: Context) {
        viewModelScope.launch {
            val file = repository.performEncryptedAutoBackup(context)
            ShareHelper.shareEncryptedBackupFile(context, file)
            repository.postStatusMessage("Encrypted AES-256 backup saved (${file.name})")
        }
    }

    fun exportFeeLedgerCsv(context: Context) {
        viewModelScope.launch {
            val file = com.example.util.CsvExportGenerator.generateFeeLedgerCsv(
                context = context,
                payments = payments.value,
                students = students.value,
                batches = batches.value
            )
            ShareHelper.shareCsvFile(context, file, "Fee Ledger Report")
            repository.postStatusMessage("Fee Ledger CSV exported (${payments.value.size} records)")
        }
    }

    fun exportAttendanceRegisterCsv(context: Context) {
        viewModelScope.launch {
            val file = com.example.util.CsvExportGenerator.generateAttendanceRegisterCsv(
                context = context,
                attendance = attendance.value,
                students = students.value,
                batches = batches.value
            )
            ShareHelper.shareCsvFile(context, file, "Attendance Register")
            repository.postStatusMessage("Attendance Register CSV exported")
        }
    }

    fun exportExpenseReportCsv(context: Context) {
        viewModelScope.launch {
            val file = com.example.util.CsvExportGenerator.generateExpenseReportCsv(
                context = context,
                expenses = expenses.value
            )
            ShareHelper.shareCsvFile(context, file, "Expense Report")
            repository.postStatusMessage("Expense Report CSV exported")
        }
    }

    fun exportStudentMasterSummaryCsv(context: Context) {
        viewModelScope.launch {
            val file = com.example.util.CsvExportGenerator.generateStudentMasterSummaryCsv(
                context = context,
                students = students.value,
                batches = batches.value,
                payments = payments.value,
                attendance = attendance.value,
                config = config.value
            )
            ShareHelper.shareCsvFile(context, file, "Student Master Summary")
            repository.postStatusMessage("Student Master Summary CSV exported")
        }
    }

    fun triggerTestRollCallNotification(context: Context) {
        val posted = com.example.worker.RollCallReminderWorker.showRollCallNotification(context)
        if (posted) {
            repository.postStatusMessage("Roll-Call reminder notification sent! Check your notification shade.")
        } else {
            repository.postStatusMessage("Notification permission not granted yet. Enable Notifications in Settings.")
        }
    }

    fun importBackupJson(jsonStr: String) {
        viewModelScope.launch {
            val res = repository.importBackupJson(jsonStr)
            repository.postStatusMessage(
                if (res.isSuccess) res.getOrNull() else "Invalid backup file: ${res.exceptionOrNull()?.localizedMessage}"
            )
        }
    }

    fun triggerCloudSync(pullFirst: Boolean = false) {
        viewModelScope.launch {
            val result = repository.syncWithCloud(pullFirst = pullFirst)
            repository.postStatusMessage(
                if (result.isSuccess) result.getOrNull() else "Offline mode active (Local changes saved safely)"
            )
        }
    }

    fun getMonthlyFinanceData(): List<MonthlyFinanceData> {
        val result = mutableListOf<MonthlyFinanceData>()
        val cal = Calendar.getInstance()
        val monthFmt = SimpleDateFormat("MMM yy", Locale.forLanguageTag("en-IN"))
        val isoMonthFmt = SimpleDateFormat("yyyy-MM", Locale.US)

        for (i in 5 downTo 0) {
            val c = cal.clone() as Calendar
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.add(Calendar.MONTH, -i)
            val monthLabel = monthFmt.format(c.time)
            val isoMonth = isoMonthFmt.format(c.time)

            val monthIncome = payments.value
                .filter { it.paymentDate.startsWith(isoMonth) }
                .sumOf { it.amount }

            val monthExpense = expenses.value
                .filter { it.date.startsWith(isoMonth) }
                .sumOf { it.amount }

            result.add(MonthlyFinanceData(monthLabel, monthIncome, monthExpense))
        }
        return result
    }

    fun getMonthlyAttendanceTrend(): List<MonthlyAttendanceData> {
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

            val monthRecords = attendance.value.filter {
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
