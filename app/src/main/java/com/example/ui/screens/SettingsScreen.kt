package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.MeterViewModel

@Composable
fun SettingsScreen(viewModel: MeterViewModel, onBack: () -> Unit) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        for ((value, label) in listOf("SYSTEM" to "Match device", "DARK" to "Dark", "LIGHT" to "Light")) {
            Row {
                RadioButton(selected = prefs?.themeMode == value, onClick = { viewModel.updateTheme(value) })
                TextButton(onClick = { viewModel.updateTheme(value) }) { Text(label) }
            }
        }
        HorizontalDivider()
        Text("Session duration", style = MaterialTheme.typography.titleMedium)
        Text("At the limit, capture stops and the session remains available to save. Paused time is excluded.")
        for (minutes in listOf(5, 15, 30, 60, 0)) {
            Row {
                RadioButton(selected = prefs?.sessionDurationLimitMinutes == minutes, onClick = { viewModel.updateDurationLimit(minutes) })
                TextButton(onClick = { viewModel.updateDurationLimit(minutes) }) { Text(if (minutes == 0) "Until I stop" else "$minutes minutes") }
            }
        }
        Text("Changing A/Z or Fast/Slow starts a new measurement session. Save the current session before changing these settings.", style = MaterialTheme.typography.bodySmall)
    }
}
