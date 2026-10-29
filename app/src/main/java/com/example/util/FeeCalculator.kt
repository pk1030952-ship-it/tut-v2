package com.example.util

import com.example.data.model.PaymentRecord
import com.example.data.model.Student
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class StudentDueInfo(
    val cyclesElapsed: Int,
    val daysElapsed: Long,
    val totalAccrued: Double,
    val totalPaid: Double,
    val pendingBalance: Double,
    val nextDueDateStr: String,
    val isOverdue: Boolean
)

object FeeCalculator {
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale("en", "IN"))
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 0
    }

    fun getTodayStr(): String = isoDateFormat.format(Date())

    fun formatDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return "-"
        return try {
            val cleanDate = if (dateStr.contains("T")) dateStr.substringBefore("T") else dateStr
            val date = isoDateFormat.parse(cleanDate)
            date?.let { displayDateFormat.format(it) } ?: dateStr
        } catch (e: Exception) {
            dateStr
        }
    }

    fun formatTimeFromIso(isoStr: String?, fallbackMillis: Long = 0L): String {
        return try {
            if (!isoStr.isNullOrBlank()) {
                val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                val parsed = parser.parse(isoStr.substringBefore("."))
                if (parsed != null) {
                    return SimpleDateFormat("hh:mm a", Locale("en", "IN")).format(parsed)
                }
            }
            if (fallbackMillis > 0L) {
                SimpleDateFormat("hh:mm a", Locale("en", "IN")).format(Date(fallbackMillis))
            } else ""
        } catch (e: Exception) {
            ""
        }
    }

    fun formatCurrency(amount: Double): String {
        return try {
            currencyFormat.format(amount)
        } catch (e: Exception) {
            "₹${amount.toLong()}"
        }
    }

    /**
     * Exact port of window.utils.calculateDues(student) from v10studendrecord.html:
     * - Normalizes both joiningDate and today to 00:00:00 midnight
     * - daysElapsed = floor((today - joinDate) / (1000 * 3600 * 24))
     * - cycles = max(0, floor(daysElapsed / 30))
     * - totalAccrued = cycles * Number(student.monthlyFee)
     * - totalPaid = sum(studentPayments.amountPaid)
     * - pendingBalance = totalAccrued - totalPaid
     * - nextDueDate = joinDate + ((cycles + 1) * 30 days)
     */
    fun calculateStudentDues(student: Student, payments: List<PaymentRecord>): StudentDueInfo {
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val joinCal = Calendar.getInstance().apply {
            try {
                val cleanJoin = student.joiningDate.substringBefore("T")
                val parsed = isoDateFormat.parse(cleanJoin)
                if (parsed != null) {
                    time = parsed
                }
            } catch (e: Exception) {
                // fallback to today
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val timeDiff = todayCal.timeInMillis - joinCal.timeInMillis
        val daysElapsed = Math.floorDiv(timeDiff, 1000L * 3600L * 24L)

        var cycles = Math.floorDiv(daysElapsed, 30L).toInt()
        if (cycles < 0) cycles = 0

        val totalAccrued = cycles * student.monthlyFee

        val totalPaid = payments
            .filter { it.studentId == student.id }
            .sumOf { it.amount }

        val pendingBalance = totalAccrued - totalPaid

        val nextDueMs = joinCal.timeInMillis + ((cycles + 1L) * 30L * 24L * 60L * 60L * 1000L)
        val nextDueDateStr = displayDateFormat.format(Date(nextDueMs))

        return StudentDueInfo(
            cyclesElapsed = cycles,
            daysElapsed = daysElapsed,
            totalAccrued = totalAccrued,
            totalPaid = totalPaid,
            pendingBalance = pendingBalance,
            nextDueDateStr = nextDueDateStr,
            isOverdue = pendingBalance > 0.0
        )
    }
}
