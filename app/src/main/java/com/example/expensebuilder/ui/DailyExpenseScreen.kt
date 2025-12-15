package com.example.expensebuilder.ui

import android.app.DatePickerDialog
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.expensebuilder.data.ExpenseItem
import com.example.expensebuilder.data.TransactionType
import com.example.expensebuilder.data.UnitType
import com.example.expensebuilder.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

// --- MISSING CLASS ADDED HERE ---
data class BalanceSummary(
    val opening: Double,
    val credit: Double,
    val debit: Double,
    val closing: Double
)
// --------------------------------

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
                            Icon(if(index == 0) Icons.Default.CalendarToday else Icons.Default.Person, contentDescription = null)
                        },
                        label = { Text(title) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            if (selectedTab == 0) DailyExpenseScreen(viewModel) else AccountScreen(viewModel)
        }
    }
}

@Composable
fun DailyExpenseScreen(viewModel: ExpenseViewModel) {
    val context = LocalContext.current
    val selectedDate by viewModel.selectedDate.collectAsState()

    // Performance Fix: We only collect the List here.
    val expenseList by viewModel.currentExpenses.collectAsState(initial = emptyList())

    val baseCurrency by viewModel.baseCurrency.collectAsState()
    val targetCurrency by viewModel.targetCurrency.collectAsState()
    val exchangeRate by viewModel.exchangeRate.collectAsState()

    // Summary Calculation (Memoized)
    val openingBalanceInput = remember { mutableStateOf("") } // Lifted state for summary calc

    // derivedStateOf requires BalanceSummary to be defined to infer type 'T'
    val summary by remember(expenseList, openingBalanceInput.value) {
        derivedStateOf {
            val currentOpening = openingBalanceInput.value.toDoubleOrNull() ?: 0.0
            var tCredit = 0.0
            var tDebit = 0.0
            expenseList.forEach {
                if(it.type == TransactionType.CREDIT) tCredit += it.totalPrice
                else tDebit += it.totalPrice
            }
            val closing = (currentOpening + tCredit) - tDebit
            BalanceSummary(currentOpening, tCredit, tDebit, closing)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Text("Daily Expense", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)

        // --- 1. Top Section (Date & Currency) ---
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                DateCurrencyHeader(
                    selectedDate = selectedDate,
                    baseCurrency = baseCurrency,
                    targetCurrency = targetCurrency,
                    exchangeRate = exchangeRate,
                    onDateClick = {
                        val calendar = Calendar.getInstance()
                        calendar.timeInMillis = selectedDate
                        DatePickerDialog(context, { _, y, m, d ->
                            val newCal = Calendar.getInstance()
                            newCal.set(y, m, d)
                            viewModel.updateDate(newCal.timeInMillis)
                        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                    },
                    onBaseChange = viewModel::updateBaseCurrency,
                    onTargetChange = viewModel::updateTargetCurrency
                )

                // --- 2. The Input Form (ISOLATED) ---
                ExpenseInputForm(
                    viewModel = viewModel,
                    selectedDate = selectedDate,
                    baseCurrency = baseCurrency,
                    openingBalanceState = openingBalanceInput
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // --- 3. Summary Card ---
                SummaryCard(summary, baseCurrency, targetCurrency, exchangeRate)
            }

            // --- 4. The List (Smart Keys) ---
            items(items = expenseList, key = { it.id }) { item ->
                ExpenseRow(item, baseCurrency) { viewModel.deleteExpense(item) }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(onClick = { viewModel.exportDailyData(context, "Excel") }) { Text("Excel") }
                    Button(onClick = { viewModel.exportDailyData(context, "PDF") }) { Text("PDF") }
                }
                Spacer(Modifier.height(50.dp))
            }
        }
    }
}

// --- ISOLATED COMPONENTS (Prevents Lag) ---

@Composable
fun DateCurrencyHeader(
    selectedDate: Long,
    baseCurrency: String,
    targetCurrency: String,
    exchangeRate: Double,
    onDateClick: () -> Unit,
    onBaseChange: (String) -> Unit,
    onTargetChange: (String) -> Unit
) {
    val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val currencies = listOf("USD", "INR", "GBP", "EUR", "JPY", "CAD")
    var baseExp by remember { mutableStateOf(false) }
    var targetExp by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Date
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)
                .clickable { onDateClick() }.padding(12.dp)
        ) {
            Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(dateFormatter.format(Date(selectedDate)), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(8.dp))

        // Base Dropdown
        Box {
            Button(onClick = { baseExp = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(baseCurrency); Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = baseExp, onDismissRequest = { baseExp = false }) {
                currencies.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { onBaseChange(c); baseExp = false }) }
            }
        }
        Text("→", modifier = Modifier.padding(horizontal = 4.dp))
        // Target Dropdown
        Box {
            Button(onClick = { targetExp = true }, contentPadding = PaddingValues(horizontal = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Text(targetCurrency); Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = targetExp, onDismissRequest = { targetExp = false }) {
                currencies.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { onTargetChange(c); targetExp = false }) }
            }
        }
    }
    Text(
        text = "Rate: 1 $baseCurrency ≈ ${String.format("%.2f", exchangeRate)} $targetCurrency",
        style = MaterialTheme.typography.labelSmall,
        color = Color.Gray,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 10.dp)
    )
}

@Composable
fun ExpenseInputForm(
    viewModel: ExpenseViewModel,
    selectedDate: Long,
    baseCurrency: String,
    openingBalanceState: MutableState<String>
) {
    val categories by viewModel.savedCategories.collectAsState(initial = emptyList())
    val suggestions by viewModel.itemSuggestions.collectAsState()

    // Local State for Form inputs
    var personName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var itemName by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf(UnitType.PIECE) }
    var selectedType by remember { mutableStateOf(TransactionType.DEBIT) }

    var catExpanded by remember { mutableStateOf(false) }
    var unitExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column {
        OutlinedTextField(value = personName, onValueChange = { personName = it }, label = { Text("Person Name") }, modifier = Modifier.fillMaxWidth())

        OutlinedTextField(
            value = openingBalanceState.value,
            onValueChange = { openingBalanceState.value = it },
            label = { Text("Opening Balance ($baseCurrency)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(),
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Select", Modifier.clickable { catExpanded = true }) }
            )
            DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                categories.forEach { cat -> DropdownMenuItem(text = { Text(cat) }, onClick = { category = cat; catExpanded = false }) }
            }
        }

        OutlinedTextField(value = itemName, onValueChange = { itemName = it; viewModel.fetchSuggestions(category, it) }, label = { Text("Item Name") }, modifier = Modifier.fillMaxWidth())
        if (suggestions.isNotEmpty()) {
            Row(modifier = Modifier.padding(4.dp)) {
                suggestions.take(3).forEach { s ->
                    Text(s, modifier = Modifier.clickable { itemName = s; viewModel.fetchSuggestions(category, "") }
                        .padding(4.dp).background(Color.LightGray.copy(alpha=0.3f)).padding(4.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
        }

        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("Qty") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.weight(0.8f)) {
                OutlinedTextField(value = selectedUnit.name, onValueChange = {}, readOnly = true, label = { Text("Unit") }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { unitExpanded = true }) })
                DropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                    UnitType.values().forEach { u -> DropdownMenuItem(text = { Text(u.name) }, onClick = { selectedUnit = u; unitExpanded = false }) }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price ($baseCurrency)") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Spacer(Modifier.width(8.dp))
            Switch(checked = selectedType == TransactionType.CREDIT, onCheckedChange = { selectedType = if (it) TransactionType.CREDIT else TransactionType.DEBIT })
            Text(if(selectedType == TransactionType.CREDIT) "Credit" else "Debit", color = if(selectedType == TransactionType.CREDIT) Color.Green else Color.Black)
        }

        Button(
            onClick = {
                if(personName.isBlank() || openingBalanceState.value.isBlank() || category.isBlank() || itemName.isBlank() || quantity.isBlank() || price.isBlank()) {
                    Toast.makeText(context, "All fields are mandatory", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addExpense(selectedDate, personName, openingBalanceState.value, category, itemName, quantity, selectedUnit, price, selectedType)
                    // Clear inputs (except person/opening balance)
                    itemName = ""; quantity = ""; price = ""
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) { Text("ADD") }
    }
}

@Composable
fun SummaryCard(summary: BalanceSummary, base: String, target: String, rate: Double) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Balance Summary (Base: $base)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            RowSummary("Opening:", summary.opening, null, base)
            RowSummary("Total Credit:", summary.credit, Color.Green, base)
            RowSummary("Total Debit:", summary.debit, Color.Red, base)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("CLOSING ($base):", fontWeight = FontWeight.Bold)
                Text("$base ${String.format("%.2f", summary.closing)}", fontWeight = FontWeight.Bold)
            }
            if(base != target) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("CLOSING ($target):", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text("$target ${String.format("%.2f", summary.closing * rate)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun RowSummary(label: String, value: Double, color: Color?, currency: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = color ?: Color.Black)
        Text(text = if(color == Color.Red) "- $currency ${String.format("%.2f", value)}" else "+ $currency ${String.format("%.2f", value)}", color = color ?: Color.Black)
    }
}

@Composable
fun ExpenseRow(item: ExpenseItem, currency: String, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.itemName, style = MaterialTheme.typography.titleMedium, color = if (item.type == TransactionType.CREDIT) Color.Green else Color.Black, fontWeight = FontWeight.Bold)
                Text("${item.category} | ${item.quantity} ${item.unit} | $currency ${item.pricePerUnit}", style = MaterialTheme.typography.bodySmall)
            }
            Text("$currency ${String.format("%.2f", item.totalPrice)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red) }
        }
    }
}