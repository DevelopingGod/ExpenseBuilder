package com.sankalp.expensebuilder.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import com.sankalp.expensebuilder.data.AccountTransaction
import com.sankalp.expensebuilder.data.DailyBankBalance
import com.sankalp.expensebuilder.data.ExpenseItem
import com.sankalp.expensebuilder.data.TransactionType
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportUtils {

    private fun showToast(context: Context, message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun getPersonName(items: List<ExpenseItem>): String {
        return items.firstOrNull { it.personName.isNotBlank() }?.personName ?: "Unknown"
    }

    private fun getFileNameDate(): String = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault()).format(Date())
    private fun convertDate(timestamp: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))

    // Helper to draw Footer on every page
    private fun drawFooter(canvas: android.graphics.Canvas, pageWidth: Int, pageHeight: Int) {
        val paint = Paint()
        paint.color = Color.GRAY
        paint.textSize = 10f
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        canvas.drawText("Downloaded From ExpenseBuilder", (pageWidth / 2).toFloat(), (pageHeight - 20).toFloat(), paint)
    }

    // ================= GENERATORS =================

    // --- Daily CSV (Unchanged) ---
    fun generateDailyCsv(items: List<ExpenseItem>, banks: List<DailyBankBalance>, baseCurr: String, targetCurr: String, rate: Double): ByteArray {
        val sb = StringBuilder()
        val name = getPersonName(items)
        val dateStr = convertDate(items.firstOrNull()?.date ?: System.currentTimeMillis())

        sb.append("Daily Expense Report\n")
        sb.append("Person Name,$name\n")
        sb.append("Date,$dateStr\n")
        sb.append("Rate,1 $baseCurr = $rate $targetCurr\n\n")

        banks.forEach { bank ->
            sb.append("BANK SOURCE: ${bank.bankName}\n")
            sb.append("Opening Cash,${bank.openingCash}\n")
            sb.append("Opening Cheque,${bank.openingCheque}\n")
            sb.append("Opening Card,${bank.openingCard}\n")
            sb.append("Category,Item Name,Additional Info,Qty,Unit,Price ($baseCurr),Price ($targetCurr),Type,Mode\n")

            val bankItems = items.filter { it.bankName == bank.bankName }
            var cCr = 0.0; var cDr = 0.0; var qCr = 0.0; var qDr = 0.0; var dCr = 0.0; var dDr = 0.0

            bankItems.groupBy { it.category }.forEach { (category, catItems) ->
                catItems.forEach { item ->
                    val converted = String.format("%.2f", item.totalPrice * rate)
                    val safeItem = item.itemName.replace(",", " ")
                    val safeInfo = item.additionalInfo.replace(",", " ")

                    sb.append("$category,$safeItem,$safeInfo,${item.quantity},${item.unit},${item.totalPrice},$converted,${item.type},${item.paymentMode}\n")

                    if(item.type == TransactionType.CREDIT) {
                        when(item.paymentMode) { "Cash"->cCr+=item.totalPrice; "Cheque"->qCr+=item.totalPrice; "Card/UPI"->dCr+=item.totalPrice }
                    } else {
                        when(item.paymentMode) { "Cash"->cDr+=item.totalPrice; "Cheque"->qDr+=item.totalPrice; "Card/UPI"->dDr+=item.totalPrice }
                    }
                }
            }

            val clCash = bank.openingCash + cCr - cDr
            val clChq = bank.openingCheque + qCr - qDr
            val clCard = bank.openingCard + dCr - dDr

            sb.append(",CLOSING SUMMARY (${bank.bankName}),,,,\n")
            sb.append(",Cash,,Closing:,$clCash\n")
            sb.append(",Cheque,,Closing:,$clChq\n")
            sb.append(",Card,,Closing:,$clCard\n")
            sb.append("\n------------------------------------------------\n\n")
        }
        return sb.toString().toByteArray()
    }

    // --- UPDATED: Daily PDF ---
    fun generateDailyPdf(items: List<ExpenseItem>, banks: List<DailyBankBalance>, baseCurr: String, targetCurr: String, rate: Double): ByteArray {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint()

        val marginLeft = 40f
        val marginRight = 550f
        val contentWidth = marginRight - marginLeft
        var y = 50f

        fun checkPageBreak() {
            if (y > 780f) {
                drawFooter(canvas, 595, 842)
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
            }
        }

        fun drawMultiLineText(text: String, x: Float, maxWidth: Float, color: Int, size: Float, isBold: Boolean): Float {
            paint.color = color
            paint.textSize = size
            paint.typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT

            val words = text.split(" ")
            var line = ""
            var currentY = y

            for (word in words) {
                val testLine = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(testLine) < maxWidth) {
                    line = testLine
                } else {
                    canvas.drawText(line, x, currentY, paint)
                    currentY += (size + 5f)
                    line = word
                }
            }
            if (line.isNotEmpty()) {
                canvas.drawText(line, x, currentY, paint)
                currentY += (size + 5f)
            }
            return currentY
        }

        val name = getPersonName(items)
        val dateStr = convertDate(items.firstOrNull()?.date ?: System.currentTimeMillis())

        // Header Title
        paint.color = Color.parseColor("#6200EE")
        paint.textSize = 20f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Daily Expense Report ($baseCurr)", marginLeft, y, paint)
        y += 25f

        // Metadata Box
        paint.color = Color.LTGRAY
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        canvas.drawRect(marginLeft, y, marginRight, y + 45f, paint)
        paint.style = Paint.Style.FILL

        y += 20f
        paint.color = Color.BLACK
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Name: $name", marginLeft + 10f, y, paint)
        canvas.drawText("Date: $dateStr", marginLeft + 250f, y, paint)
        y += 18f
        canvas.drawText("Rate: 1 $baseCurr = $rate $targetCurr", marginLeft + 10f, y, paint)
        y += 30f

        banks.forEach { bank ->
            checkPageBreak()

            // Bank Header
            paint.color = Color.parseColor("#E8EAF6")
            canvas.drawRect(marginLeft, y, marginRight, y + 25f, paint)

            paint.color = Color.parseColor("#1A237E")
            paint.textSize = 14f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("BANK: ${bank.bankName}", marginLeft + 10f, y + 18f, paint)
            y += 35f

            // Opening Balances
            paint.color = Color.DKGRAY
            paint.textSize = 11f
            paint.typeface = Typeface.DEFAULT
            canvas.drawText("Op Cash: ${bank.openingCash} | Op Chq: ${bank.openingCheque} | Op Card: ${bank.openingCard}", marginLeft + 10f, y, paint)
            y += 20f

            // Divider Line with Extra Spacing
            paint.color = Color.LTGRAY
            paint.strokeWidth = 1f
            canvas.drawLine(marginLeft, y, marginRight, y, paint)
            y += 20f // Increased spacing as requested

            val bankItems = items.filter { it.bankName == bank.bankName }
            var cCr = 0.0; var cDr = 0.0; var qCr = 0.0; var qDr = 0.0; var dCr = 0.0; var dDr = 0.0

            bankItems.groupBy { it.category }.forEach { (category, catItems) ->
                checkPageBreak()
                // Category Header: "Category - Others" style
                y = drawMultiLineText("Category - $category", marginLeft, contentWidth, Color.parseColor("#004D40"), 13f, true)
                y += 5f

                catItems.forEach { item ->
                    checkPageBreak()
                    val converted = item.totalPrice * rate
                    val sym = if(item.type == TransactionType.CREDIT) "(+)" else "(-)"

                    val infoStr = if(item.additionalInfo.isNotBlank()) "(${item.additionalInfo})" else ""
                    val mainText = "• ${item.itemName} $infoStr | ${item.quantity} ${item.unit} | [${item.paymentMode}]"
                    val priceText = "$baseCurr ${item.totalPrice}  /  $targetCurr ${String.format("%.2f", converted)}"

                    // Item Name (Left)
                    y = drawMultiLineText(mainText, marginLeft + 10f, 320f, Color.BLACK, 11f, false)

                    // Price (Right)
                    paint.color = if(item.type == TransactionType.CREDIT) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
                    paint.textAlign = Paint.Align.RIGHT
                    paint.typeface = Typeface.DEFAULT_BOLD
                    canvas.drawText("$priceText $sym", marginRight, y - 5f, paint)
                    paint.textAlign = Paint.Align.LEFT

                    y += 8f

                    if(item.type == TransactionType.CREDIT) {
                        when(item.paymentMode) { "Cash"->cCr+=item.totalPrice; "Cheque"->qCr+=item.totalPrice; "Card/UPI"->dCr+=item.totalPrice }
                    } else {
                        when(item.paymentMode) { "Cash"->cDr+=item.totalPrice; "Cheque"->qDr+=item.totalPrice; "Card/UPI"->dDr+=item.totalPrice }
                    }
                }
                y += 10f
            }

            checkPageBreak()
            val clCash = bank.openingCash + cCr - cDr
            val clChq = bank.openingCheque + qCr - qDr
            val clCard = bank.openingCard + dCr - dDr

            y += 5f
            paint.color = Color.BLACK
            paint.strokeWidth = 2f
            canvas.drawLine(marginLeft, y, marginRight, y, paint)
            y += 20f

            paint.color = Color.BLACK
            paint.textSize = 12f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("CLOSING SUMMARY (${bank.bankName}):", marginLeft, y, paint)
            y += 20f

            paint.typeface = Typeface.DEFAULT
            canvas.drawText("Cash Closing: $clCash", marginLeft + 20f, y, paint)
            y += 15f
            canvas.drawText("Cheque Closing: $clChq", marginLeft + 20f, y, paint)
            y += 15f
            canvas.drawText("Card Closing: $clCard", marginLeft + 20f, y, paint)
            y += 40f
        }

        drawFooter(canvas, 595, 842)
        pdfDocument.finishPage(page)
        val out = ByteArrayOutputStream()
        pdfDocument.writeTo(out)
        pdfDocument.close()
        return out.toByteArray()
    }

    // --- Account CSV (Unchanged) ---
    fun generateAccountCsv(items: List<AccountTransaction>, baseCurr: String, targetCurr: String, rate: Double): ByteArray {
        val sb = StringBuilder()
        val dateStr = convertDate(items.firstOrNull()?.date ?: System.currentTimeMillis())
        sb.append("Account Transactions\nDate,$dateStr\nRate,1 $baseCurr = $rate $targetCurr\n\n")
        sb.append("From Holder,From Bank,From Acc,To Beneficiary,To Bank,To Acc,Amt ($baseCurr),Amt ($targetCurr),Type,Mode\n")
        items.forEach { item ->
            val converted = String.format("%.2f", item.amount * rate)
            sb.append("${item.accountHolder},${item.bankName},'${item.accountNumber},${item.beneficiaryName},${item.toBankName},'${item.toAccountNumber},${item.amount},$converted,${item.type},${item.paymentMode}\n")
        }
        return sb.toString().toByteArray()
    }

    // --- UPDATED: Account PDF (Added Acc Numbers) ---
    fun generateAccountPdf(items: List<AccountTransaction>, baseCurr: String, targetCurr: String, rate: Double): ByteArray {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint()
        var y = 50f
        val dateStr = convertDate(items.firstOrNull()?.date ?: System.currentTimeMillis())

        // Header
        paint.color = Color.parseColor("#0277BD")
        paint.textSize = 20f
        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Account Transactions ($baseCurr)", 40f, y, paint)
        y += 25f

        paint.color = Color.BLACK
        paint.textSize = 12f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Date: $dateStr  |  Rate: 1 $baseCurr = $rate $targetCurr", 40f, y, paint)
        y += 40f

        items.forEach { item ->
            if (y > 750f) {
                drawFooter(canvas, 595, 842)
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas; y = 50f
            }

            val converted = item.amount * rate
            val sign = if(item.type == TransactionType.CREDIT) "+" else "-"

            // Transaction Box
            paint.color = Color.parseColor("#F5F5F5")
            canvas.drawRect(40f, y - 15f, 550f, y + 45f, paint)

            // From -> To (Added Account Numbers)
            paint.color = Color.DKGRAY
            paint.textSize = 11f
            paint.typeface = Typeface.DEFAULT

            val fromText = "FROM: ${item.accountHolder} | ${item.bankName} | Acc: ${item.accountNumber}"
            val toText = "TO: ${item.beneficiaryName} | ${item.toBankName} | Acc: ${item.toAccountNumber}"

            canvas.drawText(fromText, 50f, y, paint)
            y += 15f
            canvas.drawText(toText, 50f, y, paint)
            y -= 15f // Reset Y for amount calculation

            // Amount & Mode
            paint.color = if(item.type == TransactionType.CREDIT) Color.parseColor("#2E7D32") else Color.BLACK
            paint.textSize = 12f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("$sign $baseCurr ${item.amount}", 450f, y, paint)

            paint.color = Color.GRAY
            paint.textSize = 10f
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("[${item.paymentMode}]", 450f, y + 15f, paint)

            y += 60f // Spacing between items
        }

        drawFooter(canvas, 595, 842)
        pdfDocument.finishPage(page)
        val out = ByteArrayOutputStream()
        pdfDocument.writeTo(out)
        pdfDocument.close()
        return out.toByteArray()
    }

    // --- NEW: Monthly Export (Combined) ---
    fun exportMonthlyToExcel(context: Context, exps: List<ExpenseItem>, accs: List<AccountTransaction>, base: String, target: String, rate: Double, month: String) {
        val sb = StringBuilder()
        sb.append("MONTHLY REPORT: $month\n\n--- DAILY EXPENSES ---\nDate,Category,Item,Bank,Price ($base),Type,Mode\n")
        exps.forEach { e -> sb.append("${convertDate(e.date)},${e.category},${e.itemName.replace(",", " ")},${e.bankName},${e.totalPrice},${e.type},${e.paymentMode}\n") }
        sb.append("\n--- ACCOUNT TRANSACTIONS ---\nDate,From,To,Amount ($base),Type,Mode\n")
        accs.forEach { a -> sb.append("${convertDate(a.date)},${a.accountHolder},${a.beneficiaryName},${a.amount},${a.type},${a.paymentMode}\n") }
        saveFileToDownloads(context, "Monthly_Report_$month.csv", "text/csv") { it.write(sb.toString().toByteArray()) }
    }

    // --- UPDATED: Monthly PDF with Footer ---
    fun exportMonthlyToPdf(context: Context, exps: List<ExpenseItem>, accs: List<AccountTransaction>, base: String, target: String, rate: Double, month: String) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint()
        var y = 50f

        paint.textSize = 22f; paint.typeface = Typeface.DEFAULT_BOLD; paint.color = Color.parseColor("#4527A0")
        canvas.drawText("MONTHLY REPORT: $month", 40f, y, paint); y+=30f
        paint.textSize = 12f; paint.color = Color.BLACK; paint.typeface = Typeface.DEFAULT
        canvas.drawText("Total Expenses: ${exps.size} | Account Tx: ${accs.size}", 40f, y, paint); y+=40f

        paint.color = Color.parseColor("#1565C0"); paint.typeface = Typeface.DEFAULT_BOLD; paint.textSize = 14f
        canvas.drawText("--- DAILY EXPENSES ---", 40f, y, paint); y+=25f

        exps.forEach { e ->
            if(y > 800) {
                drawFooter(canvas, 595, 842)
                pdfDocument.finishPage(page); page = pdfDocument.startPage(pageInfo); canvas = page.canvas; y=50f
            }
            paint.color = Color.BLACK; paint.typeface = Typeface.DEFAULT; paint.textSize = 11f
            canvas.drawText("${convertDate(e.date)} | ${e.itemName}", 40f, y, paint)

            paint.color = if(e.type == TransactionType.CREDIT) Color.parseColor("#2E7D32") else Color.RED
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${e.totalPrice}", 550f, y, paint)
            paint.textAlign = Paint.Align.LEFT
            y+=18f
        }
        y+=30f

        if(y > 750) {
            drawFooter(canvas, 595, 842)
            pdfDocument.finishPage(page); page = pdfDocument.startPage(pageInfo); canvas = page.canvas; y=50f
        }
        paint.color = Color.parseColor("#1565C0"); paint.typeface = Typeface.DEFAULT_BOLD; paint.textSize = 14f
        canvas.drawText("--- ACCOUNT TX ---", 40f, y, paint); y+=25f

        accs.forEach { a ->
            if(y > 800) {
                drawFooter(canvas, 595, 842)
                pdfDocument.finishPage(page); page = pdfDocument.startPage(pageInfo); canvas = page.canvas; y=50f
            }
            paint.color = Color.BLACK; paint.typeface = Typeface.DEFAULT; paint.textSize = 11f
            canvas.drawText("${convertDate(a.date)} | ${a.accountHolder} -> ${a.beneficiaryName}", 40f, y, paint)

            paint.color = if(a.type == TransactionType.CREDIT) Color.parseColor("#2E7D32") else Color.BLACK
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("${a.amount}", 550f, y, paint)
            paint.textAlign = Paint.Align.LEFT
            y+=18f
        }

        drawFooter(canvas, 595, 842)
        pdfDocument.finishPage(page)
        val out = ByteArrayOutputStream()
        pdfDocument.writeTo(out)
        pdfDocument.close()
        saveFileToDownloads(context, "Monthly_Report_$month.pdf", "application/pdf") { it.write(out.toByteArray()) }
    }

    // ================= SAVERS =================

    fun exportDailyToExcel(context: Context, items: List<ExpenseItem>, banks: List<DailyBankBalance>, base: String, target: String, rate: Double) {
        val bytes = generateDailyCsv(items, banks, base, target, rate)
        saveFileToDownloads(context, "Daily_Expense_${base}_${getFileNameDate()}.csv", "text/csv") { it.write(bytes) }
    }

    fun exportDailyToPdf(context: Context, items: List<ExpenseItem>, banks: List<DailyBankBalance>, base: String, target: String, rate: Double) {
        val bytes = generateDailyPdf(items, banks, base, target, rate)
        saveFileToDownloads(context, "Daily_Expense_${base}_${getFileNameDate()}.pdf", "application/pdf") { it.write(bytes) }
    }

    fun exportAccountsToExcel(context: Context, items: List<AccountTransaction>, base: String, target: String, rate: Double) {
        if(items.isEmpty()) return
        val bytes = generateAccountCsv(items, base, target, rate)
        saveFileToDownloads(context, "Accounts_${base}_${getFileNameDate()}.csv", "text/csv") { it.write(bytes) }
    }

    fun exportAccountsToPdf(context: Context, items: List<AccountTransaction>, base: String, target: String, rate: Double) {
        if(items.isEmpty()) return
        val bytes = generateAccountPdf(items, base, target, rate)
        saveFileToDownloads(context, "Accounts_${base}_${getFileNameDate()}.pdf", "application/pdf") { it.write(bytes) }
    }

    private fun saveFileToDownloads(context: Context, fileName: String, mimeType: String, writeBlock: (OutputStream) -> Unit) {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Downloads.EXTERNAL_CONTENT_URI else MediaStore.Files.getContentUri("external")
        val uri = context.contentResolver.insert(collection, contentValues)
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { writeBlock(it) }
            showToast(context, "Saved: $fileName")
        } else {
            showToast(context, "Failed to save")
        }
    }
}