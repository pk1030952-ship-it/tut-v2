package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.example.data.model.Batch
import com.example.ui.CoachMasterViewModel
import com.example.ui.theme.CoralWarning
import com.example.ui.theme.IndigoLight
import com.example.ui.theme.IndigoPrimary

@Composable
fun BatchesScreen(
    viewModel: CoachMasterViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val batches by viewModel.batches.collectAsState()
    val students by viewModel.students.collectAsState()
    val staffList by viewModel.staff.collectAsState()

    val teachers = remember(staffList) { staffList.filter { it.role == "Teacher" } }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingBatch by remember { mutableStateOf<Batch?>(null) }
    var batchToDelete by remember { mutableStateOf<Batch?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (uiState.isAdmin) {
                FloatingActionButton(
                    onClick = {
                        editingBatch = null
                        showAddDialog = true
                    },
                    containerColor = IndigoPrimary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_batch_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Batch")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .testTag("batches_screen")
        ) {
            Text(
                text = "Batch Management (${batches.size})",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Manage schedules, subjects, teachers, and enrolled students",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (batches.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No batches found. Tap '+' to create a batch.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(batches, key = { it.id }) { batch ->
                        val enrolledStudents = students.filter { it.batchId == batch.id }
                        val assignedTeacher = staffList.find { it.id == batch.teacherId }
                        val teacherLabel = assignedTeacher?.name ?: batch.teacherName.ifBlank { "No Teacher" }

                        Card(
                            modifier = Modifier.fillMaxWidth().testTag("batch_card_${batch.id}"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = batch.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = batch.subject,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = IndigoPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    if (uiState.isAdmin) {
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    editingBatch = batch
                                                    showAddDialog = true
                                                },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                                            }
                                            IconButton(
                                                onClick = { batchToDelete = batch },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = CoralWarning, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(batch.schedule.ifBlank { "N/A" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

                                    Spacer(modifier = Modifier.width(16.dp))
                                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(batch.timing.ifBlank { "N/A" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.School, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(teacherLabel, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Box(
                                    modifier = Modifier
                                        .background(IndigoLight, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${enrolledStudents.size} Students Enrolled",
                                        color = IndigoPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add / Edit Batch Dialog (links teacherId to Staff list just like v10studendrecord.html)
    if (showAddDialog) {
        var name by remember { mutableStateOf(editingBatch?.name ?: "") }
        var subject by remember { mutableStateOf(editingBatch?.subject ?: "") }
        var schedule by remember { mutableStateOf(editingBatch?.schedule ?: "") }
        var timing by remember { mutableStateOf(editingBatch?.timing ?: "") }
        var selectedTeacherId by remember { mutableStateOf(editingBatch?.teacherId) }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (editingBatch == null) "Create Batch" else "Edit Batch") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Batch Name *") },
                        placeholder = { Text("e.g., Class 10 Maths") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Assign Teacher:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedTeacherId == null,
                            onClick = { selectedTeacherId = null },
                            label = { Text("No Teacher Assigned") }
                        )
                        teachers.forEach { t ->
                            FilterChip(
                                selected = selectedTeacherId == t.id,
                                onClick = { selectedTeacherId = t.id },
                                label = { Text(t.name) }
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = schedule,
                            onValueChange = { schedule = it },
                            label = { Text("Days") },
                            placeholder = { Text("Mon-Wed-Fri") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = timing,
                            onValueChange = { timing = it },
                            label = { Text("Timing") },
                            placeholder = { Text("5:00 PM - 6:00 PM") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && subject.isNotBlank()) {
                            val teacherObj = teachers.find { it.id == selectedTeacherId }
                            val batch = editingBatch?.copy(
                                name = name.trim(),
                                subject = subject.trim(),
                                schedule = schedule.trim(),
                                timing = timing.trim(),
                                teacherId = selectedTeacherId,
                                teacherName = teacherObj?.name ?: ""
                            ) ?: Batch(
                                name = name.trim(),
                                subject = subject.trim(),
                                schedule = schedule.trim(),
                                timing = timing.trim(),
                                teacherId = selectedTeacherId,
                                teacherName = teacherObj?.name ?: ""
                            )
                            viewModel.saveBatch(batch)
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                ) {
                    Text("Save Batch")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (batchToDelete != null) {
        AlertDialog(
            onDismissRequest = { batchToDelete = null },
            title = { Text("Delete Batch") },
            text = { Text("Deleting batch removes assigned students from it, but doesn't delete students.") },
            confirmButton = {
                Button(
                    onClick = {
                        batchToDelete?.let { viewModel.deleteBatch(it) }
                        batchToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralWarning)
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { batchToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
