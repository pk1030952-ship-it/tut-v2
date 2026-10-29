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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.PaymentRecord
import com.example.ui.CoachMasterViewModel
import com.example.ui.theme.*
import com.example.util.FeeCalculator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
    viewModel: CoachMasterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val students by viewModel.students.collectAsState()
    val batches by viewModel.batches.collectAsState()
    val allPayments by viewModel.payments.collectAsState()

    var selectedStudent by remember(students) {
        mutableStateOf(uiState.selectedStudentForPayment ?: students.firstOrNull())
    }

    LaunchedEffect(uiState.selectedStudentForPayment, students) {
        if (uiState.selectedStudentForPayment != null) {
            selectedStudent = students.find { it.id == uiState.selectedStudentForPayment!!.id } ?: uiState.selectedStudentForPayment
        } else if (selectedStudent == null && students.isNotEmpty()) {
            selectedStudent = students.first()
        }
    }

    var isStudentDropdownExpanded by remember { mutableStateOf(false) }

    var payAmount by remember { mutableStateOf("") }
    var payDate by remember { mutableStateOf(FeeCalculator.getTodayStr()) }
    var payMode by remember { mutableStateOf("Cash") }
    var payRemarks by remember { mutableStateOf("") }
    var paymentToDelete by remember { mutableStateOf<PaymentRecord?>(null) }

    val studentPayments = remember(allPayments, selectedStudent) {
        if (selectedStudent != null) {
            allPayments.filter { it.studentId == selectedStudent!!.id }
        } else emptyList()
    }

    val dueInfo = remember(selectedStudent, studentPayments) {
        if (selectedStudent != null) {
            FeeCalculator.calculateStudentDues(selectedStudent!!, studentPayments)
        } else null
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("finance_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Fee Management",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Student Selection Dropdown
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Select Student",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ExposedDropdownMenuBox(
                        expanded = isStudentDropdownExpanded,
                        onExpandedChange = { isStudentDropdownExpanded = it }
                    ) {
                        val b = batches.find { it.id == selectedStudent?.batchId }
                        val displayVal = if (selectedStudent != null) {
                            "${selectedStudent!!.name} - ${b?.name ?: "No Batch"}"
                        } else "-- Choose a Student --"

                        OutlinedTextField(
                            value = displayVal,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isStudentDropdownExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag("finance_student_dropdown"),
                            shape = RoundedCornerShape(10.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = isStudentDropdownExpanded,
                            onDismissRequest = { isStudentDropdownExpanded = false }
                        ) {
                            students.forEach { s ->
                                val batchObj = batches.find { it.id == s.batchId }
                                DropdownMenuItem(
                                    text = { Text("${s.name} - ${batchObj?.name ?: "No Batch"}") },
                                    onClick = {
                                        selectedStudent = s
                                        isStudentDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (selectedStudent != null && dueInfo != null) {
            // 3 Financial Status Cards matching v10studendrecord.html
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Total Accrued Fees
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = IndigoDark)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Total Accrued Fees", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                                Text(FeeCalculator.formatCurrency(dueInfo.totalAccrued), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                                Text(
                                    text = "Cycle: ${dueInfo.cyclesElapsed} | Next: ${dueInfo.nextDueDateStr}",
                                    fontSize = 10.sp,
                                    color = IndigoLight
                                )
                            }
                        }

                        // Total Paid History
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Total Paid History", fontSize = 11.sp, color = Color.Gray)
                                Text(FeeCalculator.formatCurrency(dueInfo.totalPaid), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = EmeraldSuccess)
                                Text("${studentPayments.size} transactions", fontSize = 10.sp, color = Color.Gray)
                            }
                        }
                    }

                    // Pending Balance Card with DUE Badge
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (dueInfo.pendingBalance > 0) CoralLight else EmeraldLight
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Pending Balance", fontSize = 12.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                                Text(
                                    FeeCalculator.formatCurrency(dueInfo.pendingBalance),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    color = if (dueInfo.pendingBalance > 0) CoralWarning else EmeraldDark
                                )
                            }
                            if (dueInfo.pendingBalance > 0) {
                                Surface(
                                    color = CoralWarning,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "DUE",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Record Payment Form (Admin only)
            if (uiState.isAdmin) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Record Payment",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = payAmount,
                                    onValueChange = { payAmount = it },
                                    label = { Text("Amount Paid (₹) *") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("pay_amount_input")
                                )

                                OutlinedTextField(
                                    value = payDate,
                                    onValueChange = { payDate = it },
                                    label = { Text("Date Received") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("pay_date_input")
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text("Payment Mode / Remarks:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("Cash", "UPI", "Bank Transfer").forEach { mode ->
                                    FilterChip(
                                        selected = payMode == mode,
                                        onClick = { payMode = mode },
                                        label = { Text(if (mode == "UPI") "UPI / Online" else mode) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = payRemarks,
                                onValueChange = { payRemarks = it },
                                label = { Text("Optional notes... (e.g. Month 2 fee)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("pay_remarks_input")
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    val amt = payAmount.toDoubleOrNull() ?: 0.0
                                    if (amt > 0.0) {
                                        viewModel.recordPayment(
                                            studentId = selectedStudent!!.id,
                                            amount = amt,
                                            date = payDate.trim(),
                                            mode = payMode,
                                            remarks = payRemarks.trim()
                                        )
                                        payAmount = ""
                                        payRemarks = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = IndigoPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("log_transaction_btn")
                            ) {
                                Text("Log Transaction", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Transaction History Header + Print/Share Options Button (matches v10studendrecord.html)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transaction History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Button(
                        onClick = { viewModel.openShareOptionsModal(selectedStudent!!) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IndigoLight,
                            contentColor = IndigoPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("share_pdf_btn")
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Options", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (studentPayments.isEmpty()) {
                item {
                    Text("No payment history found.", color = Color.Gray, fontSize = 13.sp)
                }
            } else {
                items(studentPayments, key = { it.id }) { p ->
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(FeeCalculator.formatDate(p.paymentDate), fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${p.paymentMode}${if (p.remarks.isNotBlank()) " • ${p.remarks}" else ""}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    FeeCalculator.formatCurrency(p.amount),
                                    fontWeight = FontWeight.Bold,
                                    color = EmeraldSuccess,
                                    fontSize = 15.sp
                                )
                                if (uiState.isAdmin) {
                                    IconButton(
                                        onClick = { paymentToDelete = p },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = CoralWarning, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (paymentToDelete != null) {
        AlertDialog(
            onDismissRequest = { paymentToDelete = null },
            title = { Text("Delete Transaction") },
            text = { Text("Remove this payment record?") },
            confirmButton = {
                Button(
                    onClick = {
                        paymentToDelete?.let { viewModel.deletePayment(it) }
                        paymentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralWarning)
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { paymentToDelete = null }) { Text("Cancel") }
            }
        )
    }

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
