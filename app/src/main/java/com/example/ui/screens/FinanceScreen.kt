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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.PaymentRecord
import com.example.ui.FinanceViewModel
import com.example.ui.theme.*
import com.example.util.FeeCalculator
import com.example.util.SecurityUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinanceScreen(
    financeViewModel: FinanceViewModel = viewModel(),
    isAdmin: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val selectedStudentForPayment by financeViewModel.selectedStudentForPayment.collectAsState()
    val selectedStudentForShareOptions by financeViewModel.selectedStudentForShareOptions.collectAsState()
    val students by financeViewModel.students.collectAsState()
    val batches by financeViewModel.batches.collectAsState()
    val allPayments by financeViewModel.payments.collectAsState()
    val allAttendance by financeViewModel.attendance.collectAsState()
    val config by financeViewModel.config.collectAsState()

    var selectedStudent by remember(students) {
        mutableStateOf(selectedStudentForPayment ?: students.firstOrNull())
    }

    LaunchedEffect(selectedStudentForPayment, students) {
        if (selectedStudentForPayment != null) {
            selectedStudent = students.find { it.id == selectedStudentForPayment!!.id } ?: selectedStudentForPayment
        } else if (selectedStudent == null && students.isNotEmpty()) {
            selectedStudent = students.first()
        }
    }

    var isStudentDropdownExpanded by remember { mutableStateOf(false) }

    var payAmount by remember { mutableStateOf("") }
    var payDiscount by remember { mutableStateOf("") }
    var payDate by remember { mutableStateOf(FeeCalculator.getTodayStr()) }
    var payMode by remember { mutableStateOf("Cash") }
    var payRemarks by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    var paymentToDelete by remember { mutableStateOf<PaymentRecord?>(null) }

    val studentPayments = remember(allPayments, selectedStudent) {
        if (selectedStudent != null) {
            allPayments.filter { it.studentId == selectedStudent!!.id }
        } else emptyList()
    }

    val dueInfo = remember(selectedStudent, studentPayments, allAttendance, batches, config) {
        if (selectedStudent != null) {
            val batch = batches.find { it.id == selectedStudent!!.batchId }
            val cycleLen = FeeCalculator.resolveClassesPerCycle(batch, config)
            FeeCalculator.calculateStudentDues(
                student = selectedStudent!!,
                payments = studentPayments,
                attendanceRecords = allAttendance,
                classesPerCycle = cycleLen
            )
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
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
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
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = IndigoDark)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Accrued Fees", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
                                Text(FeeCalculator.formatCurrency(dueInfo.totalAccrued), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                                Text(
                                    text = "Cycles: ${dueInfo.cyclesElapsed} (${dueInfo.currentCycleProgress}/${dueInfo.classesPerCycle}d)",
                                    fontSize = 10.sp,
                                    color = IndigoLight
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Total Paid", fontSize = 11.sp, color = Color.Gray)
                                Text(FeeCalculator.formatCurrency(dueInfo.totalPaid), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = EmeraldSuccess)
                                Text("${studentPayments.size} txns", fontSize = 10.sp, color = Color.Gray)
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Discounts", fontSize = 11.sp, color = Color(0xFF92400E))
                                Text(
                                    FeeCalculator.formatCurrency(dueInfo.totalDiscount),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color(0xFFB45309)
                                )
                                Text("Concession", fontSize = 10.sp, color = Color(0xFF92400E))
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = IndigoLight.copy(alpha = 0.65f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = "Open Days: ${dueInfo.daysElapsed} (Present: ${dueInfo.presentDays}, Absent: ${dueInfo.absentDays})  •  Holidays (Free): ${dueInfo.holidayDays}" +
                                    if (dueInfo.leaveDays > 0) "  •  Leave: ${dueInfo.leaveDays}" else "",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = IndigoDark
                            )
                            Text(
                                text = "Current Cycle Progress: ${dueInfo.currentCycleProgress} / ${dueInfo.classesPerCycle} Classes Completed (${dueInfo.nextDueDateStr})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (dueInfo.pendingBalance > 0) CoralWarning else EmeraldDark
                            )
                        }
                    }

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
                                Text("Pending Balance (Khata)", fontSize = 12.sp, color = Color.DarkGray, fontWeight = FontWeight.Medium)
                                Text(
                                    FeeCalculator.formatCurrency(dueInfo.pendingBalance),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    color = if (dueInfo.pendingBalance > 0) CoralWarning else EmeraldDark
                                )
                                if (dueInfo.totalDiscount > 0) {
                                    Text(
                                        text = "Includes ${FeeCalculator.formatCurrency(dueInfo.totalDiscount)} total discount waived",
                                        fontSize = 11.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                            Surface(
                                color = if (dueInfo.pendingBalance > 0) CoralWarning else EmeraldSuccess,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (dueInfo.pendingBalance > 0) "DUE" else "CLEARED",
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

            if (isAdmin) {
                item {
                    val enteredPaid = payAmount.toDoubleOrNull() ?: 0.0
                    val enteredDiscount = payDiscount.toDoubleOrNull() ?: 0.0
                    val totalSettledNow = enteredPaid + enteredDiscount
                    val remainingAfterEntry = (dueInfo.pendingBalance - totalSettledNow).coerceAtLeast(0.0)
                    val remainingToWaive = (dueInfo.pendingBalance - enteredPaid).coerceAtLeast(0.0)

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Receive Fee & Instant Discount",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (dueInfo.pendingBalance > 0) {
                                    AssistChip(
                                        onClick = {
                                            payAmount = dueInfo.pendingBalance.toInt().toString()
                                            payDiscount = ""
                                            formError = null
                                        },
                                        label = {
                                            Text(
                                                "Full Due: ${FeeCalculator.formatCurrency(dueInfo.pendingBalance)}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = payAmount,
                                    onValueChange = {
                                        payAmount = it
                                        formError = null
                                    },
                                    label = { Text("Paid Amount (₹) *") },
                                    placeholder = { Text("e.g. 100") },
                                    singleLine = true,
                                    isError = formError != null,
                                    modifier = Modifier.weight(1f).testTag("pay_amount_input")
                                )

                                OutlinedTextField(
                                    value = payDiscount,
                                    onValueChange = {
                                        payDiscount = it
                                        formError = null
                                    },
                                    label = { Text("Discount (₹)") },
                                    placeholder = { Text("e.g. 50") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).testTag("pay_discount_input")
                                )
                            }

                            if (formError != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = formError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.testTag("finance_form_error")
                                )
                            }

                            if (dueInfo.pendingBalance > 0 && enteredPaid > 0 && remainingToWaive > 0 && enteredDiscount != remainingToWaive) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("auto_waive_discount_chip"),
                                    onClick = {
                                        payDiscount = if (remainingToWaive % 1.0 == 0.0) {
                                            remainingToWaive.toInt().toString()
                                        } else {
                                            remainingToWaive.toString()
                                        }
                                        if (payRemarks.isBlank()) {
                                            payRemarks = "Fee settled with ₹${remainingToWaive.toInt()} discount"
                                        }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "🏷️ Baaki ${FeeCalculator.formatCurrency(remainingToWaive)} Discount mein chhod dein (Clear Khata)?",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF92400E),
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "Apply +${FeeCalculator.formatCurrency(remainingToWaive)}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = IndigoPrimary
                                        )
                                    }
                                }
                            }

                            if (totalSettledNow > 0.0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (remainingAfterEntry <= 0.0) EmeraldLight else IndigoLight.copy(alpha = 0.6f)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Paid: ${FeeCalculator.formatCurrency(enteredPaid)} + Discount: ${FeeCalculator.formatCurrency(enteredDiscount)} = ${FeeCalculator.formatCurrency(totalSettledNow)}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.DarkGray
                                            )
                                            Text(
                                                text = if (remainingAfterEntry <= 0.0) {
                                                    "New Pending Balance: ₹0 (Khata Clear ✅)"
                                                } else {
                                                    "Remaining Pending Balance: ${FeeCalculator.formatCurrency(remainingAfterEntry)}"
                                                },
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (remainingAfterEntry <= 0.0) EmeraldDark else IndigoDark
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = payDate,
                                onValueChange = { payDate = it },
                                label = { Text("Date Received (YYYY-MM-DD)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("pay_date_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text("Payment Mode:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
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
                                label = { Text("Optional notes... (e.g. Month 1 fee + concession)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("pay_remarks_input")
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    val amt = payAmount.trim().toDoubleOrNull() ?: 0.0
                                    val disc = payDiscount.trim().toDoubleOrNull() ?: 0.0
                                    if (!SecurityUtils.isValidPositiveAmount(payAmount) && disc <= 0.0) {
                                        formError = "Paid Amount must be a positive number greater than 0."
                                        return@Button
                                    }
                                    if (amt < 0.0 || disc < 0.0) {
                                        formError = "Amount and discount cannot be negative."
                                        return@Button
                                    }
                                    formError = null
                                    financeViewModel.recordPayment(
                                        studentId = selectedStudent!!.id,
                                        amount = amt,
                                        date = payDate.trim(),
                                        mode = payMode,
                                        remarks = payRemarks.trim(),
                                        discount = disc
                                    )
                                    payAmount = ""
                                    payDiscount = ""
                                    payRemarks = ""
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
                        onClick = { financeViewModel.openShareOptionsModal(selectedStudent!!) },
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
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        "${FeeCalculator.formatCurrency(p.amount)} Paid",
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldSuccess,
                                        fontSize = 15.sp
                                    )
                                    if (p.discount > 0.0) {
                                        Text(
                                            text = "+ ${FeeCalculator.formatCurrency(p.discount)} Discount",
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFB45309),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                if (isAdmin) {
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
                        paymentToDelete?.let { financeViewModel.deletePayment(it) }
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

    if (selectedStudentForShareOptions != null) {
        val s = selectedStudentForShareOptions!!
        ShareOptionsModal(
            student = s,
            onDismiss = { financeViewModel.closeShareOptionsModal() },
            onPrintFullHistory = {
                financeViewModel.closeShareOptionsModal()
                financeViewModel.generateAndShareFullHistory(context, s)
            },
            onPrintOfficialReceipt = {
                financeViewModel.closeShareOptionsModal()
                financeViewModel.generateAndShareReceipt(context, s, preferWhatsApp = false)
            },
            onSendWhatsAppReceipt = {
                financeViewModel.closeShareOptionsModal()
                financeViewModel.generateAndShareReceipt(context, s, preferWhatsApp = true)
            }
        )
    }
}
