package com.sankalp.expensebuilder.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sankalp.expensebuilder.data.TransactionType
import com.sankalp.expensebuilder.viewmodel.ExpenseViewModel
import com.sankalp.expensebuilder.viewmodel.HistoryItem
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HistoryScreen(viewModel: ExpenseViewModel) {
    // Collect the merged list (Expenses + Accounts)
    val historyItems by viewModel.historyItems.collectAsState(initial = emptyList())
    val baseCurrency by viewModel.baseCurrency.collectAsState()
    val context = LocalContext.current

    // State for Date Selection
    var selectedMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var selectedYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }

    var monthExpanded by remember { mutableStateOf(false) }
    var yearExpanded by remember { mutableStateOf(false) }
    var showDownloadMenu by remember { mutableStateOf(false) }

    // NEW: Delete Confirmation State
    var showDeleteDialog by remember { mutableStateOf(false) }

    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val years = (2024..2030).toList()

    // Trigger initial load
    LaunchedEffect(Unit) {
        viewModel.updateHistoryDate(selectedMonth, selectedYear)
    }

    // --- DELETE CONFIRMATION DIALOG ---
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Clear History?") },
            text = { Text("This will permanently delete all ${historyItems.size} transactions for ${months[selectedMonth]} $selectedYear.\n\nAre you sure?") },
            confirmButton = {
                Button(
                    onClick = {
                        // Loop through and delete all items in current view
                        historyItems.forEach { item ->
                            when(item) {
                                is HistoryItem.Expense -> viewModel.deleteExpense(item.item)
                                is HistoryItem.Account -> viewModel.deleteAccountTx(item.tx)
                            }
                        }
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete All") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // --- Header with Buttons ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Transaction History",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            // Icons Row
            Row {
                // Clear History Button
                IconButton(onClick = {
                    if (historyItems.isNotEmpty()) showDeleteDialog = true
                }) {
                    Icon(Icons.Default.Delete, "Clear History", tint = if(historyItems.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Gray)
                }

                // Download Button
                Box {
                    IconButton(onClick = { showDownloadMenu = true }) {
                        Icon(Icons.Default.Download, "Download Report", tint = MaterialTheme.colorScheme.primary)
                    }
                    DropdownMenu(expanded = showDownloadMenu, onDismissRequest = { showDownloadMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Download PDF") },
                            onClick = {
                                viewModel.exportMonthlyData(context, "PDF")
                                showDownloadMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Download Excel") },
                            onClick = {
                                viewModel.exportMonthlyData(context, "Excel")
                                showDownloadMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // --- Date Selectors (Month & Year) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Month Dropdown
            Box {
                Button(onClick = { monthExpanded = true }) {
                    Text(months[selectedMonth])
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = monthExpanded, onDismissRequest = { monthExpanded = false }) {
                    months.forEachIndexed { index, name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                selectedMonth = index
                                viewModel.updateHistoryDate(selectedMonth, selectedYear)
                                monthExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.width(16.dp))

            // Year Dropdown
            Box {
                Button(onClick = { yearExpanded = true }) {
                    Text(selectedYear.toString())
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = yearExpanded, onDismissRequest = { yearExpanded = false }) {
                    years.forEach { year ->
                        DropdownMenuItem(
                            text = { Text(year.toString()) },
                            onClick = {
                                selectedYear = year
                                viewModel.updateHistoryDate(selectedMonth, selectedYear)
                                yearExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))

        // --- List of Transactions (Expenses + Accounts) ---
        if (historyItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("No transactions found for this month.", color = Color.Gray)
            }
        } else {
            LazyColumn {
                items(historyItems) { historyItem ->
                    // Determine Date
                    val date = Date(historyItem.date)
                    val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(date)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Date Column
                            Text(
                                text = dateStr,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(60.dp)
                            )

                            // Details Column (Handles both Expense and Account)
                            Column(modifier = Modifier.weight(1f)) {
                                when (historyItem) {
                                    is HistoryItem.Expense -> {
                                        Text(historyItem.item.itemName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            text = "${historyItem.item.category} • ${historyItem.item.bankName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.DarkGray
                                        )
                                    }
                                    is HistoryItem.Account -> {
                                        Text(historyItem.tx.beneficiaryName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                        Text(
                                            text = "Transfer to: ${historyItem.tx.toBankName}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.DarkGray
                                        )
                                    }
                                }
                            }

                            // Amount Column
                            val (amount, type) = when (historyItem) {
                                is HistoryItem.Expense -> Pair(historyItem.item.totalPrice, historyItem.item.type)
                                is HistoryItem.Account -> Pair(historyItem.tx.amount, historyItem.tx.type)
                            }

                            Text(
                                text = "${if(type == TransactionType.CREDIT) "+" else "-"} $baseCurrency ${String.format("%.2f", amount)}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if(type == TransactionType.CREDIT) Color(0xFF2E7D32) else Color.Black // Green for Credit
                            )
                        }
                    }
                }

                // Bottom Spacer
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}