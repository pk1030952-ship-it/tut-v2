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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.AttendanceRecord
import com.example.data.model.Batch
import com.example.data.model.CommunicationLog
import com.example.data.model.PaymentRecord
import com.example.data.model.Student
import com.example.ui.StudentViewModel
import com.example.ui.theme.*
import com.example.util.FeeCalculator
import com.example.util.SecurityUtils
import com.example.util.StudentDueInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentsScreen(
    studentViewModel: StudentViewModel = viewModel(),
    isAdmin: Boolean = false,
    onSelectStudentForPayment: (Student) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val studentUiState by studentViewModel.studentUiState.collectAsState()
    val students by studentViewModel.students.collectAsState()
    val batches by studentViewModel.batches.collectAsState()
    val payments by studentViewModel.payments.collectAsState()
    val attendance by studentViewModel.attendance.collectAsState()
    val communications by studentViewModel.communications.collectAsState()
    val config by studentViewModel.config.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedBatchId by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingStudent by remember { mutableStateOf<Student?>(null) }
    var studentToDelete by remember { mutableStateOf<Student?>(null) }

    val batchNameMap = remember(batches) { batches.associate { it.id to it.name } }
    val batchById = remember(batches) { batches.associateBy { it.id } }

    // derivedStateOf optimizations for 200+ students 60 FPS smooth scrolling
    val paymentsByStudentId by remember(payments) {
        derivedStateOf { payments.groupBy { it.studentId } }
    }

    val attendanceByStudentId by remember(attendance) {
        derivedStateOf { attendance.groupBy { it.studentId } }
    }

    val studentDuesMap by remember(students, paymentsByStudentId, attendanceByStudentId, batchById, config) {
        derivedStateOf {
            students.associate { student ->
                val batch = student.batchId?.let { batchById[it] }
                val cycleLen = FeeCalculator.resolveClassesPerCycle(batch, config)
                val stuPayments = paymentsByStudentId[student.id] ?: emptyList()
                val stuAtt = attendanceByStudentId[student.id] ?: emptyList()
                student.id to FeeCalculator.calculateStudentDues(
                    student = student,
                    payments = stuPayments,
                    attendanceRecords = stuAtt,
                    classesPerCycle = cycleLen
                )
            }
        }
    }

    val filteredStudents by remember(students, searchQuery, selectedBatchId, batchNameMap) {
        derivedStateOf {
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
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            if (isAdmin) {
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
                text = "Tap a student's name to view their full profile, 30-day cycle & history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(10.dp))

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
                Text(
                    text = "💡 Swipe Right 👉 for Direct Fee Collection  •  Swipe Left 👈 for WhatsApp",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = IndigoPrimary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredStudents, key = { it.id }) { student ->
                        val batchName = student.batchId?.let { batchNameMap[it] } ?: "None"
                        val dueInfo = studentDuesMap[student.id] ?: remember(student, config) {
                            FeeCalculator.calculateStudentDues(
                                student = student,
                                payments = emptyList(),
                                attendanceRecords = emptyList(),
                                classesPerCycle = config.defaultClassesPerCycle
                            )
                        }

                        SwipeableStudentCard(
                            student = student,
                            batchName = batchName,
                            dueInfo = dueInfo,
                            isAdmin = isAdmin,
                            onSwipeRightCollectFee = {
                                if (isAdmin) {
                                    onSelectStudentForPayment(student)
                                } else {
                                    studentViewModel.openStudentDossier(student)
                                }
                            },
                            onSwipeLeftWhatsApp = {
                                com.example.util.ShareHelper.openDirectWhatsAppMessage(
                                    context = context,
                                    student = student,
                                    dueInfo = dueInfo,
                                    instituteName = config.instituteName
                                )
                            },
                            onCardClick = { studentViewModel.openStudentDossier(student) },
                            onEditClick = {
                                editingStudent = student
                                showAddDialog = true
                            },
                            onAttendanceHistoryClick = { studentViewModel.openAttendanceHistoryModal(student) },
                            onCommLogClick = { studentViewModel.openCommLogModal(student) },
                            onShareOptionsClick = { studentViewModel.openShareOptionsModal(student) },
                            onToggleStatus = { studentViewModel.toggleStudentStatus(student) },
                            onDeleteClick = { studentToDelete = student }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditStudentDialog(
            existingStudent = editingStudent,
            batches = batches,
            onDismiss = { showAddDialog = false },
            onSave = { student ->
                studentViewModel.saveStudent(student)
                showAddDialog = false
            }
        )
    }

    if (studentToDelete != null) {
        AlertDialog(
            onDismissRequest = { studentToDelete = null },
            title = { Text("Delete Student") },
            text = { Text("Are you sure? This removes their profile, attendance, and fee records completely.") },
            confirmButton = {
                Button(
                    onClick = {
                        studentToDelete?.let { studentViewModel.deleteStudent(it.id) }
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

    if (studentUiState.selectedStudentForDossier != null) {
        val s = studentUiState.selectedStudentForDossier!!
        val batch = batches.find { it.id == s.batchId }
        val cycleLen = FeeCalculator.resolveClassesPerCycle(batch, config)
        FinanceHistoryModal(
            student = s,
            payments = payments.filter { it.studentId == s.id },
            attendanceRecords = attendance.filter { it.studentId == s.id },
            classesPerCycle = cycleLen,
            isAdmin = isAdmin,
            onDismiss = { studentViewModel.closeStudentDossier() },
            onReceivePayment = {
                studentViewModel.closeStudentDossier()
                onSelectStudentForPayment(s)
            }
        )
    }

    if (studentUiState.selectedStudentForAttendanceHistory != null) {
        val s = studentUiState.selectedStudentForAttendanceHistory!!
        AttendanceHistoryModal(
            student = s,
            attendanceRecords = attendance.filter { it.studentId == s.id },
            onDismiss = { studentViewModel.closeAttendanceHistoryModal() }
        )
    }

    if (studentUiState.selectedStudentForCommLog != null) {
        val s = studentUiState.selectedStudentForCommLog!!
        CommunicationLogModal(
            student = s,
            logs = communications.filter { it.studentId == s.id },
            isAdmin = isAdmin,
            onDismiss = { studentViewModel.closeCommLogModal() },
            onAddNote = { date, note -> studentViewModel.addCommunicationLog(s.id, note, date) },
            onDeleteNote = { log -> studentViewModel.deleteCommunicationLog(log) }
        )
    }

    if (studentUiState.selectedStudentForShareOptions != null) {
        val s = studentUiState.selectedStudentForShareOptions!!
        ShareOptionsModal(
            student = s,
            onDismiss = { studentViewModel.closeShareOptionsModal() },
            onPrintFullHistory = {
                studentViewModel.closeShareOptionsModal()
                studentViewModel.generateAndShareFullHistory(context, s)
            },
            onPrintOfficialReceipt = {
                studentViewModel.closeShareOptionsModal()
                studentViewModel.generateAndShareReceipt(context, s, preferWhatsApp = false)
            },
            onSendWhatsAppReceipt = {
                studentViewModel.closeShareOptionsModal()
                studentViewModel.generateAndShareReceipt(context, s, preferWhatsApp = true)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableStudentCard(
    student: Student,
    batchName: String,
    dueInfo: StudentDueInfo,
    isAdmin: Boolean,
    onSwipeRightCollectFee: () -> Unit,
    onSwipeLeftWhatsApp: () -> Unit,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    onAttendanceHistoryClick: () -> Unit,
    onCommLogClick: () -> Unit,
    onShareOptionsClick: () -> Unit,
    onToggleStatus: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val currentCollectFee by rememberUpdatedState(onSwipeRightCollectFee)
    val currentWhatsApp by rememberUpdatedState(onSwipeLeftWhatsApp)

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { targetValue ->
            when (targetValue) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    currentCollectFee()
                    false // Snap back after triggering Fee Collection
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    currentWhatsApp()
                    false // Snap back after triggering WhatsApp
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val direction = dismissState.dismissDirection
            val bgColor = when (direction) {
                SwipeToDismissBoxValue.StartToEnd -> EmeraldSuccess
                SwipeToDismissBoxValue.EndToStart -> Color(0xFF128C7E)
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(bgColor)
                    .padding(horizontal = 20.dp),
                contentAlignment = when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    SwipeToDismissBoxValue.Settled -> Alignment.Center
                }
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CurrencyRupee,
                                contentDescription = "Direct Fee Collection",
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Collect Fee",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                    SwipeToDismissBoxValue.EndToStart -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "WhatsApp Parent",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Direct WhatsApp Message",
                                tint = Color.White
                            )
                        }
                    }
                    SwipeToDismissBoxValue.Settled -> {}
                }
            }
        }
    ) {
        StudentCardItem(
            student = student,
            batchName = batchName,
            dueInfo = dueInfo,
            isAdmin = isAdmin,
            onCardClick = onCardClick,
            onEditClick = onEditClick,
            onAttendanceHistoryClick = onAttendanceHistoryClick,
            onCommLogClick = onCommLogClick,
            onShareOptionsClick = onShareOptionsClick,
            onToggleStatus = onToggleStatus,
            onDeleteClick = onDeleteClick
        )
    }
}

@Composable
fun StudentCardItem(
    student: Student,
    batchName: String,
    dueInfo: StudentDueInfo,
    isAdmin: Boolean,
    onCardClick: () -> Unit,
    onEditClick: () -> Unit,
    onAttendanceHistoryClick: () -> Unit,
    onCommLogClick: () -> Unit,
    onShareOptionsClick: () -> Unit,
    onToggleStatus: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val pendingDues = dueInfo.pendingBalance
    val displayPhone = remember(student.phone, isAdmin) {
        SecurityUtils.maskPhoneNumber(student.phone, isAdmin)
    }
    val progressFraction = remember(dueInfo.currentCycleProgress, dueInfo.classesPerCycle) {
        if (dueInfo.classesPerCycle > 0) {
            (dueInfo.currentCycleProgress.toFloat() / dueInfo.classesPerCycle.toFloat()).coerceIn(0f, 1f)
        } else 0f
    }

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
                        text = "${student.rollNo.ifBlank { student.id.take(8) }} | $displayPhone",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Batch: $batchName  •  Cycle Fee: ${FeeCalculator.formatCurrency(student.monthlyFee)}",
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
                    if (dueInfo.totalDiscount > 0.0) {
                        Text(
                            text = "Disc: ${FeeCalculator.formatCurrency(dueInfo.totalDiscount)}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFB45309)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${dueInfo.currentCycleProgress} / ${dueInfo.classesPerCycle} Classes Completed (${dueInfo.classesLeftInCycle} classes left for next fee)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (dueInfo.holidayDays > 0) {
                        Text(
                            text = "🏖️ ${dueInfo.holidayDays} Holidays Free",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoPrimary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (pendingDues > 0) CoralWarning else IndigoPrimary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
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
                    IconButton(onClick = onEditClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Profile", tint = IndigoPrimary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onAttendanceHistoryClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Attendance History", tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onCommLogClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Comment, contentDescription = "Communication Log", tint = Color(0xFF0EA5E9), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onShareOptionsClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Share, contentDescription = "Share & Print Options", tint = Color(0xFF8B5CF6), modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onToggleStatus, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (student.status == "active") Icons.Default.PersonOff else Icons.Default.HowToReg,
                            contentDescription = "Toggle Active Status",
                            tint = if (student.status == "active") CoralWarning else EmeraldSuccess,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = rollNo,
                        onValueChange = {
                            rollNo = it
                            errorMessage = null
                        },
                        label = { Text("Roll No / Admission ID *") },
                        placeholder = { Text("e.g., 101 or STU101") },
                        singleLine = true,
                        isError = errorMessage != null && rollNo.isBlank(),
                        modifier = Modifier.weight(1f).testTag("student_form_roll")
                    )
                    AssistChip(
                        onClick = {
                            rollNo = "STU${System.currentTimeMillis().toString().takeLast(4)}"
                            errorMessage = null
                        },
                        label = { Text("Auto ID", fontSize = 11.sp) }
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("Full Name *") },
                    singleLine = true,
                    isError = errorMessage != null && name.isBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("student_form_name")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = {
                        phone = it.filter { ch -> ch.isDigit() }.take(10)
                        errorMessage = null
                    },
                    label = { Text("10-Digit Mobile Number *") },
                    placeholder = { Text("e.g., 9876543210") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = errorMessage != null && !SecurityUtils.isValidTenDigitPhone(phone),
                    supportingText = {
                        Text("${phone.length}/10 digits", fontSize = 10.sp)
                    },
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
                        onValueChange = {
                            monthlyFee = it
                            errorMessage = null
                        },
                        label = { Text("Monthly Fee (₹) *") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = errorMessage != null && !SecurityUtils.isValidPositiveAmount(monthlyFee),
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
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CoralLight),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("student_form_error")
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = CoralWarning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val validationError = SecurityUtils.validateStudentInput(
                        rollNo = rollNo,
                        name = name,
                        phone = phone,
                        monthlyFeeStr = monthlyFee
                    )
                    if (validationError != null) {
                        errorMessage = validationError
                        return@Button
                    }

                    val feeDouble = monthlyFee.trim().toDouble()
                    val cleanRoll = rollNo.trim()

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
 * 1. FINANCE HISTORY & STUDENT DOSSIER MODAL (Includes Improvement 5 Working Days & Holiday Proof)
 */
@Composable
fun FinanceHistoryModal(
    student: Student,
    payments: List<PaymentRecord>,
    attendanceRecords: List<AttendanceRecord>? = null,
    classesPerCycle: Int = 30,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onReceivePayment: () -> Unit
) {
    val dueInfo = remember(student, payments, attendanceRecords, classesPerCycle) {
        FeeCalculator.calculateStudentDues(
            student = student,
            payments = payments,
            attendanceRecords = attendanceRecords,
            classesPerCycle = classesPerCycle
        )
    }

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
                        text = "Full 30-day working cycle & fee breakdown",
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("PENDING DUES", fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(
                                text = FeeCalculator.formatCurrency(dueInfo.pendingBalance),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (dueInfo.pendingBalance > 0) CoralWarning else EmeraldSuccess
                            )
                        }
                    }

                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("DISCOUNT", fontSize = 9.sp, color = Color(0xFF92400E), fontWeight = FontWeight.Bold)
                            Text(
                                text = FeeCalculator.formatCurrency(dueInfo.totalDiscount),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309)
                            )
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
                            Text("CYCLES", fontSize = 9.sp, color = IndigoPrimary, fontWeight = FontWeight.Bold)
                            Text(
                                text = "${dueInfo.cyclesElapsed} Cycles",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = IndigoPrimary
                            )
                        }
                    }
                }

                // Working Days & Holiday Proof Card (Improvement 5)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = IndigoLight.copy(alpha = 0.65f)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "📋 Working Days & Holiday Proof",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = IndigoDark
                        )
                        Text(
                            text = "• Total Tuition Open Days: ${dueInfo.daysElapsed} Days (Present: ${dueInfo.presentDays}, Absent/Bunk: ${dueInfo.absentDays})",
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )
                        Text(
                            text = "• Tuition Holidays (Not Charged): ${dueInfo.holidayDays} Days (Free / Extended)",
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )
                        if (dueInfo.leaveDays > 0) {
                            Text(
                                text = "• Approved Leave (Fee Paused): ${dueInfo.leaveDays} Days",
                                fontSize = 11.sp,
                                color = Color(0xFFB45309)
                            )
                        }
                        Text(
                            text = "• Current Cycle Progress: ${dueInfo.currentCycleProgress} / ${dueInfo.classesPerCycle} Days Completed -> ${if (dueInfo.pendingBalance > 0) "Fee Due Now!" else "${dueInfo.classesLeftInCycle} classes left"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (dueInfo.pendingBalance > 0) CoralWarning else EmeraldDark
                        )
                    }
                }

                Text("Payment History", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                if (payments.isEmpty()) {
                    Text("No payment records found.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp),
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
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = FeeCalculator.formatCurrency(p.amount),
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldSuccess
                                    )
                                    if (p.discount > 0.0) {
                                        Text(
                                            text = "+${FeeCalculator.formatCurrency(p.discount)} Disc.",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFB45309)
                                        )
                                    }
                                }
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
 * 2. ATTENDANCE HISTORY MODAL (Shows Present, Absent/Bunk, Holiday, and Approved Leave)
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
    val holidayCount = sorted.count { it.status == "holiday" }
    val leaveCount = sorted.count { it.status == "leave" }
    val openTotal = presentCount + absentCount
    val pct = if (openTotal > 0) Math.round((presentCount.toFloat() / openTotal) * 100f) else 0

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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = EmeraldLight)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("$presentCount", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = EmeraldSuccess)
                            Text("Present", fontSize = 10.sp, color = Color.DarkGray)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = CoralLight)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("$absentCount", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = CoralWarning)
                            Text("Absent", fontSize = 10.sp, color = Color.DarkGray)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("$holidayCount", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.DarkGray)
                            Text("Holidays", fontSize = 10.sp, color = Color.DarkGray)
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = IndigoLight)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("$pct%", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = IndigoPrimary)
                            Text("Rate", fontSize = 10.sp, color = Color.DarkGray)
                        }
                    }
                }

                if (leaveCount > 0) {
                    Text(
                        text = "⏸️ Approved Leave (Fee Paused): $leaveCount Days",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFB45309)
                    )
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
                                Column {
                                    Text(FeeCalculator.formatDate(r.date), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    if (r.reason.isNotBlank()) {
                                        Text(r.reason, fontSize = 10.sp, color = Color.Gray)
                                    }
                                }

                                val (badgeBg, badgeFg, label) = when (r.status) {
                                    "present" -> Triple(EmeraldLight, EmeraldDark, "Present (+1d)")
                                    "absent" -> Triple(CoralLight, CoralWarning, "Absent (+1d)")
                                    "holiday" -> Triple(Color(0xFFE2E8F0), Color(0xFF334155), "Holiday (0d) 🏖️")
                                    "leave" -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "Leave (0d) ⏸️")
                                    else -> Triple(Color.LightGray, Color.DarkGray, r.status)
                                }

                                Box(
                                    modifier = Modifier
                                        .background(badgeBg, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeFg
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
