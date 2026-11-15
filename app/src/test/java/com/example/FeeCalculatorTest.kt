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

    @Test
    fun testInstantDiscountClearsPendingBalance() {
        // User scenario: Student completes 30 working days, ₹150 fee becomes due.
        // Parent pays ₹100 cash and teacher grants ₹50 instant discount.
        // Pending balance (khata) must become ₹0 (cleared).
        val student = Student(
            id = "stu-discount",
            rollNo = "104",
            name = "Rohan Verma",
            phone = "9876543212",
            monthlyFee = 150.0,
            joiningDate = "2025-01-01",
            status = "active"
        )

        val attendanceRecords = (1..30).map { day ->
            AttendanceRecord(
                studentId = student.id,
                date = String.format("2025-01-%02d", day),
                status = "present"
            )
        }

        val payments = listOf(
            PaymentRecord(
                studentId = student.id,
                amount = 100.0,
                discount = 50.0,
                paymentDate = "2025-01-31",
                paymentMode = "Cash",
                remarks = "₹50 concession waived"
            )
        )

        val dueInfo = FeeCalculator.calculateStudentDues(
            student = student,
            payments = payments,
            attendanceRecords = attendanceRecords,
            classesPerCycle = 30
        )

        assertEquals(1, dueInfo.cyclesElapsed)
        assertEquals(150.0, dueInfo.totalAccrued, 0.01)
        assertEquals(100.0, dueInfo.totalPaid, 0.01)
        assertEquals(50.0, dueInfo.totalDiscount, 0.01)
        assertEquals(0.0, dueInfo.pendingBalance, 0.01)
        assertEquals(false, dueInfo.isOverdue)
    }

    @Test
    fun testSecurityUtilsHashingAndVerification() {
        val rawPin = "4590"
        val hashedPin = com.example.util.SecurityUtils.hashSecret(rawPin)
        assertTrue(com.example.util.SecurityUtils.isHashed(hashedPin))
        assertTrue(com.example.util.SecurityUtils.verifySecret(rawPin, hashedPin))
        assertEquals(false, com.example.util.SecurityUtils.verifySecret("0000", hashedPin))
        assertEquals(false, com.example.util.SecurityUtils.verifySecret("P@88337", hashedPin))

        // Verify default PIN (8833) and default Password (P@88337) match DEFAULT hashes
        assertTrue(
            com.example.util.SecurityUtils.verifySecret(
                "8833",
                com.example.util.SecurityUtils.DEFAULT_ADMIN_PIN_HASH
            )
        )
        assertTrue(
            com.example.util.SecurityUtils.verifySecret(
                "P@88337",
                com.example.util.SecurityUtils.DEFAULT_ADMIN_PASSWORD_HASH
            )
        )
    }

    @Test
    fun testStudentFormInputValidation() {
        // Blank roll number check
        val blankRoll = com.example.util.SecurityUtils.validateStudentInput(
            rollNo = "   ",
            name = "Rahul Kumar",
            phone = "9876543210",
            monthlyFeeStr = "1500"
        )
        assertTrue(blankRoll != null)

        // Blank name check
        val blankName = com.example.util.SecurityUtils.validateStudentInput(
            rollNo = "101",
            name = "",
            phone = "9876543210",
            monthlyFeeStr = "1500"
        )
        assertTrue(blankName != null)

        // Invalid phone (not 10 digits)
        val shortPhone = com.example.util.SecurityUtils.validateStudentInput(
            rollNo = "101",
            name = "Rahul Kumar",
            phone = "98765",
            monthlyFeeStr = "1500"
        )
        assertTrue(shortPhone != null)

        // Invalid monthly fee (0 or negative)
        val zeroFee = com.example.util.SecurityUtils.validateStudentInput(
            rollNo = "101",
            name = "Rahul Kumar",
            phone = "9876543210",
            monthlyFeeStr = "0"
        )
        assertTrue(zeroFee != null)

        // Valid input
        val valid = com.example.util.SecurityUtils.validateStudentInput(
            rollNo = "101",
            name = "Rahul Kumar",
            phone = "9876543210",
            monthlyFeeStr = "1500"
        )
        assertEquals(null, valid)
    }

    @Test
    fun testGuestModePhoneMasking() {
        val rawPhone = "9876543210"
        val maskedGuest = com.example.util.SecurityUtils.maskPhoneNumber(rawPhone, isAdmin = false)
        val unmaskedAdmin = com.example.util.SecurityUtils.maskPhoneNumber(rawPhone, isAdmin = true)

        assertEquals("98765*****", maskedGuest)
        assertEquals("9876543210", unmaskedAdmin)
    }

    @Test
    fun testEncryptedBackupRoundTrip() {
        val sampleJson = """{"students":[{"id":"stu1","name":"Aarav"}],"attendance":{},"payments":[]}"""
        val encrypted = com.example.util.SecurityUtils.encryptBackupPayload(sampleJson)
        assertTrue(encrypted.startsWith("CM_ENC_V1:"))
        val decrypted = com.example.util.SecurityUtils.decryptBackupPayloadIfNeeded(encrypted)
        assertEquals(sampleJson, decrypted)
    }

    @Test
    fun testWorkSchedulerDelayComputation() {
        val delayTo9Am = com.example.worker.WorkScheduler.computeDelayUntilHour(9, 0)
        assertTrue(delayTo9Am > 0L)
        assertTrue(delayTo9Am <= 24 * 60 * 60 * 1000L)
    }
}


