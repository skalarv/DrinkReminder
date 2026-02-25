package com.drinkreminder.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.drinkreminder.app.ui.theme.DarkModeOption
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Daily Goal
        SettingsSection(title = "Daily Goal") {
            Text(
                text = "${uiState.dailyGoalBottles} bottles (${uiState.dailyGoalBottles * 800}ml)",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = uiState.dailyGoalBottles.toFloat(),
                onValueChange = { viewModel.setDailyGoalBottles(it.roundToInt()) },
                valueRange = 3f..10f,
                steps = 6,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("3", style = MaterialTheme.typography.labelSmall)
                Text("10", style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Active Hours
        SettingsSection(title = "Active Hours") {
            Text(
                text = "Reminders between ${formatHour(uiState.activeHoursStart)} - ${formatHour(uiState.activeHoursEnd)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Start: ${formatHour(uiState.activeHoursStart)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = uiState.activeHoursStart.toFloat(),
                onValueChange = {
                    val hour = it.roundToInt()
                    if (hour < uiState.activeHoursEnd) {
                        viewModel.setActiveHoursStart(hour)
                    }
                },
                valueRange = 5f..12f,
                steps = 6,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "End: ${formatHour(uiState.activeHoursEnd)}",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = uiState.activeHoursEnd.toFloat(),
                onValueChange = {
                    val hour = it.roundToInt()
                    if (hour > uiState.activeHoursStart) {
                        viewModel.setActiveHoursEnd(hour)
                    }
                },
                valueRange = 16f..23f,
                steps = 6,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottle Deadlines
        BottleDeadlinesSection(
            deadlines = uiState.bottleDeadlines,
            activeHoursStart = uiState.activeHoursStart,
            activeHoursEnd = uiState.activeHoursEnd,
            onDeadlineChange = { index, time -> viewModel.setBottleDeadline(index, time) },
            onReset = { viewModel.resetDeadlinesToDefault() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Dark Mode
        SettingsSection(title = "Appearance") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                DarkModeOption.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = uiState.darkModeOption == option,
                        onClick = { viewModel.setDarkMode(option) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = DarkModeOption.entries.size
                        )
                    ) {
                        Text(
                            when (option) {
                                DarkModeOption.SYSTEM -> "System"
                                DarkModeOption.LIGHT -> "Light"
                                DarkModeOption.DARK -> "Dark"
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Notifications
        SettingsSection(title = "Notifications") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Drink reminders",
                    style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = uiState.notificationsEnabled,
                    onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BottleDeadlinesSection(
    deadlines: List<LocalTime>,
    activeHoursStart: Int,
    activeHoursEnd: Int,
    onDeadlineChange: (Int, LocalTime) -> Unit,
    onReset: () -> Unit
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var editingIndex by remember { mutableIntStateOf(-1) }
    var showTimePicker by remember { mutableStateOf(false) }

    SettingsSection(title = "Bottle Deadlines") {
        Text(
            text = "Set a deadline for each bottle",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        deadlines.forEachIndexed { index, deadline ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bottle ${index + 1}",
                    style = MaterialTheme.typography.bodyLarge
                )
                OutlinedButton(
                    onClick = {
                        editingIndex = index
                        showTimePicker = true
                    }
                ) {
                    Text(deadline.format(timeFormatter))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset to defaults")
        }
    }

    if (showTimePicker && editingIndex in deadlines.indices) {
        val currentDeadline = deadlines[editingIndex]
        val timePickerState = rememberTimePickerState(
            initialHour = currentDeadline.hour,
            initialMinute = currentDeadline.minute,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Bottle ${editingIndex + 1} deadline") },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = LocalTime.of(timePickerState.hour, timePickerState.minute)
                        // Clamp to active hours range
                        val clamped = selected
                            .coerceAtLeast(LocalTime.of(activeHoursStart, 0))
                            .coerceAtMost(LocalTime.of(activeHoursEnd, 0))
                        onDeadlineChange(editingIndex, clamped)
                        showTimePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

private fun formatHour(hour: Int): String {
    return "%02d:00".format(hour)
}

private fun LocalTime.coerceAtLeast(min: LocalTime): LocalTime =
    if (this.isBefore(min)) min else this

private fun LocalTime.coerceAtMost(max: LocalTime): LocalTime =
    if (this.isAfter(max)) max else this
