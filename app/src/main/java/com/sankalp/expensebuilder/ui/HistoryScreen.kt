package com.sankalp.expensebuilder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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

    var showMonthYearPicker by remember { mutableStateOf(false) }
    var showDownloadMenu by remember { mutableStateOf(false) }

    // NEW: Delete Confirmation State
    var showDeleteDialog by remember { mutableStateOf(false) }

    val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

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

    // --- MONTH YEAR PICKER DIALOG ---
    if (showMonthYearPicker) {
        MonthYearPickerDialog(
            initialMonth = selectedMonth,
            initialYear = selectedYear,
            onDismiss = { showMonthYearPicker = false },
            onDateSelected = { month, year ->
                selectedMonth = month
                selectedYear = year
                viewModel.updateHistoryDate(selectedMonth, selectedYear)
                showMonthYearPicker = false
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

        // --- NEW CALENDAR-LIKE DATE SELECTOR ---
        Button(
            onClick = { showMonthYearPicker = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.DateRange, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${months[selectedMonth]} $selectedYear",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
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

// --- NEW COMPOSABLE: Calendar-like Month/Year Picker ---
@Composable
fun MonthYearPickerDialog(
    initialMonth: Int,
    initialYear: Int,
    onDismiss: () -> Unit,
    onDateSelected: (Int, Int) -> Unit
) {
    var tempMonth by remember { mutableIntStateOf(initialMonth) }
    var tempYear by remember { mutableIntStateOf(initialYear) }
    val monthsShort = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Select Month & Year", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(20.dp))

                // --- Year Selector (Arrows) ---
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = { tempYear-- }) {
                        Icon(Icons.Default.KeyboardArrowLeft, "Prev Year")
                    }
                    Text(
                        text = tempYear.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    IconButton(onClick = { tempYear++ }) {
                        Icon(Icons.Default.KeyboardArrowRight, "Next Year")
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // --- Month Grid (3 columns x 4 rows) ---
                Column(modifier = Modifier.fillMaxWidth()) {
                    val rows = monthsShort.chunked(3)
                    for (row in rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (monthName in row) {
                                val index = monthsShort.indexOf(monthName)
                                val isSelected = index == tempMonth

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .padding(4.dp)
                                        .size(width = 80.dp, height = 40.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { tempMonth = index }
                                ) {
                                    Text(
                                        text = monthName,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // --- Action Buttons ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onDateSelected(tempMonth, tempYear) }) { Text("OK") }
                }
            }
        }
    }
}