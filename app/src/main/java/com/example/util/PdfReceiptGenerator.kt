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
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), 110f, paint)

        // Accent strip
        paint.color = Color.parseColor("#FBBF24")
        canvas.drawRect(0f, 110f, pageWidth.toFloat(), 116f, paint)

        // Institute Title
        paint.color = Color.WHITE
        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(config.instituteName.ifBlank { "CoachMaster" }, (pageWidth / 2).toFloat(), 48f, paint)

        // Institute Address & Phone
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val subHeader = listOfNotNull(
            config.address.takeIf { it.isNotBlank() },
            config.contactPhone.takeIf { it.isNotBlank() }?.let { "Ph: $it" }
        ).joinToString("  |  ")
        if (subHeader.isNotBlank()) {
            canvas.drawText(subHeader, (pageWidth / 2).toFloat(), 70f, paint)
        }

        // Tagline badge
        paint.color = Color.parseColor("#FBBF24")
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("FEE RECEIPT", (pageWidth / 2).toFloat(), 95f, paint)

        paint.textAlign = Paint.Align.LEFT

        // Date and Receipt ID info below header
        var currentY = 145f
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Generated on: ${FeeCalculator.formatDate(FeeCalculator.getTodayStr())}", 40f, currentY, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("ID/Roll: ${student.rollNo.ifBlank { student.id.take(8) }}", (pageWidth - 40).toFloat(), currentY, paint)
        paint.textAlign = Paint.Align.LEFT

        currentY += 15f
        paint.color = Color.parseColor("#E2E8F0")
        paint.strokeWidth = 1f
        canvas.drawLine(40f, currentY, (pageWidth - 40).toFloat(), currentY, paint)

        // Student Info Box
        currentY += 25f
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Student: ${student.name}", 40f, currentY, paint)

        currentY += 18f
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.parseColor("#334155")
        canvas.drawText("Batch: ${batch?.name ?: "N/A"}", 40f, currentY, paint)
        canvas.drawText("Joining: ${FeeCalculator.formatDate(student.joiningDate)}", 320f, currentY, paint)

        currentY += 16f
        canvas.drawText("Phone: ${student.phone}", 40f, currentY, paint)
        canvas.drawText("Monthly Fee: ${FeeCalculator.formatCurrency(student.monthlyFee)}", 320f, currentY, paint)

        // Financial Overview Card
        currentY += 28f
        paint.color = Color.parseColor("#F8FAFC")
        canvas.drawRoundRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 75f, 12f, 12f, paint)

        paint.color = Color.parseColor("#CBD5E1")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRoundRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 75f, 12f, 12f, paint)
        paint.style = Paint.Style.FILL

        // Accrued
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("TOTAL ACCRUED (${dueInfo.cyclesElapsed} Cycles)", 60f, currentY + 28f, paint)
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 15f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(FeeCalculator.formatCurrency(dueInfo.totalAccrued), 60f, currentY + 54f, paint)

        // Total Paid
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("TOTAL AMOUNT PAID", 240f, currentY + 28f, paint)
        paint.color = Color.parseColor("#10B981")
        paint.textSize = 15f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(FeeCalculator.formatCurrency(dueInfo.totalPaid), 240f, currentY + 54f, paint)

        // Pending Balance
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("REMAINING BALANCE DUE", 400f, currentY + 28f, paint)
        paint.color = if (dueInfo.pendingBalance > 0) Color.parseColor("#EF4444") else Color.parseColor("#10B981")
        paint.textSize = 15f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(FeeCalculator.formatCurrency(dueInfo.pendingBalance), 400f, currentY + 54f, paint)

        // Payment History Table
        currentY += 105f
        paint.color = Color.parseColor("#0F172A")
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Transaction History", 40f, currentY, paint)

        currentY += 15f
        paint.color = Color.parseColor("#F1F5F9")
        canvas.drawRect(40f, currentY, (pageWidth - 40).toFloat(), currentY + 24f, paint)
        paint.color = Color.parseColor("#475569")
        paint.textSize = 10f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Date", 50f, currentY + 16f, paint)
        canvas.drawText("Mode", 160f, currentY + 16f, paint)
        canvas.drawText("Remarks", 260f, currentY + 16f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Amount", (pageWidth - 55).toFloat(), currentY + 16f, paint)
        paint.textAlign = Paint.Align.LEFT

        currentY += 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        if (recentPayments.isEmpty()) {
            paint.color = Color.parseColor("#94A3B8")
            canvas.drawText("No payment records found.", 50f, currentY + 20f, paint)
            currentY += 35f
        } else {
            recentPayments.take(12).forEach { payment ->
                currentY += 20f
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
                canvas.drawLine(40f, currentY + 6f, (pageWidth - 40).toFloat(), currentY + 6f, paint)
                currentY += 6f
            }
        }

        // Signature blocks
        val signY = 740f
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
     * in v10studendrecord.html (includes Financial Summary, Attendance History, and Payment History).
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
        var y = 45f
        paint.color = Color.parseColor("#1E293B")
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("$instName - Student Report", (pageWidth / 2).toFloat(), y, paint)

        y += 20f
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = Color.parseColor("#64748B")
        canvas.drawText(
            "${student.name}  •  Roll: ${student.rollNo.ifBlank { "-" }}  •  Batch: ${batch?.name ?: "N/A"}  •  Joined: ${FeeCalculator.formatDate(student.joiningDate)}",
            (pageWidth / 2).toFloat(),
            y,
            paint
        )
        paint.textAlign = Paint.Align.LEFT

        // 3 Summary Cards
        y += 22f
        paint.color = Color.parseColor("#F8FAFC")
        canvas.drawRoundRect(40f, y, (pageWidth - 40).toFloat(), y + 60f, 8f, 8f, paint)
        paint.color = Color.parseColor("#E2E8F0")
        paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(40f, y, (pageWidth - 40).toFloat(), y + 60f, 8f, 8f, paint)
        paint.style = Paint.Style.FILL

        paint.textSize = 9f
        paint.color = Color.parseColor("#64748B")
        canvas.drawText("TOTAL ACCRUED", 55f, y + 22f, paint)
        canvas.drawText("TOTAL PAID", 240f, y + 22f, paint)
        canvas.drawText("PENDING BALANCE", 410f, y + 22f, paint)

        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.color = Color.parseColor("#1E293B")
        canvas.drawText(FeeCalculator.formatCurrency(dueInfo.totalAccrued), 55f, y + 44f, paint)
        paint.color = Color.parseColor("#10B981")
        canvas.drawText(FeeCalculator.formatCurrency(dueInfo.totalPaid), 240f, y + 44f, paint)
        paint.color = if (dueInfo.pendingBalance > 0) Color.parseColor("#EF4444") else Color.parseColor("#10B981")
        canvas.drawText(FeeCalculator.formatCurrency(dueInfo.pendingBalance), 410f, y + 44f, paint)

        // Attendance History Section
        y += 85f
        paint.color = Color.parseColor("#1E293B")
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Attendance History (${attendance.size} records)", 40f, y, paint)

        y += 10f
        paint.color = Color.parseColor("#F8FAFC")
        canvas.drawRect(40f, y, (pageWidth - 40).toFloat(), y + 20f, paint)
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 10f
        canvas.drawText("Date", 50f, y + 14f, paint)
        canvas.drawText("Status", 300f, y + 14f, paint)

        y += 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        if (attendance.isEmpty()) {
            y += 16f
            paint.color = Color.parseColor("#94A3B8")
            canvas.drawText("No attendance records", 50f, y, paint)
            y += 10f
        } else {
            attendance.sortedByDescending { it.date }.take(12).forEach { r ->
                y += 16f
                paint.color = Color.parseColor("#1E293B")
                canvas.drawText(FeeCalculator.formatDate(r.date), 50f, y, paint)
                paint.color = if (r.status == "present") Color.parseColor("#10B981") else Color.parseColor("#EF4444")
                canvas.drawText(if (r.status == "present") "Present" else "Absent", 300f, y, paint)
            }
        }

        // Payment History Section
        y += 30f
        paint.color = Color.parseColor("#1E293B")
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Payment History (${payments.size} records)", 40f, y, paint)

        y += 10f
        paint.color = Color.parseColor("#F8FAFC")
        canvas.drawRect(40f, y, (pageWidth - 40).toFloat(), y + 20f, paint)
        paint.color = Color.parseColor("#64748B")
        paint.textSize = 10f
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
            payments.sortedByDescending { it.paymentDate }.take(12).forEach { p ->
                if (y < 780f) {
                    y += 16f
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
