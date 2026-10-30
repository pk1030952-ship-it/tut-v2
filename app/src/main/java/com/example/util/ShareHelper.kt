package com.example.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.model.Student
import java.io.File

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
