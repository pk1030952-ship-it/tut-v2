package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Exam
import com.example.ui.CoachMasterViewModel
import com.example.ui.theme.CoralWarning
import com.example.ui.theme.IndigoPrimary
import com.example.util.FeeCalculator

@Composable
fun ExamsScreen(
    viewModel: CoachMasterViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val exams by viewModel.exams.collectAsState()
    val examResults by viewModel.examResults.collectAsState()
    val batches by viewModel.batches.collectAsState()
    val students by viewModel.students.collectAsState()

    var showScheduleDialog by remember { mutableStateOf(false) }
    var activeExamForMarks by remember { mutableStateOf<Exam?>(null) }
    var examToDelete by remember { mutableStateOf<Exam?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (uiState.isAdmin) {
                FloatingActionButton(
                    onClick = { showScheduleDialog = true },
                    containerColor = IndigoPrimary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("schedule_exam_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Create Exam")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .testTag("exams_screen")
        ) {
            Text(
                text = "Exams & Results",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Schedule unit tests, record student marks, and view scores",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (exams.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No exams scheduled. Create an exam to record marks.", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(exams, key = { it.id }) { exam ->
                        val batch = batches.find { it.id == exam.batchId }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
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
                                    Column {
                                        Text(exam.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(batch?.name ?: "Unknown Batch", fontSize = 12.sp, color = IndigoPrimary, fontWeight = FontWeight.Medium)
                                    }

                                    if (uiState.isAdmin) {
                                        IconButton(
                                            onClick = { examToDelete = exam },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = CoralWarning, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(FeeCalculator.formatDate(exam.date), fontSize = 13.sp, color = Color.Gray)
                                    Text("Total: ${exam.totalMarks}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = { activeExamForMarks = exam },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Score, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add / View Marks")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Schedule Exam Dialog
    if (showScheduleDialog) {
        var name by remember { mutableStateOf("") }
        var selectedBatchId by remember { mutableStateOf(batches.firstOrNull()?.id ?: "") }
        var date by remember { mutableStateOf(FeeCalculator.getTodayStr()) }
        var totalMarks by remember { mutableStateOf("100") }

        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            title = { Text("Schedule Exam") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Exam Name *") },
                        placeholder = { Text("e.g. Unit Test 1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Select Batch:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                    batches.forEach { b ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            RadioButton(
                                selected = selectedBatchId == b.id,
                                onClick = { selectedBatchId = b.id }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(b.name, fontSize = 13.sp)
                        }
                    }

                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = totalMarks,
                        onValueChange = { totalMarks = it },
                        label = { Text("Total Marks *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotBlank() && selectedBatchId.isNotBlank()) {
                            val marksInt = totalMarks.toIntOrNull() ?: 100
                            viewModel.saveExam(
                                Exam(
                                    name = name.trim(),
                                    batchId = selectedBatchId,
                                    date = date.trim(),
                                    totalMarks = marksInt
                                )
                            )
                            showScheduleDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                ) {
                    Text("Save Exam")
                }
            },
            dismissButton = {
                TextButton(onClick = { showScheduleDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Marks Entry / View Dialog (Pre-fills existing marks from examResults!)
    if (activeExamForMarks != null) {
        val exam = activeExamForMarks!!
        val batchStudents = remember(students, exam.batchId) {
            students.filter { it.batchId == exam.batchId }
        }
        val existingMarksForExam = remember(examResults, exam.id) {
            examResults.filter { it.examId == exam.id }.associate { it.studentId to it.marksObtained }
        }
        val marksMap = remember(exam.id, existingMarksForExam) {
            mutableStateMapOf<String, String>().apply {
                existingMarksForExam.forEach { (stuId, score) ->
                    val display = if (score % 1.0 == 0.0) score.toInt().toString() else score.toString()
                    put(stuId, display)
                }
            }
        }

        AlertDialog(
            onDismissRequest = { activeExamForMarks = null },
            title = {
                Column {
                    Text(exam.name, fontWeight = FontWeight.Bold)
                    Text("Total Marks: ${exam.totalMarks}", fontSize = 12.sp, color = Color.Gray)
                }
            },
            text = {
                if (batchStudents.isEmpty()) {
                    Text("No students found in this batch.", color = Color.Gray)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(batchStudents) { s ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(s.name, modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                OutlinedTextField(
                                    value = marksMap[s.id] ?: "",
                                    onValueChange = { if (uiState.isAdmin) marksMap[s.id] = it },
                                    readOnly = !uiState.isAdmin,
                                    label = { Text("/ ${exam.totalMarks}") },
                                    singleLine = true,
                                    modifier = Modifier.width(100.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (uiState.isAdmin) {
                    Button(
                        onClick = {
                            val parsed = marksMap.mapNotNull { (stuId, mStr) ->
                                val score = mStr.trim().toDoubleOrNull()
                                if (score != null) Pair(stuId, score) else null
                            }.toMap()
                            viewModel.saveExamMarks(exam.id, parsed)
                            activeExamForMarks = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary)
                    ) {
                        Text("Save Marks")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { activeExamForMarks = null }) { Text("Close") }
            }
        )
    }

    if (examToDelete != null) {
        AlertDialog(
            onDismissRequest = { examToDelete = null },
            title = { Text("Delete Exam") },
            text = { Text("Will delete exam and all recorded marks.") },
            confirmButton = {
                Button(
                    onClick = {
                        examToDelete?.let { viewModel.deleteExam(it) }
                        examToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralWarning)
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { examToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
