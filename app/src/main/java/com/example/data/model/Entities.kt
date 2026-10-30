package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "students")
data class Student(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val rollNo: String = "", // Maps to "roll" in Firebase v10studendrecord.html
    val name: String,
    val phone: String,
    val parentPhone: String = "",
    val batchId: String? = null,
    val monthlyFee: Double,
    val joiningDate: String, // YYYY-MM-DD
    val status: String = "active", // "active" or "inactive"
    val notes: String = ""
)

@Entity(tableName = "batches")
data class Batch(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val subject: String,
    val schedule: String = "",
    val timing: String = "",
    val teacherId: String? = null, // Maps to "teacherId" in Firebase v10studendrecord.html
    val teacherName: String = "",
    val classesPerCycle: Int = 0 // 0 = inherit global InstituteConfig.defaultClassesPerCycle (e.g., 30 or 26)
)

@Entity(tableName = "staff")
data class Staff(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val role: String = "Teacher", // "Teacher", "Admin", "Support"
    val subject: String = "",
    val phone: String = "",
    val salary: Double = 0.0
)

@Entity(tableName = "attendance")
data class AttendanceRecord(
    val date: String, // YYYY-MM-DD
    val studentId: String,
    val batchId: String? = null,
    val status: String = "present", // "present", "absent", "holiday", or "leave"
    val reason: String = "", // Reason for holiday or approved medical leave
    @PrimaryKey val id: String = "${date}_${studentId}"
)

@Entity(tableName = "payments")
data class PaymentRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), // Maps to "transactionId" in Firebase
    val studentId: String,
    val amount: Double, // Maps to "amountPaid" in Firebase
    val paymentDate: String, // YYYY-MM-DD
    val paymentMode: String = "Cash", // Maps to "mode" ("Cash", "UPI", "Bank Transfer")
    val remarks: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isoTimestamp: String = "" // Preserves exact ISO timestamp string from web app
)

@Entity(tableName = "expenses")
data class ExpenseRecord(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: String, // YYYY-MM-DD
    val category: String, // "Rent", "Salary", "Electricity", "Maintenance", "Marketing", "Other"
    val description: String,
    val amount: Double
)

@Entity(tableName = "exams")
data class Exam(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val batchId: String,
    val date: String, // YYYY-MM-DD
    val totalMarks: Int
)

@Entity(tableName = "exam_results")
data class ExamResult(
    val examId: String,
    val studentId: String,
    val marksObtained: Double,
    @PrimaryKey val id: String = "${examId}_${studentId}"
)

@Entity(tableName = "communication_logs")
data class CommunicationLog(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val studentId: String,
    val date: String, // YYYY-MM-DD
    val note: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isoTimestamp: String = ""
)

@Entity(tableName = "institute_config")
data class InstituteConfig(
    @PrimaryKey val id: Int = 1,
    val instituteName: String = "CoachMaster",
    val address: String = "",
    val contactPhone: String = "",
    val adminPin: String = "8833",
    val adminPassword: String = "P@88337",
    val darkMode: Boolean = false,
    val isBiometricEnabled: Boolean = true,
    val currencySymbol: String = "₹",
    val defaultClassesPerCycle: Int = 30
)
