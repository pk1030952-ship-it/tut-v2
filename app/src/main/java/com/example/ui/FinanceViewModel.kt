package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.*
import com.example.data.repository.CoachMasterRepository
import com.example.ui.components.MonthlyFinanceData
import com.example.util.FeeCalculator
import com.example.util.PdfReceiptGenerator
import com.example.util.ShareHelper
import com.example.util.StudentDueInfo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CoachMasterRepository.getInstance(application)

    private val _selectedStudentForPayment = MutableStateFlow<Student?>(null)
    val selectedStudentForPayment: StateFlow<Student?> = _selectedStudentForPayment.asStateFlow()

    private val _selectedStudentForShareOptions = MutableStateFlow<Student?>(null)
    val selectedStudentForShareOptions: StateFlow<Student?> = _selectedStudentForShareOptions.asStateFlow()

    val students: StateFlow<List<Student>> = repository.allStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeStudents: StateFlow<List<Student>> = repository.activeStudents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val batches: StateFlow<List<Batch>> = repository.allBatches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val payments: StateFlow<List<PaymentRecord>> = repository.allPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenses: StateFlow<List<ExpenseRecord>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val attendance: StateFlow<List<AttendanceRecord>> = repository.allAttendance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val config: StateFlow<InstituteConfig> = repository.config
        .map { it ?: InstituteConfig() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InstituteConfig())

    fun selectStudentForPayment(student: Student?) {
        _selectedStudentForPayment.value = student
    }

    fun openShareOptionsModal(student: Student) {
        _selectedStudentForShareOptions.value = student
    }

    fun closeShareOptionsModal() {
        _selectedStudentForShareOptions.value = null
    }

    fun calculateDuesForStudent(student: Student): StudentDueInfo {
        val batch = batches.value.find { it.id == student.batchId }
        val cycleLen = FeeCalculator.resolveClassesPerCycle(batch, config.value)
        return FeeCalculator.calculateStudentDues(
            student = student,
            payments = payments.value,
            attendanceRecords = attendance.value,
            classesPerCycle = cycleLen
        )
    }

    fun recordPayment(
        studentId: String,
        amount: Double,
        date: String,
        mode: String,
        remarks: String,
        discount: Double = 0.0
    ) {
        if (amount <= 0.0 && discount <= 0.0) {
            repository.postStatusMessage("Amount or discount must be a positive number greater than 0.")
            return
        }
        if (amount < 0.0 || discount < 0.0) {
            repository.postStatusMessage("Amount and discount cannot be negative.")
            return
        }
        viewModelScope.launch {
            val payment = PaymentRecord(
                studentId = studentId,
                amount = amount,
                discount = discount,
                paymentDate = date,
                paymentMode = mode,
                remarks = remarks
            )
            repository.recordPayment(payment)
            val msg = if (discount > 0.0) {
                "Recorded ${FeeCalculator.formatCurrency(amount)} Paid + ${FeeCalculator.formatCurrency(discount)} Discount!"
            } else {
                "Payment of ${FeeCalculator.formatCurrency(amount)} recorded successfully"
            }
            repository.postStatusMessage(msg)
        }
    }

    fun deletePayment(payment: PaymentRecord) {
        viewModelScope.launch {
            repository.deletePayment(payment)
            repository.postStatusMessage("Transaction removed")
        }
    }

    fun recordExpense(date: String, category: String, description: String, amount: Double) {
        if (amount <= 0.0) {
            repository.postStatusMessage("Expense amount must be a positive number greater than 0.")
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

    fun getMonthlyFinanceData(
        allPayments: List<PaymentRecord> = payments.value,
        allExpenses: List<ExpenseRecord> = expenses.value
    ): List<MonthlyFinanceData> {
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

            val monthIncome = allPayments
                .filter { it.paymentDate.startsWith(isoMonth) }
                .sumOf { it.amount }

            val monthExpense = allExpenses
                .filter { it.date.startsWith(isoMonth) }
                .sumOf { it.amount }

            result.add(MonthlyFinanceData(monthLabel, monthIncome, monthExpense))
        }
        return result
    }
}
