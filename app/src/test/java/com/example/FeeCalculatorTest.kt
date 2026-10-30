package com.example

import com.example.data.model.AttendanceRecord
import com.example.data.model.PaymentRecord
import com.example.data.model.Student
import com.example.util.FeeCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeeCalculatorTest {

    @Test
    fun testCurrencyFormatting() {
        val formatted = FeeCalculator.formatCurrency(1500.0)
        assertTrue(formatted.contains("1,500") || formatted.contains("1500"))
    }

    @Test
    fun testFeeAccrualAndDues() {
        val student = Student(
            id = "test-stu-1",
            rollNo = "101",
            name = "Test Student",
            phone = "9876543210",
            monthlyFee = 1500.0,
            joiningDate = "2025-01-01",
            status = "active"
        )

        val payments = listOf(
            PaymentRecord(
                studentId = student.id,
                amount = 1500.0,
                paymentDate = "2025-01-05"
            )
        )

        val dueInfo = FeeCalculator.calculateStudentDues(student, payments)

        // Since 2025-01-01 is more than 30 days ago, at least 1 cycle has elapsed
        assertTrue(dueInfo.cyclesElapsed >= 1)
        assertEquals(1500.0, dueInfo.totalPaid, 0.01)
        assertEquals(dueInfo.totalAccrued - 1500.0, dueInfo.pendingBalance, 0.01)
    }

    @Test
    fun testAttendanceDrivenBillingWithHolidaysAndLeaves() {
        val student = Student(
            id = "stu-billing",
            rollNo = "102",
            name = "Aarav Sharma",
            phone = "9876543210",
            monthlyFee = 2000.0,
            joiningDate = "2025-01-01",
            status = "active"
        )

        val records = mutableListOf<AttendanceRecord>()
        // 27 present days + 3 absent days = 30 billable working days
        for (i in 1..27) {
            records.add(
                AttendanceRecord(
                    studentId = student.id,
                    date = String.format("2025-01-%02d", i),
                    status = "present"
                )
            )
        }
        for (i in 28..30) {
            records.add(
                AttendanceRecord(
                    studentId = student.id,
                    date = String.format("2025-01-%02d", i),
                    status = "absent"
                )
            )
        }
        // Add 5 tuition holidays and 2 approved medical leaves -> should NOT increment billable days
        for (i in 1..5) {
            records.add(
                AttendanceRecord(
                    studentId = student.id,
                    date = String.format("2025-02-%02d", i),
                    status = "holiday",
                    reason = "Festival Holiday"
                )
            )
        }
        for (i in 6..7) {
            records.add(
                AttendanceRecord(
                    studentId = student.id,
                    date = String.format("2025-02-%02d", i),
                    status = "leave",
                    reason = "Medical Leave"
                )
            )
        }

        val dueInfo = FeeCalculator.calculateStudentDues(
            student = student,
            payments = emptyList(),
            attendanceRecords = records,
            classesPerCycle = 30
        )

        assertEquals(27, dueInfo.presentDays)
        assertEquals(3, dueInfo.absentDays)
        assertEquals(5, dueInfo.holidayDays)
        assertEquals(2, dueInfo.leaveDays)
        assertEquals(30, dueInfo.daysElapsed)
        assertEquals(1, dueInfo.cyclesElapsed)
        assertEquals(2000.0, dueInfo.totalAccrued, 0.01)
        assertEquals(2000.0, dueInfo.pendingBalance, 0.01)
        assertEquals(0, dueInfo.currentCycleProgress)
        assertEquals(30, dueInfo.classesLeftInCycle)
    }

    @Test
    fun testCustomBatchCycleLength26Days() {
        val student = Student(
            id = "stu-26",
            rollNo = "103",
            name = "Priya Patel",
            phone = "9876543211",
            monthlyFee = 1800.0,
            joiningDate = "2025-01-01",
            status = "active"
        )

        val records = (1..24).map { day ->
            AttendanceRecord(
                studentId = student.id,
                date = String.format("2025-01-%02d", day),
                status = "present"
            )
        }

        val dueInfo = FeeCalculator.calculateStudentDues(
            student = student,
            payments = emptyList(),
            attendanceRecords = records,
            classesPerCycle = 26
        )

        assertEquals(24, dueInfo.daysElapsed)
        assertEquals(0, dueInfo.cyclesElapsed)
        assertEquals(24, dueInfo.currentCycleProgress)
        assertEquals(2, dueInfo.classesLeftInCycle)
    }
}

