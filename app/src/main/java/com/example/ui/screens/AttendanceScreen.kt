package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Student
import com.example.ui.AttendanceViewModel
import com.example.ui.components.DeclareHolidayDialog
import com.example.ui.theme.*
import com.example.util.FeeCalculator
import com.example.util.ShareHelper
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    attendanceViewModel: AttendanceViewModel = viewModel(),
    isAdmin: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vmSelectedDate by attendanceViewModel.selectedDate.collectAsState()
    val activeStudents by attendanceViewModel.activeStudents.collectAsState()
    val batches by attendanceViewModel.batches.collectAsState()
    val attendanceRecords by attendanceViewModel.attendance.collectAsState()
    val config by attendanceViewModel.config.collectAsState()

    var selectedDate by remember(vmSelectedDate) { mutableStateOf(vmSelectedDate) }
    var selectedBatchId by remember { mutableStateOf<String?>(null) }
    var showHolidayDialog by remember { mutableStateOf(false) }

    val filteredStudents = remember(activeStudents, selectedBatchId) {
        if (selectedBatchId == null) activeStudents
        else activeStudents.filter { it.batchId == selectedBatchId }
    }

    val existingDayRecords = remember(attendanceRecords, selectedDate) {
        attendanceRecords.filter { it.date == selectedDate }
    }

    val isSelectedDateHoliday = remember(existingDayRecords) {
        existingDayRecords.isNotEmpty() && existingDayRecords.all { it.status == "holiday" }
    }

    val holidayReasonForDate = remember(existingDayRecords) {
        existingDayRecords.firstOrNull { it.status == "holiday" }?.reason?.ifBlank { "Tuition Holiday" } ?: ""
    }

    val currentDayAttendance = remember(attendanceRecords, selectedDate, activeStudents) {
        val map = mutableStateMapOf<String, String>()
        activeStudents.forEach { student ->
            val match = existingDayRecords.find { it.studentId == student.id }
            val st = match?.status ?: "present"
            map[student.id] = if (st == "holiday") "present" else st
        }
        map
    }

    val currentDayReason = remember(attendanceRecords, selectedDate, activeStudents) {
        val map = mutableStateMapOf<String, String>()
        activeStudents.forEach { student ->
            val match = existingDayRecords.find { it.studentId == student.id }
            map[student.id] = if (match?.status == "leave") match.reason else ""
        }
        map
    }

    val isAlreadySaved = remember(existingDayRecords) {
        existingDayRecords.isNotEmpty()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (isAdmin && !isSelectedDateHoliday) {
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val presentCount = filteredStudents.count { currentDayAttendance[it.id] == "present" }
                            val absentCount = filteredStudents.count { currentDayAttendance[it.id] == "absent" }
                            val leaveCount = filteredStudents.count { currentDayAttendance[it.id] == "leave" }
                            Text(
                                text = "$presentCount Present  •  $absentCount Absent" +
                                    if (leaveCount > 0) "  •  $leaveCount Leave" else "",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary
                            )
                            Text(
                                text = if (isAlreadySaved) "Roster synced (+1 Working Day)" else "Pending save",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isAlreadySaved) EmeraldSuccess else Color.Gray
                            )
                        }

                        Button(
                            onClick = {
                                val subsetAttendance = filteredStudents.associate { s ->
                                    s.id to (currentDayAttendance[s.id] ?: "present")
                                }
                                val subsetReasons = filteredStudents.associate { s ->
                                    s.id to (currentDayReason[s.id] ?: "")
                                }
                                attendanceViewModel.markAttendance(
                                    date = selectedDate,
                                    attendanceMap = subsetAttendance,
                                    batchId = selectedBatchId,
                                    reasonMap = subsetReasons
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("save_attendance_btn")
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Roster", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val cal = Calendar.getInstance()
                            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            try {
                                fmt.parse(selectedDate)?.let {
                                    cal.time = it
                                    cal.add(Calendar.DAY_OF_YEAR, -1)
                                    val newDate = fmt.format(cal.time)
                                    selectedDate = newDate
                                    attendanceViewModel.setSelectedDate(newDate)
                                }
                            } catch (_: Exception) {}
                        }
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Day")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${FeeCalculator.formatDate(selectedDate)} (${FeeCalculator.getDayOfWeekLabel(selectedDate)})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                isSelectedDateHoliday -> "🏖️ Tuition Closed ($holidayReasonForDate)"
                                selectedDate == FeeCalculator.getTodayStr() -> "Today • Working Day (+1)"
                                else -> "Historical Record"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelectedDateHoliday) CoralWarning else IndigoPrimary
                        )
                    }

                    IconButton(
                        onClick = {
                            val cal = Calendar.getInstance()
                            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            try {
                                fmt.parse(selectedDate)?.let {
                                    cal.time = it
                                    cal.add(Calendar.DAY_OF_YEAR, 1)
                                    val newDate = fmt.format(cal.time)
                                    selectedDate = newDate
                                    attendanceViewModel.setSelectedDate(newDate)
                                }
                            } catch (_: Exception) {}
                        }
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Day")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isAdmin) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelectedDateHoliday) CoralLight else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("declare_holiday_toggle_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Tuition Closed Today (Holiday) 🏖️",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelectedDateHoliday) Color(0xFF991B1B) else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isSelectedDateHoliday) {
                                    "Chutti Active: $holidayReasonForDate (0 Fee Counted)"
                                } else {
                                    "Turn ON to freeze 30-day fee counter & broadcast on WhatsApp"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelectedDateHoliday) Color(0xFFB91C1C) else Color.Gray
                            )
                        }

                        Switch(
                            checked = isSelectedDateHoliday,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    showHolidayDialog = true
                                } else {
                                    val defaultPresent = filteredStudents.associate { it.id to "present" }
                                    attendanceViewModel.markAttendance(
                                        date = selectedDate,
                                        attendanceMap = defaultPresent,
                                        batchId = selectedBatchId
                                    )
                                }
                            },
                            modifier = Modifier.testTag("holiday_switch")
                        )
                    }
                }
            }

            if (FeeCalculator.isSunday(selectedDate) && !isAlreadySaved && isAdmin) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = IndigoLight),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📅 Sunday Choice: Count Paid Off or Freeze Fee?",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoDark,
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = { attendanceViewModel.markPaidWeeklyOff(selectedDate, "Paid Sunday") },
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Paid (+1d)", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { showHolidayDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Holiday (0d)", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedBatchId == null,
                        onClick = { selectedBatchId = null },
                        label = { Text("All Active (${activeStudents.size})") }
                    )
                }
                items(batches) { batch ->
                    FilterChip(
                        selected = selectedBatchId == batch.id,
                        onClick = { selectedBatchId = batch.id },
                        label = { Text(batch.name) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isSelectedDateHoliday) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BeachAccess,
                            contentDescription = null,
                            tint = CoralWarning,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Tuition Closed (Holiday) 🏖️",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Reason: $holidayReasonForDate",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = IndigoPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Is din kisi bhi student ka 30-day billing counter nahi badhega (+0 Working Days).",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (isAdmin) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = {
                                        ShareHelper.shareHolidayNoticeOnWhatsApp(
                                            context = context,
                                            instituteName = config.instituteName,
                                            dateDisplay = FeeCalculator.formatDate(selectedDate),
                                            reason = holidayReasonForDate
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess)
                                ) {
                                    Icon(Icons.Default.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Broadcast on WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                OutlinedButton(
                                    onClick = {
                                        val defaultPresent = filteredStudents.associate { it.id to "present" }
                                        attendanceViewModel.markAttendance(
                                            date = selectedDate,
                                            attendanceMap = defaultPresent,
                                            batchId = selectedBatchId
                                        )
                                    }
                                ) {
                                    Text("Re-Open Class", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                if (isAdmin) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Present/Absent = +1 Day  |  Leave = Fee Paused (0d)",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    filteredStudents.forEach { currentDayAttendance[it.id] = "present" }
                                }
                            ) {
                                Icon(Icons.Default.DoneAll, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("All Present", color = EmeraldSuccess, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            TextButton(
                                onClick = {
                                    filteredStudents.forEach { currentDayAttendance[it.id] = "absent" }
                                }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = CoralWarning, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("All Absent", color = CoralWarning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                if (filteredStudents.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No students available in this selection", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredStudents, key = { it.id }) { student ->
                            val batch = batches.find { it.id == student.batchId }
                            val currentStatus = currentDayAttendance[student.id] ?: "present"
                            val currentReason = currentDayReason[student.id] ?: ""

                            AttendanceRosterItem(
                                student = student,
                                batchName = batch?.name ?: "No Batch",
                                status = currentStatus,
                                leaveReason = currentReason,
                                isAdmin = isAdmin,
                                onStatusChange = { newStatus ->
                                    currentDayAttendance[student.id] = newStatus
                                    if (newStatus == "leave" && currentDayReason[student.id].isNullOrBlank()) {
                                        currentDayReason[student.id] = "Medical / Approved Leave"
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showHolidayDialog) {
        DeclareHolidayDialog(
            initialDate = selectedDate,
            instituteName = config.instituteName,
            onDismiss = { showHolidayDialog = false },
            onSaveHoliday = { start, end, reason, broadcastWhatsApp ->
                attendanceViewModel.markTuitionHolidayRange(start, end, reason, selectedBatchId)
                showHolidayDialog = false
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
}

@Composable
fun AttendanceRosterItem(
    student: Student,
    batchName: String,
    status: String,
    leaveReason: String = "",
    isAdmin: Boolean,
    onStatusChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("attendance_item_${student.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Roll: ${student.rollNo.ifBlank { "-" }}  •  $batchName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (status == "leave") {
                    Text(
                        text = "⏸️ Approved Leave (Fee Paused - 0 Day)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD97706)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val isPresent = status == "present"
                val isAbsent = status == "absent"
                val isLeave = status == "leave"

                Button(
                    onClick = { if (isAdmin) onStatusChange("present") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPresent) EmeraldSuccess else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isPresent) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp).testTag("att_present_${student.id}")
                ) {
                    Text("Present", fontSize = 11.sp, fontWeight = if (isPresent) FontWeight.Bold else FontWeight.Normal)
                }

                Button(
                    onClick = { if (isAdmin) onStatusChange("absent") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAbsent) CoralWarning else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isAbsent) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp).testTag("att_absent_${student.id}")
                ) {
                    Text("Absent", fontSize = 11.sp, fontWeight = if (isAbsent) FontWeight.Bold else FontWeight.Normal)
                }

                Button(
                    onClick = { if (isAdmin) onStatusChange("leave") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isLeave) AmberAccent else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isLeave) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 9.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp).testTag("att_leave_${student.id}")
                ) {
                    Text("Leave ⏸", fontSize = 11.sp, fontWeight = if (isLeave) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
