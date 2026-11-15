package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.Student
import java.io.File
import java.net.URLEncoder

object ShareHelper {

    fun sharePdfReceipt(
        context: Context,
        pdfFile: File,
        student: Student,
        balanceDue: Double,
        preferWhatsApp: Boolean = false
    ) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )

            val shareText = if (balanceDue > 0) {
                "Namaste, fee receipt/report for ${student.name} is attached. Current pending balance: ${FeeCalculator.formatCurrency(balanceDue)}. Thank you!"
            } else {
                "Namaste, official fee receipt/report for ${student.name} is attached. All dues cleared. Thank you!"
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, shareText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            if (preferWhatsApp) {
                val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    setPackage("com.whatsapp")
                }
                if (whatsappIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(whatsappIntent)
                    return
                }
            }

            val chooser = Intent.createChooser(shareIntent, "Share / Print PDF via...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Error sharing PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareCsvFile(context: Context, csvFile: File, reportTitle: String) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                csvFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, reportTitle)
                putExtra(Intent.EXTRA_TEXT, "$reportTitle (${csvFile.name}) exported from CoachMaster for accounting / Excel.")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Export / Share $reportTitle (.CSV)...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "CSV export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareBackupJsonFile(context: Context, jsonContent: String) {
        try {
            val receiptsDir = File(context.cacheDir, "receipts").apply { mkdirs() }
            val backupFile = File(receiptsDir, "coachmaster_backup_${FeeCalculator.getTodayStr()}.json")
            backupFile.writeText(jsonContent)

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                backupFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, backupFile.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Save / Share Backup JSON...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Backup export error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareEncryptedBackupFile(context: Context, encryptedFile: File) {
        try {
            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                encryptedFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, encryptedFile.name)
                putExtra(Intent.EXTRA_TEXT, "CoachMaster Encrypted Auto-Backup (${encryptedFile.name})")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Encrypted Auto-Backup...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Encrypted backup share error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendLowAttendanceWarning(
        context: Context,
        student: Student,
        attendancePercent: Int,
        presentDays: Int,
        totalDays: Int,
        instituteName: String
    ) {
        val inst = instituteName.ifBlank { "CoachMaster" }
        val message = "⚠️ Attendance Alert ($inst): Namaste, aapke ward ${student.name} (Roll No: ${student.rollNo.ifBlank { "-" }}) ki class attendance sirf $attendancePercent% ($presentDays/$totalDays classes) hai, jo 75% minimum requirement se kam hai. Kripya regular class attendance ensure karein."
        sendWhatsAppOrTextToPhone(context, student.phone, message)
    }

    fun openDirectWhatsAppMessage(
        context: Context,
        student: Student,
        dueInfo: StudentDueInfo,
        instituteName: String
    ) {
        val inst = instituteName.ifBlank { "CoachMaster" }
        val message = if (dueInfo.pendingBalance > 0) {
            "Namaste from $inst! Reminder for ${student.name} (Roll: ${student.rollNo.ifBlank { "-" }}): Current pending fee balance is ${FeeCalculator.formatCurrency(dueInfo.pendingBalance)} (${dueInfo.daysElapsed} working days completed). Kindly clear at your earliest convenience. Thank you!"
        } else {
            "Namaste from $inst! Update for ${student.name} (Roll: ${student.rollNo.ifBlank { "-" }}): Cycle progress ${dueInfo.currentCycleProgress}/${dueInfo.classesPerCycle} classes completed. All fee dues are currently cleared. Thank you!"
        }
        sendWhatsAppOrTextToPhone(context, student.phone, message)
    }

    private fun sendWhatsAppOrTextToPhone(context: Context, phone: String, message: String) {
        val digitsOnly = phone.filter { it.isDigit() }
        val formattedPhone = when {
            digitsOnly.length == 10 -> "91$digitsOnly"
            digitsOnly.length > 10 -> digitsOnly
            else -> ""
        }

        try {
            if (formattedPhone.isNotEmpty()) {
                val encodedMsg = URLEncoder.encode(message, "UTF-8")
                val waUri = Uri.parse("https://wa.me/$formattedPhone?text=$encodedMsg")
                val viewIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (viewIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(viewIntent)
                    return
                }
            }

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(sendIntent, "Send Message to Parent via...")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not launch messaging app: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareTextSummary(context: Context, text: String) {
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share via..."))
        } catch (e: Exception) {
            Toast.makeText(context, "Sharing failed", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareHolidayNoticeOnWhatsApp(
        context: Context,
        instituteName: String,
        dateDisplay: String,
        reason: String
    ) {
        val cleanInst = instituteName.ifBlank { "CoachMaster" }
        val cleanReason = reason.ifBlank { "Institute Holiday" }
        val noticeText = "📢 Notice: $cleanInst mein $dateDisplay ko $cleanReason ke karan chutti rahegi. Note: Chutti ke dino ki fee count nahi ki jayegi."
        try {
            val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, noticeText)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (whatsappIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(whatsappIntent)
            } else {
                val chooser = Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, noticeText)
                    },
                    "Broadcast Holiday Notice via..."
                )
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Broadcast failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
