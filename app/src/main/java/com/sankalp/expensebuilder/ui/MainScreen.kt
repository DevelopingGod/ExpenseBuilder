package com.sankalp.expensebuilder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Info // NEW Icon
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
    var selectedTab by remember { mutableIntStateOf(0) }
    // NEW: Added 3rd Tab
    val tabs = listOf("Daily Expense", "Accounts", "Guide")
    val serverIp by viewModel.serverIp.collectAsState()

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
                            // NEW: Icon logic
                            val icon = when(index) {
                                0 -> Icons.Default.CalendarToday
                                1 -> Icons.Default.Person
                                else -> Icons.Default.Info
                            }
                            Icon(icon, null)
                        },
                        label = { Text(title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when(selectedTab) {
                0 -> DailyExpenseScreen(viewModel)
                1 -> AccountScreen(viewModel)
                2 -> AppGuideScreen() // NEW Screen
            }
        }
    }
}