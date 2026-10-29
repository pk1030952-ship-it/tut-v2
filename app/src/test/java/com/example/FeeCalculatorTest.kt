package com.example

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
}
