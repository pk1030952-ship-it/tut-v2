package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.CoachMasterRepository
import com.example.util.FeeCalculator
import com.example.util.PdfReceiptGenerator
import com.example.util.SecurityUtils
import com.example.util.ShareHelper
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class StudentUiState(
    val searchQuery: String = "",
    val selectedBatchFilter: String? = null,
    val selectedStudentForDossier: Student? = null,
    val selectedStudentForAttendanceHistory: Student? = null,
    val selectedStudentForCommLog: Student? = null,
    val selectedStudentForShareOptions: Student? = null
)

class StudentViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CoachMasterRepository.getInstance(application)

    private val _studentUiState = MutableStateFlow(StudentUiState())
    val studentUiState: StateFlow<StudentUiState> = _studentUiState.asStateFlow()

    val students: StateFlow<List<Student>> = repository.allStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeStudents: StateFlow<List<Student>> = repository.activeStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val batches: StateFlow<List<Batch>> = repository.allBatches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payments: StateFlow<List<PaymentRecord>> = repository.allPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val attendance: StateFlow<List<AttendanceRecord>> = repository.allAttendance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val communications: StateFlow<List<CommunicationLog>> = repository.allCommunications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val config: StateFlow<InstituteConfig> = repository.config
        .map { it ?: InstituteConfig() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InstituteConfig())

    fun setSearchQuery(query: String) {
        _studentUiState.update { it.copy(searchQuery = query) }
    }

    fun setSelectedBatchFilter(batchId: String?) {
        _studentUiState.update { it.copy(selectedBatchFilter = batchId) }
    }

    fun openStudentDossier(student: Student) {
        _studentUiState.update { it.copy(selectedStudentForDossier = student) }
    }

    fun closeStudentDossier() {
        _studentUiState.update { it.copy(selectedStudentForDossier = null) }
    }

    fun openAttendanceHistoryModal(student: Student) {
        _studentUiState.update { it.copy(selectedStudentForAttendanceHistory = student) }
    }

    fun closeAttendanceHistoryModal() {
        _studentUiState.update { it.copy(selectedStudentForAttendanceHistory = null) }
    }

    fun openCommLogModal(student: Student) {
        _studentUiState.update { it.copy(selectedStudentForCommLog = student) }
    }

    fun closeCommLogModal() {
        _studentUiState.update { it.copy(selectedStudentForCommLog = null) }
    }

    fun openShareOptionsModal(student: Student) {
        _studentUiState.update { it.copy(selectedStudentForShareOptions = student) }
    }

    fun closeShareOptionsModal() {
        _studentUiState.update { it.copy(selectedStudentForShareOptions = null) }
    }

    fun saveStudent(student: Student) {
        val validationError = SecurityUtils.validateStudentInput(
            rollNo = student.rollNo,
            name = student.name,
            phone = student.phone,
            monthlyFeeStr = student.monthlyFee.toString()
        )
        if (validationError != null) {
            repository.postStatusMessage(validationError)
            return
        }
        viewModelScope.launch {
            repository.saveStudent(student)
            repository.postStatusMessage("Student profile saved & synced: ${student.name}")
        }
    }

    fun deleteStudent(studentId: String) {
        viewModelScope.launch {
            repository.deleteStudent(studentId)
            _studentUiState.update {
                it.copy(
                    selectedStudentForDossier = null,
                    selectedStudentForAttendanceHistory = null,
                    selectedStudentForCommLog = null,
                    selectedStudentForShareOptions = null
                )
            }
            repository.postStatusMessage("Student record permanently deleted")
        }
    }

    fun toggleStudentStatus(student: Student) {
        viewModelScope.launch {
            repository.toggleStudentStatus(student)
            repository.postStatusMessage("Status updated")
        }
    }

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

    fun addCommunicationLog(studentId: String, note: String, date: String = FeeCalculator.getTodayStr()) {
        viewModelScope.launch {
            val log = CommunicationLog(
                studentId = studentId,
                date = date,
                note = note
            )
            repository.addCommunicationLog(log)
            repository.postStatusMessage("Note added to log")
        }
    }

    fun deleteCommunicationLog(log: CommunicationLog) {
        viewModelScope.launch {
            repository.deleteCommunicationLog(log)
            repository.postStatusMessage("Note deleted")
        }
    }

    fun generateAndShareReceipt(context: Context, student: Student, preferWhatsApp: Boolean = false) {
        viewModelScope.launch {
            val studentPayments = payments.value.filter { it.studentId == student.id }
            val batch = batches.value.find { it.id == student.batchId }
            val currentConfig = config.value
            val cycleLen = FeeCalculator.resolveClassesPerCycle(batch, currentConfig)
            val dueInfo = FeeCalculator.calculateStudentDues(
                student = student,
                payments = studentPayments,
                attendanceRecords = attendance.value,
                classesPerCycle = cycleLen
            )

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
            val cycleLen = FeeCalculator.resolveClassesPerCycle(batch, currentConfig)
            val dueInfo = FeeCalculator.calculateStudentDues(
                student = student,
                payments = studentPayments,
                attendanceRecords = attendance.value,
                classesPerCycle = cycleLen
            )

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
}
