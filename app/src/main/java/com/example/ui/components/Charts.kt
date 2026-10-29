package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CoralWarning
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.IndigoPrimary
import com.example.util.FeeCalculator

data class MonthlyFinanceData(
    val monthLabel: String,
    val income: Double,
    val expense: Double
)

@Composable
fun IncomeExpenseBarChart(
    data: List<MonthlyFinanceData>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No financial data available yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        return
    }

    val maxVal = remember(data) {
        val highest = data.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1.0
        if (highest <= 0.0) 1000.0 else highest
    }

    var animationTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animationTrigger = true }
    val progress by animateFloatAsState(
        targetValue = if (animationTrigger) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "barAnimation"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(10.dp).background(IndigoPrimary, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Income", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))

            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.size(10.dp).background(CoralWarning, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Expense", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val chartWidth = size.width
            val chartHeight = size.height - 30f // Leave space for labels
            val groupCount = data.size
            val groupWidth = chartWidth / groupCount
            val barWidth = (groupWidth * 0.28f).coerceAtLeast(10f)
            val corner = CornerRadius(6f, 6f)

            // Draw baseline
            drawLine(
                color = Color.LightGray.copy(alpha = 0.4f),
                start = Offset(0f, chartHeight),
                end = Offset(chartWidth, chartHeight),
                strokeWidth = 2f
            )

            data.forEachIndexed { index, item ->
                val groupCenterX = (index * groupWidth) + (groupWidth / 2)

                // Income bar
                val incomeHeight = ((item.income / maxVal) * chartHeight * progress).toFloat()
                val incomeLeft = groupCenterX - barWidth - 3f
                drawRoundRect(
                    color = IndigoPrimary,
                    topLeft = Offset(incomeLeft, chartHeight - incomeHeight),
                    size = Size(barWidth, incomeHeight),
                    cornerRadius = corner
                )

                // Expense bar
                val expenseHeight = ((item.expense / maxVal) * chartHeight * progress).toFloat()
                val expenseLeft = groupCenterX + 3f
                drawRoundRect(
                    color = CoralWarning,
                    topLeft = Offset(expenseLeft, chartHeight - expenseHeight),
                    size = Size(barWidth, expenseHeight),
                    cornerRadius = corner
                )
            }
        }

        // Labels row below bars
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            data.forEach { item ->
                Text(
                    text = item.monthLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }
    }
}

data class MonthlyAttendanceData(
    val monthLabel: String,
    val percentage: Int // 0 to 100
)

@Composable
fun AttendanceTrendLineChart(
    data: List<MonthlyAttendanceData>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No attendance trend available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        return
    }

    var animationTrigger by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { animationTrigger = true }
    val progress by animateFloatAsState(
        targetValue = if (animationTrigger) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "lineAnimation"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val chartWidth = size.width
            val chartHeight = size.height - 24f
            val count = data.size
            if (count < 2) return@Canvas

            val stepX = chartWidth / (count - 1)

            // Draw horizontal guide lines (50% and 100%)
            drawLine(
                color = Color.LightGray.copy(alpha = 0.25f),
                start = Offset(0f, 0f),
                end = Offset(chartWidth, 0f),
                strokeWidth = 1f
            )
            drawLine(
                color = Color.LightGray.copy(alpha = 0.25f),
                start = Offset(0f, chartHeight / 2),
                end = Offset(chartWidth, chartHeight / 2),
                strokeWidth = 1f
            )
            drawLine(
                color = Color.LightGray.copy(alpha = 0.4f),
                start = Offset(0f, chartHeight),
                end = Offset(chartWidth, chartHeight),
                strokeWidth = 2f
            )

            val points = data.mapIndexed { index, item ->
                val x = index * stepX
                val pct = (item.percentage / 100f).coerceIn(0f, 1f)
                val y = chartHeight - (pct * chartHeight * progress)
                Offset(x, y)
            }

            // Area path under line
            val areaPath = Path().apply {
                moveTo(points.first().x, chartHeight)
                points.forEach { lineTo(it.x, it.y) }
                lineTo(points.last().x, chartHeight)
                close()
            }

            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(EmeraldSuccess.copy(alpha = 0.35f), Color.Transparent),
                    startY = 0f,
                    endY = chartHeight
                )
            )

            // Line path
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val pPrev = points[i - 1]
                    val pCurr = points[i]
                    val midX = (pPrev.x + pCurr.x) / 2
                    cubicTo(midX, pPrev.y, midX, pCurr.y, pCurr.x, pCurr.y)
                }
            }

            drawPath(
                path = linePath,
                color = EmeraldSuccess,
                style = Stroke(width = 5f)
            )

            // Point circles
            points.forEach { pt ->
                drawCircle(color = Color.White, radius = 6f, center = pt)
                drawCircle(color = EmeraldSuccess, radius = 4f, center = pt)
            }
        }

        // Labels row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            data.forEach { item ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = item.monthLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "${item.percentage}%",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSuccess
                    )
                }
            }
        }
    }
}
