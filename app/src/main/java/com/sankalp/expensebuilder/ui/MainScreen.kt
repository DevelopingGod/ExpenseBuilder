package com.sankalp.expensebuilder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sankalp.expensebuilder.viewmodel.ExpenseViewModel

@Composable
fun MainScreen(viewModel: ExpenseViewModel) {
    // Default Landing Page: Guide (Index 0)
    var selectedTab by remember { mutableIntStateOf(0) }

    // Updated Tab List
    val tabs = listOf("Guide", "Daily", "Accounts", "History", "Files")

    val serverIp by viewModel.serverIp.collectAsState()
    val showFirstRun by viewModel.showFirstRunDialog.collectAsState()

    // --- FIRST RUN CURRENCY SELECTION DIALOG ---
    if (showFirstRun) {
        var selectedCurr by remember { mutableStateOf("USD") }
        var exp by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = {}, // Prevent dismissal without selection
            title = { Text("Welcome! Select Currency") },
            text = {
                Column {
                    Text("Please select your preferred base currency. This will be the default for your reports.")
                    Spacer(Modifier.height(10.dp))
                    Box {
                        OutlinedButton(onClick = { exp = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedCurr)
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = exp, onDismissRequest = { exp = false }) {
                            viewModel.availableCurrencies.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c) },
                                    onClick = { selectedCurr = c; exp = false }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { viewModel.setPreferredCurrency(selectedCurr) }) {
                    Text("Save & Continue")
                }
            }
        )
    }

    // --- WIFI SERVER DIALOG ---
    if (serverIp != null) {
        AlertDialog(
            onDismissRequest = { },
            confirmButton = { TextButton(onClick = { viewModel.toggleServer() }) { Text("Stop Server") } },
            title = { Text("WiFi Server Running") },
            text = {
                Column {
                    Text("Type this URL in your Laptop Browser:")
                    Spacer(Modifier.height(8.dp))
                    Text(serverIp!!, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Keep app open. Laptop and Phone must be on same WiFi.")
                }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.toggleServer() },
                containerColor = if (serverIp != null) Color.Green else MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(Icons.Default.Wifi, null)
                Spacer(Modifier.width(8.dp))
                Text(if (serverIp != null) "WiFi ON" else "Connect PC")
            }
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            val icon = when(index) {
                                0 -> Icons.Default.Info          // Guide
                                1 -> Icons.Default.CalendarToday // Daily
                                2 -> Icons.Default.Person        // Accounts
                                3 -> Icons.Default.History       // History
                                else -> Icons.Default.Folder     // Files/Downloads
                            }
                            Icon(icon, null)
                        },
                        label = { Text(title, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when(selectedTab) {
                0 -> AppGuideScreen()
                1 -> DailyExpenseScreen(viewModel)
                2 -> AccountScreen(viewModel)
                3 -> HistoryScreen(viewModel)   // NEW
                4 -> DownloadsScreen(viewModel) // NEW
            }
        }
    }
}