package com.example.expensebuilder.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.expensebuilder.data.AccountTransaction
import com.example.expensebuilder.data.AppDatabase
import com.example.expensebuilder.data.ExpenseItem
import com.example.expensebuilder.data.TransactionType
import com.example.expensebuilder.data.UnitType
import com.example.expensebuilder.utils.ExportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.Calendar

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).expenseDao()
    private val prefs = application.getSharedPreferences("expense_builder_prefs", Context.MODE_PRIVATE)

    // --- STATE MANAGEMENT ---
    private val _selectedDate = MutableStateFlow(getTodayTimestamp())
    val selectedDate: StateFlow<Long> = _selectedDate

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentExpenses = _selectedDate.flatMapLatest { date -> dao.getExpensesByDate(date) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentAccountTx = _selectedDate.flatMapLatest { date -> dao.getAccountTxByDate(date) }

    // --- CURRENCY FEATURES ---
    val availableCurrencies = listOf("USD", "INR", "GBP", "EUR", "JPY", "CAD", "AUD", "SGD")

    private val _baseCurrency = MutableStateFlow("USD")
    val baseCurrency: StateFlow<String> = _baseCurrency

    private val _targetCurrency = MutableStateFlow("INR")
    val targetCurrency: StateFlow<String> = _targetCurrency

    private val _exchangeRate = MutableStateFlow(1.0)
    val exchangeRate: StateFlow<Double> = _exchangeRate

    // --- CATEGORIES ---
    private val defaultCategories = listOf(
        "Home Expenses", "Snacks & Fruit", "Utilities", "CNG/Petrol",
        "Assets", "Medical Expenses", "Education Expenses", "Rent", "Loans"
    )

    val savedCategories: Flow<List<String>> = dao.getAllCategories().map { dbCategories ->
        (dbCategories + defaultCategories).distinct().sorted()
    }

    private val _itemSuggestions = MutableStateFlow<List<String>>(emptyList())
    val itemSuggestions: StateFlow<List<String>> = _itemSuggestions

    init {
        refreshRates()
    }

    // --- ACTIONS ---

    fun updateDate(newDate: Long) {
        _selectedDate.value = newDate
    }

    fun updateBaseCurrency(newBase: String) {
        _baseCurrency.value = newBase
        refreshRates()
    }

    fun updateTargetCurrency(newTarget: String) {
        _targetCurrency.value = newTarget
        refreshRates()
    }

    private fun refreshRates() {
        val base = _baseCurrency.value
        val target = _targetCurrency.value

        if (base == target) {
            _exchangeRate.value = 1.0
            return
        }

        viewModelScope.launch {
            val todayKey = "RATE_${base}_${target}_${getTodayDateString()}"
            val cachedRate = prefs.getFloat(todayKey, -1f)

            if (cachedRate != -1f) {
                // CACHE HIT
                _exchangeRate.value = cachedRate.toDouble()
            } else {
                // CACHE MISS
                fetchAndCacheRate(base, target, todayKey)
            }
        }
    }

    // --- NATIVE JAVA NETWORKING (No Libraries, No Crashes) ---
    private suspend fun fetchAndCacheRate(base: String, target: String, cacheKey: String) {
        withContext(Dispatchers.IO) {
            try {
                // 1. Direct URL Connection (Built into Android)
                // This bypasses Retrofit/OkHttp/Gson entirely.
                val urlString = "https://open.er-api.com/v6/latest/$base"
                val jsonString = URL(urlString).readText()

                // 2. Native Parsing
                val rootObject = JSONObject(jsonString)
                val ratesObject = rootObject.getJSONObject("rates")

                var rate = 1.0
                if (ratesObject.has(target)) {
                    rate = ratesObject.getDouble(target)
                }

                // 3. Save & Update
                prefs.edit().putFloat(cacheKey, rate.toFloat()).apply()
                _exchangeRate.value = rate

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(getApplication(), "Rate Failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getTodayDateString(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
    }

    // --- EXPORT OPERATIONS ---
    fun exportDailyData(context: Context, type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = currentExpenses.first()
            val base = _baseCurrency.value
            val target = _targetCurrency.value
            val rate = _exchangeRate.value

            if (type == "Excel") {
                ExportUtils.exportDailyToExcel(context, currentList, base, target, rate)
            } else {
                ExportUtils.exportDailyToPdf(context, currentList, base, target, rate)
            }
        }
    }

    fun exportAccountData(context: Context, type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = currentAccountTx.first()
            val base = _baseCurrency.value
            val target = _targetCurrency.value
            val rate = _exchangeRate.value

            if (type == "Excel") {
                ExportUtils.exportAccountsToExcel(context, currentList, base, target, rate)
            } else {
                ExportUtils.exportAccountsToPdf(context, currentList, base, target, rate)
            }
        }
    }

    // --- DATABASE & HELPERS ---
    fun fetchSuggestions(category: String, query: String) {
        viewModelScope.launch {
            if (query.isNotEmpty()) _itemSuggestions.value = dao.getItemSuggestions(category, query)
            else _itemSuggestions.value = emptyList()
        }
    }

    // ... inside ExpenseViewModel class ...

    fun addExpense(
        date: Long, personName: String, openingBal: String, category: String,
        itemName: String, qty: String, unit: UnitType, price: String, type: TransactionType,
        paymentMode: String // <--- NEW PARAMETER
    ) {
        viewModelScope.launch {
            val validQty = qty.toDoubleOrNull() ?: 0.0
            val validPrice = price.toDoubleOrNull() ?: 0.0
            val validOpening = openingBal.toDoubleOrNull() ?: 0.0

            val expense = ExpenseItem(
                date = date, day = getDayFromDate(date), personName = personName, openingBalance = validOpening,
                category = category, itemName = itemName, quantity = validQty, unit = unit,
                pricePerUnit = validPrice, totalPrice = validPrice, type = type,
                paymentMode = paymentMode // <--- SAVE IT
            )
            dao.insertExpense(expense)
        }
    }

    fun addAccountTx(
        date: Long, holder: String, bank: String, accNum: String,
        beneficiary: String, toBank: String, toAccNum: String,
        amount: String, type: TransactionType,
        paymentMode: String // <--- NEW PARAMETER
    ) {
        viewModelScope.launch {
            val validAmount = amount.toDoubleOrNull() ?: 0.0
            val tx = AccountTransaction(
                date = date, day = getDayFromDate(date),
                accountHolder = holder, bankName = bank, accountNumber = accNum,
                beneficiaryName = beneficiary, toBankName = toBank, toAccountNumber = toAccNum,
                amount = validAmount, type = type,
                paymentMode = paymentMode // <--- SAVE IT
            )
            dao.insertAccountTx(tx)
        }
    }

    fun deleteExpense(item: ExpenseItem) {
        viewModelScope.launch { dao.deleteExpense(item) }
    }

    private fun getTodayTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun getDayFromDate(date: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        return java.text.SimpleDateFormat("EEEE", java.util.Locale.getDefault()).format(calendar.time)
    }
}