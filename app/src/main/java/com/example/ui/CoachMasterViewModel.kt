package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.*
import com.example.data.repository.CoachMasterRepository
import com.example.ui.components.MonthlyAttendanceData
import com.example.ui.components.MonthlyFinanceData
import com.example.util.FeeCalculator
import com.example.util.PdfReceiptGenerator
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
    val searchQuery: String = "",
    val selectedBatchFilter: String? = null,
    val selectedDate: String = FeeCalculator.getTodayStr(),
    val isSyncing: Boolean = false,
    val isCloudConnected: Boolean = true,
    val statusMessage: String? = null,
    val selectedStudentForDossier: Student? = null,
    val selectedStudentForAttendanceHistory: Student? = null,
    val selectedStudentForCommLog: Student? = null,
    val selectedStudentForShareOptions: Student? = null,
    val selectedStudentForPayment: Student? = null,
    val showGlobalSearchModal: Boolean = false
)

class CoachMasterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CoachMasterRepository(
        database = AppDatabase.getInstance(application),
        appContext = application.applicationContext
    )

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

    val communications: StateFlow<List<CommunicationLog>> = repository.allCommunications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val config: StateFlow<InstituteConfig> = repository.config
        .map { it ?: InstituteConfig() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InstituteConfig())

    init {
        // Connect to Firebase Firestore (tuitionAppDb/mainData) on boot and listen in real time
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            val result = repository.pullFromCloud()
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    isCloudConnected = result.isSuccess
                )
            }
            repository.startRealtimeCloudSync { connected, msg ->
                _uiState.update { state ->
                    state.copy(
                        isCloudConnected = connected,
                        statusMessage = msg ?: state.statusMessage
                    )
                }
            }
        }
    }

    fun unlockAsAdmin() {
        _uiState.update { it.copy(isUnlocked = true, isAdmin = true, statusMessage = "Welcome back, Pankaj jii!") }
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

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSelectedBatchFilter(batchId: String?) {
        _uiState.update { it.copy(selectedBatchFilter = batchId) }
    }

    fun setSelectedDate(date: String) {
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    fun openGlobalSearch() {
        _uiState.update { it.copy(showGlobalSearchModal = true) }
    }

    fun closeGlobalSearch() {
        _uiState.update { it.copy(showGlobalSearchModal = false) }
    }

    fun openStudentDossier(student: Student) {
        _uiState.update { it.copy(selectedStudentForDossier = student) }
    }

    fun closeStudentDossier() {
        _uiState.update { it.copy(selectedStudentForDossier = null) }
    }

    fun openAttendanceHistoryModal(student: Student) {
        _uiState.update { it.copy(selectedStudentForAttendanceHistory = student) }
    }

    fun closeAttendanceHistoryModal() {
        _uiState.update { it.copy(selectedStudentForAttendanceHistory = null) }
    }

    fun openCommLogModal(student: Student) {
        _uiState.update { it.copy(selectedStudentForCommLog = student) }
    }

    fun closeCommLogModal() {
        _uiState.update { it.copy(selectedStudentForCommLog = null) }
    }

    fun openShareOptionsModal(student: Student) {
        _uiState.update { it.copy(selectedStudentForShareOptions = student) }
    }

    fun closeShareOptionsModal() {
        _uiState.update { it.copy(selectedStudentForShareOptions = null) }
    }

    fun selectStudentForPayment(student: Student?) {
        _uiState.update { it.copy(selectedStudentForPayment = student, currentTab = AppNavDestination.FINANCE) }
    }

    // Student CRUD
    fun saveStudent(student: Student) {
        viewModelScope.launch {
            repository.saveStudent(student)
            _uiState.update { it.copy(statusMessage = "Student profile saved & synced: ${student.name}") }
        }
    }

    fun deleteStudent(studentId: String) {
        viewModelScope.launch {
            repository.deleteStudent(studentId)
            _uiState.update {
                it.copy(
                    statusMessage = "Student record permanently deleted",
                    selectedStudentForDossier = null,
                    selectedStudentForAttendanceHistory = null,
                    selectedStudentForCommLog = null,
                    selectedStudentForShareOptions = null
                )
            }
        }
    }

    fun toggleStudentStatus(student: Student) {
        viewModelScope.launch {
            repository.toggleStudentStatus(student)
            _uiState.update { it.copy(statusMessage = "Status updated") }
        }
    }

    // Batch CRUD
    fun saveBatch(batch: Batch) {
        viewModelScope.launch {
            repository.saveBatch(batch)
            _uiState.update { it.copy(statusMessage = "Batch saved: ${batch.name}") }
        }
    }

    fun deleteBatch(batch: Batch) {
        viewModelScope.launch {
            repository.deleteBatch(batch)
            _uiState.update { it.copy(statusMessage = "Batch deleted") }
        }
    }

    // Staff CRUD
    fun saveStaff(staffMember: Staff) {
        viewModelScope.launch {
            repository.saveStaff(staffMember)
            _uiState.update { it.copy(statusMessage = "Staff member saved: ${staffMember.name}") }
        }
    }

    fun deleteStaff(staffMember: Staff) {
        viewModelScope.launch {
            repository.deleteStaff(staffMember)
            _uiState.update { it.copy(statusMessage = "Staff member removed") }
        }
    }

    // Attendance
    fun markAttendance(date: String, attendanceMap: Map<String, String>, batchId: String?) {
        viewModelScope.launch {
            val studentLookup = students.value.associateBy { it.id }
            val records = attendanceMap.map { (studentId, status) ->
                AttendanceRecord(
                    id = "${date}_${studentId}",
                    date = date,
                    studentId = studentId,
                    batchId = batchId ?: studentLookup[studentId]?.batchId,
                    status = status
                )
            }
            repository.recordAttendanceBatch(date, records)
            _uiState.update { it.copy(statusMessage = "Attendance saved for ${FeeCalculator.formatDate(date)}") }
        }
    }

    // Payments
    fun recordPayment(studentId: String, amount: Double, date: String, mode: String, remarks: String) {
        viewModelScope.launch {
            val payment = PaymentRecord(
                studentId = studentId,
                amount = amount,
                paymentDate = date,
                paymentMode = mode,
                remarks = remarks
            )
            repository.recordPayment(payment)
            _uiState.update { it.copy(statusMessage = "Payment recorded successfully") }
        }
    }

    fun deletePayment(payment: PaymentRecord) {
        viewModelScope.launch {
            repository.deletePayment(payment)
            _uiState.update { it.copy(statusMessage = "Transaction removed") }
        }
    }

    // Expenses
    fun recordExpense(date: String, category: String, description: String, amount: Double) {
        viewModelScope.launch {
            val expense = ExpenseRecord(
                date = date,
                category = category,
                description = description,
                amount = amount
            )
            repository.recordExpense(expense)
            _uiState.update { it.copy(statusMessage = "Expense logged") }
        }
    }

    fun deleteExpense(expense: ExpenseRecord) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
            _uiState.update { it.copy(statusMessage = "Expense deleted") }
        }
    }

    // Exams
    fun saveExam(exam: Exam) {
        viewModelScope.launch {
            repository.saveExam(exam)
            _uiState.update { it.copy(statusMessage = "Exam scheduled") }
        }
    }

    fun deleteExam(exam: Exam) {
        viewModelScope.launch {
            repository.deleteExam(exam)
            _uiState.update { it.copy(statusMessage = "Exam deleted") }
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
            _uiState.update { it.copy(statusMessage = "Marks saved successfully") }
        }
    }

    // Communication Log
    fun addCommunicationLog(studentId: String, note: String, date: String = FeeCalculator.getTodayStr()) {
        viewModelScope.launch {
            val log = CommunicationLog(
                studentId = studentId,
                date = date,
                note = note
            )
            repository.addCommunicationLog(log)
            _uiState.update { it.copy(statusMessage = "Note added to log") }
        }
    }

    fun deleteCommunicationLog(log: CommunicationLog) {
        viewModelScope.launch {
            repository.deleteCommunicationLog(log)
            _uiState.update { it.copy(statusMessage = "Note deleted") }
        }
    }

    // Settings & Dark Mode
    fun updateConfig(newConfig: InstituteConfig) {
        viewModelScope.launch {
            repository.saveConfig(newConfig)
            _uiState.update { it.copy(statusMessage = "Institute Profile Saved") }
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
            _uiState.update { it.copy(statusMessage = "Factory reset complete.") }
        }
    }

    fun seedDemoData() {
        viewModelScope.launch {
            repository.seedDemoData()
            _uiState.update { it.copy(statusMessage = "Demo data loaded", currentTab = AppNavDestination.DASHBOARD) }
        }
    }

    // Backup Export & Import
    fun exportBackupJson(context: Context) {
        viewModelScope.launch {
            val json = repository.exportBackupJson()
            ShareHelper.shareBackupJsonFile(context, json)
            _uiState.update { it.copy(statusMessage = "Backup JSON generated") }
        }
    }

    fun importBackupJson(jsonStr: String) {
        viewModelScope.launch {
            val res = repository.importBackupJson(jsonStr)
            _uiState.update {
                it.copy(
                    statusMessage = if (res.isSuccess) res.getOrNull() else "Invalid backup file: ${res.exceptionOrNull()?.localizedMessage}"
                )
            }
        }
    }

    // PDF Receipt & Full History Report
    fun generateAndShareReceipt(context: Context, student: Student, preferWhatsApp: Boolean = false) {
        viewModelScope.launch {
            val studentPayments = payments.value.filter { it.studentId == student.id }
            val batch = batches.value.find { it.id == student.batchId }
            val currentConfig = config.value
            val dueInfo = FeeCalculator.calculateStudentDues(student, studentPayments)

            val pdfFile = PdfReceiptGenerator.generateReceiptPdf(
                context = context,
                student = student,
                batch = batch,
                config = currentConfig,
                dueInfo = dueInfo,
                recentPayments = studentPayments
            )

            ShareHelper.sharePdfReceipt(context, pdfFile, student, dueInfo.pendingBalance, preferWhatsApp)
        }
    }

    fun generateAndShareFullHistory(context: Context, student: Student) {
        viewModelScope.launch {
            val studentPayments = payments.value.filter { it.studentId == student.id }
            val studentAtt = attendance.value.filter { it.studentId == student.id }
            val batch = batches.value.find { it.id == student.batchId }
            val currentConfig = config.value
            val dueInfo = FeeCalculator.calculateStudentDues(student, studentPayments)

            val pdfFile = PdfReceiptGenerator.generateFullHistoryPdf(
                context = context,
                student = student,
                batch = batch,
                config = currentConfig,
                dueInfo = dueInfo,
                payments = studentPayments,
                attendance = studentAtt
            )

            ShareHelper.sharePdfReceipt(context, pdfFile, student, dueInfo.pendingBalance, preferWhatsApp = false)
        }
    }

    // Cloud Sync
    fun triggerCloudSync(pullFirst: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true) }
            val result = repository.syncWithCloud(pullFirst = pullFirst)
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    isCloudConnected = result.isSuccess,
                    statusMessage = if (result.isSuccess) result.getOrNull() else "Offline mode active (Local changes saved safely)"
                )
            }
        }
    }

    // Analytical Helpers (Exact match with v10studendrecord.html reportsModule)
    fun getMonthlyFinanceData(): List<MonthlyFinanceData> {
        val result = mutableListOf<MonthlyFinanceData>()
        val cal = Calendar.getInstance()
        val monthFmt = SimpleDateFormat("MMM yy", Locale("en", "IN"))
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
        val monthFmt = SimpleDateFormat("MMM yy", Locale("en", "IN"))
        val isoMonthFmt = SimpleDateFormat("yyyy-MM", Locale.US)

        for (i in 5 downTo 0) {
            val c = cal.clone() as Calendar
            c.set(Calendar.DAY_OF_MONTH, 1)
            c.add(Calendar.MONTH, -i)
            val monthLabel = monthFmt.format(c.time)
            val isoMonth = isoMonthFmt.format(c.time)

            val monthRecords = attendance.value.filter { it.date.startsWith(isoMonth) }
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
