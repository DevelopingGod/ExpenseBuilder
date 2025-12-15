package com.example.expensebuilder.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.expensebuilder.viewmodel.ExpenseViewModel

@Composable
fun MainScreen(viewModel: ExpenseViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Daily Expense", "Accounts")

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = if (index == 0) Icons.Default.CalendarToday else Icons.Default.Person,
                                contentDescription = null
                            )
                        },
                        label = { Text(title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (selectedTab == 0) {
                DailyExpenseScreen(viewModel)
            } else {
                AccountScreen(viewModel)
            }
        }
    }
}