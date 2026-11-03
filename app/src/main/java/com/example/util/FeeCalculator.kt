package com.example.util

import com.example.data.model.AttendanceRecord
import com.example.data.model.Batch
import com.example.data.model.InstituteConfig
import com.example.data.model.PaymentRecord
import com.example.data.model.Student
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class StudentDueInfo(
    val cyclesElapsed: Int,
    val daysElapsed: Long, // Total Billable Open Days (present + absent)
    val classesPerCycle: Int = 30,
    val currentCycleProgress: Int = 0, // e.g., 24 out of 30
    val classesLeftInCycle: Int = 30, // e.g., 6 classes left for next fee
    val presentDays: Int = 0,
    val absentDays: Int = 0,
    val holidayDays: Int = 0,
    val leaveDays: Int = 0,
    val totalAccrued: Double,
    val totalPaid: Double,
    val totalDiscount: Double = 0.0,
    val pendingBalance: Double,
    val nextDueDateStr: String,
    val isOverdue: Boolean
)

object FeeCalculator {
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("en-IN"))
    private val dayOfWeekFormat = SimpleDateFormat("EEEE", Locale.forLanguageTag("en-IN"))
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).apply {
        maximumFractionDigits = 0
    }

    fun getTodayStr(): String = isoDateFormat.format(Date())

    fun getYesterdayStr(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        return isoDateFormat.format(cal.time)
    }

    fun isSunday(dateStr: String): Boolean {
        return try {
            val clean = dateStr.substringBefore("T")
            val parsed = isoDateFormat.parse(clean) ?: return false
            val cal = Calendar.getInstance().apply { time = parsed }
            cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        } catch (e: Exception) {
            false
        }
    }

    fun getDayOfWeekLabel(dateStr: String): String {
        return try {
            val clean = dateStr.substringBefore("T")
            val parsed = isoDateFormat.parse(clean) ?: return ""
            dayOfWeekFormat.format(parsed)
        } catch (e: Exception) {
            ""
        }
    }

    fun generateDateRange(startDateStr: String, endDateStr: String): List<String> {
        return try {
            val start = isoDateFormat.parse(startDateStr.substringBefore("T")) ?: return listOf(startDateStr)
            val end = isoDateFormat.parse(endDateStr.substringBefore("T")) ?: return listOf(startDateStr)
            if (end.before(start)) return listOf(startDateStr)

            val result = mutableListOf<String>()
            val cal = Calendar.getInstance().apply {
                time = start
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val endCal = Calendar.getInstance().apply {
                time = end
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            var safety = 0
            while (!cal.after(endCal) && safety < 120) {
                result.add(isoDateFormat.format(cal.time))
                cal.add(Calendar.DAY_OF_YEAR, 1)
                safety++
            }
            if (result.isEmpty()) listOf(startDateStr) else result
        } catch (e: Exception) {
            listOf(startDateStr)
        }
    }

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
                    return SimpleDateFormat("hh:mm a", Locale.forLanguageTag("en-IN")).format(parsed)
                }
            }
            if (fallbackMillis > 0L) {
                SimpleDateFormat("hh:mm a", Locale.forLanguageTag("en-IN")).format(Date(fallbackMillis))
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

    fun resolveClassesPerCycle(batch: Batch?, config: InstituteConfig?): Int {
        val batchCycle = batch?.classesPerCycle ?: 0
        if (batchCycle > 0) return batchCycle
        val globalCycle = config?.defaultClassesPerCycle ?: 30
        return if (globalCycle > 0) globalCycle else 30
    }

    /**
     * Attendance-Driven Working-Day Billing Engine:
     * 1. Normal Class (Open + Present): +1 Billable Day
     * 2. Student Bunk / Absent (Open + Absent): +1 Billable Day
     * 3. Tuition Holiday (Closed + Holiday): +0 Billable Days (Fee Frozen)
     * 4. Approved Leave (Open + Leave): +0 Billable Days (Fee Paused for student)
     *
     * Instant Discount Support:
     * Both actual cash/online payments (`amount`) and instant concessions (`discount`) settle
     * the student's accrued fee (`pendingBalance = totalAccrued - totalPaid - totalDiscount`).
     */
    fun calculateStudentDues(
        student: Student,
        payments: List<PaymentRecord>,
        attendanceRecords: List<AttendanceRecord>? = null,
        classesPerCycle: Int = 30
    ): StudentDueInfo {
        val effectiveCycleLength = if (classesPerCycle > 0) classesPerCycle else 30
        val cleanJoin = student.joiningDate.substringBefore("T")

        val studentPayments = payments.filter { it.studentId == student.id }
        val totalPaid = studentPayments.sumOf { it.amount }
        val totalDiscount = studentPayments.sumOf { it.discount }

        // Filter student's attendance records on or after joiningDate
        val studentAtt = attendanceRecords?.filter { record ->
            record.studentId == student.id && record.date >= cleanJoin
        }

        if (studentAtt != null) {
            val presentDays = studentAtt.count { it.status == "present" }
            val absentDays = studentAtt.count { it.status == "absent" }
            val holidayDays = studentAtt.count { it.status == "holiday" }
            val leaveDays = studentAtt.count { it.status == "leave" }

            val totalBillableDays = presentDays + absentDays
            val completedCycles = totalBillableDays / effectiveCycleLength
            val currentCycleProgress = totalBillableDays % effectiveCycleLength
            val classesLeft = effectiveCycleLength - currentCycleProgress

            val totalAccrued = completedCycles * student.monthlyFee
            val pendingBalance = totalAccrued - totalPaid - totalDiscount

            val nextDueStatusStr = if (pendingBalance > 0) {
                "Fee Due Now! ($completedCycles Cycle${if (completedCycles > 1) "s" else ""} Completed)"
            } else {
                "$classesLeft classes left for next fee"
            }

            return StudentDueInfo(
                cyclesElapsed = completedCycles,
                daysElapsed = totalBillableDays.toLong(),
                classesPerCycle = effectiveCycleLength,
                currentCycleProgress = currentCycleProgress,
                classesLeftInCycle = classesLeft,
                presentDays = presentDays,
                absentDays = absentDays,
                holidayDays = holidayDays,
                leaveDays = leaveDays,
                totalAccrued = totalAccrued,
                totalPaid = totalPaid,
                totalDiscount = totalDiscount,
                pendingBalance = pendingBalance,
                nextDueDateStr = nextDueStatusStr,
                isOverdue = pendingBalance > 0.0
            )
        }

        // Legacy fallback when attendanceRecords is not supplied
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val joinCal = Calendar.getInstance().apply {
            try {
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
        val daysElapsed = Math.floorDiv(timeDiff, 1000L * 3600L * 24L).coerceAtLeast(0L)

        val cycles = Math.floorDiv(daysElapsed, effectiveCycleLength.toLong()).toInt().coerceAtLeast(0)
        val currentProgress = (daysElapsed % effectiveCycleLength.toLong()).toInt()
        val classesLeft = effectiveCycleLength - currentProgress
        val totalAccrued = cycles * student.monthlyFee
        val pendingBalance = totalAccrued - totalPaid - totalDiscount

        val nextDueMs = joinCal.timeInMillis + ((cycles + 1L) * effectiveCycleLength * 24L * 60L * 60L * 1000L)
        val nextDueDateStr = displayDateFormat.format(Date(nextDueMs))

        return StudentDueInfo(
            cyclesElapsed = cycles,
            daysElapsed = daysElapsed,
            classesPerCycle = effectiveCycleLength,
            currentCycleProgress = currentProgress,
            classesLeftInCycle = classesLeft,
            presentDays = daysElapsed.toInt(),
            absentDays = 0,
            holidayDays = 0,
            leaveDays = 0,
            totalAccrued = totalAccrued,
            totalPaid = totalPaid,
            totalDiscount = totalDiscount,
            pendingBalance = pendingBalance,
            nextDueDateStr = nextDueDateStr,
            isOverdue = pendingBalance > 0.0
        )
    }
}
