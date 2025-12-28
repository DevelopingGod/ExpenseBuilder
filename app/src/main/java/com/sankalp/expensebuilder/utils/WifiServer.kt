package com.sankalp.expensebuilder.utils

import com.sankalp.expensebuilder.data.AccountTransaction
import com.sankalp.expensebuilder.data.DailyBankBalance
import com.sankalp.expensebuilder.data.ExpenseDao
import com.sankalp.expensebuilder.data.ExpenseItem
import com.sankalp.expensebuilder.data.TransactionType
import com.sankalp.expensebuilder.data.UnitType
import com.google.gson.Gson
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Date

data class CurrencyState(val base: String, val target: String, val rate: Double)

class WifiServer(
    private val dao: ExpenseDao,
    private val currencyProvider: () -> CurrencyState,
    private val currencyUpdater: (String, String) -> Unit
) : NanoHTTPD(8080) {

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun serve(session: IHTTPSession?): Response {
        val uri = session?.uri ?: return newFixedLengthResponse("Error")
        val method = session.method

        if (method == Method.POST) {
            val files = HashMap<String, String>()
            try { session.parseBody(files) } catch (e: Exception) { e.printStackTrace() }
            val postBody = files["postData"] ?: "{}"

            return when (uri) {
                "/api/addExpense" -> handleAddExpense(postBody)
                "/api/deleteExpense" -> handleDeleteExpense(postBody)
                "/api/addAccount" -> handleAddAccount(postBody)
                "/api/deleteAccount" -> handleDeleteAccount(postBody)
                "/api/setCurrency" -> handleSetCurrency(postBody)
                "/api/addBank" -> handleAddBank(postBody)
                "/api/deleteBank" -> handleDeleteBank(postBody)
                "/api/clearHistory" -> handleClearHistory(postBody)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
            }
        }

        return when (uri) {
            "/" -> newFixedLengthResponse(Response.Status.OK, MIME_HTML, getHtmlDashboard())
            "/api/expenses" -> handleGetExpenses(session.parameters)
            "/api/accounts" -> handleGetAccounts(session.parameters)
            "/api/categories" -> handleGetCategories()
            "/api/banks" -> handleGetBanks(session.parameters)
            "/api/history" -> handleGetHistory(session.parameters)
            "/api/export" -> handleExport(session.parameters)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
    }

    // --- DATE PARSING HELPER ---
    private fun parseDateParam(params: Map<String, List<String>>): Long {
        val dateStr = params["date"]?.firstOrNull()
        return if (!dateStr.isNullOrEmpty()) {
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                sdf.parse(dateStr)?.time ?: getTodayTimestamp()
            } catch (e: Exception) { getTodayTimestamp() }
        } else {
            getTodayTimestamp()
        }
    }

    // --- MONTH RANGE HELPER ---
    private fun getMonthRange(date: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = date
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        val end = cal.timeInMillis
        return Pair(start, end)
    }

    // --- HANDLERS ---

    private fun handleSetCurrency(json: String): Response {
        val data = gson.fromJson(json, Map::class.java)
        val base = data["base"].toString()
        val target = data["target"].toString()
        scope.launch(Dispatchers.Main) { currencyUpdater(base, target) }
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Updated")
    }

    private fun handleGetCategories(): Response {
        val list = runBlocking { dao.getAllCategoriesSync() }
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(list))
    }

    private fun handleGetBanks(params: Map<String, List<String>>): Response {
        val date = parseDateParam(params)
        val list = runBlocking { dao.getBankBalancesByDateSync(date) }
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(list))
    }

    private fun handleGetExpenses(params: Map<String, List<String>>): Response {
        val date = parseDateParam(params)
        val list = runBlocking { dao.getExpensesByDateSync(date) }
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(list))
    }

    private fun handleGetAccounts(params: Map<String, List<String>>): Response {
        val date = parseDateParam(params)
        val list = runBlocking { dao.getAccountTxByDateSync(date) }
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(list))
    }

    private fun handleGetHistory(params: Map<String, List<String>>): Response {
        val date = parseDateParam(params)
        val (start, end) = getMonthRange(date)

        val exps = ArrayList<ExpenseItem>()
        val accs = ArrayList<AccountTransaction>()
        val cal = Calendar.getInstance()
        cal.timeInMillis = start

        runBlocking {
            while (cal.timeInMillis <= end) {
                val d = cal.timeInMillis
                exps.addAll(dao.getExpensesByDateSync(d))
                accs.addAll(dao.getAccountTxByDateSync(d))
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        val combined = mapOf("expenses" to exps, "accounts" to accs)
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(combined))
    }

    private fun handleClearHistory(json: String): Response {
        val data = gson.fromJson(json, Map::class.java)
        val dateStr = data["date"]?.toString()
        val date = if(!dateStr.isNullOrEmpty()) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)?.time ?: getTodayTimestamp() else getTodayTimestamp()

        runBlocking {
            val exps = dao.getExpensesByDateSync(date)
            exps.forEach { dao.deleteExpense(it) }
            val accs = dao.getAccountTxByDateSync(date)
            accs.forEach { dao.deleteAccountTx(it) }
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Cleared")
    }

    private fun handleAddBank(json: String): Response {
        try {
            val data = gson.fromJson(json, Map::class.java)
            val dateStr = data["date"]?.toString()
            val date = if(!dateStr.isNullOrEmpty()) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)?.time ?: getTodayTimestamp() else getTodayTimestamp()

            val bankName = data["bankName"].toString().trim()
            val opCash = data["opCash"].toString().toDoubleOrNull() ?: 0.0
            val opCheque = data["opCheque"].toString().toDoubleOrNull() ?: 0.0
            val opCard = data["opCard"].toString().toDoubleOrNull() ?: 0.0

            val balance = DailyBankBalance(date, bankName, opCash, opCheque, opCard)
            runBlocking { dao.insertBankBalance(balance) }
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Added")
        } catch (e: Exception) { return newFixedLengthResponse("Error: ${e.message}") }
    }

    private fun handleDeleteBank(json: String): Response {
        val data = gson.fromJson(json, Map::class.java)
        val bankName = data["bankName"].toString().trim()
        val dateStr = data["date"]?.toString()
        val date = if(!dateStr.isNullOrEmpty()) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)?.time ?: getTodayTimestamp() else getTodayTimestamp()

        runBlocking {
            val list = dao.getBankBalancesByDateSync(date)
            val item = list.find { it.bankName == bankName }
            if (item != null) dao.deleteBankBalance(item)
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Deleted")
    }

    private fun handleAddExpense(json: String): Response {
        try {
            val data = gson.fromJson(json, Map::class.java)
            val dateStr = data["date"]?.toString()
            val date = if(!dateStr.isNullOrEmpty()) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)?.time ?: getTodayTimestamp() else getTodayTimestamp()

            val bank = data["bankName"]?.toString()?.trim() ?: "Unknown"
            val info = data["additionalInfo"]?.toString()?.trim() ?: ""

            val item = ExpenseItem(
                date = date, day = getDayFromDate(date),
                personName = data["personName"].toString().trim(),
                bankName = bank,
                additionalInfo = info,
                openingCash = 0.0, openingCheque = 0.0, openingCard = 0.0,
                category = data["category"].toString().trim(),
                itemName = data["itemName"].toString().trim(),
                quantity = data["quantity"].toString().toDoubleOrNull() ?: 0.0,
                unit = UnitType.valueOf(data["unit"].toString()),
                pricePerUnit = data["price"].toString().toDoubleOrNull() ?: 0.0,
                totalPrice = data["price"].toString().toDoubleOrNull() ?: 0.0,
                type = TransactionType.valueOf(data["type"].toString()),
                paymentMode = data["paymentMode"].toString().trim()
            )
            runBlocking { dao.insertExpense(item) }
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Added")
        } catch (e: Exception) { return newFixedLengthResponse("Error: ${e.message}") }
    }

    private fun handleDeleteExpense(json: String): Response {
        val data = gson.fromJson(json, Map::class.java)
        val id = data["id"].toString().toDouble().toInt()
        val dateStr = data["date"]?.toString()
        val date = if(!dateStr.isNullOrEmpty()) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)?.time ?: getTodayTimestamp() else getTodayTimestamp()

        runBlocking {
            val list = dao.getExpensesByDateSync(date)
            val item = list.find { it.id == id }
            if (item != null) dao.deleteExpense(item)
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Deleted")
    }

    private fun handleAddAccount(json: String): Response {
        try {
            val data = gson.fromJson(json, Map::class.java)
            val dateStr = data["date"]?.toString()
            val date = if(!dateStr.isNullOrEmpty()) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)?.time ?: getTodayTimestamp() else getTodayTimestamp()

            val tx = AccountTransaction(
                date = date, day = getDayFromDate(date),
                accountHolder = data["holder"].toString().trim(),
                bankName = data["bank"].toString().trim(),
                accountNumber = data["accNum"].toString().trim(),
                beneficiaryName = data["benName"].toString().trim(),
                toBankName = data["toBank"].toString().trim(),
                toAccountNumber = data["toAccNum"].toString().trim(),
                amount = data["amount"].toString().toDoubleOrNull() ?: 0.0,
                type = TransactionType.valueOf(data["type"].toString()),
                paymentMode = data["paymentMode"].toString().trim()
            )
            runBlocking { dao.insertAccountTx(tx) }
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Added")
        } catch (e: Exception) { return newFixedLengthResponse("Error") }
    }

    private fun handleDeleteAccount(json: String): Response {
        val data = gson.fromJson(json, Map::class.java)
        val id = data["id"].toString().toDouble().toInt()
        val dateStr = data["date"]?.toString()
        val date = if(!dateStr.isNullOrEmpty()) SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)?.time ?: getTodayTimestamp() else getTodayTimestamp()

        runBlocking {
            val list = dao.getAccountTxByDateSync(date)
            val item = list.find { it.id == id }
            if (item != null) dao.deleteAccountTx(item)
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Deleted")
    }

    private fun handleExport(params: Map<String, List<String>>): Response {
        val type = params["type"]?.firstOrNull() ?: "csv"
        val screen = params["screen"]?.firstOrNull() ?: "daily"
        val date = parseDateParam(params)
        val curr = currencyProvider()

        var bytes: ByteArray = ByteArray(0)
        var mime = "text/csv"
        var filename = "export.csv"
        var isEmpty = true

        runBlocking {
            if (screen == "daily") {
                val list = dao.getExpensesByDateSync(date)
                val banks = dao.getBankBalancesByDateSync(date)
                if (list.isNotEmpty() || banks.isNotEmpty()) {
                    isEmpty = false
                    if (type == "pdf") {
                        bytes = ExportUtils.generateDailyPdf(list, banks, curr.base, curr.target, curr.rate)
                        mime = "application/pdf"; filename = "Daily_${getFileNameDate()}.pdf"
                    } else {
                        bytes = ExportUtils.generateDailyCsv(list, banks, curr.base, curr.target, curr.rate)
                        filename = "Daily_${getFileNameDate()}.csv"
                    }
                }
            } else if (screen == "hist") {
                // FIXED: Fetch whole Month for History Report
                val (start, end) = getMonthRange(date)
                val exps = ArrayList<ExpenseItem>()
                val accs = ArrayList<AccountTransaction>()

                val cal = Calendar.getInstance()
                cal.timeInMillis = start

                while(cal.timeInMillis <= end) {
                    val d = cal.timeInMillis
                    exps.addAll(dao.getExpensesByDateSync(d))
                    accs.addAll(dao.getAccountTxByDateSync(d))
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }

                if (exps.isNotEmpty() || accs.isNotEmpty()) {
                    isEmpty = false

                    // FIXED: Generate dummy banks from expense items to enable PDF grouping
                    val dummyBanks = exps.map { it.bankName }.distinct().map { DailyBankBalance(date, it, 0.0, 0.0, 0.0) }

                    if (type == "pdf") {
                        // Pass dummyBanks instead of emptyList so data is visible
                        bytes = ExportUtils.generateDailyPdf(exps, dummyBanks, curr.base, curr.target, curr.rate)
                        mime = "application/pdf"; filename = "Monthly_Report.pdf"
                    } else {
                        val sb = StringBuilder()
                        sb.append("Monthly Expense Report\n\n--- Expenses ---\n")
                        sb.append(String(ExportUtils.generateDailyCsv(exps, dummyBanks, curr.base, curr.target, curr.rate)))
                        sb.append("\n\n--- Accounts ---\n")
                        sb.append(String(ExportUtils.generateAccountCsv(accs, curr.base, curr.target, curr.rate)))
                        bytes = sb.toString().toByteArray()
                        filename = "Monthly_Report.csv"
                    }
                }
            } else {
                val list = dao.getAccountTxByDateSync(date)
                if (list.isNotEmpty()) {
                    isEmpty = false
                    if (type == "pdf") {
                        bytes = ExportUtils.generateAccountPdf(list, curr.base, curr.target, curr.rate)
                        mime = "application/pdf"; filename = "Accounts_${getFileNameDate()}.pdf"
                    } else {
                        bytes = ExportUtils.generateAccountCsv(list, curr.base, curr.target, curr.rate)
                        filename = "Accounts_${getFileNameDate()}.csv"
                    }
                }
            }
        }

        if (isEmpty) return newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_PLAINTEXT, "No data")

        val response = newFixedLengthResponse(Response.Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong())
        response.addHeader("Content-Disposition", "attachment; filename=\"$filename\"")
        return response
    }

    private fun getTodayTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    private fun getDayFromDate(date: Long): String = SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(date))
    private fun getFileNameDate(): String = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())

    // --- DASHBOARD HTML ---
    private fun getHtmlDashboard(): String {
        val curr = currencyProvider()
        val rateStr = String.format("%.3f", curr.rate)
        val isConvOn = curr.rate != 1.0
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        fun currOpts(selected: String): String {
            val currs = listOf("USD", "INR", "EUR", "GBP", "JPY", "CAD", "AUD", "SGD")
            return currs.joinToString("") {
                val sel = if(it == selected) "selected" else ""
                "<option value='$it' $sel>$it</option>"
            }
        }

        return """
            <!DOCTYPE html><html><head><title>ExpenseBuilder Web</title>
            <style>
                body { font-family: 'Segoe UI', sans-serif; padding:0; margin:0; background:#f4f7f6; height:100vh; display:flex; flex-direction:column; font-size: 16px; }
                
                header { background:#6200EE; color:white; padding:12px 30px; display:flex; justify-content:space-between; align-items:center; box-shadow:0 2px 5px rgba(0,0,0,0.2); }
                .app-title { font-size: 1.6rem; font-weight: bold; }
                
                .curr-controls { display:flex; align-items:center; gap:12px; background:rgba(255,255,255,0.15); padding:6px 15px; border-radius:30px; }
                select.curr-select { padding:5px; border-radius:4px; border:none; font-weight:bold; color:#333; font-size:0.95rem; cursor:pointer; background:white; outline:none; min-width:80px; }
                .curr-label { font-weight:600; font-size:0.9rem; color:white; white-space:nowrap; }

                .switch { position:relative; display:inline-block; width:50px; height:26px; flex-shrink:0; }
                .switch input { opacity:0; width:0; height:0; }
                .slider { position:absolute; cursor:pointer; top:0; left:0; right:0; bottom:0; background-color:#ccc; transition:.4s; border-radius:34px; }
                .slider:before { position:absolute; content:""; height:18px; width:18px; left:4px; bottom:4px; background-color:white; transition:.4s; border-radius:50%; }
                input:checked + .slider { background-color:#03DAC6; }
                input:checked + .slider:before { transform:translateX(24px); }
                .rate-badge { background:white; color:#6200EE; padding:4px 10px; border-radius:4px; font-weight:bold; font-size:0.9rem; white-space:nowrap; }

                .nav-container { background:#fff; padding:10px 30px; border-bottom:1px solid #ddd; display:flex; gap:15px; align-items:center; flex-shrink:0; }
                .tab { padding:10px 24px; background:#f0f0f0; border-radius:25px; cursor:pointer; font-weight:600; color:#666; transition:0.3s; user-select:none; }
                .tab.active { background:#6200EE; color:white; box-shadow:0 2px 5px rgba(98,0,238,0.3); }
                .tab:hover:not(.active) { background:#e0e0e0; }
                
                .date-box { display:flex; align-items:center; gap:8px; }
                .date-box input { padding:8px; border:1px solid #ccc; border-radius:6px; font-family:inherit; font-size:1rem; }

                .container { flex:1; display:flex; overflow:hidden; padding:20px; gap:20px; } 
                .panel-form { width:360px; min-width:300px; background:white; border-radius:10px; padding:20px; overflow-y:auto; box-shadow:0 2px 8px rgba(0,0,0,0.05); }
                .panel-list { flex:1; background:white; border-radius:10px; padding:20px; overflow-y:auto; box-shadow:0 2px 8px rgba(0,0,0,0.05); }
                .panel-guide { flex:1; background:white; border-radius:10px; padding:40px; overflow-y:auto; box-shadow:0 2px 8px rgba(0,0,0,0.05); max-width:900px; margin:0 auto; }
                
                .form-group { margin-bottom:15px; }
                label { display:block; font-size:0.9rem; color:#555; margin-bottom:6px; font-weight:600; }
                input, select { width:100%; padding:10px; border:1px solid #ddd; border-radius:6px; box-sizing:border-box; font-size:0.95rem; }
                
                /* BUTTON STYLES */
                button.add-btn { width:100%; background:#6200EE; color:white; border:none; padding:12px; margin-top:20px; border-radius:8px; font-weight:bold; cursor:pointer; transition:0.2s; font-size:1rem; box-shadow:0 2px 4px rgba(0,0,0,0.1); }
                button.add-btn:hover { background:#3700b3; transform:translateY(-2px); box-shadow:0 4px 8px rgba(98,0,238,0.3); }
                
                button.btn-sec { width:100%; background:#CFD8DC; color:#37474F; border:none; padding:12px; margin-top:0; border-radius:8px; font-weight:bold; cursor:pointer; transition:0.2s; font-size:1rem; }
                button.btn-sec:hover { background:#B0BEC5; color:#263238; }

                button.btn-green { width:100%; background:#4CAF50; color:white; border:none; padding:12px; margin-top:20px; border-radius:8px; font-weight:bold; cursor:pointer; transition:0.2s; font-size:1rem; box-shadow:0 2px 4px rgba(0,0,0,0.1); }
                button.btn-green:hover { background:#388E3C; transform:translateY(-1px); box-shadow:0 4px 8px rgba(0,0,0,0.2); }
                
                button.btn-red { background:#D32F2F; color:white; border:none; padding:8px 12px; border-radius:6px; font-weight:bold; cursor:pointer; transition:0.2s; font-size:0.9rem; }
                button.btn-red:hover { background:#B71C1C; }
                
                .bank-popup { background:#F3E5F5; border:2px dashed #7E57C2; padding:20px; border-radius:12px; margin-bottom:20px; }
                
                table { width:100%; border-collapse:collapse; margin-top:10px; }
                th, td { padding:14px; text-align:left; border-bottom:1px solid #eee; }
                th { background:#f9f9f9; font-weight:700; color:#555; font-size:0.9rem; }
                tr:hover { background:#fafafa; }
                .credit { color:#2e7d32; font-weight:bold; background:#e8f5e9; padding:4px 8px; border-radius:12px; font-size:0.85rem; }
                .debit { color:#c62828; font-weight:bold; background:#ffebee; padding:4px 8px; border-radius:12px; font-size:0.85rem; }
                
                .actions-bar { display:flex; justify-content:flex-end; gap:12px; margin-bottom:15px; }
                .btn-dl { text-decoration:none; padding:10px 20px; border-radius:6px; color:white; font-weight:bold; font-size:0.95rem; transition:0.3s; box-shadow:0 2px 4px rgba(0,0,0,0.15); display:inline-block; }
                .xls { background:linear-gradient(135deg, #1D6F42, #43A047); }
                .pdf { background:linear-gradient(135deg, #B71C1C, #E53935); }
                .btn-dl:hover { transform:translateY(-2px); box-shadow:0 5px 12px rgba(0,0,0,0.2); opacity:0.95; }
                
                .hidden { display:none !important; }
                .guide-sec { margin-bottom:30px; }
                .guide-sec h3 { color:#6200EE; border-bottom:1px solid #eee; padding-bottom:5px; }
                .dev-links { text-align:center; margin-top:40px; padding-top:20px; border-top:1px solid #eee; }
                .dev-links a { display:inline-block; margin:0 15px; color:#0077b5; font-weight:bold; text-decoration:none; padding:10px 20px; border:2px solid #0077b5; border-radius:30px; transition:0.3s; }
                .dev-links a:hover { background:#0077b5; color:white; }

                /* SUMMARY STYLES */
                .sum-bank-card { border:1px solid #eee; border-radius:8px; overflow:hidden; margin-bottom:15px; }
                .sum-header { background:#f5f5f5; padding:8px 12px; display:flex; justify-content:space-between; align-items:center; border-bottom:1px solid #ddd; }
                .sum-header h4 { margin:0; color:#1A237E; font-size:1rem; }
                .sum-table { width:100%; font-size:0.9rem; }
                .sum-table td { padding:6px 12px; border-bottom:1px dashed #eee; }
                .sum-label { font-weight:600; color:#666; width:80px; }
                .sum-val { font-family:monospace; }
                .sum-pos { color:#2e7d32; font-weight:bold; } .sum-neg { color:#c62828; font-weight:bold; }
                .grand-total { text-align:right; font-size:1.2rem; font-weight:bold; color:#1A237E; margin-top:20px; border-top:2px solid #eee; padding-top:10px; }

            </style>
            </head><body>
            
            <header>
                <div class="app-title">ExpenseBuilder 💻</div>
                <div class="curr-controls">
                    <span class="curr-label">Base:</span>
                    <select id="baseCurr" class="curr-select" onchange="updCurr()">${currOpts(curr.base)}</select>
                    <div style="width:1px; height:20px; background:rgba(255,255,255,0.4); margin:0 5px;"></div>
                    <span class="curr-label">Convert:</span>
                    <label class="switch">
                        <input type="checkbox" id="convToggle" ${if(isConvOn) "checked" else ""}>
                        <span class="slider round"></span>
                    </label>
                    <span id="targetSection" style="display:flex; align-items:center; gap:8px; margin-left:10px;">
                        <span style="color:white; font-size:1.2rem;">&rarr;</span>
                        <select id="targetCurr" class="curr-select" onchange="updCurr()">${currOpts(curr.target)}</select>
                        <span class="rate-badge">Rate: $rateStr</span>
                    </span>
                </div>
            </header>
            
            <div class="nav-container">
                <div style="display:flex; gap:10px;">
                    <div class="tab active" id="tabD" onclick="setTab('daily')">Daily Expense</div>
                    <div class="tab" id="tabA" onclick="setTab('acc')">Accounts</div>
                    <div class="tab" id="tabH" onclick="setTab('hist')">History</div>
                    <div class="tab" id="tabG" onclick="setTab('guide')">Guide</div>
                </div>
                <div class="date-box">
                    <span style="font-weight:bold; color:#555;">Date:</span>
                    <input type="date" id="dateInput" value="$todayStr" onchange="refreshAll()">
                </div>
            </div>
            
            <div class="container">
                <div class="panel-form" id="panelForm">
                    <div id="bankForm" class="bank-popup hidden">
                        <h4 style="margin-top:0; color:#4A148C;">Add New Bank</h4>
                        <label>Name</label><input id="bn" placeholder="e.g. HDFC">
                        <label>Opening Balances ($rateStr)</label>
                        <input id="bc" type="number" placeholder="Cash" style="margin-bottom:8px;">
                        <input id="bq" type="number" placeholder="Cheque" style="margin-bottom:8px;">
                        <input id="bd" type="number" placeholder="Card">
                        <div style="display:flex; gap:10px; margin-top:15px;">
                            <button class="btn-green" style="margin-top:0" onclick="addBank()">Save</button>
                            <button class="btn-sec" onclick="el('bankForm').classList.add('hidden')">Cancel</button>
                        </div>
                    </div>
                    
                    <div id="dailyForm">
                        <h3 style="display:flex; justify-content:space-between; margin-top:0;">Add Expense <span style="font-size:0.8rem; color:#6200EE; cursor:pointer;" onclick="el('bankForm').classList.remove('hidden')">+ Bank</span></h3>
                        <label>Person</label><input id="dn">
                        <label>Bank</label><select id="db"><option>Loading...</option></select>
                        <label>Category</label><select id="dc"><option>Loading...</option></select>
                        <label>Item</label><input id="di">
                        <label>Info</label><input id="dinf" placeholder="Optional">
                        <label>Qty</label><input type="number" id="dq">
                        <label>Unit</label>
                        <select id="du">
                            <option>PIECE</option><option>KG</option><option>GRAM</option><option>LITER</option><option>ML</option><option>DOZEN</option><option>Not Applicable</option><option>Not Available</option>
                        </select>
                        <label>Price (${curr.base})</label><input id="dp" type="number">
                        <label>Type / Mode</label>
                        <div style="display:flex; gap:8px;">
                            <select id="dt"><option value="DEBIT">Debit</option><option value="CREDIT">Credit</option></select>
                            <select id="dm"><option>Cash</option><option>Cheque</option><option>Card/UPI</option></select>
                        </div>
                        <button class="btn-green" onclick="addD()">ADD ITEM</button>
                    </div>
                    
                    <div id="accForm" class="hidden">
                        <h3 style="margin-top:0;">Add Transaction</h3>
                        <label>From: Holder / Bank / Acc</label>
                        <input id="ah" placeholder="Holder"><input id="ab" placeholder="Bank" style="margin-top:5px;"><input id="aan" placeholder="Acc Num" style="margin-top:5px;">
                        
                        <label style="margin-top:10px;">To: Beneficiary / Bank / Acc</label>
                        <input id="atn" placeholder="Name"><input id="atb" placeholder="Bank" style="margin-top:5px;"><input id="atan" placeholder="Acc Num" style="margin-top:5px;">
                        
                        <label>Amount (${curr.base})</label><input id="aa" type="number">
                        
                        <label>Type / Mode</label>
                        <div style="display:flex; gap:8px;">
                            <select id="at"><option value="DEBIT">Debit</option><option value="CREDIT">Credit</option></select>
                            <select id="am"><option>Cash</option><option>Cheque</option><option>Card/UPI</option></select>
                        </div>
                        <button class="btn-green" onclick="addA()">ADD TRANSACTION</button>
                    </div>
                </div>
                
                <div class="panel-list" id="panelList">
                    <div class="actions-bar">
                        <a href="javascript:dl('csv')" class="btn-dl xls">📊 Download Excel</a>
                        <a href="javascript:dl('pdf')" class="btn-dl pdf">📄 Download PDF</a>
                    </div>
                    <div id="listD"><table><thead><tr><th>Item</th><th>Qty</th><th>Price</th><th>Type</th><th>Mode</th><th>Act</th></tr></thead><tbody></tbody></table><div id="summD" class="summary-card hidden"></div></div>
                    <div id="listA" class="hidden"><table><thead><tr><th>Details</th><th>Amount</th><th>Type</th><th>Mode</th><th>Act</th></tr></thead><tbody></tbody></table></div>
                    <div id="listH" class="hidden">
                        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:15px;">
                            <h3 style="color:#6200EE; margin:0;">History (Monthly View)</h3>
                            <button class="btn-red" onclick="clearHist()">Clear Date History</button>
                        </div>
                        <table><thead><tr><th>Date</th><th>Description</th><th>Amount</th><th>Type</th><th>Mode</th></tr></thead><tbody></tbody></table>
                    </div>
                </div>
                
                <div class="panel-guide hidden" id="panelGuide">
                    <h2>User Guide & Tips</h2>
                    <div class="guide-sec">
                        <h3>1. Getting Started</h3>
                        <ul>
                            <li><b>Add Bank:</b> Click "+ Bank" in the Daily Expense form. Enter Opening Balance.</li>
                            <li><b>Date Selection:</b> Use the Date Picker in the top-right corner to view or add data for past or future dates.</li>
                        </ul>
                    </div>
                    <div class="guide-sec">
                        <h3>2. Managing Expenses</h3>
                        <ul>
                            <li>Select the correct <b>Bank</b> to track balances.</li>
                            <li>Use <b>DOZEN</b> for bulk items like fruits.</li>
                            <li><b>Additional Info</b> helps searching later.</li>
                        </ul>
                    </div>
                    <div class="guide-sec">
                        <h3>3. Accounts & History</h3>
                        <ul>
                            <li><b>Accounts Tab:</b> Log transfers (Loans, Rent, Salary).</li>
                            <li><b>History Tab:</b> Shows all transactions for the <b>Entire Month</b> of the selected date.</li>
                            <li><b>Clear History:</b> Use the red button to delete records for the *specific selected date*.</li>
                            <li><b>Toggle Convert:</b> Turn ON to see estimated foreign values. Turn OFF to hide.</li>
                        </ul>
                    </div>
                    <div class="dev-links"><a href="https://www.linkedin.com/in/sankalp-indish/" target="_blank">LinkedIn</a><a href="https://github.com/DevelopingGod" target="_blank">GitHub</a></div>
                </div>
            </div>
            
            <script>
                const el = (id) => document.getElementById(id);
                let curTab = 'daily';
                
                const toggle = el('convToggle');
                const targetSec = el('targetSection');
                
                function updateUI() { targetSec.style.display = toggle.checked ? 'flex' : 'none'; }
                toggle.addEventListener('change', updateUI);
                el('baseCurr').value = "${curr.base}";
                el('targetCurr').value = "${curr.target}";
                if(${isConvOn}) { toggle.checked = true; targetSec.classList.remove('hidden'); } else { toggle.checked = false; }
                updateUI(); 
                
                function setTab(t) {
                    curTab = t;
                    ['tabD','tabA','tabH','tabG'].forEach(id => el(id).className = 'tab');
                    ['panelForm','panelList','panelGuide'].forEach(id => el(id).classList.add('hidden'));
                    ['dailyForm','accForm','listD','listA','listH'].forEach(id => el(id).classList.add('hidden'));
                    
                    if(t === 'daily') {
                        el('tabD').className = 'tab active';
                        el('panelForm').classList.remove('hidden');
                        el('panelList').classList.remove('hidden');
                        el('dailyForm').classList.remove('hidden');
                        el('listD').classList.remove('hidden');
                        ldD();
                    } else if(t === 'acc') {
                        el('tabA').className = 'tab active';
                        el('panelForm').classList.remove('hidden');
                        el('panelList').classList.remove('hidden');
                        el('accForm').classList.remove('hidden');
                        el('listA').classList.remove('hidden');
                        ldA();
                    } else if(t === 'hist') {
                        el('tabH').className = 'tab active';
                        el('panelList').classList.remove('hidden');
                        el('listH').classList.remove('hidden');
                        ldH();
                    } else if(t === 'guide') {
                        el('tabG').className = 'tab active';
                        el('panelGuide').classList.remove('hidden');
                    }
                }
                
                const getDParam = () => `?date=` + el('dateInput').value;
                function refreshAll() { if(curTab==='daily') ldD(); else if(curTab==='acc') ldA(); else if(curTab==='hist') ldH(); }
                function post(u,d,cb){ d.date = el('dateInput').value; fetch(u,{method:'POST', body:JSON.stringify(d)}).then(cb); }
                function updCurr() { post('/api/setCurrency', {base:el('baseCurr').value, target:el('targetCurr').value}, ()=>location.reload()); }
                
                // FIXED: Try/Catch for Empty Downloads
                function dl(t) { 
                   let u = `/api/export?type=${'$'}{t}&screen=${'$'}{curTab}&date=` + el('dateInput').value;
                   fetch(u).then(r => {
                       if(r.status === 204) alert("No data available to download.");
                       else window.location.href = u;
                   }).catch(e => alert("Download failed: " + e));
                }

                function clearHist() { if(confirm('Permanently delete all data for this DATE?')) post('/api/clearHistory', {}, refreshAll); }
                
                function addBank() { post('/api/addBank', { bankName:el('bn').value, opCash:el('bc').value, opCheque:el('bq').value, opCard:el('bd').value }, ()=>{ el('bankForm').classList.add('hidden'); loadBanks(); ldD(); }); }
                function delBank(n) { if(confirm('Delete Bank?')) post('/api/deleteBank', {bankName:n}, ldD); }
                function del(api, id) { if(confirm('Delete?')) post('/api/'+api, {id:id}, refreshAll); }
                
                function addD() {
                    if(!el('dn').value || !el('db').value || !el('dc').value || !el('di').value || !el('dp').value) return alert("Fill Mandatory Fields: Person, Bank, Category, Item, Price!");
                    post('/api/addExpense', {
                        personName:el('dn').value, bankName:el('db').value, category:el('dc').value, itemName:el('di').value, additionalInfo:el('dinf').value,
                        quantity:el('dq').value, unit:el('du').value, price:el('dp').value, type:el('dt').value, paymentMode:el('dm').value
                    }, ldD);
                }
                function addA() {
                     if(!el('ah').value || !el('ab').value || !el('aan').value || !el('atn').value || !el('aa').value) return alert("Fill Mandatory Fields: Holder, Bank, Acc Num, Beneficiary, Amount!");
                     post('/api/addAccount', { holder:el('ah').value, bank:el('ab').value, accNum:el('aan').value, benName:el('atn').value, toBank:el('atb').value, toAccNum:el('atan').value, amount:el('aa').value, type:el('at').value, paymentMode:el('am').value }, ldA);
                }
                
                function loadBanks() { fetch('/api/banks'+getDParam()).then(r=>r.json()).then(d=>{ let h=d.length?'':'<option>No Banks</option>'; d.forEach(b=>h+=`<option>${'$'}{b.bankName}</option>`); el('db').innerHTML=h; }); }
                function loadCats() { fetch('/api/categories').then(r=>r.json()).then(d=>{ let h=''; [...new Set(["Home Expenses","Snacks & Fruit","Utilities","CNG/Petrol","Assets","Medical Expenses","Education Expenses","Rent","Loans","Flights", "Hotel", "Groceries", "Electronics", "Others",...d])].sort().forEach(c=>h+=`<option>${'$'}{c}</option>`); el('dc').innerHTML=h; }); }
                
                function ldD() {
                    Promise.all([fetch('/api/expenses'+getDParam()).then(r=>r.json()), fetch('/api/banks'+getDParam()).then(r=>r.json())]).then(([ex, bk]) => {
                        let h = '';
                        ex.forEach(e => {
                            h += `<tr><td><b>${'$'}{e.itemName}</b><br><small>${'$'}{e.bankName} | ${'$'}{e.category}</small></td>
                                  <td>${'$'}{e.quantity} ${'$'}{e.unit}</td><td>${'$'}{e.totalPrice}</td>
                                  <td><span class="${'$'}{e.type==='CREDIT'?'credit':'debit'}">${'$'}{e.type}</span></td>
                                  <td>${'$'}{e.paymentMode}</td>
                                  <td><button onclick="del('deleteExpense',${'$'}{e.id})" style="color:red;border:none;cursor:pointer;font-weight:bold;background:none;">X</button></td></tr>`;
                        });
                        el('listD').querySelector('tbody').innerHTML = h || '<tr><td colspan="6">No Expenses for this date</td></tr>';
                        
                        if(bk.length) {
                            let s = '<h4>Closing Summary (${curr.base})</h4>';
                            let gt = 0;
                            bk.forEach(b => {
                                let bex = ex.filter(x => x.bankName === b.bankName);
                                let calc = (m) => {
                                    let cr = bex.filter(x => x.paymentMode === m && x.type === 'CREDIT').reduce((a,c)=>a+c.totalPrice,0);
                                    let dr = bex.filter(x => x.paymentMode === m && x.type === 'DEBIT').reduce((a,c)=>a+c.totalPrice,0);
                                    return cr - dr;
                                };
                                let netC=calc('Cash'), netQ=calc('Cheque'), netK=calc('Card/UPI');
                                let clCash = b.openingCash + netC;
                                let clChq = b.openingCheque + netQ;
                                let clCard = b.openingCard + netK;
                                let tot = clCash + clChq + clCard;
                                gt += tot;
                                
                                s += `<div class="sum-bank-card">
                                        <div class="sum-header"><h4>${'$'}{b.bankName}</h4> <span onclick="delBank('${'$'}{b.bankName}')" style="cursor:pointer;color:red;font-weight:bold;">&times;</span></div>
                                        <table class="sum-table">
                                            <tr><td class="sum-label">Opening</td> <td class="sum-val">C: ${'$'}{b.openingCash}</td> <td class="sum-val">Q: ${'$'}{b.openingCheque}</td> <td class="sum-val">D: ${'$'}{b.openingCard}</td></tr>
                                            <tr><td class="sum-label">Activity</td> <td class="sum-val ${'$'}{netC>=0?'sum-pos':'sum-neg'}">${'$'}{netC}</td> <td class="sum-val ${'$'}{netQ>=0?'sum-pos':'sum-neg'}">${'$'}{netQ}</td> <td class="sum-val ${'$'}{netK>=0?'sum-pos':'sum-neg'}">${'$'}{netK}</td></tr>
                                            <tr><td class="sum-label">Closing</td> <td class="sum-val"><b>${'$'}{clCash.toFixed(2)}</b></td> <td class="sum-val"><b>${'$'}{clChq.toFixed(2)}</b></td> <td class="sum-val"><b>${'$'}{clCard.toFixed(2)}</b></td></tr>
                                        </table>
                                      </div>`;
                            });
                            s += `<div class="grand-total">GRAND TOTAL: ${'$'}{gt.toFixed(2)}</div>`;
                            el('summD').innerHTML = s; el('summD').classList.remove('hidden');
                        } else el('summD').classList.add('hidden');
                    });
                }
                
                function ldA() {
                    fetch('/api/accounts'+getDParam()).then(r=>r.json()).then(d => {
                        let h = '';
                        d.forEach(a => {
                             h += `<tr><td><b>${'$'}{a.beneficiaryName}</b><br><small>To: ${'$'}{a.toBankName} (${'$'}{a.toAccountNumber})</small></td>
                                   <td>${'$'}{a.amount}</td><td><span class="${'$'}{a.type==='CREDIT'?'credit':'debit'}">${'$'}{a.type}</span></td>
                                   <td>${'$'}{a.paymentMode}</td>
                                   <td><button onclick="del('deleteAccount',${'$'}{a.id})" style="color:red;border:none;cursor:pointer;background:none;">X</button></td></tr>`;
                        });
                        el('listA').querySelector('tbody').innerHTML = h || '<tr><td colspan="5">No Transactions for this date</td></tr>';
                    });
                }
                
                function ldH() {
                    // FIXED: Monthly View in History Tab (matches download)
                    fetch('/api/history'+getDParam()).then(r=>r.json()).then(d => {
                        let all = [...(d.expenses||[]).map(x=>({...x, k:'EXP'})), ...(d.accounts||[]).map(x=>({...x, k:'ACC'}))];
                        all.sort((a,b) => b.id - a.id);
                        let h = '';
                        all.forEach(i => {
                            let dateStr = new Date(i.date).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
                            let desc = i.k === 'EXP' ? i.itemName : i.beneficiaryName;
                            let amt = i.k === 'EXP' ? i.totalPrice : i.amount;
                            h += `<tr><td>${'$'}{dateStr}</td><td>${'$'}{desc}</td><td>${'$'}{amt}</td><td><span class="${'$'}{i.type==='CREDIT'?'credit':'debit'}">${'$'}{i.type}</span></td><td>${'$'}{i.paymentMode}</td></tr>`;
                        });
                        el('listH').querySelector('tbody').innerHTML = h || '<tr><td colspan="5">No History for this month</td></tr>';
                    });
                }

                loadBanks(); loadCats(); ldD();
            </script></body></html>
        """.trimIndent()
    }
}