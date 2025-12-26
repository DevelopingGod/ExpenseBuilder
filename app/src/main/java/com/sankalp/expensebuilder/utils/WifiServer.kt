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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
            }
        }

        return when (uri) {
            "/" -> newFixedLengthResponse(Response.Status.OK, MIME_HTML, getHtmlDashboard())
            "/api/expenses" -> handleGetExpenses()
            "/api/accounts" -> handleGetAccounts()
            "/api/categories" -> handleGetCategories()
            "/api/banks" -> handleGetBanks()
            "/api/export" -> handleExport(session.parameters)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        }
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

    private fun handleGetBanks(): Response {
        val list = runBlocking { dao.getBankBalancesByDateSync(getTodayTimestamp()) }
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(list))
    }

    private fun handleGetExpenses(): Response {
        val list = runBlocking { dao.getExpensesByDateSync(getTodayTimestamp()) }
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(list))
    }

    private fun handleGetAccounts(): Response {
        val list = runBlocking { dao.getAccountTxByDateSync(getTodayTimestamp()) }
        return newFixedLengthResponse(Response.Status.OK, "application/json", gson.toJson(list))
    }

    private fun handleAddBank(json: String): Response {
        try {
            val data = gson.fromJson(json, Map::class.java)
            val date = getTodayTimestamp()
            val bankName = data["bankName"].toString()
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
        val bankName = data["bankName"].toString()
        val date = getTodayTimestamp()
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
            val date = getTodayTimestamp()

            val bank = data["bankName"]?.toString() ?: "Unknown"
            val info = data["additionalInfo"]?.toString() ?: ""

            val item = ExpenseItem(
                date = date, day = getDayFromDate(date),
                personName = data["personName"].toString(),
                bankName = bank,
                additionalInfo = info,
                openingCash = 0.0,
                openingCheque = 0.0,
                openingCard = 0.0,
                category = data["category"].toString(),
                itemName = data["itemName"].toString(),
                quantity = data["quantity"].toString().toDoubleOrNull() ?: 0.0,
                unit = UnitType.valueOf(data["unit"].toString()),
                pricePerUnit = data["price"].toString().toDoubleOrNull() ?: 0.0,
                totalPrice = data["price"].toString().toDoubleOrNull() ?: 0.0,
                type = TransactionType.valueOf(data["type"].toString()),
                paymentMode = data["paymentMode"].toString()
            )
            runBlocking { dao.insertExpense(item) }
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Added")
        } catch (e: Exception) { return newFixedLengthResponse("Error: ${e.message}") }
    }

    private fun handleDeleteExpense(json: String): Response {
        val data = gson.fromJson(json, Map::class.java)
        val id = data["id"].toString().toDouble().toInt()
        runBlocking {
            val list = dao.getExpensesByDateSync(getTodayTimestamp())
            val item = list.find { it.id == id }
            if (item != null) dao.deleteExpense(item)
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Deleted")
    }

    private fun handleAddAccount(json: String): Response {
        try {
            val data = gson.fromJson(json, Map::class.java)
            val date = getTodayTimestamp()
            val tx = AccountTransaction(
                date = date, day = getDayFromDate(date),
                accountHolder = data["holder"].toString(),
                bankName = data["bank"].toString(),
                accountNumber = data["accNum"].toString(),
                beneficiaryName = data["benName"].toString(),
                toBankName = data["toBank"].toString(),
                toAccountNumber = data["toAccNum"].toString(),
                amount = data["amount"].toString().toDoubleOrNull() ?: 0.0,
                type = TransactionType.valueOf(data["type"].toString()),
                paymentMode = data["paymentMode"].toString()
            )
            runBlocking { dao.insertAccountTx(tx) }
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Added")
        } catch (e: Exception) { return newFixedLengthResponse("Error") }
    }

    private fun handleDeleteAccount(json: String): Response {
        val data = gson.fromJson(json, Map::class.java)
        val id = data["id"].toString().toDouble().toInt()
        runBlocking {
            val list = dao.getAccountTxByDateSync(getTodayTimestamp())
            val item = list.find { it.id == id }
            if (item != null) dao.deleteAccountTx(item)
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "Deleted")
    }

    private fun handleExport(params: Map<String, List<String>>): Response {
        val type = params["type"]?.firstOrNull() ?: "csv"
        val screen = params["screen"]?.firstOrNull() ?: "daily"
        val curr = currencyProvider()

        var bytes: ByteArray = ByteArray(0)
        var mime = "text/csv"
        var filename = "export.csv"
        var isEmpty = true

        runBlocking {
            if (screen == "daily") {
                val list = dao.getExpensesByDateSync(getTodayTimestamp())
                val banks = dao.getBankBalancesByDateSync(getTodayTimestamp())

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
            } else {
                val list = dao.getAccountTxByDateSync(getTodayTimestamp())
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

        if (isEmpty) return newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_PLAINTEXT, "")

        val response = newFixedLengthResponse(Response.Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong())
        response.addHeader("Content-Disposition", "attachment; filename=\"$filename\"")
        return response
    }

    private fun getTodayTimestamp(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0); calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    private fun getDayFromDate(date: Long): String = SimpleDateFormat("EEEE", Locale.getDefault()).format(java.util.Date(date))
    private fun getFileNameDate(): String = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(java.util.Date())

    // --- DASHBOARD HTML ---
    private fun getHtmlDashboard(): String {
        val curr = currencyProvider()
        val rateStr = String.format("%.3f", curr.rate)

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
                header { background:#6200EE; color:white; padding:15px 30px; display:flex; justify-content:space-between; align-items:center; }
                .app-title { font-size: 1.8rem; font-weight: bold; }
                .curr-controls select { padding:5px; border-radius:4px; border:none; margin:0 5px; font-weight:bold; color:#333; font-size:1rem; }
                      
                /* Layout */
                .nav-container { background: #fff; padding: 10px 30px; border-bottom: 1px solid #ddd; display:flex; gap: 15px; align-items:center; }
                .main-body { flex:1; display:flex; overflow:hidden; position:relative; }
                
                /* Split View for Data */
                .workspace-view { display: flex; width: 100%; height: 100%; }
                .form-panel { width: 350px; min-width: 300px; max-width: 50%; background: white; border-right: 1px solid #ddd; padding: 20px; overflow-y: auto; }
                .list-panel { flex:1; padding:20px; overflow-y: auto; }
                
                /* Full Screen Guide View */
                .guide-view { width: 100%; height: 100%; overflow-y: auto; padding: 40px; box-sizing: border-box; background: #f9f9ff; }
                .guide-content { max-width: 900px; margin: 0 auto; background: white; padding: 40px; border-radius: 8px; box-shadow: 0 4px 15px rgba(0,0,0,0.05); }
                
                /* Form Elements */
                .form-group { margin-bottom:15px; }
                label { display:block; font-size:0.9rem; color:#666; margin-bottom:5px; font-weight:600; }
                input, select { width:100%; padding:10px; border:1px solid #ccc; border-radius:4px; box-sizing:border-box; font-size:1rem; }
                button.add-btn { background:#6200EE; color:white; border:none; padding:14px; width:100%; border-radius:6px; font-weight:bold; cursor:pointer; margin-top:15px; font-size:1rem; }
                button.add-btn:hover { background: #3700b3; }
                
                /* Tabs */
                .tab { padding:10px 20px; background:#e0e0e0; border-radius:20px; cursor:pointer; font-weight:bold; color:#555; transition:0.2s; user-select: none; }
                .tab.active { background:#6200EE; color:white; }
                
                /* Tables & Summaries */
                table { width:100%; border-collapse:collapse; background:white; border-radius:8px; box-shadow:0 1px 3px rgba(0,0,0,0.1); }
                th, td { padding:15px; text-align:left; border-bottom:1px solid #eee; font-size:1rem; }
                th { background:#f8f9fa; font-weight:bold; color:#444; }
                .credit { color:green; font-weight:bold; background:#e8f5e9; padding:4px 8px; border-radius:4px; font-size:0.9rem; } 
                .debit { color:red; font-weight:bold; background:#ffebee; padding:4px 8px; border-radius:4px; font-size:0.9rem; }
                .export-bar { display: flex; justify-content: flex-end; gap: 15px; margin-bottom: 20px; }
                .btn-export { text-decoration: none; font-size: 1.1rem; padding: 10px 20px; border-radius: 6px; color: white; font-weight: bold; display: flex; align-items: center; gap: 10px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                .xls { background: #1D6F42; } .pdf { background: #D32F2F; }
                
                .hidden { display:none !important; }
                .btn-del { border:none; background:none; color:#aaa; font-size:1.4rem; cursor:pointer; }
                .btn-del:hover { color:red; }
                
                .radio-group { display: flex; gap: 15px; align-items: center; }
                .radio-item { display: flex; align-items: center; cursor: pointer; }
                .radio-item input { width: auto; margin-right: 8px; transform: scale(1.2); }
                .radio-item label { margin: 0; cursor: pointer; }
                
                /* Summary Styling */
                .summary-card { margin-top: 20px; background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
                .summary-bank { margin-bottom: 15px; border-bottom: 1px solid #eee; padding-bottom: 10px; }
                .summary-bank h4 { margin: 0 0 5px 0; color: #6200EE; font-size: 1.1rem; }
                .sum-row { display: flex; justify-content: space-between; font-size: 0.95rem; margin-bottom: 4px; }
                .grand-total { font-size: 1.2rem; font-weight: bold; text-align: right; margin-top: 15px; color: #333; }
                .link-btn { color: #6200EE; cursor: pointer; font-size: 0.9rem; font-weight: bold; text-decoration: underline; margin-left: 10px; }
                #bankForm { border: 2px dashed #6200EE; padding: 15px; background: #f9f9ff; border-radius: 8px; margin-bottom: 20px; }
                
                /* Guide Styling */
                .guide-section { margin-bottom: 30px; }
                .guide-section h2 { color: #333; margin-bottom: 20px; text-align: center; border-bottom: 3px solid #6200EE; display: inline-block; padding-bottom: 10px; }
                .guide-section h3 { color: #6200EE; border-bottom: 2px solid #eee; padding-bottom: 8px; margin-top: 0; }
                .guide-section ul { line-height: 1.8; color: #444; font-size: 1.05rem; }
                .guide-warning { background: #fff3e0; padding: 15px; border-left: 5px solid #ff9800; margin: 15px 0; color: #d84315; border-radius: 4px; }
                .dev-links { text-align: center; margin-top: 40px; padding-top: 20px; border-top: 1px solid #eee; }
                .dev-links a { display: inline-block; margin: 0 15px; color: #0077b5; font-weight: bold; text-decoration: none; font-size: 1.1rem; padding: 10px 20px; border: 1px solid #0077b5; border-radius: 5px; transition: 0.2s; }
                .dev-links a:hover { background: #0077b5; color: white; }
            </style>
            </head><body>
            <header>
                <div class="app-title">ExpenseBuilder 💻</div>
                <div class="curr-controls">
                    <select id="baseCurr" onchange="updCurr()">${currOpts(curr.base)}</select>
                    <span>&rarr;</span>
                    <select id="targetCurr" onchange="updCurr()">${currOpts(curr.target)}</select>
                    <span style="margin-left:10px; font-size:1rem; background:rgba(255,255,255,0.2); padding:5px 12px; border-radius:4px;">Rate: $rateStr</span>
                </div>
            </header>
            
            <div class="nav-container">
                <div class="tab active" id="tabD" onclick="sw('daily')">Daily Expense</div>
                <div class="tab" id="tabA" onclick="sw('acc')">Accounts</div>
                <div class="tab" id="tabG" onclick="sw('guide')">App Guide & Tips</div>
            </div>

            <div class="main-body">
                
                <div id="workspaceView" class="workspace-view">
                    <div class="form-panel">
                        <div id="bankForm" class="hidden">
                            <h4 style="margin-top:0;">Add New Bank / Source</h4>
                            <div class="form-group"><label>Bank Name</label><input id="b_name" placeholder="e.g. HDFC"></div>
                            <div class="form-group"><label>Opening Balances</label>
                                <div style="display:flex;gap:5px;">
                                    <input id="b_cash" placeholder="Cash" type="number">
                                    <input id="b_chq" placeholder="Chq" type="number">
                                    <input id="b_card" placeholder="Card" type="number">
                                </div>
                            </div>
                            <div style="display:flex; gap:10px;">
                                <button class="add-btn" style="background:#4CAF50; margin-top:0" onclick="addBank()">Save</button>
                                <button class="add-btn" style="background:#ccc; margin-top:0" onclick="toggleBankForm()">Cancel</button>
                            </div>
                        </div>

                        <h3 style="margin-top:0; font-size:1.4rem; border-bottom:2px solid #eee; padding-bottom:10px;">Add Entry</h3>
                        <div id="dailyForm">
                             <div class="form-group"><label>Person Name</label><input id="d_name"></div>
                             <div class="form-group"><label>Select Bank / Source <span class="link-btn" onclick="toggleBankForm()">[+] Add New</span></label><select id="d_bank"><option value="">Loading Banks...</option></select></div>
                             <div class="form-group"><label>Category</label><select id="d_cat"><option value="">Loading Cats...</option></select></div>
                             <div class="form-group"><label>Item Name</label><input id="d_item"></div>
                             <div class="form-group"><label>Additional Info (Optional)</label><input id="d_info"></div>
                             <div class="form-group"><label>Quantity</label><input type="number" id="d_qty"></div>
                             <div class="form-group"><label>Unit</label><select id="d_unit"><option>PIECE</option><option>KG</option><option>GRAM</option><option>LITER</option><option>ML</option><option>Not Applicable</option><option>Not Available</option></select></div>
                             <div class="form-group"><label>Price (${curr.base})</label><input type="number" id="d_price"></div>
                             <div class="form-group"><label>Type</label><select id="d_type"><option value="DEBIT">Debit</option><option value="CREDIT">Credit</option></select></div>
                             <div class="form-group">
                                <label>Payment Mode</label>
                                <div class="radio-group">
                                    <div class="radio-item"><input type="radio" name="d_mode" value="Cash" id="dm1" checked><label for="dm1">Cash</label></div>
                                    <div class="radio-item"><input type="radio" name="d_mode" value="Cheque" id="dm2"><label for="dm2">Cheque</label></div>
                                    <div class="radio-item"><input type="radio" name="d_mode" value="Card/UPI" id="dm3"><label for="dm3">Card</label></div>
                                </div>
                            </div>
                             <button class="add-btn" onclick="addD()">ADD ITEM</button>
                        </div>
                        <div id="accForm" class="hidden">
                             <div class="form-group"><label>From Holder</label><input id="a_hold"></div>
                             <div class="form-group"><label>From Bank</label><input id="a_bank"></div>
                             <div class="form-group"><label>From Acc</label><input id="a_anum"></div>
                             <div class="form-group"><label>To Beneficiary</label><input id="a_ben"></div>
                             <div class="form-group"><label>To Bank</label><input id="a_tbank"></div>
                             <div class="form-group"><label>To Acc</label><input id="a_tnum"></div>
                             <div class="form-group"><label>Amount (${curr.base})</label><input type="number" id="a_amt"></div>
                             <div class="form-group"><label>Type</label><select id="a_type"><option value="DEBIT">Debit</option><option value="CREDIT">Credit</option></select></div>
                             <div class="form-group">
                                <label>Payment Mode</label>
                                <div class="radio-group">
                                    <div class="radio-item"><input type="radio" name="a_mode" value="Cash" id="am1" checked><label for="am1">Cash</label></div>
                                    <div class="radio-item"><input type="radio" name="a_mode" value="Cheque" id="am2"><label for="am2">Cheque</label></div>
                                    <div class="radio-item"><input type="radio" name="a_mode" value="Card/UPI" id="am3"><label for="am3">Card</label></div>
                                </div>
                            </div>
                             <button class="add-btn" onclick="addA()">ADD TXN</button>
                        </div>
                    </div>

                    <div class="list-panel">
                        <div class="export-bar"><button onclick="dl('csv')" class="btn-export xls">📊 Download Excel</button><button onclick="dl('pdf')" class="btn-export pdf">📄 Download PDF</button></div>
                        <div id="listD"><table><thead><tr><th>Item</th><th>Qty</th><th>Price</th><th>Type</th><th>Mode</th><th></th></tr></thead><tbody></tbody></table><div id="summaryD" class="summary-card hidden"></div></div>
                        <div id="listA" class="hidden"><table><thead><tr><th>Details</th><th>Amount</th><th>Type</th><th>Mode</th><th></th></tr></thead><tbody></tbody></table></div>
                    </div>
                </div>

                <div id="guideView" class="guide-view hidden">
                    <div class="guide-content">
                        <div style="text-align:center;"><h2>App Use Guide & Tips</h2></div>
                        
                        <div class="guide-section">
                            <h3>1. Getting Started: Banks & Sources</h3>
                            <ul>
                                <li>Before adding expenses, you must add a <b>Bank or Money Source</b>.</li>
                                <li>Use the <b>[+] Add New</b> button next to the Bank dropdown in the form.</li>
                                <li>Enter the Opening Balance for that source (e.g., 'HDFC' with 5000 opening).</li>
                                <li>⚠️ <b>Security Check:</b> Add at least 1 entry through the mobile UI first to initialize the database securely.</li>
                            </ul>
                        </div>

                        <div class="guide-section">
                            <h3>2. Credit and Debit</h3>
                            <ul>
                                <li><b style="color:green">Credit (+)</b>: Money deposited / Money Refunded / Money Received</li>
                                <li><b style="color:red">Debit (-)</b>: Money deducted / Money Paid / Money Spent</li>
                            </ul>
                        </div>
                        
                        <div class="guide-section">
                            <h3>3. Managing Expenses</h3>
                            <ul>
                                <li>Select the specific <b>Bank</b> for every expense entry.</li>
                                <li>Use the <b>Additional Info</b> field for details like "Dinner with team" or "Taxi to Airport".</li>
                                <li>Quantity and Unit are optional but recommended for inventory tracking.</li>
                            </ul>
                        </div>

                        <div class="guide-section">
                            <h3>4. Currency & Reports (Crucial Tips)</h3>
                            <div class="guide-warning">
                                <strong>⚠️ IMPORTANT:</strong> Reports are generated based on the currently selected <b>Base Currency</b>.
                            </div>
                            <ul>
                                <li>The app updates exchange rates automatically every 24 hours.</li>
                                <li><b>Tip:</b> If you have accounts in different currencies (e.g., INR and SGD), switch the Base Currency to the relevant one before generating reports. This ensures opening balances logically match the currency context.</li>
                            </ul>
                        </div>

                        <div class="guide-section">
                            <h3>5. Web Dashboard "Catches"</h3>
                            <div class="guide-warning">
                                <strong>⚠️ REFRESH WARNING:</strong> Changing the currency using the dropdown above will <b>refresh the page</b> immediately.
                            </div>
                            <ul>
                                <li>Ensure you have saved any open form data before switching currencies.</li>
                                <li>Keep the mobile app open while using the Web Dashboard.</li>
                            </ul>
                        </div>
                        

                            <div class="dev-links">
                                <a href="https://www.linkedin.com/in/sankalp-indish/" target="_blank">LinkedIn Profile</a>
                                <a href="https://github.com/DevelopingGod" target="_blank">GitHub Repository</a>
                            </div>

                        
                        
                    </div>
                </div>

            </div>
            <script>
                document.getElementById('baseCurr').value = "${curr.base}";
                document.getElementById('targetCurr').value = "${curr.target}";
                let curS = 'daily';
                const defaults = ["Home Expenses", "Snacks & Fruit", "Utilities", "CNG/Petrol", "Assets", "Medical Expenses", "Education Expenses", "Rent", "Loans", "Others"];

                function sw(s){ curS=s; 
                    // 1. Handle Tabs
                    document.getElementById('tabD').className = s==='daily'?'tab active':'tab';
                    document.getElementById('tabA').className = s==='acc'?'tab active':'tab';
                    document.getElementById('tabG').className = s==='guide'?'tab active':'tab';

                    // 2. Handle Views
                    if (s === 'guide') {
                        document.getElementById('workspaceView').classList.add('hidden');
                        document.getElementById('guideView').classList.remove('hidden');
                    } else {
                        document.getElementById('workspaceView').classList.remove('hidden');
                        document.getElementById('guideView').classList.add('hidden');
                        
                        // Sub-toggle for daily vs account
                        document.getElementById('dailyForm').classList.toggle('hidden', s!=='daily');
                        document.getElementById('accForm').classList.toggle('hidden', s==='daily');
                        document.getElementById('listD').classList.toggle('hidden', s!=='daily');
                        document.getElementById('listA').classList.toggle('hidden', s==='daily');
                        if(s==='daily') ldD(); else ldA();
                    }
                }

                function ldD(){ 
                    Promise.all([fetch('/api/expenses').then(r=>r.json()), fetch('/api/banks').then(r=>r.json())]).then(([exps, banks]) => {
                        let h=''; exps.forEach(i=>{ 
                            let info = i.additionalInfo ? `<br><i style='color:#555;font-size:0.8rem'>(${'$'}{i.additionalInfo})</i>` : '';
                            let bank = `<br><b style='color:#00008B;font-size:0.8rem'>[${'$'}{i.bankName}]</b>`;
                            h+=`<tr><td><b>${'$'}{i.itemName}</b>${'$'}{info}${'$'}{bank}<br><small style='color:#777'>${'$'}{i.category}</small></td><td>${'$'}{i.quantity} ${'$'}{i.unit}</td><td>${'$'}{i.totalPrice}</td><td><span class="${'$'}{i.type==='CREDIT'?'credit':'debit'}">${'$'}{i.type}</span></td><td>${'$'}{i.paymentMode}</td><td style="text-align:right"><button class="btn-del" onclick="del('deleteExpense',${'$'}{i.id})">&times;</button></td></tr>`}); 
                        document.querySelector('#listD tbody').innerHTML=h||'<tr><td colspan="6" align="center">No entries</td></tr>';
                        
                        if(banks.length > 0) {
                            let s = '<h3>Closing Summary (${curr.base})</h3>';
                            let gt = 0;
                            banks.forEach(b => {
                                let bExps = exps.filter(e => e.bankName === b.bankName);
                                let calc = (m, op) => {
                                    let cr = bExps.filter(e => e.paymentMode === m && e.type === 'CREDIT').reduce((a,c)=>a+c.totalPrice,0);
                                    let dr = bExps.filter(e => e.paymentMode === m && e.type === 'DEBIT').reduce((a,c)=>a+c.totalPrice,0);
                                    return op + cr - dr;
                                };
                                let cCash = calc('Cash', b.openingCash);
                                let cChq = calc('Cheque', b.openingCheque);
                                let cCard = calc('Card/UPI', b.openingCard);
                                let tot = cCash + cChq + cCard;
                                gt += tot;
                                s += `<div class="summary-bank">
                                        <div style="display:flex; justify-content:space-between; align-items:center;">
                                            <h4 style="margin:0;">${'$'}{b.bankName}</h4>
                                            <span style="cursor:pointer; color:red; font-weight:bold;" onclick="delBank('${'$'}{b.bankName}')" title="Delete Bank">&times;</span>
                                        </div>
                                        <div class="sum-row"><span>Cash:</span><b>${'$'}{cCash.toFixed(2)}</b></div>
                                        <div class="sum-row"><span>Cheque:</span><b>${'$'}{cChq.toFixed(2)}</b></div>
                                        <div class="sum-row"><span>Card/UPI:</span><b>${'$'}{cCard.toFixed(2)}</b></div>
                                        <div class="sum-row" style="margin-top:5px;color:#6200EE"><span>Total:</span><b>${'$'}{tot.toFixed(2)}</b></div>
                                      </div>`;
                            });
                            s += `<div class="grand-total">GRAND TOTAL: ${'$'}{gt.toFixed(2)}</div>`;
                            document.getElementById('summaryD').innerHTML = s;
                            document.getElementById('summaryD').classList.remove('hidden');
                        } else { document.getElementById('summaryD').classList.add('hidden'); }
                    });
                }

                function ldA(){ fetch('/api/accounts').then(r=>r.json()).then(d=>{ 
                    let h=''; d.forEach(i=>{ h+=`<tr><td><b>${'$'}{i.beneficiaryName}</b><br><small>To: ${'$'}{i.toBankName}</small></td><td>${'$'}{i.amount}</td><td><span class="${'$'}{i.type==='CREDIT'?'credit':'debit'}">${'$'}{i.type}</span></td><td>${'$'}{i.paymentMode}</td><td style="text-align:right"><button class="btn-del" onclick="del('deleteAccount',${'$'}{i.id})">&times;</button></td></tr>`}); 
                    document.querySelector('#listA tbody').innerHTML=h||'<tr><td colspan="5" align="center">No transactions</td></tr>'; 
                }); }

                function loadCats() { fetch('/api/categories').then(r=>r.json()).then(dbCats=>{ 
                    let allCats = [...new Set([...defaults, ...dbCats])].sort();
                    let h='<option value="">Select Category...</option>'; 
                    allCats.forEach(c=>{ h+=`<option value="${'$'}{c}">${'$'}{c}</option>` }); 
                    document.getElementById('d_cat').innerHTML=h; 
                }); }

                function loadBanks() { fetch('/api/banks').then(r=>r.json()).then(banks=>{ 
                    let h=''; 
                    if(banks.length === 0) h='<option value="">No Banks Added</option>';
                    else banks.forEach(b=>{ h+=`<option value="${'$'}{b.bankName}">${'$'}{b.bankName}</option>` }); 
                    document.getElementById('d_bank').innerHTML=h; 
                }); }

                function toggleBankForm() { document.getElementById('bankForm').classList.toggle('hidden'); }

                function addBank() {
                    if(!v('b_name')) return alert("Enter Bank Name");
                    post('/api/addBank', { bankName:v('b_name'), opCash:v('b_cash'), opCheque:v('b_chq'), opCard:v('b_card') }, () => {
                        toggleBankForm();
                        loadBanks(); 
                        ldD();
                    });
                }

                function delBank(name) {
                    if(confirm("Are you sure you want to delete Bank '" + name + "' and its opening balance?")) {
                        post('/api/deleteBank', {bankName: name}, () => {
                            loadBanks(); 
                            ldD(); 
                        });
                    }
                }

                function addD(){ 
                    if(!v('d_name')||!v('d_bank')||!v('d_cat')||!v('d_item')||!v('d_price')) return alert("Fill all fields! (Including Bank)"); 
                    post('/api/addExpense', { 
                        personName:v('d_name'), bankName:v('d_bank'), additionalInfo:v('d_info'),
                        category:v('d_cat'), itemName:v('d_item'), quantity:v('d_qty'), unit:v('d_unit'), price:v('d_price'), type:v('d_type'), paymentMode:radio('d_mode') 
                    }, ldD); 
                }
                function addA(){ if(!v('a_hold')||!v('a_ben')||!v('a_amt')) return alert("Fill all fields!"); post('/api/addAccount', { holder:v('a_hold'), bank:v('a_bank'), accNum:v('a_anum'), benName:v('a_ben'), toBank:v('a_tbank'), toAccNum:v('a_tnum'), amount:v('a_amt'), type:v('a_type'), paymentMode:radio('a_mode') }, ldA); }
                
                function updCurr(){ post('/api/setCurrency', { base:v('baseCurr'), target:v('targetCurr') }, () => location.reload()); }
                function dl(t){ let u=`/api/export?type=${'$'}{t}&screen=${'$'}{curS}`; fetch(u).then(r=>{ if(r.status===204) alert('No data!'); else window.location.href=u; }); }
                
                function post(u,d,cb){ fetch(u,{method:'POST', body:JSON.stringify(d)}).then(cb); }
                function del(u,id){ if(confirm('Delete?')) post('/api/'+u, {id:id}, curS==='daily'?ldD:ldA); }
                function v(id){ return document.getElementById(id).value; }
                function radio(name){ return document.querySelector(`input[name="${'$'}{name}"]:checked`).value; }

                ldD(); loadCats(); loadBanks();
            </script></body></html>
        """.trimIndent()
    }
}