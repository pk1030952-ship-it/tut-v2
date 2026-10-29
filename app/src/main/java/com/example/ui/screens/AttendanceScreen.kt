package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AttendanceRecord
import com.example.data.model.Batch
import com.example.data.model.Student
import com.example.ui.CoachMasterViewModel
import com.example.ui.theme.*
import com.example.util.FeeCalculator
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    viewModel: CoachMasterViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeStudents by viewModel.activeStudents.collectAsState()
    val batches by viewModel.batches.collectAsState()
    val attendanceRecords by viewModel.attendance.collectAsState()

    var selectedDate by remember { mutableStateOf(FeeCalculator.getTodayStr()) }
    var selectedBatchId by remember { mutableStateOf<String?>(null) }

    // Filter students by batch
    val filteredStudents = remember(activeStudents, selectedBatchId) {
        if (selectedBatchId == null) activeStudents
        else activeStudents.filter { it.batchId == selectedBatchId }
    }

    // Local mutable state for current date roll call: Map<StudentId, Status>
    val currentDayAttendance = remember(attendanceRecords, selectedDate) {
        val existing = attendanceRecords.filter { it.date == selectedDate }
        val map = mutableStateMapOf<String, String>()
        activeStudents.forEach { student ->
            val match = existing.find { it.studentId == student.id }
            map[student.id] = match?.status ?: "present" // Default to present
        }
        map
    }

    val isAlreadySaved = remember(attendanceRecords, selectedDate) {
        attendanceRecords.any { it.date == selectedDate }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (uiState.isAdmin) {
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
                            val presentCount = currentDayAttendance.values.count { it == "present" }
                            Text(
                                text = "$presentCount / ${filteredStudents.size} Present",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary
                            )
                            Text(
                                text = if (isAlreadySaved) "Roster synced to local storage" else "Pending save",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isAlreadySaved) EmeraldSuccess else Color.Gray
                            )
                        }

                        Button(
                            onClick = {
                                viewModel.markAttendance(
                                    date = selectedDate,
                                    attendanceMap = currentDayAttendance,
                                    batchId = selectedBatchId
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
            // Date Switcher Row
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
                                    selectedDate = fmt.format(cal.time)
                                }
                            } catch (e: Exception) {}
                        }
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Day")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = FeeCalculator.formatDate(selectedDate),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (selectedDate == FeeCalculator.getTodayStr()) "Today" else "Historical Record",
                            style = MaterialTheme.typography.labelSmall,
                            color = IndigoPrimary
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
                                    selectedDate = fmt.format(cal.time)
                                }
                            } catch (e: Exception) {}
                        }
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next Day")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Batch selection chips
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

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Mark All Actions
            if (uiState.isAdmin) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            filteredStudents.forEach { currentDayAttendance[it.id] = "present" }
                        }
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("All Present", color = EmeraldSuccess, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = {
                            filteredStudents.forEach { currentDayAttendance[it.id] = "absent" }
                        }
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = CoralWarning, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("All Absent", color = CoralWarning, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Student Roster
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

                        AttendanceRosterItem(
                            student = student,
                            batchName = batch?.name ?: "No Batch",
                            status = currentStatus,
                            isAdmin = uiState.isAdmin,
                            onStatusChange = { newStatus ->
                                currentDayAttendance[student.id] = newStatus
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AttendanceRosterItem(
    student: Student,
    batchName: String,
    status: String,
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
                .padding(horizontal = 14.dp, vertical = 10.dp),
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
            }

            // Segmented Present / Absent Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val isPresent = status == "present"

                Button(
                    onClick = { if (isAdmin) onStatusChange("present") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPresent) EmeraldSuccess else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (isPresent) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp).testTag("att_present_${student.id}")
                ) {
                    Text("Present", fontSize = 12.sp, fontWeight = if (isPresent) FontWeight.Bold else FontWeight.Normal)
                }

                Button(
                    onClick = { if (isAdmin) onStatusChange("absent") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isPresent) CoralWarning else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (!isPresent) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.height(36.dp).testTag("att_absent_${student.id}")
                ) {
                    Text("Absent", fontSize = 12.sp, fontWeight = if (!isPresent) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
