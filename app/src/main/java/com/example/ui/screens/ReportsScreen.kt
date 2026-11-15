package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Student
import com.example.ui.CoachMasterViewModel
import com.example.ui.components.AttendanceTrendLineChart
import com.example.ui.components.IncomeExpenseBarChart
import com.example.ui.theme.*
import com.example.util.FeeCalculator
import com.example.util.SecurityUtils
import com.example.util.ShareHelper

private data class LowAttendanceStudentItem(
    val student: Student,
    val batchName: String,
    val presentDays: Int,
    val totalOpenDays: Int,
    val attendancePercent: Int
)

@Composable
fun ReportsScreen(
    viewModel: CoachMasterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val activeStudents by viewModel.activeStudents.collectAsState()
    val batches by viewModel.batches.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val attendance by viewModel.attendance.collectAsState()
    val config by viewModel.config.collectAsState()

    val financeChartData = remember(payments, expenses) {
        viewModel.getMonthlyFinanceData()
    }

    val attendanceTrendData = remember(attendance) {
        viewModel.getMonthlyAttendanceTrend()
    }

    val batchById = remember(batches) {
        batches.associateBy { it.id }
    }

    val paymentsByStudent = remember(payments) {
        payments.groupBy { it.studentId }
    }

    val attendanceByStudent = remember(attendance) {
        attendance.groupBy { it.studentId }
    }

    val overdueStudents by remember(activeStudents, paymentsByStudent, attendanceByStudent, batchById, config) {
        derivedStateOf {
            activeStudents.map { student ->
                val batch = student.batchId?.let { batchById[it] }
                val cycleLen = FeeCalculator.resolveClassesPerCycle(batch, config)
                val stuPayments = paymentsByStudent[student.id] ?: emptyList()
                val stuAttendance = attendanceByStudent[student.id] ?: emptyList()
                val due = FeeCalculator.calculateStudentDues(student, stuPayments, stuAttendance, cycleLen)
                Pair(student, due)
            }.filter { it.second.pendingBalance > 0 }
                .sortedByDescending { it.second.pendingBalance }
        }
    }

    // Low Attendance Alert (<75%) calculation
    val lowAttendanceStudents by remember(activeStudents, attendanceByStudent, batchById) {
        derivedStateOf {
            activeStudents.mapNotNull { student ->
                val records = attendanceByStudent[student.id] ?: emptyList()
                val present = records.count { it.status == "present" }
                val absent = records.count { it.status == "absent" }
                val totalOpen = present + absent
                if (totalOpen == 0) return@mapNotNull null
                val pct = Math.round((present.toFloat() / totalOpen.toFloat()) * 100f)
                if (pct < 75) {
                    LowAttendanceStudentItem(
                        student = student,
                        batchName = student.batchId?.let { batchById[it]?.name } ?: "General",
                        presentDays = present,
                        totalOpenDays = totalOpen,
                        attendancePercent = pct
                    )
                } else {
                    null
                }
            }.sortedBy { it.attendancePercent }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("reports_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Reports, Accounting & Analytics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Export Excel (.CSV) ledgers for CA/Accounting, monitor <75% attendance alerts, and track 6-month trends.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }

        // 1. Excel / CSV Data Export Card (For CA & Accounting)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("csv_export_card")
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TableView,
                            contentDescription = null,
                            tint = EmeraldSuccess
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Excel / CSV Data Export (CA & Accounting)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "One-tap .CSV spreadsheet export compatible with Microsoft Excel & Google Sheets",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.exportFeeLedgerCsv(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_fee_ledger_csv_btn")
                        ) {
                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Fee Ledger (.CSV)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { viewModel.exportAttendanceRegisterCsv(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_attendance_csv_btn")
                        ) {
                            Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Attendance (.CSV)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.exportExpenseReportCsv(context) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_expense_csv_btn")
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = CoralWarning, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Expense Report (.CSV)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CoralWarning)
                        }

                        OutlinedButton(
                            onClick = { viewModel.exportStudentMasterSummaryCsv(context) },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_student_summary_csv_btn")
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, tint = IndigoPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Master Summary (.CSV)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = IndigoPrimary)
                        }
                    }
                }
            }
        }

        // 2. Low Attendance Alert (<75%) Card with 1-Tap Parent Warning
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("low_attendance_alert_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = CoralWarning
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Low Attendance Alert (<75%)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Auto-generated list of students below 75% attendance",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (lowAttendanceStudents.isEmpty()) EmeraldLight else CoralLight)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${lowAttendanceStudents.size} Students",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (lowAttendanceStudents.isEmpty()) EmeraldDark else CoralWarning
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    if (lowAttendanceStudents.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "All students have ≥ 75% attendance. Great consistency!",
                                color = EmeraldSuccess,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        lowAttendanceStudents.forEach { item ->
                            val maskedPhone = SecurityUtils.maskPhoneNumber(item.student.phone, uiState.isAdmin)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp)
                                    .testTag("low_att_student_${item.student.id}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.student.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(CoralLight)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "${item.attendancePercent}% (${item.presentDays}/${item.totalOpenDays})",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CoralWarning
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${item.batchName} • $maskedPhone",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                                    )
                                }

                                Button(
                                    onClick = {
                                        ShareHelper.sendLowAttendanceWarning(
                                            context = context,
                                            student = item.student,
                                            attendancePercent = item.attendancePercent,
                                            presentDays = item.presentDays,
                                            totalDays = item.totalOpenDays,
                                            instituteName = config.instituteName
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CoralWarning),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("warn_parent_btn_${item.student.id}")
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("1-Tap Warn", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        }
                    }
                }
            }
        }

        // Chart 1: Income vs. Expenses (Last 6 Months)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().testTag("chart_income_expense")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BarChart, contentDescription = null, tint = IndigoPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Income vs Expenses (Last 6 Months)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    IncomeExpenseBarChart(data = financeChartData)
                }
            }
        }

        // Chart 2: Attendance Trend (Last 6 Months)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().testTag("chart_attendance_trend")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, tint = EmeraldSuccess)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Attendance Trend (Last 6 Months)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    AttendanceTrendLineChart(data = attendanceTrendData)
                }
            }
        }

        // All Pending Dues Table
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "All Pending Dues",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Total: ${FeeCalculator.formatCurrency(overdueStudents.sumOf { it.second.pendingBalance })}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = CoralWarning
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (overdueStudents.isEmpty()) {
                        Text("All fees cleared.", color = EmeraldSuccess)
                    } else {
                        overdueStudents.forEach { (student, due) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(student.name, fontWeight = FontWeight.Medium)
                                    Text(
                                        text = SecurityUtils.maskPhoneNumber(student.phone, uiState.isAdmin),
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                Text(
                                    text = FeeCalculator.formatCurrency(due.pendingBalance),
                                    fontWeight = FontWeight.Bold,
                                    color = CoralWarning
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }

        // Batch-wise Summary Table
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Batch-wise Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    batches.forEach { batch ->
                        val bStudents = activeStudents.filter { it.batchId == batch.id }
                        val potentialRev = bStudents.sumOf { it.monthlyFee }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(batch.name, fontWeight = FontWeight.Medium)
                                Text("${bStudents.size} Students", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Text(
                                text = FeeCalculator.formatCurrency(potentialRev),
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
}
