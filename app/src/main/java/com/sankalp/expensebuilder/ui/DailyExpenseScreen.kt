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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sankalp.expensebuilder.data.*
import com.sankalp.expensebuilder.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DailyExpenseScreen(viewModel: ExpenseViewModel) {
    val context = LocalContext.current
    val selectedDate by viewModel.selectedDate.collectAsState()
    val expenseList by viewModel.currentExpenses.collectAsState(initial = emptyList())
    val bankBalances by viewModel.currentBankBalances.collectAsState(initial = emptyList())

    val baseCurrency by viewModel.baseCurrency.collectAsState()
    val targetCurrency by viewModel.targetCurrency.collectAsState()
    val exchangeRate by viewModel.exchangeRate.collectAsState()

    // Currency Conversion Toggle State
    val isConversionEnabled by viewModel.isConversionEnabled.collectAsState()

    var showAddBankDialog by remember { mutableStateOf(false) }

    if (showAddBankDialog) {
        AddBankDialog(
            baseCurrency = baseCurrency,
            onDismiss = { showAddBankDialog = false },
            onAdd = { name, cash, chq, card ->
                viewModel.addOrUpdateBankBalance(selectedDate, name, cash, chq, card)
                showAddBankDialog = false
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Text("Daily Expense", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)

        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Spacer(modifier = Modifier.height(10.dp))

                // UPDATED HEADER UI
                DateCurrencyHeader(
                    selectedDate = selectedDate,
                    baseCurrency = baseCurrency,
                    targetCurrency = targetCurrency,
                    exchangeRate = exchangeRate,
                    isConversionEnabled = isConversionEnabled,
                    onToggleConversion = viewModel::toggleConversion,
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Bank Sources / Opening Balances", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    Button(onClick = { showAddBankDialog = true }, contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.height(35.dp)) {
                        Text("+ Add Bank")
                    }
                }

                bankBalances.forEach { bank ->
                    BankBalanceRow(bank, baseCurrency) { viewModel.deleteBankBalance(bank) }
                }

                if (bankBalances.isEmpty()) {
                    Text("No banks added. Add a bank (e.g., SBI, Cash) to start.", color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ExpenseInputForm(
                    viewModel = viewModel,
                    selectedDate = selectedDate,
                    baseCurrency = baseCurrency,
                    bankBalances = bankBalances
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (bankBalances.isNotEmpty()) {
                    MultiBankSummaryCard(expenseList, bankBalances, baseCurrency)
                }
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

// --- UPDATED: Header with Toggle BELOW Date ---
@Composable
fun DateCurrencyHeader(
    selectedDate: Long,
    baseCurrency: String,
    targetCurrency: String,
    exchangeRate: Double,
    isConversionEnabled: Boolean,
    onToggleConversion: (Boolean) -> Unit,
    onDateClick: () -> Unit,
    onBaseChange: (String) -> Unit,
    onTargetChange: (String) -> Unit
) {
    val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val currencies = listOf("USD", "INR", "GBP", "EUR", "JPY", "CAD", "SGD")
    var baseExp by remember { mutableStateOf(false) }
    var targetExp by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ROW 1: Date and Currency Selectors
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

            if (isConversionEnabled) {
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
        }

        // ROW 2: Toggle Switch & Rate Text
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Currency Converter (On/Off):",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 8.dp)
            )

            Switch(
                checked = isConversionEnabled,
                onCheckedChange = onToggleConversion,
                modifier = Modifier.scale(0.8f)
            )
            Spacer(Modifier.width(8.dp))
            if (isConversionEnabled) {
                Text(
                    text = "1 $baseCurrency ≈ ${String.format("%.2f", exchangeRate)} $targetCurrency",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            } else {
                Text(
                    text = "Currency Conversion OFF",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// --- Dialog with Vertical Layout ---
@Composable
fun AddBankDialog(baseCurrency: String, onDismiss: () -> Unit, onAdd: (String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var cash by remember { mutableStateOf("") }
    var cheque by remember { mutableStateOf("") }
    var card by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Bank / Source") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Bank Name (e.g. HDFC)") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Opening Balances ($baseCurrency):", style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(value = cash, onValueChange = { cash = it }, label = { Text("Cash") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(value = cheque, onValueChange = { cheque = it }, label = { Text("Cheque") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(value = card, onValueChange = { card = it }, label = { Text("Card") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) onAdd(name, cash.ifBlank { "0" }, cheque.ifBlank { "0" }, card.ifBlank { "0" })
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun BankBalanceRow(bank: DailyBankBalance, currency: String, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(Modifier.padding(8.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(bank.bankName, fontWeight = FontWeight.Bold)
                Text("Op: $currency ${bank.openingCash} (C) | ${bank.openingCheque} (Q) | ${bank.openingCard} (D)", style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete", tint = Color.Red, modifier = Modifier.size(20.dp)) }
        }
    }
}

// --- Form with Credit/Debit Dropdown ---
@Composable
fun ExpenseInputForm(
    viewModel: ExpenseViewModel,
    selectedDate: Long,
    baseCurrency: String,
    bankBalances: List<DailyBankBalance>
) {
    val categories by viewModel.savedCategories.collectAsState(initial = emptyList())
    val suggestions by viewModel.itemSuggestions.collectAsState()

    var personName by remember { mutableStateOf("") }
    var selectedBank by remember { mutableStateOf(if (bankBalances.isNotEmpty()) bankBalances[0].bankName else "") }

    LaunchedEffect(bankBalances) {
        if (selectedBank.isBlank() && bankBalances.isNotEmpty()) selectedBank = bankBalances[0].bankName
    }

    var category by remember { mutableStateOf("") }
    var itemName by remember { mutableStateOf("") }
    var additionalInfo by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var selectedUnit by remember { mutableStateOf(UnitType.PIECE) }
    var selectedType by remember { mutableStateOf(TransactionType.DEBIT) }

    var selectedPaymentMode by remember { mutableStateOf("Cash") }
    val paymentOptions = listOf("Cash", "Cheque", "Card/UPI")

    var catExpanded by remember { mutableStateOf(false) }
    var bankExpanded by remember { mutableStateOf(false) }
    var unitExpanded by remember { mutableStateOf(false) }
    var typeExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Column {
        OutlinedTextField(value = personName, onValueChange = { personName = it }, label = { Text("Person Name") }, modifier = Modifier.fillMaxWidth())

        Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedTextField(
                value = selectedBank, onValueChange = {}, readOnly = true, label = { Text("Select Bank / Source") }, modifier = Modifier.fillMaxWidth(),
                trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { bankExpanded = true }) }
            )
            DropdownMenu(expanded = bankExpanded, onDismissRequest = { bankExpanded = false }) {
                if (bankBalances.isEmpty()) {
                    DropdownMenuItem(text = { Text("No Banks Added") }, onClick = { bankExpanded = false })
                }
                bankBalances.forEach { b ->
                    DropdownMenuItem(text = { Text(b.bankName) }, onClick = { selectedBank = b.bankName; bankExpanded = false })
                }
            }
        }

        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = category, onValueChange = { category = it }, label = { Text("Category") }, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Select", Modifier.clickable { catExpanded = true }) }
                )
                DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                    categories.forEach { cat -> DropdownMenuItem(text = { Text(cat) }, onClick = { category = cat; catExpanded = false }) }
                }
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

        OutlinedTextField(value = additionalInfo, onValueChange = { additionalInfo = it }, label = { Text("Additional Info (Optional)") }, modifier = Modifier.fillMaxWidth())

        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                OutlinedTextField(value = quantity, onValueChange = { quantity = it }, label = { Text("Qty") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = selectedUnit.name, onValueChange = {}, readOnly = true, label = { Text("Unit") }, modifier = Modifier.fillMaxWidth(), trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { unitExpanded = true }) })
                    DropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                        UnitType.values().forEach { u -> DropdownMenuItem(text = { Text(u.name) }, onClick = { selectedUnit = u; unitExpanded = false }) }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(Modifier.weight(1f)) {
                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Price ($baseCurrency)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Spacer(Modifier.height(4.dp))

                Box(Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedType.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, null, Modifier.clickable { typeExpanded = true }) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (selectedType == TransactionType.CREDIT) Color.Green else Color.Red,
                            unfocusedTextColor = if (selectedType == TransactionType.CREDIT) Color.Green else Color.Red
                        )
                    )
                    DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                        DropdownMenuItem(text = { Text("DEBIT", color = Color.Red) }, onClick = { selectedType = TransactionType.DEBIT; typeExpanded = false })
                        DropdownMenuItem(text = { Text("CREDIT", color = Color.Green) }, onClick = { selectedType = TransactionType.CREDIT; typeExpanded = false })
                    }
                }
            }
        }

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
                if(personName.isBlank() || selectedBank.isBlank() || category.isBlank() || itemName.isBlank() || quantity.isBlank() || price.isBlank()) {
                    Toast.makeText(context, "Required fields are empty", Toast.LENGTH_SHORT).show()
                } else {
                    viewModel.addExpense(
                        selectedDate, personName,
                        selectedBank,
                        category, itemName, quantity, selectedUnit, price, selectedType, selectedPaymentMode,
                        additionalInfo
                    )
                    itemName = ""; quantity = ""; price = ""; additionalInfo = ""
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) { Text("ADD ENTRY") }
    }
}

@Composable
fun MultiBankSummaryCard(items: List<ExpenseItem>, banks: List<DailyBankBalance>, currency: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Closing Summary ($currency)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            var grandTotal = 0.0

            banks.forEach { bank ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Text(bank.bankName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                val bankItems = items.filter { it.bankName == bank.bankName }

                fun calc(mode: String, open: Double): Double {
                    val cr = bankItems.filter { it.paymentMode == mode && it.type == TransactionType.CREDIT }.sumOf { it.totalPrice }
                    val dr = bankItems.filter { it.paymentMode == mode && it.type == TransactionType.DEBIT }.sumOf { it.totalPrice }
                    return open + cr - dr
                }

                val cCash = calc("Cash", bank.openingCash)
                val cChq = calc("Cheque", bank.openingCheque)
                val cCard = calc("Card/UPI", bank.openingCard)
                val bankTotal = cCash + cChq + cCard
                grandTotal += bankTotal

                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Cash:"); Text(String.format("%.2f", cCash)) }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Cheque:"); Text(String.format("%.2f", cChq)) }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text("Card:"); Text(String.format("%.2f", cCard)) }
                Text("Total: ${String.format("%.2f", bankTotal)}", fontSize = androidx.compose.ui.unit.TextUnit.Unspecified, fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("GRAND TOTAL ALL BANKS:", fontWeight = FontWeight.Bold);
                Text("$currency ${String.format("%.2f", grandTotal)}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun ExpenseRow(item: ExpenseItem, currency: String, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.itemName, style = MaterialTheme.typography.titleMedium, color = if (item.type == TransactionType.CREDIT) Color.Green else Color.Black, fontWeight = FontWeight.Bold)
                val infoText = if(item.additionalInfo.isNotBlank()) "(${item.additionalInfo}) " else ""
                Text("${infoText}${item.category} | ${item.quantity} ${item.unit} | ${item.paymentMode}", style = MaterialTheme.typography.bodySmall)
                Text(item.bankName, style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
            }
            Text("$currency ${String.format("%.2f", item.totalPrice)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red) }
        }
    }
}