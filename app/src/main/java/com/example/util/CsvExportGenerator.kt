package com.example.util

import android.content.Context
import com.example.data.model.*
import java.io.File

object CsvExportGenerator {

    private fun escapeCsv(value: String): String {
        val cleaned = value.replace("\r", " ").replace("\n", " ")
        return if (cleaned.contains(",") || cleaned.contains("\"")) {
            "\"${cleaned.replace("\"", "\"\"")}\""
        } else {
            cleaned
        }
    }

    /**
     * 1. Monthly / Full Fee Ledger CSV for CA & Accounting
     */
    fun generateFeeLedgerCsv(
        context: Context,
        payments: List<PaymentRecord>,
        students: List<Student>,
        batches: List<Batch>
    ): File {
        val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(receiptsDir, "CoachMaster_Fee_Ledger_${FeeCalculator.getTodayStr()}.csv")

        val studentMap = students.associateBy { it.id }
        val batchMap = batches.associateBy { it.id }

        val sb = StringBuilder()
        // UTF-8 BOM so Microsoft Excel opens columns and currency symbols cleanly
        sb.append("\uFEFF")
        sb.appendLine("Payment ID,Date,Time,Student Name,Roll No,Batch,Phone,Amount Paid (INR),Discount (INR),Total Credited (INR),Payment Mode,Remarks")

        payments.sortedByDescending { it.paymentDate }.forEach { payment ->
            val stu = studentMap[payment.studentId]
            val batchName = stu?.batchId?.let { batchMap[it]?.name } ?: "Unassigned"
            val timeStr = FeeCalculator.formatTimeFromIso(payment.isoTimestamp, payment.timestamp)
            val totalCredited = payment.amount + payment.discount

            sb.appendLine(
                listOf(
                    escapeCsv(payment.id.take(8).uppercase()),
                    escapeCsv(payment.paymentDate),
                    escapeCsv(timeStr),
                    escapeCsv(stu?.name ?: "Unknown"),
                    escapeCsv(stu?.rollNo ?: "-"),
                    escapeCsv(batchName),
                    escapeCsv(stu?.phone ?: "-"),
                    String.format("%.2f", payment.amount),
                    String.format("%.2f", payment.discount),
                    String.format("%.2f", totalCredited),
                    escapeCsv(payment.paymentMode),
                    escapeCsv(payment.remarks)
                ).joinToString(",")
            )
        }

        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    /**
     * 2. Attendance Register CSV
     */
    fun generateAttendanceRegisterCsv(
        context: Context,
        attendance: List<AttendanceRecord>,
        students: List<Student>,
        batches: List<Batch>
    ): File {
        val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(receiptsDir, "CoachMaster_Attendance_Register_${FeeCalculator.getTodayStr()}.csv")

        val studentMap = students.associateBy { it.id }
        val batchMap = batches.associateBy { it.id }

        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.appendLine("Date,Student Name,Roll No,Batch,Phone,Status,Billable Day Impact,Reason / Notes")

        attendance.sortedWith(compareByDescending<AttendanceRecord> { it.date }.thenBy { studentMap[it.studentId]?.name ?: "" })
            .forEach { rec ->
                val stu = studentMap[rec.studentId]
                val batchName = stu?.batchId?.let { batchMap[it]?.name } ?: "Unassigned"
                val billableImpact = if (rec.status == "present" || rec.status == "absent") "+1 Working Day" else "0 Day (Fee Paused)"

                sb.appendLine(
                    listOf(
                        escapeCsv(rec.date),
                        escapeCsv(stu?.name ?: "Unknown"),
                        escapeCsv(stu?.rollNo ?: "-"),
                        escapeCsv(batchName),
                        escapeCsv(stu?.phone ?: "-"),
                        escapeCsv(rec.status.uppercase()),
                        escapeCsv(billableImpact),
                        escapeCsv(rec.reason)
                    ).joinToString(",")
                )
            }

        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    /**
     * 3. Institute Expense Report CSV for CA & Accounting
     */
    fun generateExpenseReportCsv(
        context: Context,
        expenses: List<ExpenseRecord>
    ): File {
        val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(receiptsDir, "CoachMaster_Expense_Report_${FeeCalculator.getTodayStr()}.csv")

        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.appendLine("Expense ID,Date,Category,Description,Amount (INR)")

        expenses.sortedByDescending { it.date }.forEach { exp ->
            sb.appendLine(
                listOf(
                    escapeCsv(exp.id.take(8).uppercase()),
                    escapeCsv(exp.date),
                    escapeCsv(exp.category),
                    escapeCsv(exp.description),
                    String.format("%.2f", exp.amount)
                ).joinToString(",")
            )
        }

        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    /**
     * 4. Complete Student Accounting & Attendance Master Summary CSV
     */
    fun generateStudentMasterSummaryCsv(
        context: Context,
        students: List<Student>,
        batches: List<Batch>,
        payments: List<PaymentRecord>,
        attendance: List<AttendanceRecord>,
        config: InstituteConfig
    ): File {
        val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val file = File(receiptsDir, "CoachMaster_Student_Master_Summary_${FeeCalculator.getTodayStr()}.csv")

        val batchMap = batches.associateBy { it.id }
        val paymentsByStu = payments.groupBy { it.studentId }
        val attendanceByStu = attendance.groupBy { it.studentId }

        val sb = StringBuilder()
        sb.append("\uFEFF")
        sb.appendLine("Roll No,Student Name,Phone,Batch,Status,Joining Date,Cycle Fee (INR),Working Days,Present Days,Absent Days,Attendance %,Cycles Completed,Total Accrued (INR),Total Paid (INR),Total Discount (INR),Pending Balance (INR)")

        students.forEach { stu ->
            val batch = stu.batchId?.let { batchMap[it] }
            val cycleLen = FeeCalculator.resolveClassesPerCycle(batch, config)
            val stuPayments = paymentsByStu[stu.id] ?: emptyList()
            val stuAtt = attendanceByStu[stu.id] ?: emptyList()
            val due = FeeCalculator.calculateStudentDues(
                student = stu,
                payments = stuPayments,
                attendanceRecords = stuAtt,
                classesPerCycle = cycleLen
            )
            val totalClasses = due.presentDays + due.absentDays
            val attPct = if (totalClasses > 0) Math.round((due.presentDays.toFloat() / totalClasses) * 100f) else 100

            sb.appendLine(
                listOf(
                    escapeCsv(stu.rollNo.ifBlank { stu.id.take(8) }),
                    escapeCsv(stu.name),
                    escapeCsv(stu.phone),
                    escapeCsv(batch?.name ?: "Unassigned"),
                    escapeCsv(stu.status.uppercase()),
                    escapeCsv(stu.joiningDate),
                    String.format("%.2f", stu.monthlyFee),
                    due.daysElapsed.toString(),
                    due.presentDays.toString(),
                    due.absentDays.toString(),
                    "$attPct%",
                    due.cyclesElapsed.toString(),
                    String.format("%.2f", due.totalAccrued),
                    String.format("%.2f", due.totalPaid),
                    String.format("%.2f", due.totalDiscount),
                    String.format("%.2f", due.pendingBalance)
                ).joinToString(",")
            )
        }

        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }
}
