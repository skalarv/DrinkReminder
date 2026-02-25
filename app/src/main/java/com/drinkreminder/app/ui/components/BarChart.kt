package com.drinkreminder.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.drinkreminder.app.ui.theme.GoalMetGreen
import com.drinkreminder.app.ui.theme.WaterBlue
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class BarChartEntry(
    val date: LocalDate,
    val valueMl: Int
)

@Composable
fun BarChart(
    entries: List<BarChartEntry>,
    goalMl: Int,
    modifier: Modifier = Modifier,
    barHeight: Float = 160f
) {
    if (entries.isEmpty()) return

    val maxValue = maxOf(entries.maxOf { it.valueMl }, goalMl).toFloat()
    val goalColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        entries.forEach { entry ->
            val barFraction = if (maxValue > 0) entry.valueMl / maxValue else 0f
            val goalFraction = if (maxValue > 0) goalMl / maxValue else 0f
            val barColor = if (entry.valueMl >= goalMl) GoalMetGreen else WaterBlue

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(36.dp)
            ) {
                Text(
                    text = "${entry.valueMl / 1000f}".take(3),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Canvas(
                    modifier = Modifier
                        .width(24.dp)
                        .height(barHeight.dp)
                ) {
                    val canvasHeight = size.height
                    val canvasWidth = size.width

                    // Bar
                    val barH = canvasHeight * barFraction
                    drawRoundRect(
                        color = barColor,
                        topLeft = Offset(0f, canvasHeight - barH),
                        size = Size(canvasWidth, barH),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )

                    // Goal line
                    val goalY = canvasHeight * (1f - goalFraction)
                    drawLine(
                        color = goalColor,
                        start = Offset(-4.dp.toPx(), goalY),
                        end = Offset(canvasWidth + 4.dp.toPx(), goalY),
                        strokeWidth = 2.dp.toPx()
                    )
                }

                Text(
                    text = entry.date.format(DateTimeFormatter.ofPattern("EEE")),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
