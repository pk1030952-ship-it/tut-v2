package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AttendanceRecord
import com.example.data.model.Batch
import com.example.data.model.CommunicationLog
import com.example.data.model.PaymentRecord
import com.example.data.model.Student
import com.example.ui.CoachMasterViewModel
import com.example.ui.theme.*
import com.example.util.FeeCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentsScreen(
    viewModel: CoachMasterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val students by viewModel.students.collectAsState()
    val batches by viewModel.batches.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val attendance by viewModel.attendance.collectAsState()
    val communications by viewModel.communications.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedBatchId by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var studentToDelete by remember { mutableStateOf<Student?>(null) }

    val batchNameMap = remember(batches) { batches.associate { it.id to it.name } }

    // Filter students by name, phone, roll, or batch name (matching v10studendrecord.html)
    val filteredStudents = remember(students, searchQuery, selectedBatchId, batchNameMap) {
        students.filter { student ->
            val bName = student.batchId?.let { batchNameMap[it] } ?: ""
            val matchesQuery = student.name.contains(searchQuery, ignoreCase = true) ||
                    student.phone.contains(searchQuery) ||
                    student.rollNo.contains(searchQuery, ignoreCase = true) ||
                    bName.contains(searchQuery, ignoreCase = true)
            val matchesBatch = selectedBatchId == null || student.batchId == selectedBatchId
            matchesQuery && matchesBatch
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (uiState.isAdmin) {
                FloatingActionButton(
                    onClick = {
                        editingStudent = null
                        showAddDialog = true
                    },
                    containerColor = IndigoPrimary,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("add_student_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Student")
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
            Text(
                text = "Student Directory",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tap a student's name to view their full profile & history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Search Input
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("student_search_input"),
                placeholder = { Text("Search students by name, phone or batch...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Batch filter chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedBatchId == null,
                        onClick = { selectedBatchId = null },
                        label = { Text("All Batches (${students.size})") },
                        modifier = Modifier.testTag("batch_filter_all")
                    )
                }
                items(batches) { batch ->
                    val count = students.count { it.batchId == batch.id }
                    FilterChip(
                        selected = selectedBatchId == batch.id,
                        onClick = { selectedBatchId = batch.id },
                        label = { Text("${batch.name} ($count)") },
                        modifier = Modifier.testTag("batch_filter_${batch.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredStudents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No students found. Add a new student to get started.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredStudents, key = { it.id }) { student ->
                        val batch = batches.find { it.id == student.batchId }
                        val dueInfo = FeeCalculator.calculateStudentDues(student, payments)

                        StudentCardItem(
                            student = student,
                            batchName = batch?.name ?: "None",
                            pendingDues = dueInfo.pendingBalance,
                            isAdmin = uiState.isAdmin,
                            onCardClick = { viewModel.openStudentDossier(student) },
                            onEditClick = {
                                editingStudent = student
                                showAddDialog = true
                            },
                            onAttendanceHistoryClick = { viewModel.openAttendanceHistoryModal(student) },
                            onCommLogClick = { viewModel.openCommLogModal(student) },
                            onShareOptionsClick = { viewModel.openShareOptionsModal(student) },
                            onToggleStatus = { viewModel.toggleStudentStatus(student) },
                            onDeleteClick = { studentToDelete = student }
                        )
                    }
                }
            }
        }
    }

    // Add / Edit Student Dialog
    if (showAddDialog) {
        AddEditStudentDialog(
            existingStudent = editingStudent,
            batches = batches,
            onDismiss = { showAddDialog = false },
            onSave = { student ->
                viewModel.saveStudent(student)
                showAddDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    if (studentToDelete != null) {
        AlertDialog(
            onDismissRequest = { studentToDelete = null },
            title = { Text("Delete Student") },
            text = { Text("Are you sure? This removes their profile, attendance, and fee records completely.") },
            confirmButton = {
                Button(
                    onClick = {
                        studentToDelete?.let { viewModel.deleteStudent(it.id) }
                        studentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralWarning)
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { studentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // 1. Finance History & Profile Modal (Matches finance-history-modal in v10studendrecord.html)
    if (uiState.selectedStudentForDossier != null) {
        val s = uiState.selectedStudentForDossier!!
        FinanceHistoryModal(
            student = s,
            payments = payments.filter { it.studentId == s.id },
            isAdmin = uiState.isAdmin,
            onDismiss = { viewModel.closeStudentDossier() },
            onReceivePayment = {
                viewModel.closeStudentDossier()
                viewModel.selectStudentForPayment(s)
            }
        )
    }

    // 2. Attendance History Modal (Matches attendance-history-modal in v10studendrecord.html)
    if (uiState.selectedStudentForAttendanceHistory != null) {
        val s = uiState.selectedStudentForAttendanceHistory!!
        AttendanceHistoryModal(
            student = s,
            attendanceRecords = attendance.filter { it.studentId == s.id },
            onDismiss = { viewModel.closeAttendanceHistoryModal() }
        )
    }

    // 3. Communication Log Modal (Matches comm-log-modal in v10studendrecord.html)
    if (uiState.selectedStudentForCommLog != null) {
        val s = uiState.selectedStudentForCommLog!!
        CommunicationLogModal(
            student = s,
            logs = communications.filter { it.studentId == s.id },
            isAdmin = uiState.isAdmin,
            onDismiss = { viewModel.closeCommLogModal() },
            onAddNote = { date, note -> viewModel.addCommunicationLog(s.id, note, date) },
            onDeleteNote = { log -> viewModel.deleteCommunicationLog(log) }
        )
    }

    // 4. Share & Print Options Modal (Matches share-options-modal in v10studendrecord.html)
    if (uiState.selectedStudentForShareOptions != null) {
        val s = uiState.selectedStudentForShareOptions!!
        ShareOptionsModal(
            student = s,
            onDismiss = { viewModel.closeShareOptionsModal() },
            onPrintFullHistory = {
                viewModel.closeShareOptionsModal()
                viewModel.generateAndShareFullHistory(context, s)
            },
            onPrintOfficialReceipt = {
                viewModel.closeShareOptionsModal()
                viewModel.generateAndShareReceipt(context, s, preferWhatsApp = false)
            },
            onSendWhatsAppReceipt = {
                viewModel.closeShareOptionsModal()
                viewModel.generateAndShareReceipt(context, s, preferWhatsApp = true)
            }
        )
    }
}

@Composable
fun StudentCardItem(
    student: Student,
    batchName: String,
    pendingDues: Double,
    isAdmin: Boolean,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    onAttendanceHistoryClick: () -> Unit,
    onCommLogClick: () -> Unit,
    onShareOptionsClick: () -> Unit,
    onToggleStatus: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("student_card_${student.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(IndigoPrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = student.name.firstOrNull()?.uppercase() ?: "S",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = IndigoPrimary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = student.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textDecoration = TextDecoration.Underline,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = IndigoPrimary.copy(alpha = 0.7f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (student.status == "active") EmeraldLight else Color.LightGray.copy(alpha = 0.4f)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (student.status == "active") "Active" else "Inactive",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (student.status == "active") EmeraldDark else Color.DarkGray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${student.rollNo.ifBlank { student.id.take(8) }} | ${student.phone}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Batch: $batchName  •  Monthly Fee: ${FeeCalculator.formatCurrency(student.monthlyFee)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (pendingDues > 0) {
                        Text(
                            text = FeeCalculator.formatCurrency(pendingDues),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = CoralWarning
                        )
                        Text("Due", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CoralWarning)
                    } else {
                        Text("Cleared", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmeraldSuccess)
                    }
                }
            }

            if (isAdmin) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // 1. Edit Profile
                    IconButton(onClick = onEditClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = IndigoPrimary, modifier = Modifier.size(18.dp))
                    }
                    // 2. Attendance History
                    IconButton(onClick = onAttendanceHistoryClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Attendance History", tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                    }
                    // 3. Communication Log
                    IconButton(onClick = onCommLogClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Comment, contentDescription = "Communication Log", tint = Color(0xFF0EA5E9), modifier = Modifier.size(18.dp))
                    }
                    // 4. Share & Print Options
                    IconButton(onClick = onShareOptionsClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Share & Print Options", tint = Color(0xFF8B5CF6), modifier = Modifier.size(18.dp))
                    }
                    // 5. Toggle Active Status
                    IconButton(onClick = onToggleStatus, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (student.status == "active") Icons.Default.PersonOff else Icons.Default.HowToReg,
                            contentDescription = "Toggle Active Status",
                            tint = if (student.status == "active") CoralWarning else EmeraldSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // 6. Delete Permanently
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Permanently", tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditStudentDialog(
    existingStudent: Student?,
    batches: List<Batch>,
    onDismiss: () -> Unit,
    onSave: (Student) -> Unit
) {
    var rollNo by remember { mutableStateOf(existingStudent?.rollNo ?: "") }
    var name by remember { mutableStateOf(existingStudent?.name ?: "") }
    var phone by remember { mutableStateOf(existingStudent?.phone ?: "") }
    var selectedBatchId by remember { mutableStateOf(existingStudent?.batchId) }
    var monthlyFee by remember { mutableStateOf(existingStudent?.monthlyFee?.toInt()?.toString() ?: "") }
    var joiningDate by remember { mutableStateOf(existingStudent?.joiningDate ?: FeeCalculator.getTodayStr()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existingStudent == null) "Register New Student" else "Edit Student Profile") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = rollNo,
                    onValueChange = { rollNo = it },
                    label = { Text("Admission No / ID") },
                    placeholder = { Text("Auto-generated if empty") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("student_form_roll")
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("student_form_name")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Contact Number *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("student_form_phone")
                )

                Text("Assign to Batch:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedBatchId == null,
                        onClick = { selectedBatchId = null },
                        label = { Text("No Batch") }
                    )
                    batches.forEach { batch ->
                        FilterChip(
                            selected = selectedBatchId == batch.id,
                            onClick = { selectedBatchId = batch.id },
                            label = { Text(batch.name) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = monthlyFee,
                        onValueChange = { monthlyFee = it },
                        label = { Text("Monthly Fee (₹) *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("student_form_fee")
                    )

                    OutlinedTextField(
                        value = joiningDate,
                        onValueChange = { joiningDate = it },
                        label = { Text("Joining Date *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank() || phone.isBlank() || monthlyFee.isBlank()) {
                        errorMessage = "Name, Contact Number, and Monthly Fee are required."
                        return@Button
                    }
                    val feeDouble = monthlyFee.toDoubleOrNull() ?: 0.0
                    val cleanRoll = rollNo.trim().ifBlank { "STU${System.currentTimeMillis().toString().takeLast(4)}" }

                    val student = existingStudent?.copy(
                        name = name.trim(),
                        rollNo = cleanRoll,
                        phone = phone.trim(),
                        batchId = selectedBatchId,
                        monthlyFee = feeDouble,
                        joiningDate = joiningDate.trim()
                    ) ?: Student(
                        name = name.trim(),
                        rollNo = cleanRoll,
                        phone = phone.trim(),
                        batchId = selectedBatchId,
                        monthlyFee = feeDouble,
                        joiningDate = joiningDate.trim()
                    )

                    onSave(student)
                },
                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                modifier = Modifier.testTag("student_form_save_btn")
            ) {
                Text("Save Profile")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * 1. FINANCE HISTORY MODAL (Matches finance-history-modal in v10studendrecord.html)
 */
@Composable
fun FinanceHistoryModal(
    student: Student,
    payments: List<PaymentRecord>,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onReceivePayment: () -> Unit
) {
    val dueInfo = remember(student, payments) { FeeCalculator.calculateStudentDues(student, payments) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "${student.name}'s Profile & History", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(
                        text = "Full fee breakdown",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("PENDING DUES", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(
                                text = FeeCalculator.formatCurrency(dueInfo.pendingBalance),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = CoralWarning
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = IndigoLight)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("TIME COMPLETED", fontSize = 10.sp, color = IndigoPrimary, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${dueInfo.cyclesElapsed} Cycles",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(EmeraldLight, RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Next 30-day cycle completes on: ${dueInfo.nextDueDateStr}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = EmeraldDark
                    )
                }

                Text("Payment History", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                if (payments.isEmpty()) {
                    Text("No payment records found.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(payments) { p ->
                            val timeStr = FeeCalculator.formatTimeFromIso(p.isoTimestamp, p.timestamp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(FeeCalculator.formatDate(p.paymentDate), fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                    if (timeStr.isNotBlank()) {
                                        Text(timeStr, fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                                Text(
                                    text = FeeCalculator.formatCurrency(p.amount),
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldSuccess,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                                    Text(p.paymentMode, fontSize = 11.sp, color = Color.Gray)
                                    Text(p.remarks.ifBlank { "-" }, fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        }
                    }
                }

                if (isAdmin) {
                    Button(
                        onClick = onReceivePayment,
                        colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CurrencyRupee, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Receive Payment", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/**
 * 2. ATTENDANCE HISTORY MODAL (Matches attendance-history-modal in v10studendrecord.html)
 */
@Composable
fun AttendanceHistoryModal(
    student: Student,
    attendanceRecords: List<AttendanceRecord>,
    onDismiss: () -> Unit
) {
    val sorted = remember(attendanceRecords) { attendanceRecords.sortedByDescending { it.date } }
    val presentCount = sorted.count { it.status == "present" }
    val absentCount = sorted.count { it.status == "absent" }
    val total = sorted.size
    val pct = if (total > 0) Math.round((presentCount.toFloat() / total) * 100f) else 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${student.name}'s Attendance", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = EmeraldLight)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("$presentCount", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = EmeraldSuccess)
                            Text("Present", fontSize = 11.sp, color = Color.DarkGray)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CoralLight)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("$absentCount", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = CoralWarning)
                            Text("Absent", fontSize = 11.sp, color = Color.DarkGray)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = IndigoLight)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("$pct%", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = IndigoPrimary)
                            Text("Attendance", fontSize = 11.sp, color = Color.DarkGray)
                        }
                    }
                }

                if (sorted.isEmpty()) {
                    Text("No attendance records found.", color = Color.Gray, fontSize = 13.sp)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(sorted) { r ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(FeeCalculator.formatDate(r.date), fontSize = 13.sp)
                                val isPresent = r.status == "present"
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isPresent) EmeraldLight else CoralLight,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = if (isPresent) "Present" else "Absent",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPresent) EmeraldDark else CoralWarning
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/**
 * 3. COMMUNICATION LOG MODAL (Matches comm-log-modal in v10studendrecord.html)
 */
@Composable
fun CommunicationLogModal(
    student: Student,
    logs: List<CommunicationLog>,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onAddNote: (String, String) -> Unit,
    onDeleteNote: (CommunicationLog) -> Unit
) {
    var noteDate by remember { mutableStateOf(FeeCalculator.getTodayStr()) }
    var noteText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${student.name} - Comms Log", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isAdmin) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("New Note / Call Log", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            OutlinedTextField(
                                value = noteDate,
                                onValueChange = { noteDate = it },
                                label = { Text("Date (YYYY-MM-DD)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = noteText,
                                onValueChange = { noteText = it },
                                label = { Text("Note") },
                                placeholder = { Text("e.g., Parent requested extension for fees...") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (noteText.isNotBlank()) {
                                        onAddNote(noteDate.trim(), noteText.trim())
                                        noteText = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Add Note")
                            }
                        }
                    }
                }

                if (logs.isEmpty()) {
                    Text("No notes recorded yet.", fontSize = 13.sp, color = Color.Gray)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(logs, key = { it.id }) { log ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(FeeCalculator.formatDate(log.date), fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                                    Text(log.note, fontSize = 13.sp)
                                }
                                if (isAdmin) {
                                    IconButton(
                                        onClick = { onDeleteNote(log) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Note", tint = CoralWarning, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

/**
 * 4. SHARE & PRINT OPTIONS MODAL (Matches share-options-modal in v10studendrecord.html)
 */
@Composable
fun ShareOptionsModal(
    student: Student,
    onDismiss: () -> Unit,
    onPrintFullHistory: () -> Unit,
    onPrintOfficialReceipt: () -> Unit,
    onSendWhatsAppReceipt: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Share & Print Options", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPrintFullHistory,
                    colors = ButtonDefaults.buttonColors(containerColor = IndigoLight, contentColor = IndigoPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Print / Share Full History Report", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onPrintOfficialReceipt,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDBEAFE), contentColor = Color(0xFF1D4ED8)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Print / Download Official Receipt PDF", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onSendWhatsAppReceipt,
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldLight, contentColor = EmeraldDark),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Receipt PDF on WhatsApp", fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
