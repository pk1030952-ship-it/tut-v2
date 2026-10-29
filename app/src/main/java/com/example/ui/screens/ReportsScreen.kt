package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.CoachMasterViewModel
import com.example.ui.components.AttendanceTrendLineChart
import com.example.ui.components.IncomeExpenseBarChart
import com.example.ui.theme.CoralWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimary
import com.example.util.FeeCalculator

@Composable
fun ReportsScreen(
    viewModel: CoachMasterViewModel,
    modifier: Modifier = Modifier
) {
    val activeStudents by viewModel.activeStudents.collectAsState()
    val batches by viewModel.batches.collectAsState()
    val payments by viewModel.payments.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val attendance by viewModel.attendance.collectAsState()

    val financeChartData = remember(payments, expenses) {
        viewModel.getMonthlyFinanceData()
    }

    val attendanceTrendData = remember(attendance) {
        viewModel.getMonthlyAttendanceTrend()
    }

    val overdueStudents = remember(activeStudents, payments) {
        activeStudents.map { student ->
            val due = FeeCalculator.calculateStudentDues(student, payments)
            Pair(student, due)
        }.filter { it.second.pendingBalance > 0 }
            .sortedByDescending { it.second.pendingBalance }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .testTag("reports_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Reports & Analytics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        // Chart 1: Income vs. Expenses (Last 6 Months)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().testTag("chart_income_expense")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BarChart, contentDescription = null, tint = IndigoPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Income vs Expenses (Last 6 Months)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    IncomeExpenseBarChart(data = financeChartData)
                }
            }
        }

        // Chart 2: Attendance Trend (Last 6 Months)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth().testTag("chart_attendance_trend")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ShowChart, contentDescription = null, tint = EmeraldSuccess)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Attendance Trend (Last 6 Months)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    AttendanceTrendLineChart(data = attendanceTrendData)
                }
            }
        }

        // All Pending Dues Table
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "All Pending Dues",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Total: ${FeeCalculator.formatCurrency(overdueStudents.sumOf { it.second.pendingBalance })}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = CoralWarning
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (overdueStudents.isEmpty()) {
                        Text("All fees cleared.", color = EmeraldSuccess)
                    } else {
                        overdueStudents.forEach { (student, due) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(student.name, fontWeight = FontWeight.Medium)
                                Text(
                                    text = FeeCalculator.formatCurrency(due.pendingBalance),
                                    fontWeight = FontWeight.Bold,
                                    color = CoralWarning
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        }
                    }
                }
            }
        }

        // Batch-wise Summary Table
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Batch-wise Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    batches.forEach { batch ->
                        val bStudents = activeStudents.filter { it.batchId == batch.id }
                        val potentialRev = bStudents.sumOf { it.monthlyFee }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(batch.name, fontWeight = FontWeight.Medium)
                                Text("${bStudents.size} Students", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            }
                            Text(
                                text = FeeCalculator.formatCurrency(potentialRev),
                                fontWeight = FontWeight.Bold,
                                color = EmeraldSuccess
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    }
                }
            }
        }
    }
}
