package com.sankalp.expensebuilder.viewmodel

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sankalp.expensebuilder.data.*
import com.sankalp.expensebuilder.utils.ExportUtils
import com.sankalp.expensebuilder.utils.WifiServer
import com.sankalp.expensebuilder.utils.CurrencyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.URL
import java.util.Calendar

// Data class for Downloaded Files
data class DownloadedFile(val id: Long, val name: String, val dateAdded: Long, val uri: Uri)

// Sealed class to merge Expense and Account items for History
sealed class HistoryItem {
    data class Expense(val item: ExpenseItem) : HistoryItem()
    data class Account(val tx: AccountTransaction) : HistoryItem()

    val date: Long get() = when(this) {
        is Expense -> item.date
        is Account -> tx.date
    }
}

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getDatabase(application).expenseDao()
    private val prefs = application.getSharedPreferences("expense_builder_prefs", Context.MODE_PRIVATE)

    // --- STATE MANAGEMENT ---
    private val _selectedDate = MutableStateFlow(getTodayTimestamp())
    val selectedDate: StateFlow<Long> = _selectedDate

    // Live list of expenses for the selected date
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentExpenses = _selectedDate.flatMapLatest { date -> dao.getExpensesByDate(date) }

    // Live list of Bank Balances
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentBankBalances = _selectedDate.flatMapLatest { date -> dao.getBankBalancesByDate(date) }

    // Live list of Account Transactions
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentAccountTx = _selectedDate.flatMapLatest { date -> dao.getAccountTxByDate(date) }

    // --- HISTORY TAB STATE (Combined) ---
    private val _historyMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    private val _historyYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val historyItems: Flow<List<HistoryItem>> = combine(_historyMonth, _historyYear) { month, year ->
        val start = getMonthStart(year, month)
        val end = getMonthEnd(year, month)
        Pair(start, end)
    }.flatMapLatest { (start, end) ->
        // Merge Expenses and Account Tx into one list sorted by date
        combine(dao.getExpensesByDateRange(start, end), dao.getAccountTxByDateRange(start, end)) { exps, accs ->
            val merged = mutableListOf<HistoryItem>()
            merged.addAll(exps.map { HistoryItem.Expense(it) })
            merged.addAll(accs.map { HistoryItem.Account(it) })
            merged.sortedByDescending { it.date }
        }
    }

    fun updateHistoryDate(month: Int, year: Int) {
        _historyMonth.value = month
        _historyYear.value = year
    }

    // --- DOWNLOADS MANAGEMENT (Updated Logic) ---
    private val _downloadedFiles = MutableStateFlow<List<DownloadedFile>>(emptyList())
    val downloadedFiles: StateFlow<List<DownloadedFile>> = _downloadedFiles

    fun fetchDownloadedFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val hiddenIds = prefs.getStringSet("HIDDEN_FILES", emptySet()) ?: emptySet()
            val fileList = mutableListOf<DownloadedFile>()
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.EXTERNAL_CONTENT_URI else MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_ADDED)

            // FIX 1: Filter by extension logic to catch Excel/CSV files reliably
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? OR ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%.pdf", "%.csv")

            getApplication<Application>().contentResolver.query(collection, projection, selection, selectionArgs, "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    if (hiddenIds.contains(id.toString())) continue // Skip hidden files

                    val name = cursor.getString(nameCol)

                    // Extra check to ensure we only show OUR app's files
                    if(name.startsWith("Daily_", true) || name.startsWith("Accounts_", true) || name.startsWith("Monthly_", true)) {
                        val date = cursor.getLong(dateCol) * 1000L
                        val uri = android.content.ContentUris.withAppendedId(collection, id)
                        fileList.add(DownloadedFile(id, name, date, uri))
                    }
                }
            }
            _downloadedFiles.value = fileList
        }
    }

    fun renameFile(file: DownloadedFile, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
                getApplication<Application>().contentResolver.update(file.uri, values, null, null)
                fetchDownloadedFiles() // Refresh list
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(getApplication(), "Rename failed: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    // Hide from list, keep in phone
    fun hideFileRecord(file: DownloadedFile) {
        val currentHidden = prefs.getStringSet("HIDDEN_FILES", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        currentHidden.add(file.id.toString())
        prefs.edit().putStringSet("HIDDEN_FILES", currentHidden).apply()
        fetchDownloadedFiles()
    }

    // Delete from phone
    fun deleteFile(file: DownloadedFile) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.delete(file.uri, null, null)
                fetchDownloadedFiles()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(getApplication(), "Delete failed", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    // FIX 2: Clear records ONLY for current tab category
    fun clearTabRecords(prefix: String) {
        val currentHidden = prefs.getStringSet("HIDDEN_FILES", mutableSetOf())?.toMutableSet() ?: mutableSetOf()

        // Find visible files matching the prefix
        val filesToHide = _downloadedFiles.value.filter {
            if (prefix == "Others") {
                !it.name.startsWith("Daily_", true) && !it.name.startsWith("Accounts_", true)
            } else {
                it.name.startsWith(prefix, true)
            }
        }

        filesToHide.forEach { currentHidden.add(it.id.toString()) }
        prefs.edit().putStringSet("HIDDEN_FILES", currentHidden).apply()
        fetchDownloadedFiles()
    }

    fun clearAllRecords() {
        val currentHidden = prefs.getStringSet("HIDDEN_FILES", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        _downloadedFiles.value.forEach { currentHidden.add(it.id.toString()) }
        prefs.edit().putStringSet("HIDDEN_FILES", currentHidden).apply()
        fetchDownloadedFiles()
    }

    // --- CURRENCY FEATURES ---
    val availableCurrencies = listOf("USD", "INR", "GBP", "EUR", "JPY", "CAD", "AUD", "SGD")

    private val _baseCurrency = MutableStateFlow(prefs.getString("BASE_CURR", "USD") ?: "USD")
    val baseCurrency: StateFlow<String> = _baseCurrency

    private val _targetCurrency = MutableStateFlow("INR")
    val targetCurrency: StateFlow<String> = _targetCurrency

    private val _exchangeRate = MutableStateFlow(1.0)
    val exchangeRate: StateFlow<Double> = _exchangeRate

    // NEW: Currency Conversion Toggle
    private val _isConversionEnabled = MutableStateFlow(true)
    val isConversionEnabled: StateFlow<Boolean> = _isConversionEnabled

    fun toggleConversion(enabled: Boolean) {
        _isConversionEnabled.value = enabled
        if (!enabled) {
            _exchangeRate.value = 1.0 // Reset rate if disabled
        } else {
            refreshRates() // Fetch real rate if enabled
        }
    }

    // --- CATEGORIES ---
    private val defaultCategories = listOf(
        "Home Expenses", "Snacks & Fruit", "Utilities", "CNG/Petrol",
        "Assets", "Medical Expenses", "Education Expenses", "Rent", "Loans", "Others"
    )

    val savedCategories: Flow<List<String>> = dao.getAllCategories().map { dbCategories ->
        (dbCategories + defaultCategories).distinct().sorted()
    }

    private val _itemSuggestions = MutableStateFlow<List<String>>(emptyList())
    val itemSuggestions: StateFlow<List<String>> = _itemSuggestions

    // --- FIRST RUN CHECK (NEW) ---
    private val _showFirstRunDialog = MutableStateFlow(false)
    val showFirstRunDialog: StateFlow<Boolean> = _showFirstRunDialog

    init {
        checkFirstRun()
        refreshRates()
        fetchDownloadedFiles() // Load files on start
    }

    private fun checkFirstRun() {
        val isFirst = prefs.getBoolean("IS_FIRST_RUN", true)
        if (isFirst) _showFirstRunDialog.value = true
    }

    fun setPreferredCurrency(currency: String) {
        prefs.edit().putString("BASE_CURR", currency).putBoolean("IS_FIRST_RUN", false).apply()
        _baseCurrency.value = currency
        _showFirstRunDialog.value = false
        refreshRates()
    }

    // --- WIFI SERVER LOGIC ---
    private var server: WifiServer? = null
    private val _serverIp = MutableStateFlow<String?>(null)
    val serverIp: StateFlow<String?> = _serverIp

    fun toggleServer() {
        if (server != null) {
            server?.stop()
            server = null
            _serverIp.value = null
        } else {
            try {
                server = WifiServer(
                    dao,
                    currencyProvider = {
                        CurrencyState(_baseCurrency.value, _targetCurrency.value, _exchangeRate.value)
                    },
                    currencyUpdater = { base, target ->
                        updateBaseCurrency(base)
                        updateTargetCurrency(target)
                    }
                )
                server?.start()
                _serverIp.value = "http://${getIpAddress()}:8080"
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun getIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "Unknown"
                    }
                }
            }
        } catch (ex: Exception) { }
        return "Unknown"
    }

    override fun onCleared() {
        super.onCleared()
        server?.stop()
    }

    // --- ACTIONS ---

    fun updateDate(newDate: Long) {
        _selectedDate.value = newDate
    }

    fun updateBaseCurrency(newBase: String) {
        _baseCurrency.value = newBase
        prefs.edit().putString("BASE_CURR", newBase).apply() // Save preference
        refreshRates()
    }

    fun updateTargetCurrency(newTarget: String) {
        _targetCurrency.value = newTarget
        refreshRates()
    }

    private fun refreshRates() {
        // If disabled, don't fetch
        if (!_isConversionEnabled.value) return

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
                _exchangeRate.value = cachedRate.toDouble()
            } else {
                fetchAndCacheRate(base, target, todayKey)
            }
        }
    }

    private suspend fun fetchAndCacheRate(base: String, target: String, cacheKey: String) {
        withContext(Dispatchers.IO) {
            try {
                val urlString = "https://open.er-api.com/v6/latest/$base"
                val jsonString = URL(urlString).readText()

                val rootObject = JSONObject(jsonString)
                val ratesObject = rootObject.getJSONObject("rates")

                var rate = 1.0
                if (ratesObject.has(target)) {
                    rate = ratesObject.getDouble(target)
                }

                prefs.edit().putFloat(cacheKey, rate.toFloat()).apply()
                _exchangeRate.value = rate

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Rate Failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun getTodayDateString(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
    }

    // --- MULTI-BANK FEATURE ---
    fun addOrUpdateBankBalance(date: Long, bankName: String, cash: String, cheque: String, card: String) {
        viewModelScope.launch {
            val vCash = cash.toDoubleOrNull() ?: 0.0
            val vCheque = cheque.toDoubleOrNull() ?: 0.0
            val vCard = card.toDoubleOrNull() ?: 0.0

            val balance = DailyBankBalance(
                date = date,
                bankName = bankName,
                openingCash = vCash,
                openingCheque = vCheque,
                openingCard = vCard
            )
            dao.insertBankBalance(balance)
        }
    }

    fun deleteBankBalance(balance: DailyBankBalance) {
        viewModelScope.launch {
            dao.deleteBankBalance(balance)
        }
    }

    // --- ADD EXPENSE ---
    fun addExpense(
        date: Long, personName: String,
        bankName: String,
        category: String, itemName: String, qty: String, unit: UnitType,
        price: String, type: TransactionType, paymentMode: String,
        additionalInfo: String
    ) {
        viewModelScope.launch {
            val validQty = qty.toDoubleOrNull() ?: 0.0
            val validPrice = price.toDoubleOrNull() ?: 0.0

            val expense = ExpenseItem(
                date = date,
                day = getDayFromDate(date),
                personName = personName,
                bankName = bankName,
                additionalInfo = additionalInfo,
                category = category,
                itemName = itemName,
                quantity = validQty,
                unit = unit,
                pricePerUnit = validPrice,
                totalPrice = validPrice,
                type = type,
                paymentMode = paymentMode,
                // Legacy fields 0.0
                openingCash = 0.0,
                openingCheque = 0.0,
                openingCard = 0.0
            )
            dao.insertExpense(expense)
        }
    }

    // --- ACCOUNT TRANSACTIONS ---
    fun addAccountTx(
        date: Long, holder: String, bank: String, accNum: String,
        beneficiary: String, toBank: String, toAccNum: String,
        amount: String, type: TransactionType,
        paymentMode: String
    ) {
        viewModelScope.launch {
            val validAmount = amount.toDoubleOrNull() ?: 0.0
            val tx = AccountTransaction(
                date = date, day = getDayFromDate(date),
                accountHolder = holder, bankName = bank, accountNumber = accNum,
                beneficiaryName = beneficiary, toBankName = toBank, toAccountNumber = toAccNum,
                amount = validAmount, type = type,
                paymentMode = paymentMode
            )
            dao.insertAccountTx(tx)
        }
    }

    fun deleteExpense(item: ExpenseItem) {
        viewModelScope.launch { dao.deleteExpense(item) }
    }

    fun deleteAccountTx(tx: AccountTransaction) {
        viewModelScope.launch { dao.deleteAccountTx(tx) }
    }

    // --- EXPORT OPERATIONS (Updated for Toggle & Download Refresh) ---
    fun exportDailyData(context: Context, type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = currentExpenses.first()
            val currentBalances = currentBankBalances.first()

            if (currentList.isEmpty() && currentBalances.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No data to export", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // Check Toggle
            val rate = if (_isConversionEnabled.value) _exchangeRate.value else 1.0

            if (type == "Excel") {
                ExportUtils.exportDailyToExcel(context, currentList, currentBalances, _baseCurrency.value, _targetCurrency.value, rate)
            } else {
                ExportUtils.exportDailyToPdf(context, currentList, currentBalances, _baseCurrency.value, _targetCurrency.value, rate)
            }
            fetchDownloadedFiles() // Refresh list after download
        }
    }

    fun exportAccountData(context: Context, type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = currentAccountTx.first()

            if (currentList.isEmpty()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "No data to export", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // Check Toggle
            val rate = if (_isConversionEnabled.value) _exchangeRate.value else 1.0

            if (type == "Excel") {
                ExportUtils.exportAccountsToExcel(context, currentList, _baseCurrency.value, _targetCurrency.value, rate)
            } else {
                ExportUtils.exportAccountsToPdf(context, currentList, _baseCurrency.value, _targetCurrency.value, rate)
            }
            fetchDownloadedFiles() // Refresh list after download
        }
    }

    // NEW: Export Monthly Report (Combined)
    fun exportMonthlyData(context: Context, type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val items = historyItems.first()
            if (items.isEmpty()) {
                withContext(Dispatchers.Main) { Toast.makeText(context, "No history for this month", Toast.LENGTH_SHORT).show() }
                return@launch
            }

            // Separate lists from HistoryItems
            val exps = items.filterIsInstance<HistoryItem.Expense>().map { it.item }
            val accs = items.filterIsInstance<HistoryItem.Account>().map { it.tx }
            val rate = if (_isConversionEnabled.value) _exchangeRate.value else 1.0

            val monthStr = "${_historyMonth.value+1}-${_historyYear.value}"

            if (type == "Excel") {
                ExportUtils.exportMonthlyToExcel(context, exps, accs, _baseCurrency.value, _targetCurrency.value, rate, monthStr)
            } else {
                ExportUtils.exportMonthlyToPdf(context, exps, accs, _baseCurrency.value, _targetCurrency.value, rate, monthStr)
            }

            fetchDownloadedFiles() // Refresh list
        }
    }

    // --- DATABASE & HELPERS ---
    fun fetchSuggestions(category: String, query: String) {
        viewModelScope.launch {
            if (query.isNotEmpty()) _itemSuggestions.value = dao.getItemSuggestions(category, query)
            else _itemSuggestions.value = emptyList()
        }
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

    // Helpers for History
    private fun getMonthStart(year: Int, month: Int): Long {
        val c = Calendar.getInstance()
        c.set(year, month, 1, 0, 0, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    private fun getMonthEnd(year: Int, month: Int): Long {
        val c = Calendar.getInstance()
        c.set(year, month, c.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
        c.set(Calendar.MILLISECOND, 999)
        return c.timeInMillis
    }
}