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
import com.sankalp.expensebuilder.data.AccountTransaction
import com.sankalp.expensebuilder.data.TransactionType
import com.sankalp.expensebuilder.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AccountScreen(viewModel: ExpenseViewModel) {
    val context = LocalContext.current
    val selectedDate by viewModel.selectedDate.collectAsState()
    val accountList by viewModel.currentAccountTx.collectAsState(initial = emptyList())

    // Currency States
    val baseCurrency by viewModel.baseCurrency.collectAsState()
    val targetCurrency by viewModel.targetCurrency.collectAsState()
    val exchangeRate by viewModel.exchangeRate.collectAsState()
    // Conversion Toggle State
    val isConversionEnabled by viewModel.isConversionEnabled.collectAsState()

    var baseCurrExpanded by remember { mutableStateOf(false) }
    var targetCurrExpanded by remember { mutableStateOf(false) }

    // FROM Fields
    var holderName by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var accNumber by remember { mutableStateOf("") }

    // TO Fields
    var beneficiaryName by remember { mutableStateOf("") }
    var toBankName by remember { mutableStateOf("") }
    var toAccountNumber by remember { mutableStateOf("") }

    var amount by remember { mutableStateOf("") }

    // UPDATED: Type Selection
    var selectedType by remember { mutableStateOf(TransactionType.DEBIT) }
    var typeExpanded by remember { mutableStateOf(false) }

    // --- Payment Mode State ---
    var selectedPaymentMode by remember { mutableStateOf("Cash") }
    val paymentOptions = listOf("Cash", "Cheque", "Card/UPI")

    val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        item {
            Text("Accounts", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(10.dp))

            // --- DATE & CURRENCY SELECTION ---
            Column(modifier = Modifier.fillMaxWidth()) {
                // Row 1: Date and Currency Selectors
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    // Date Picker
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small)
                            .clickable {
                                val calendar = Calendar.getInstance()
                                calendar.timeInMillis = selectedDate
                                DatePickerDialog(context, { _, y, m, d ->
                                    val newCal = Calendar.getInstance()
                                    newCal.set(y, m, d)
                                    viewModel.updateDate(newCal.timeInMillis)
                                }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                            }
                            .padding(12.dp)
                    ) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(dateFormatter.format(Date(selectedDate)), style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.width(8.dp))

                    // Base Currency
                    Box {
                        Button(onClick = { baseCurrExpanded = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                            Text(baseCurrency)
                            Icon(Icons.Default.ArrowDropDown, null)
                        }
                        DropdownMenu(expanded = baseCurrExpanded, onDismissRequest = { baseCurrExpanded = false }) {
                            viewModel.availableCurrencies.forEach { cur ->
                                DropdownMenuItem(text = { Text(cur) }, onClick = { viewModel.updateBaseCurrency(cur); baseCurrExpanded = false })
                            }
                        }
                    }

                    // Target Currency (Only visible if enabled)
                    if (isConversionEnabled) {
                        Text("→", modifier = Modifier.padding(horizontal = 4.dp))
                        Box {
                            Button(onClick = { targetCurrExpanded = true }, contentPadding = PaddingValues(horizontal = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                                Text(targetCurrency)
                                Icon(Icons.Default.ArrowDropDown, null)
                            }
                            DropdownMenu(expanded = targetCurrExpanded, onDismissRequest = { targetCurrExpanded = false }) {
                                viewModel.availableCurrencies.forEach { cur ->
                                    DropdownMenuItem(text = { Text(cur) }, onClick = { viewModel.updateTargetCurrency(cur); targetCurrExpanded = false })
                                }
                            }
                        }
                    }
                }

                // Row 2: Toggle Switch & Rate
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
                        onCheckedChange = { viewModel.toggleConversion(it) },
                        modifier = Modifier.scale(0.8f)
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isConversionEnabled) {
                        Text(
                            text = "1 $baseCurrency ≈ ${String.format("%.3f", exchangeRate)} $targetCurrency",
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
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // --- INPUT FORM ---
        item {
            Text("From (Sender)", fontWeight = FontWeight.Bold, color = Color.Gray)
            OutlinedTextField(value = holderName, onValueChange = { holderName = it }, label = { Text("Account Holder Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = bankName, onValueChange = { bankName = it }, label = { Text("Bank Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = accNumber, onValueChange = { accNumber = it }, label = { Text("Account Number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            Text("To (Beneficiary)", fontWeight = FontWeight.Bold, color = Color.Gray)
            OutlinedTextField(value = beneficiaryName, onValueChange = { beneficiaryName = it }, label = { Text("Beneficiary Account Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = toBankName, onValueChange = { toBankName = it }, label = { Text("Beneficiary Bank Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = toAccountNumber, onValueChange = { toAccountNumber = it }, label = { Text("Beneficiary Account Number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Amount Field
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount ($baseCurrency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )

                Spacer(Modifier.width(8.dp))

                // UPDATED: Dropdown for Credit/Debit (Consistent UI)
                Box(modifier = Modifier.weight(0.8f)) {
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

            // --- Payment Mode Radio Buttons ---
            Spacer(modifier = Modifier.height(8.dp))
            Text("Payment Mode:", style = MaterialTheme.typography.labelMedium)
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

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (holderName.isBlank() || bankName.isBlank() || accNumber.isBlank() ||
                        beneficiaryName.isBlank() || toBankName.isBlank() || toAccountNumber.isBlank() || amount.isBlank()) {
                        Toast.makeText(context, "All fields are mandatory", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.addAccountTx(
                            selectedDate, holderName, bankName, accNumber,
                            beneficiaryName, toBankName, toAccountNumber,
                            amount, selectedType, selectedPaymentMode
                        )
                        amount = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("ADD TRANSACTION") }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        }

        // --- LIST WITH DELETE BUTTON ---
        items(items = accountList, key = { it.id }) { tx ->
            AccountRow(tx, baseCurrency) { viewModel.deleteAccountTx(tx) }
        }

        // --- FOOTER & EXPORT ---
        item {
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { viewModel.exportAccountData(context, "Excel") }) { Text("Excel ($baseCurrency)") }
                Button(onClick = { viewModel.exportAccountData(context, "PDF") }) { Text("PDF ($baseCurrency)") }
            }
            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}

@Composable
fun AccountRow(tx: AccountTransaction, currency: String, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(tx.beneficiaryName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("To: ${tx.toBankName} | Acc: ${tx.toAccountNumber} | [${tx.paymentMode}]", style = MaterialTheme.typography.bodySmall)
            }

            Text(text = "${if(tx.type == TransactionType.CREDIT) "+" else "-"} $currency ${tx.amount}", color = if(tx.type == TransactionType.CREDIT) Color.Green else Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)

            // --- DELETE BUTTON ---
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
            }
        }
    }
}