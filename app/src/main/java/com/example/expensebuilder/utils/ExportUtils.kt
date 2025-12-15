package com.example.expensebuilder.utils

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
import com.example.expensebuilder.data.AccountTransaction
import com.example.expensebuilder.data.ExpenseItem
import com.example.expensebuilder.data.TransactionType
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

    // --- HELPER: Find valid metadata ---
    private fun getPersonName(items: List<ExpenseItem>): String {
        return items.firstOrNull { it.personName.isNotBlank() }?.personName ?: "Unknown"
    }

    private fun getOpeningBalance(items: List<ExpenseItem>): Double {
        return items.firstOrNull { it.openingBalance > 0.0 }?.openingBalance ?: 0.0
    }

    // ================= DAILY EXPORT (CSV) =================
    // (This part is working fine, keeping it same)
    fun exportDailyToExcel(context: Context, items: List<ExpenseItem>, baseCurr: String, targetCurr: String, rate: Double) {
        if (items.isEmpty()) { showToast(context, "No data to export"); return }
        try {
            val sb = StringBuilder()
            val name = getPersonName(items)
            val openingBal = getOpeningBalance(items)
            val dateStr = convertDate(items.first().date)

            sb.append("Daily Expense Report\n")
            sb.append("Person Name,$name\n")
            sb.append("Date,$dateStr\n")
            sb.append("Rate,1 $baseCurr = $rate $targetCurr\n")
            sb.append("Opening Balance,$openingBal $baseCurr\n\n")
            sb.append("Category,Item Name,Qty,Unit,Price ($baseCurr),Price ($targetCurr),Type,Mode\n")

            var totalCredit = 0.0
            var totalDebit = 0.0
            val grouped = items.groupBy { it.category }

            grouped.forEach { (category, catItems) ->
                var catTotal = 0.0
                catItems.forEach { item ->
                    val converted = String.format("%.2f", item.totalPrice * rate)
                    val safeItemName = item.itemName.replace(",", " ")
                    sb.append("$category,$safeItemName,${item.quantity},${item.unit},${item.totalPrice},$converted,${item.type},${item.paymentMode}\n")

                    catTotal += item.totalPrice
                    if (item.type == TransactionType.CREDIT) totalCredit += item.totalPrice
                    else totalDebit += item.totalPrice
                }
                sb.append(",Subtotal ($category),,,$catTotal,,,\n")
            }

            val closing = openingBal + totalCredit - totalDebit
            val closingConv = String.format("%.2f", closing * rate)

            sb.append("\nSUMMARY\n")
            sb.append(",Total Credit (+),,,$totalCredit\n")
            sb.append(",Total Debit (-),,,$totalDebit\n")
            sb.append(",CLOSING BALANCE ($baseCurr),,,$closing\n")
            sb.append(",CLOSING BALANCE ($targetCurr),,,$closingConv\n")

            val fileName = "Daily_Expense_${baseCurr}_${getFileNameDate()}.csv"
            saveFileToDownloads(context, fileName, "text/csv") { it.write(sb.toString().toByteArray()) }

        } catch (e: Exception) { e.printStackTrace(); showToast(context, "Export Failed: ${e.message}") }
    }

    // ================= DAILY PDF EXPORT =================
    // (Working fine)
    fun exportDailyToPdf(context: Context, items: List<ExpenseItem>, baseCurr: String, targetCurr: String, rate: Double) {
        if (items.isEmpty()) { showToast(context, "No data to export"); return }
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            val paint = Paint()
            var y = 50f

            fun checkPageBreak() {
                if (y > 780f) { pdfDocument.finishPage(page); page = pdfDocument.startPage(pageInfo); canvas = page.canvas; y = 50f }
            }

            val name = getPersonName(items)
            val openingBal = getOpeningBalance(items)

            paint.textSize = 14f; paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("Daily Report ($baseCurr)", 50f, y, paint); y += 20f
            paint.textSize = 12f; paint.typeface = Typeface.DEFAULT
            canvas.drawText("Name: $name", 50f, y, paint); y += 15f
            canvas.drawText("Date: ${convertDate(items.first().date)}", 50f, y, paint); y += 15f
            canvas.drawText("Rate: 1 $baseCurr = $rate $targetCurr", 50f, y, paint); y += 15f
            canvas.drawText("Open Bal: $baseCurr $openingBal", 50f, y, paint); y += 25f

            var totalCredit = 0.0; var totalDebit = 0.0
            items.groupBy { it.category }.forEach { (category, catItems) ->
                checkPageBreak(); paint.color = Color.BLUE; paint.textSize = 14f
                canvas.drawText(category, 50f, y, paint); y += 20f
                paint.color = Color.BLACK; paint.textSize = 10f

                catItems.forEach { item ->
                    checkPageBreak()
                    val converted = item.totalPrice * rate
                    val sym = if(item.type == TransactionType.CREDIT) "(+)" else "(-)"
                    val line = "${item.itemName} | ${item.quantity} ${item.unit} | $baseCurr ${item.totalPrice} | $targetCurr ${String.format("%.2f", converted)} $sym | [${item.paymentMode}]"
                    canvas.drawText(line, 60f, y, paint)
                    if (item.type == TransactionType.CREDIT) totalCredit += item.totalPrice else totalDebit += item.totalPrice
                    y += 15f
                }
                y += 10f
            }

            checkPageBreak(); y += 10f
            val closing = openingBal + totalCredit - totalDebit
            val closingConv = closing * rate
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("CLOSING ($baseCurr): $closing", 50f, y, paint); y += 15f
            canvas.drawText("CLOSING ($targetCurr): ${String.format("%.2f", closingConv)}", 50f, y, paint)

            pdfDocument.finishPage(page)
            val fileName = "Daily_Expense_${baseCurr}_${getFileNameDate()}.pdf"
            saveFileToDownloads(context, fileName, "application/pdf") { pdfDocument.writeTo(it) }
            pdfDocument.close()
        } catch (e: Exception) { e.printStackTrace(); showToast(context, "PDF Failed") }
    }

    // ================= ACCOUNTS EXPORT (CSV) =================
    // (Working fine)
    fun exportAccountsToExcel(context: Context, items: List<AccountTransaction>, baseCurr: String, targetCurr: String, rate: Double) {
        if (items.isEmpty()) { showToast(context, "No data to export"); return }
        try {
            val sb = StringBuilder()
            sb.append("Account Transactions\n")
            sb.append("Rate,1 $baseCurr = $rate $targetCurr\n\n")
            sb.append("From Holder,From Bank,From Acc,To Beneficiary,To Bank,To Acc,Amt ($baseCurr),Amt ($targetCurr),Type,Mode\n")

            items.forEach { item ->
                val converted = String.format("%.2f", item.amount * rate)
                sb.append("${item.accountHolder},${item.bankName},'${item.accountNumber},${item.beneficiaryName},${item.toBankName},'${item.toAccountNumber},${item.amount},$converted,${item.type},${item.paymentMode}\n")
            }

            val fileName = "Accounts_${baseCurr}_${getFileNameDate()}.csv"
            saveFileToDownloads(context, fileName, "text/csv") { it.write(sb.toString().toByteArray()) }
        } catch (e: Exception) { e.printStackTrace(); showToast(context, "Export Failed") }
    }

    // ================= ACCOUNTS PDF EXPORT (UPDATED) =================
    // FIX: Now explicitly shows Account Numbers and Bank Names
    fun exportAccountsToPdf(context: Context, items: List<AccountTransaction>, baseCurr: String, targetCurr: String, rate: Double) {
        if (items.isEmpty()) { showToast(context, "No data to export"); return }
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas
            val paint = Paint()
            var y = 50f

            paint.textSize = 18f; paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("Account Tx ($baseCurr)", 50f, y, paint); y += 20f
            paint.textSize = 12f; canvas.drawText("Rate: 1 $baseCurr = $rate $targetCurr", 50f, y, paint); y += 40f
            paint.textSize = 10f; paint.typeface = Typeface.DEFAULT

            items.forEach { item ->
                // Check page break logic (approx 4 lines per item + spacing)
                if (y > 720f) { pdfDocument.finishPage(page); page = pdfDocument.startPage(pageInfo); canvas = page.canvas; y = 50f }

                val converted = item.amount * rate
                val sign = if(item.type == TransactionType.CREDIT) "+" else "-"

                // Line 1: From Details
                paint.color = Color.DKGRAY
                canvas.drawText("FROM: ${item.accountHolder} | ${item.bankName} | ${item.accountNumber}", 50f, y, paint)
                y += 15f

                // Line 2: To Details
                canvas.drawText("TO:   ${item.beneficiaryName} | ${item.toBankName} | ${item.toAccountNumber}", 50f, y, paint)
                y += 15f

                // Line 3: Amount & Mode
                paint.color = if(item.type == TransactionType.CREDIT) Color.parseColor("#006400") else Color.BLACK
                paint.typeface = Typeface.DEFAULT_BOLD
                canvas.drawText("$sign $baseCurr ${item.amount}  =>  $targetCurr ${String.format("%.2f", converted)}  [${item.paymentMode}]", 50f, y, paint)

                paint.color = Color.BLACK
                paint.typeface = Typeface.DEFAULT

                y += 20f
                canvas.drawLine(50f, y, 500f, y, paint) // Divider line
                y += 15f
            }
            pdfDocument.finishPage(page)
            val fileName = "Accounts_${baseCurr}_${getFileNameDate()}.pdf"
            saveFileToDownloads(context, fileName, "application/pdf") { pdfDocument.writeTo(it) }
            pdfDocument.close()
        } catch (e: Exception) { e.printStackTrace(); showToast(context, "PDF Failed") }
    }

    // --- SHARED HELPERS ---
    private fun getFileNameDate(): String = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault()).format(Date())
    private fun convertDate(timestamp: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))

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