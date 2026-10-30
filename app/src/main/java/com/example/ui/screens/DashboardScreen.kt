package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
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
import com.example.ui.components.DeclareHolidayDialog
import com.example.ui.components.MetricKpiCard
import com.example.ui.theme.*
import com.example.util.FeeCalculator
import com.example.util.ShareHelper
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

    var holidayDialogInitialDate by remember { mutableStateOf<String?>(null) }

    val todayStr = remember { FeeCalculator.getTodayStr() }
    val todayRecords = remember(attendance, todayStr) {
        attendance.filter { it.date == todayStr }
    }

    val isTodayHoliday = remember(todayRecords) {
        todayRecords.isNotEmpty() && todayRecords.all { it.status == "holiday" }
    }

    val isTodayOpen = remember(todayRecords) {
        todayRecords.any { it.status == "present" || it.status == "absent" || it.status == "leave" }
    }

    val todayHolidayReason = remember(todayRecords) {
        todayRecords.firstOrNull { it.status == "holiday" }?.reason?.ifBlank { "Tuition Holiday" } ?: ""
    }

    val todayAttendancePct = remember(activeStudents, todayRecords, isTodayHoliday) {
        when {
            isTodayHoliday -> "Holiday 🏖️"
            activeStudents.isNotEmpty() && todayRecords.isNotEmpty() -> {
                val present = todayRecords.count { it.status == "present" }
                "${Math.round((present.toFloat() / todayRecords.size) * 100f)}%"
            }
            activeStudents.isNotEmpty() && todayRecords.isEmpty() -> "Pending"
            else -> "0%"
        }
    }

    val currentMonthStr = remember {
        SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
    }

    // Monthly Open Days vs Holiday Days count for Dashboard Tracker
    val (monthlyOpenDaysCount, monthlyHolidayDaysCount) = remember(attendance, currentMonthStr) {
        val monthGrouped = attendance
            .filter { it.date.startsWith(currentMonthStr) }
            .groupBy { it.date }
        var openDays = 0
        var holidayDays = 0
        monthGrouped.forEach { (_, records) ->
            if (records.all { it.status == "holiday" }) {
                holidayDays++
            } else if (records.any { it.status == "present" || it.status == "absent" || it.status == "leave" }) {
                openDays++
            }
        }
        Pair(openDays, holidayDays)
    }

    // Unmarked Past Days (including Sunday detection)
    val unmarkedPastDates = remember(attendance, activeStudents) {
        viewModel.getUnmarkedPastDates(attendance, activeStudents)
    }

    val monthlyRevenue = remember(payments, currentMonthStr) {
        payments.filter { it.paymentDate.startsWith(currentMonthStr) }.sumOf { it.amount }
    }

    val monthlyExpense = remember(expenses, currentMonthStr) {
        expenses.filter { it.date.startsWith(currentMonthStr) }.sumOf { it.amount }
    }

    val netProfit = monthlyRevenue - monthlyExpense

    // Overdue students list using the new 30-working-day attendance engine
    val overdueList = remember(activeStudents, payments, attendance, batches, config) {
        activeStudents.map { student ->
            val batch = batches.find { it.id == student.batchId }
            val cycleLen = FeeCalculator.resolveClassesPerCycle(batch, config)
            val due = FeeCalculator.calculateStudentDues(
                student = student,
                payments = payments,
                attendanceRecords = attendance,
                classesPerCycle = cycleLen
            )
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

        // Today's Tuition Status Tracker Card + Unmarked Days / Sunday Alerts (Improvements 1, 2, 3)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().testTag("tuition_status_tracker_card")
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Today's Tuition Status",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Is mahine: $monthlyOpenDaysCount Days Open  |  $monthlyHolidayDaysCount Days Tuition Band (Holidays)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }

                        // Today's Live Status Pill
                        val (badgeBg, badgeFg, badgeText) = when {
                            isTodayHoliday -> Triple(CoralLight, CoralWarning, "CLOSED (Holiday) 🏖️")
                            isTodayOpen -> Triple(EmeraldLight, EmeraldDark, "OPEN 🟢")
                            else -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "UNMARKED ⚠️")
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(badgeBg)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = badgeText,
                                color = badgeFg,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (isTodayHoliday && todayHolidayReason.isNotBlank()) {
                        Text(
                            text = "Reason: $todayHolidayReason (No fee counted today)",
                            fontSize = 12.sp,
                            color = CoralWarning,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Today is Sunday Prompt
                    if (FeeCalculator.isSunday(todayStr) && todayRecords.isEmpty() && uiState.isAdmin) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = IndigoLight),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "📅 Aaj Sunday hai! Kya aaj ki class/day fee cycle mein count karni hai ya Holiday rakhna hai?",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = IndigoDark
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.markPaidWeeklyOff(todayStr, "Paid Sunday") },
                                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Count Paid Sunday (+1 Day)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    OutlinedButton(
                                        onClick = { holidayDialogInitialDate = todayStr },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Sunday Holiday (0 Day)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    if (uiState.isAdmin) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.navigateToAttendanceForDate(todayStr) },
                                colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("take_roll_call_today_btn")
                            ) {
                                Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Take Roll Call (Open)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { holidayDialogInitialDate = todayStr },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).testTag("mark_holiday_today_btn")
                            ) {
                                Icon(Icons.Default.BeachAccess, contentDescription = null, tint = CoralWarning, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Mark Tuition Holiday", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CoralWarning)
                            }
                        }
                    }

                    // Red Warning Banner for Unmarked Past Days (Improvement 2)
                    if (unmarkedPastDates.isNotEmpty() && uiState.isAdmin) {
                        val mostRecentUnmarked = unmarkedPastDates.first()
                        val isYesterday = mostRecentUnmarked == FeeCalculator.getYesterdayStr()
                        val isSun = FeeCalculator.isSunday(mostRecentUnmarked)
                        val dayPrefix = if (isYesterday) "Kal (${FeeCalculator.formatDate(mostRecentUnmarked)})" else FeeCalculator.formatDate(mostRecentUnmarked)

                        Card(
                            colors = CardDefaults.cardColors(containerColor = CoralLight),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("unmarked_day_alert_banner")
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = if (isSun) {
                                        "⚠️ Sunday ($dayPrefix) ka status pending hai: Paid Weekly Off (+1 Day) count karein ya Holiday (0 Day) mark karein!"
                                    } else {
                                        "⚠️ $dayPrefix ka status pending hai: Roll Call lein ya Holiday mark karein!"
                                    },
                                    color = Color(0xFF991B1B),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (unmarkedPastDates.size > 1) {
                                    Text(
                                        text = "Total ${unmarkedPastDates.size} recent days unmarked: ${unmarkedPastDates.take(3).joinToString(", ") { FeeCalculator.formatDate(it) }}",
                                        color = Color(0xFFB91C1C),
                                        fontSize = 11.sp
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { viewModel.navigateToAttendanceForDate(mostRecentUnmarked) },
                                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Roll Call Lein", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = { holidayDialogInitialDate = mostRecentUnmarked },
                                        colors = ButtonDefaults.buttonColors(containerColor = CoralWarning),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text("Holiday Mark Karein", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    if (isSun) {
                                        OutlinedButton(
                                            onClick = { viewModel.markPaidWeeklyOff(mostRecentUnmarked, "Paid Sunday") },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text("Paid Sunday (+1)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = student.name,
                                        fontWeight = FontWeight.SemiBold,
                                        textDecoration = TextDecoration.Underline,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${due.daysElapsed} Working Days (${due.cyclesElapsed} Cycles Completed)",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }

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

    // Declare Holiday Dialog from Dashboard
    if (holidayDialogInitialDate != null) {
        DeclareHolidayDialog(
            initialDate = holidayDialogInitialDate!!,
            instituteName = config.instituteName,
            onDismiss = { holidayDialogInitialDate = null },
            onSaveHoliday = { start, end, reason, broadcastWhatsApp ->
                viewModel.markTuitionHolidayRange(start, end, reason)
                holidayDialogInitialDate = null
                if (broadcastWhatsApp) {
                    val dateDisplay = if (start == end) {
                        FeeCalculator.formatDate(start)
                    } else {
                        "${FeeCalculator.formatDate(start)} se ${FeeCalculator.formatDate(end)}"
                    }
                    ShareHelper.shareHolidayNoticeOnWhatsApp(
                        context = context,
                        instituteName = config.instituteName,
                        dateDisplay = dateDisplay,
                        reason = reason
                    )
                }
            }
        )
    }

    // Finance History Modal when tapping a student's name on Dashboard
    if (uiState.selectedStudentForDossier != null) {
        val dossierStudent = uiState.selectedStudentForDossier!!
        val batch = batches.find { it.id == dossierStudent.batchId }
        val cycleLen = FeeCalculator.resolveClassesPerCycle(batch, config)
        FinanceHistoryModal(
            student = dossierStudent,
            payments = payments.filter { it.studentId == dossierStudent.id },
            attendanceRecords = attendance.filter { it.studentId == dossierStudent.id },
            classesPerCycle = cycleLen,
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
