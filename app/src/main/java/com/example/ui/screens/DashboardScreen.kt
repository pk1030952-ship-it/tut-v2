package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppNavDestination
import com.example.ui.CoachMasterViewModel
import com.example.ui.components.MetricKpiCard
import com.example.ui.theme.*
import com.example.util.FeeCalculator
import java.text.SimpleDateFormat
import java.util.*

private data class DashboardActivityItem(
    val dateStr: String,
    val sortTimeMs: Long,
    val title: String,
    val icon: ImageVector,
    val iconTint: Color
)

@Composable
fun DashboardScreen(
    viewModel: CoachMasterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val students by viewModel.students.collectAsState()
    val activeStudents by viewModel.activeStudents.collectAsState()
    val batches by viewModel.batches.collectAsState()
    val attendance by viewModel.attendance.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val exams by viewModel.exams.collectAsState()
    val config by viewModel.config.collectAsState()

    val todayStr = remember { FeeCalculator.getTodayStr() }
    val todayRecords = remember(attendance, todayStr) {
        attendance.filter { it.date == todayStr }
    }
    val todayAttendancePct = remember(activeStudents, todayRecords) {
        if (activeStudents.isNotEmpty() && todayRecords.isNotEmpty()) {
            val present = todayRecords.count { it.status == "present" }
            "${Math.round((present.toFloat() / todayRecords.size) * 100f)}%"
        } else if (activeStudents.isNotEmpty() && todayRecords.isEmpty()) {
            "N/A"
        } else {
            "0%"
        }
    }

    val currentMonthStr = remember {
        SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    }

    val monthlyRevenue = remember(payments, currentMonthStr) {
        payments.filter { it.paymentDate.startsWith(currentMonthStr) }.sumOf { it.amount }
    }

    val monthlyExpense = remember(expenses, currentMonthStr) {
        expenses.filter { it.date.startsWith(currentMonthStr) }.sumOf { it.amount }
    }

    val netProfit = monthlyRevenue - monthlyExpense

    // Overdue students list (Top 5 like v10studendrecord.html)
    val overdueList = remember(activeStudents, payments) {
        activeStudents.map { student ->
            val due = FeeCalculator.calculateStudentDues(student, payments)
            Pair(student, due)
        }.filter { it.second.pendingBalance > 0 }
            .sortedByDescending { it.second.pendingBalance }
    }

    // Recent Activity list (Payments, Student Admissions, Scheduled Exams - Top 8 like v10studendrecord.html)
    val recentActivities = remember(payments, students, exams) {
        val list = mutableListOf<DashboardActivityItem>()
        val studentMap = students.associateBy { it.id }
        val isoParser = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        payments.forEach { p ->
            val sName = studentMap[p.studentId]?.name ?: "Unknown"
            val dStr = p.isoTimestamp.ifBlank { p.paymentDate }
            val ms = if (p.timestamp > 0L) p.timestamp else (runCatching { isoParser.parse(p.paymentDate)?.time }.getOrNull() ?: 0L)
            list.add(
                DashboardActivityItem(
                    dateStr = dStr,
                    sortTimeMs = ms,
                    title = "Received ${FeeCalculator.formatCurrency(p.amount)} from $sName",
                    icon = Icons.Default.CurrencyRupee,
                    iconTint = EmeraldSuccess
                )
            )
        }

        students.forEach { s ->
            val ms = runCatching { isoParser.parse(s.joiningDate)?.time }.getOrNull() ?: 0L
            list.add(
                DashboardActivityItem(
                    dateStr = s.joiningDate,
                    sortTimeMs = ms,
                    title = "Admitted ${s.name}",
                    icon = Icons.Default.PersonAdd,
                    iconTint = IndigoPrimary
                )
            )
        }

        exams.forEach { e ->
            val ms = runCatching { isoParser.parse(e.date)?.time }.getOrNull() ?: 0L
            list.add(
                DashboardActivityItem(
                    dateStr = e.date,
                    sortTimeMs = ms,
                    title = "Scheduled Exam: ${e.name}",
                    icon = Icons.Default.Description,
                    iconTint = AmberAccent
                )
            )
        }

        list.sortedByDescending { it.sortTimeMs }.take(8)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Overview Header & Cloud Sync Banner
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = IndigoPrimary),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Overview Dashboard",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Real-time metrics for ${config.instituteName.ifBlank { "CoachMaster" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }

                        Button(
                            onClick = { viewModel.triggerCloudSync(pullFirst = true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(alpha = 0.2f),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("cloud_sync_button")
                        ) {
                            if (uiState.isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Syncing...", fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cloud Sync", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // 4 KPI Metric Cards matching v10studendrecord.html
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricKpiCard(
                        title = "Active Students",
                        value = "${activeStudents.size}",
                        icon = Icons.Default.Groups,
                        iconBgColor = IndigoLight,
                        iconTintColor = IndigoPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricKpiCard(
                        title = "Today's Attendance",
                        value = todayAttendancePct,
                        icon = Icons.Default.EventAvailable,
                        iconBgColor = EmeraldLight,
                        iconTintColor = EmeraldSuccess,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricKpiCard(
                        title = "Collection (Month)",
                        value = FeeCalculator.formatCurrency(monthlyRevenue),
                        icon = Icons.Default.AccountBalanceWallet,
                        iconBgColor = IndigoLight,
                        iconTintColor = IndigoPrimary,
                        valueColor = IndigoPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricKpiCard(
                        title = "Net (Collection - Exp.)",
                        value = FeeCalculator.formatCurrency(netProfit),
                        icon = Icons.Default.ShowChart,
                        iconBgColor = if (netProfit >= 0) EmeraldLight else CoralLight,
                        iconTintColor = if (netProfit >= 0) EmeraldSuccess else CoralWarning,
                        valueColor = if (netProfit >= 0) EmeraldSuccess else CoralWarning,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Quick Navigation Bar (Includes Staff, Timetable, Reports, Expenses)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickActionButton(
                    label = "Staff",
                    icon = Icons.Default.Badge,
                    modifier = Modifier.weight(1f)
                ) { viewModel.selectTab(AppNavDestination.STAFF) }

                QuickActionButton(
                    label = "Timetable",
                    icon = Icons.Default.TableChart,
                    modifier = Modifier.weight(1f)
                ) { viewModel.selectTab(AppNavDestination.TIMETABLE) }

                QuickActionButton(
                    label = "Exams",
                    icon = Icons.Default.Description,
                    modifier = Modifier.weight(1f)
                ) { viewModel.selectTab(AppNavDestination.EXAMS) }

                QuickActionButton(
                    label = "Reports",
                    icon = Icons.Default.ShowChart,
                    modifier = Modifier.weight(1f)
                ) { viewModel.selectTab(AppNavDestination.REPORTS) }
            }
        }

        // Section 1: Urgent Action: Overdue Fees
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                tint = CoralWarning,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Urgent Action: Overdue Fees",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Tap name for history",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    if (overdueList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No pending dues. Excellent!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray
                            )
                        }
                    } else {
                        overdueList.take(5).forEach { (student, due) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.openStudentDossier(student) }
                                    .padding(vertical = 10.dp)
                                    .testTag("overdue_student_${student.id}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = student.name,
                                    fontWeight = FontWeight.SemiBold,
                                    textDecoration = TextDecoration.Underline,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    text = FeeCalculator.formatCurrency(due.pendingBalance),
                                    fontWeight = FontWeight.Bold,
                                    color = CoralWarning,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                )

                                if (uiState.isAdmin) {
                                    Button(
                                        onClick = { viewModel.selectStudentForPayment(student) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = IndigoLight,
                                            contentColor = IndigoPrimary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Collect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        }
                    }
                }
            }
        }

        // Section 2: Recent Activity (Matches dash-activity-list in v10studendrecord.html)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = AmberAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recent Activity",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    if (recentActivities.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No recent activity", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
                        }
                    } else {
                        recentActivities.forEach { act ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = act.icon,
                                        contentDescription = null,
                                        tint = act.iconTint,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = act.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = FeeCalculator.formatDate(act.dateStr),
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        }
                    }
                }
            }
        }
    }

    // Finance History Modal when tapping a student's name on Dashboard
    if (uiState.selectedStudentForDossier != null) {
        val dossierStudent = uiState.selectedStudentForDossier!!
        FinanceHistoryModal(
            student = dossierStudent,
            payments = payments.filter { it.studentId == dossierStudent.id },
            isAdmin = uiState.isAdmin,
            onDismiss = { viewModel.closeStudentDossier() },
            onReceivePayment = {
                viewModel.closeStudentDossier()
                viewModel.selectStudentForPayment(dossierStudent)
            }
        )
    }
}

@Composable
fun QuickActionButton(
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clickable { onClick() }
            .testTag("quick_action_${label.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = IndigoPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
