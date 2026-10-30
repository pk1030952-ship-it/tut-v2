package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.model.AttendanceRecord
import com.example.data.model.Batch
import com.example.data.model.InstituteConfig
import com.example.data.model.PaymentRecord
import com.example.data.model.Student
import java.io.File
import java.io.FileOutputStream

object PdfReceiptGenerator {

    fun generateReceiptPdf(
        context: Context,
        student: Student,
        batch: Batch?,
        config: InstituteConfig,
        dueInfo: StudentDueInfo,
        recentPayments: List<PaymentRecord>
    ): File {
        val pdfDoc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDoc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { isAntiAlias = true }

        // Header Background Banner
        paint.color = Color.parseColor("#1E1B4B")
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 105f, paint)

        // Accent strip
        paint.color = Color.parseColor("#FBBF24")
        canvas.drawRect(0f, 105f, pageWidth.toFloat(), 111f, paint)

        // Institute Title
        paint.color = Color.WHITE
        paint.textSize = 23f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(config.instituteName.ifBlank { "CoachMaster" }, (pageWidth / 2).toFloat(), 45f, paint)

        // Institute Address & Phone
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val subHeader = listOfNotNull(
            config.address.takeIf { it.isNotBlank() },
            config.contactPhone.takeIf { it.isNotBlank() }?.let { "Ph: $it" }
        ).joinToString("  |  ")
        if (subHeader.isNotBlank()) {
            canvas.drawText(subHeader, (pageWidth / 2).toFloat(), 66f, paint)
        }

        // Tagline badge
        paint.color = Color.parseColor("#FBBF24")
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("FEE RECEIPT & WORKING DAYS PROOF", (pageWidth / 2).toFloat(), 90f, paint)

        paint.textAlign = Paint.Align.LEFT

        // Date and Receipt ID info below header
        var currentY = 135f
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Generated on: ${FeeCalculator.formatDate(FeeCalculator.getTodayStr())}", 40f, currentY, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("ID/Roll: ${student.rollNo.ifBlank { student.id.take(8) }}", (pageWidth - 40).toFloat(), currentY, paint)
        paint.textAlign = Paint.Align.LEFT

        currentY += 12f
        paint.color = Color.parseColor("#E2E8F0")
        paint.strokeWidth = 1f
        canvas.drawLine(40f, currentY, (pageWidth - 40).toFloat(), currentY, paint)

        // Student Info Box
        currentY += 22f
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Student: ${student.name}", 40f, currentY, paint)

        currentY += 16f
        paint.textSize = 10.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.parseColor("#334155")
        canvas.drawText("Batch: ${batch?.name ?: "N/A"} (${dueInfo.classesPerCycle} Days/Cycle)", 40f, currentY, paint)
        canvas.drawText("Joining: ${FeeCalculator.formatDate(student.joiningDate)}", 320f, currentY, paint)

        currentY += 15f
        canvas.drawText("Phone: ${student.phone}", 40f, currentY, paint)
        canvas.drawText("Cycle Fee: ${FeeCalculator.formatCurrency(student.monthlyFee)}", 320f, currentY, paint)

        // Financial Overview Card
        currentY += 20f
        paint.color = Color.parseColor("#F8FAFC")
        canvas.drawRoundRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 68f, 10f, 10f, paint)

        paint.color = Color.parseColor("#CBD5E1")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 68f, 10f, 10f, paint)
        paint.style = Paint.Style.FILL

        // Accrued
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("TOTAL ACCRUED (${dueInfo.cyclesElapsed} Cycles)", 56f, currentY + 25f, paint)
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(FeeCalculator.formatCurrency(dueInfo.totalAccrued), 56f, currentY + 48f, paint)

        // Total Paid
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("TOTAL AMOUNT PAID", 240f, currentY + 25f, paint)
        paint.color = Color.parseColor("#10B981")
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(FeeCalculator.formatCurrency(dueInfo.totalPaid), 240f, currentY + 48f, paint)

        // Pending Balance
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("REMAINING BALANCE DUE", 400f, currentY + 25f, paint)
        paint.color = if (dueInfo.pendingBalance > 0) Color.parseColor("#EF4444") else Color.parseColor("#10B981")
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(FeeCalculator.formatCurrency(dueInfo.pendingBalance), 400f, currentY + 48f, paint)

        // Working Days & Holiday Proof Box (Improvement 5)
        currentY += 80f
        paint.color = Color.parseColor("#EEF2FF")
        canvas.drawRoundRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 86f, 10f, 10f, paint)
        paint.color = Color.parseColor("#C7D2FE")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 86f, 10f, 10f, paint)
        paint.style = Paint.Style.FILL

        paint.color = Color.parseColor("#312E81")
        paint.textSize = 10.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ATTENDANCE & WORKING DAYS BILLING PROOF", 54f, currentY + 18f, paint)

        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.textSize = 9.5f
        paint.color = Color.parseColor("#1E293B")
        canvas.drawText(
            "• Total Tuition Open Days: ${dueInfo.daysElapsed} Days (Present: ${dueInfo.presentDays}, Absent/Bunk: ${dueInfo.absentDays})",
            54f,
            currentY + 36f,
            paint
        )
        canvas.drawText(
            "• Tuition Holidays (Not Charged): ${dueInfo.holidayDays} Days (Free / Extended)" +
                if (dueInfo.leaveDays > 0) "  |  Approved Leave (Fee Paused): ${dueInfo.leaveDays} Days" else "",
            54f,
            currentY + 53f,
            paint
        )
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = if (dueInfo.pendingBalance > 0) Color.parseColor("#DC2626") else Color.parseColor("#047857")
        val cycleProofText = if (dueInfo.pendingBalance > 0) {
            "• Current Cycle Progress: ${dueInfo.currentCycleProgress} / ${dueInfo.classesPerCycle} Classes (${dueInfo.cyclesElapsed} Full Cycle${if (dueInfo.cyclesElapsed == 1) "" else "s"} Completed -> Fee Due Now!)"
        } else {
            "• Current Cycle Progress: ${dueInfo.currentCycleProgress} / ${dueInfo.classesPerCycle} Classes Completed (${dueInfo.classesLeftInCycle} classes left for next fee)"
        }
        canvas.drawText(cycleProofText, 54f, currentY + 72f, paint)

        // Payment History Table
        currentY += 108f
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 12.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Transaction History", 40f, currentY, paint)

        currentY += 12f
        paint.color = Color.parseColor("#F1F5F9")
        canvas.drawRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 22f, paint)
        paint.color = Color.parseColor("#475569")
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Date", 50f, currentY + 15f, paint)
        canvas.drawText("Mode", 160f, currentY + 15f, paint)
        canvas.drawText("Remarks", 260f, currentY + 15f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Amount", (pageWidth - 55).toFloat(), currentY + 15f, paint)
        paint.textAlign = Paint.Align.LEFT

        currentY += 22f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        if (recentPayments.isEmpty()) {
            paint.color = Color.parseColor("#94A3B8")
            canvas.drawText("No payment records found.", 50f, currentY + 18f, paint)
            currentY += 30f
        } else {
            recentPayments.take(10).forEach { payment ->
                currentY += 18f
                paint.color = Color.parseColor("#1E293B")
                canvas.drawText(FeeCalculator.formatDate(payment.paymentDate), 50f, currentY, paint)
                canvas.drawText(payment.paymentMode, 160f, currentY, paint)
                val cleanRemarks = payment.remarks.ifBlank { "-" }.take(35)
                canvas.drawText(cleanRemarks, 260f, currentY, paint)

                paint.textAlign = Paint.Align.RIGHT
                paint.color = Color.parseColor("#10B981")
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                canvas.drawText(FeeCalculator.formatCurrency(payment.amount), (pageWidth - 55).toFloat(), currentY, paint)
                paint.textAlign = Paint.Align.LEFT
                paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

                paint.color = Color.parseColor("#F1F5F9")
                paint.strokeWidth = 1f
                canvas.drawLine(40f, currentY + 5f, (pageWidth - 40).toFloat(), currentY + 5f, paint)
                currentY += 5f
            }
        }

        // Signature blocks
        val signY = 745f
        paint.color = Color.parseColor("#64748B")
        paint.strokeWidth = 1f
        canvas.drawLine(50f, signY, 190f, signY, paint)
        canvas.drawLine(400f, signY, 540f, signY, paint)

        paint.textSize = 9f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Student / Parent Signature", 120f, signY + 15f, paint)
        canvas.drawText("Authorized Signature", 470f, signY + 15f, paint)

        paint.textSize = 8f
        paint.color = Color.parseColor("#94A3B8")
        canvas.drawText("This is a system-generated receipt by ${config.instituteName.ifBlank { "CoachMaster" }}.", (pageWidth / 2).toFloat(), 810f, paint)

        pdfDoc.finishPage(page)

        val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val cleanName = student.name.replace("\\s+".toRegex(), "_")
        val outputFile = File(receiptsDir, "${cleanName}_Fee_Receipt.pdf")

        FileOutputStream(outputFile).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()

        return outputFile
    }

    /**
     * Generates the "Print Full History Report" PDF matching window.shareModule.printFullHistory()
     * in v10studendrecord.html (includes Financial Summary, Working Days Proof, Attendance History, and Payment History).
     */
    fun generateFullHistoryPdf(
        context: Context,
        student: Student,
        batch: Batch?,
        config: InstituteConfig,
        dueInfo: StudentDueInfo,
        payments: List<PaymentRecord>,
        attendance: List<AttendanceRecord>
    ): File {
        val pdfDoc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDoc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply { isAntiAlias = true }

        val instName = config.instituteName.ifBlank { "CoachMaster" }

        // Title
        var y = 42f
        paint.color = Color.parseColor("#1E293B")
        paint.textSize = 19f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("$instName - Student Dossier & Report", (pageWidth / 2).toFloat(), y, paint)

        y += 18f
        paint.textSize = 10.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.parseColor("#64748B")
        canvas.drawText(
            "${student.name}  •  Roll: ${student.rollNo.ifBlank { "-" }}  •  Batch: ${batch?.name ?: "N/A"} (${dueInfo.classesPerCycle}d/cycle)  •  Joined: ${FeeCalculator.formatDate(student.joiningDate)}",
            (pageWidth / 2).toFloat(),
            y,
            paint
        )
        paint.textAlign = Paint.Align.LEFT

        // 3 Summary Cards
        y += 18f
        paint.color = Color.parseColor("#F8FAFC")
        canvas.drawRoundRect(40f, y, (pageWidth - 40).toFloat(), y + 54f, 8f, 8f, paint)
        paint.color = Color.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(40f, y, (pageWidth - 40).toFloat(), y + 54f, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        paint.textSize = 9f
        paint.color = Color.parseColor("#64748B")
        canvas.drawText("TOTAL ACCRUED (${dueInfo.cyclesElapsed} CYCLES)", 55f, y + 20f, paint)
        canvas.drawText("TOTAL PAID", 240f, y + 20f, paint)
        canvas.drawText("PENDING BALANCE", 410f, y + 20f, paint)

        paint.textSize = 13.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = Color.parseColor("#1E293B")
        canvas.drawText(FeeCalculator.formatCurrency(dueInfo.totalAccrued), 55f, y + 40f, paint)
        paint.color = Color.parseColor("#10B981")
        canvas.drawText(FeeCalculator.formatCurrency(dueInfo.totalPaid), 240f, y + 40f, paint)
        paint.color = if (dueInfo.pendingBalance > 0) Color.parseColor("#EF4444") else Color.parseColor("#10B981")
        canvas.drawText(FeeCalculator.formatCurrency(dueInfo.pendingBalance), 410f, y + 40f, paint)

        // Working Days & Holiday Proof Box
        y += 66f
        paint.color = Color.parseColor("#EEF2FF")
        canvas.drawRoundRect(40f, y, (pageWidth - 40).toFloat(), y + 58f, 8f, 8f, paint)
        paint.color = Color.parseColor("#312E81")
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(
            "Total Tuition Open Days: ${dueInfo.daysElapsed} Days (Present: ${dueInfo.presentDays}, Absent/Bunk: ${dueInfo.absentDays})",
            52f,
            y + 18f,
            paint
        )
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(
            "Tuition Holidays (Not Charged): ${dueInfo.holidayDays} Days (Free / Extended)  |  Approved Leave: ${dueInfo.leaveDays} Days",
            52f,
            y + 34f,
            paint
        )
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(
            "Current Cycle Progress: ${dueInfo.currentCycleProgress} / ${dueInfo.classesPerCycle} Days Completed (${dueInfo.nextDueDateStr})",
            52f,
            y + 49f,
            paint
        )

        // Attendance History Section
        y += 78f
        paint.color = Color.parseColor("#1E293B")
        paint.textSize = 12.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Attendance & Holiday History (${attendance.size} records)", 40f, y, paint)

        y += 10f
        paint.color = Color.parseColor("#F8FAFC")
        canvas.drawRect(40f, y, (pageWidth - 40).toFloat(), y + 20f, paint)
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 9.5f
        canvas.drawText("Date", 50f, y + 14f, paint)
        canvas.drawText("Status", 220f, y + 14f, paint)
        canvas.drawText("Billing / Reason", 340f, y + 14f, paint)

        y += 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        if (attendance.isEmpty()) {
            y += 16f
            paint.color = Color.parseColor("#94A3B8")
            canvas.drawText("No attendance records", 50f, y, paint)
            y += 10f
        } else {
            attendance.sortedByDescending { it.date }.take(11).forEach { r ->
                y += 15f
                paint.color = Color.parseColor("#1E293B")
                canvas.drawText(FeeCalculator.formatDate(r.date), 50f, y, paint)
                val (statusLabel, statusColor, billingNote) = when (r.status) {
                    "present" -> Triple("Present", "#10B981", "+1 Working Day")
                    "absent" -> Triple("Absent / Bunk", "#EF4444", "+1 Working Day")
                    "holiday" -> Triple("Tuition Holiday", "#64748B", "0 Days (${r.reason.ifBlank { "Free" }})")
                    "leave" -> Triple("Approved Leave", "#D97706", "0 Days (${r.reason.ifBlank { "Fee Paused" }})")
                    else -> Triple(r.status, "#64748B", "")
                }
                paint.color = Color.parseColor(statusColor)
                canvas.drawText(statusLabel, 220f, y, paint)
                paint.color = Color.parseColor("#475569")
                canvas.drawText(billingNote.take(35), 340f, y, paint)
            }
        }

        // Payment History Section
        y += 26f
        paint.color = Color.parseColor("#1E293B")
        paint.textSize = 12.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Payment History (${payments.size} records)", 40f, y, paint)

        y += 10f
        paint.color = Color.parseColor("#F8FAFC")
        canvas.drawRect(40f, y, (pageWidth - 40).toFloat(), y + 20f, paint)
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 9.5f
        canvas.drawText("Date", 50f, y + 14f, paint)
        canvas.drawText("Amount", 180f, y + 14f, paint)
        canvas.drawText("Mode & Remarks", 300f, y + 14f, paint)

        y += 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        if (payments.isEmpty()) {
            y += 16f
            paint.color = Color.parseColor("#94A3B8")
            canvas.drawText("No payment records", 50f, y, paint)
        } else {
            payments.sortedByDescending { it.paymentDate }.take(10).forEach { p ->
                if (y < 780f) {
                    y += 15f
                    paint.color = Color.parseColor("#1E293B")
                    canvas.drawText(FeeCalculator.formatDate(p.paymentDate), 50f, y, paint)
                    paint.color = Color.parseColor("#10B981")
                    canvas.drawText(FeeCalculator.formatCurrency(p.amount), 180f, y, paint)
                    paint.color = Color.parseColor("#475569")
                    canvas.drawText("${p.paymentMode} - ${p.remarks.ifBlank { "-" }}".take(40), 300f, y, paint)
                }
            }
        }

        paint.textSize = 9f
        paint.color = Color.parseColor("#94A3B8")
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Generated on ${FeeCalculator.formatDate(FeeCalculator.getTodayStr())}", (pageWidth / 2).toFloat(), 815f, paint)

        pdfDoc.finishPage(page)

        val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
        val cleanName = student.name.replace("\\s+".toRegex(), "_")
        val outputFile = File(receiptsDir, "${cleanName}_Full_History_Report.pdf")

        FileOutputStream(outputFile).use { out ->
            pdfDoc.writeTo(out)
        }
        pdfDoc.close()

        return outputFile
    }
}
