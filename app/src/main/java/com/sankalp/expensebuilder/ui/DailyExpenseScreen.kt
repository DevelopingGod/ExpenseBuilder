package com.sankalp.expensebuilder.ui

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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sankalp.expensebuilder.data.ExpenseItem
import com.sankalp.expensebuilder.data.TransactionType
import com.sankalp.expensebuilder.data.UnitType
import com.sankalp.expensebuilder.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DailyExpenseScreen(viewModel: ExpenseViewModel) {
    val context = LocalContext.current
    val selectedDate by viewModel.selectedDate.collectAsState()
    val expenseList by viewModel.currentExpenses.collectAsState(initial = emptyList())

    val baseCurrency by viewModel.baseCurrency.collectAsState()
    val targetCurrency by viewModel.targetCurrency.collectAsState()
    val exchangeRate by viewModel.exchangeRate.collectAsState()

    // --- State for 3 Opening Balances ---
    val opCash = remember { mutableStateOf("") }
    val opCheque = remember { mutableStateOf("") }
    val opCard = remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Text("Daily Expense", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)

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

                // --- 1. Input Form (Now handles 3 Opening Balances) ---
                ExpenseInputForm(
                    viewModel = viewModel,
                    selectedDate = selectedDate,
                    baseCurrency = baseCurrency,
                    opCash = opCash,
                    opCheque = opCheque,
                    opCard = opCard
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // --- 2. Detailed Summary Card (Shows Split Totals) ---
                DetailedSummaryCard(expenseList, opCash.value, opCheque.value, opCard.value, baseCurrency)
            }

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

@Composable
fun ExpenseInputForm(
    viewModel: ExpenseViewModel,
    selectedDate: Long,
    baseCurrency: String,
    opCash: MutableState<String>,
    opCheque: MutableState<String>,
    opCard: MutableState<String>
) {
    val categories by viewModel.savedCategories.collectAsState(initial = emptyList())
    val suggestions by viewModel.itemSuggestions.collectAsState()

    var personName by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var itemName by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf(UnitType.PIECE) }
    var selectedType by remember { mutableStateOf(TransactionType.DEBIT) }

    // --- Payment Mode State ---
    var selectedPaymentMode by remember { mutableStateOf("Cash") }
    val paymentOptions = listOf("Cash", "Cheque", "Card/UPI")

    var catExpanded by remember { mutableStateOf(false) }
    var unitExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column {
        OutlinedTextField(value = personName, onValueChange = { personName = it }, label = { Text("Person Name") }, modifier = Modifier.fillMaxWidth())

        // --- 3 Opening Balance Fields (Row) ---
        Text("Opening Balances ($baseCurrency)", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top=8.dp))
        Row(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = opCash.value, onValueChange = { opCash.value = it },
                label = { Text("Cash") }, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.width(4.dp))
            OutlinedTextField(
                value = opCheque.value, onValueChange = { opCheque.value = it },
                label = { Text("Cheque") }, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.width(4.dp))
            OutlinedTextField(
                value = opCard.value, onValueChange = { opCard.value = it },
                label = { Text("Card") }, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }

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

        // --- Payment Mode Radio Buttons ---
        Text("Payment Mode:", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            paymentOptions.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { selectedPaymentMode = option }) {
                    RadioButton(
                        selected = (option == selectedPaymentMode),
                        onClick = { selectedPaymentMode = option }
                    )
                    Text(text = option, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
        }

        Button(
            onClick = {
                if(personName.isBlank() || category.isBlank() || itemName.isBlank() || quantity.isBlank() || price.isBlank()) {
                    Toast.makeText(context, "Required fields are empty (Name, Item, Price etc)", Toast.LENGTH_SHORT).show()
                } else {
                    // Pass ALL 3 Opening Balances
                    viewModel.addExpense(
                        selectedDate, personName,
                        opCash.value, opCheque.value, opCard.value,
                        category, itemName, quantity, selectedUnit, price, selectedType, selectedPaymentMode
                    )
                    itemName = ""; quantity = ""; price = ""
                    // selectedPaymentMode = "Cash" // Optional reset
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) { Text("ADD") }
    }
}

@Composable
fun DetailedSummaryCard(items: List<ExpenseItem>, opCashStr: String, opChequeStr: String, opCardStr: String, currency: String) {
    val opCash = opCashStr.toDoubleOrNull() ?: 0.0
    val opCheque = opChequeStr.toDoubleOrNull() ?: 0.0
    val opCard = opCardStr.toDoubleOrNull() ?: 0.0

    // Logic to calculate closing balance for a specific mode
    fun calcClosing(mode: String, open: Double): Double {
        val credit = items.filter { it.paymentMode == mode && it.type == TransactionType.CREDIT }.sumOf { it.totalPrice }
        val debit = items.filter { it.paymentMode == mode && it.type == TransactionType.DEBIT }.sumOf { it.totalPrice }
        return open + credit - debit
    }

    // Exact string matches for modes are important!
    val closeCash = calcClosing("Cash", opCash)
    val closeCheque = calcClosing("Cheque", opCheque)
    val closeCard = calcClosing("Card/UPI", opCard)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Closing Balances ($currency)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Cash:"); Text("$currency ${String.format("%.2f", closeCash)}", fontWeight = FontWeight.Bold) }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Cheque:"); Text("$currency ${String.format("%.2f", closeCheque)}", fontWeight = FontWeight.Bold) }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Card/UPI:"); Text("$currency ${String.format("%.2f", closeCard)}", fontWeight = FontWeight.Bold) }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            val total = closeCash + closeCheque + closeCard
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("GRAND TOTAL:", fontWeight = FontWeight.Bold);
                Text("$currency ${String.format("%.2f", total)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// Reusing existing components if they aren't in this file
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

        Box {
            Button(onClick = { baseExp = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(baseCurrency); Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = baseExp, onDismissRequest = { baseExp = false }) {
                currencies.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { onBaseChange(c); baseExp = false }) }
            }
        }
        Text("→", modifier = Modifier.padding(horizontal = 4.dp))
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
fun ExpenseRow(item: ExpenseItem, currency: String, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.itemName, style = MaterialTheme.typography.titleMedium, color = if (item.type == TransactionType.CREDIT) Color.Green else Color.Black, fontWeight = FontWeight.Bold)
                Text("${item.category} | ${item.quantity} ${item.unit} | ${item.paymentMode}", style = MaterialTheme.typography.bodySmall)
            }
            Text("$currency ${String.format("%.2f", item.totalPrice)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red) }
        }
    }
}