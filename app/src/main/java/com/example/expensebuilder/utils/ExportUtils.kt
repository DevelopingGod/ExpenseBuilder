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

    private fun getOpeningBalances(items: List<ExpenseItem>): Triple<Double, Double, Double> {
        val item = items.firstOrNull { it.openingCash > 0 || it.openingCheque > 0 || it.openingCard > 0 }
        return Triple(item?.openingCash ?: 0.0, item?.openingCheque ?: 0.0, item?.openingCard ?: 0.0)
    }

    private fun getFileNameDate(): String = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault()).format(Date())
    private fun convertDate(timestamp: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))

    // ================= GENERATORS (Return Bytes for Laptop & Mobile) =================

    fun generateDailyCsv(items: List<ExpenseItem>, baseCurr: String, targetCurr: String, rate: Double): ByteArray {
        val sb = StringBuilder()
        val name = getPersonName(items)
        val (opCash, opCheque, opCard) = getOpeningBalances(items)
        val dateStr = convertDate(items.firstOrNull()?.date ?: System.currentTimeMillis())

        sb.append("Daily Expense Report\n")
        sb.append("Person Name,$name\n")
        sb.append("Date,$dateStr\n")
        sb.append("Rate,1 $baseCurr = $rate $targetCurr\n")
        sb.append("Opening Cash,$opCash $baseCurr\n")
        sb.append("Opening Cheque,$opCheque $baseCurr\n")
        sb.append("Opening Card/UPI,$opCard $baseCurr\n\n")

        sb.append("Category,Item Name,Qty,Unit,Price ($baseCurr),Price ($targetCurr),Type,Mode\n")

        var cashCr = 0.0; var cashDr = 0.0; var chequeCr = 0.0; var chequeDr = 0.0; var cardCr = 0.0; var cardDr = 0.0

        items.groupBy { it.category }.forEach { (category, catItems) ->
            var catTotal = 0.0
            catItems.forEach { item ->
                val converted = String.format("%.2f", item.totalPrice * rate)
                val safeItemName = item.itemName.replace(",", " ")
                sb.append("$category,$safeItemName,${item.quantity},${item.unit},${item.totalPrice},$converted,${item.type},${item.paymentMode}\n")
                catTotal += item.totalPrice
                if (item.type == TransactionType.CREDIT) {
                    when(item.paymentMode) { "Cash" -> cashCr += item.totalPrice; "Cheque" -> chequeCr += item.totalPrice; "Card/UPI" -> cardCr += item.totalPrice }
                } else {
                    when(item.paymentMode) { "Cash" -> cashDr += item.totalPrice; "Cheque" -> chequeDr += item.totalPrice; "Card/UPI" -> cardDr += item.totalPrice }
                }
            }
            sb.append(",Subtotal ($category),,,$catTotal,,,\n")
        }

        val closeCash = opCash + cashCr - cashDr
        val closeCheque = opCheque + chequeCr - chequeDr
        val closeCard = opCard + cardCr - cardDr
        val grandTotal = closeCash + closeCheque + closeCard

        sb.append("\nCLOSING SUMMARY ($baseCurr)\n")
        sb.append("Mode,Opening,Credit (+),Debit (-),Closing\n")
        sb.append("Cash,$opCash,$cashCr,$cashDr,$closeCash\n")
        sb.append("Cheque,$opCheque,$chequeCr,$chequeDr,$closeCheque\n")
        sb.append("Card/UPI,$opCard,$cardCr,$cardDr,$closeCard\n")
        sb.append(",,,,GRAND TOTAL: $grandTotal\n")

        return sb.toString().toByteArray()
    }

    fun generateDailyPdf(items: List<ExpenseItem>, baseCurr: String, targetCurr: String, rate: Double): ByteArray {
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
        val (opCash, opCheque, opCard) = getOpeningBalances(items)

        paint.textSize = 14f; paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Daily Report ($baseCurr)", 50f, y, paint); y += 20f
        paint.textSize = 12f; paint.typeface = Typeface.DEFAULT
        canvas.drawText("Name: $name", 50f, y, paint); y += 15f
        canvas.drawText("Date: ${convertDate(items.firstOrNull()?.date ?: System.currentTimeMillis())}", 50f, y, paint); y += 15f
        canvas.drawText("Rate: 1 $baseCurr = $rate $targetCurr", 50f, y, paint); y += 20f

        paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Opening Balances:", 50f, y, paint); y += 15f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Cash: $opCash | Cheque: $opCheque | Card: $opCard", 50f, y, paint); y += 25f

        var cashCr = 0.0; var cashDr = 0.0; var chequeCr = 0.0; var chequeDr = 0.0; var cardCr = 0.0; var cardDr = 0.0

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

                if (item.type == TransactionType.CREDIT) {
                    when(item.paymentMode) { "Cash" -> cashCr += item.totalPrice; "Cheque" -> chequeCr += item.totalPrice; "Card/UPI" -> cardCr += item.totalPrice }
                } else {
                    when(item.paymentMode) { "Cash" -> cashDr += item.totalPrice; "Cheque" -> chequeDr += item.totalPrice; "Card/UPI" -> cardDr += item.totalPrice }
                }
                y += 15f
            }
            y += 10f
        }

        checkPageBreak(); y += 10f
        val closeCash = opCash + cashCr - cashDr
        val closeCheque = opCheque + chequeCr - chequeDr
        val closeCard = opCard + cardCr - cardDr
        val grandTotal = closeCash + closeCheque + closeCard

        paint.textSize = 12f; paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("CLOSING SUMMARY ($baseCurr)", 50f, y, paint); y += 20f
        paint.typeface = Typeface.DEFAULT
        canvas.drawText("Cash: $closeCash", 50f, y, paint); y += 15f
        canvas.drawText("Cheque: $closeCheque", 50f, y, paint); y += 15f
        canvas.drawText("Card/UPI: $closeCard", 50f, y, paint); y += 20f
        paint.textSize = 14f; paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("GRAND TOTAL: $grandTotal", 50f, y, paint)

        pdfDocument.finishPage(page)
        val out = ByteArrayOutputStream()
        pdfDocument.writeTo(out)
        pdfDocument.close()
        return out.toByteArray()
    }

    fun generateAccountCsv(items: List<AccountTransaction>, baseCurr: String, targetCurr: String, rate: Double): ByteArray {
        val sb = StringBuilder()
        val dateStr = convertDate(items.firstOrNull()?.date ?: System.currentTimeMillis())
        sb.append("Account Transactions\n")
        sb.append("Date,$dateStr\n")
        sb.append("Rate,1 $baseCurr = $rate $targetCurr\n\n")
        sb.append("From Holder,From Bank,From Acc,To Beneficiary,To Bank,To Acc,Amt ($baseCurr),Amt ($targetCurr),Type,Mode\n")

        items.forEach { item ->
            val converted = String.format("%.2f", item.amount * rate)
            sb.append("${item.accountHolder},${item.bankName},'${item.accountNumber},${item.beneficiaryName},${item.toBankName},'${item.toAccountNumber},${item.amount},$converted,${item.type},${item.paymentMode}\n")
        }
        return sb.toString().toByteArray()
    }

    fun generateAccountPdf(items: List<AccountTransaction>, baseCurr: String, targetCurr: String, rate: Double): ByteArray {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint()
        var y = 50f
        val dateStr = convertDate(items.firstOrNull()?.date ?: System.currentTimeMillis())

        paint.textSize = 18f; paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("Account Tx ($baseCurr)", 50f, y, paint); y += 25f
        paint.textSize = 14f; canvas.drawText("Date: $dateStr", 50f, y, paint); y += 20f
        paint.textSize = 12f; canvas.drawText("Rate: 1 $baseCurr = $rate $targetCurr", 50f, y, paint); y += 40f
        paint.textSize = 10f; paint.typeface = Typeface.DEFAULT

        items.forEach { item ->
            if (y > 720f) { pdfDocument.finishPage(page); page = pdfDocument.startPage(pageInfo); canvas = page.canvas; y = 50f }

            val converted = item.amount * rate
            val sign = if(item.type == TransactionType.CREDIT) "+" else "-"

            paint.color = Color.DKGRAY
            canvas.drawText("FROM: ${item.accountHolder} | ${item.bankName} | ${item.accountNumber}", 50f, y, paint)
            y += 15f
            canvas.drawText("TO:   ${item.beneficiaryName} | ${item.toBankName} | ${item.toAccountNumber}", 50f, y, paint)
            y += 15f

            paint.color = if(item.type == TransactionType.CREDIT) Color.parseColor("#006400") else Color.BLACK
            paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("$sign $baseCurr ${item.amount}  =>  $targetCurr ${String.format("%.2f", converted)}  [${item.paymentMode}]", 50f, y, paint)

            paint.color = Color.BLACK; paint.typeface = Typeface.DEFAULT
            y += 20f; canvas.drawLine(50f, y, 500f, y, paint); y += 15f
        }
        pdfDocument.finishPage(page)
        val out = ByteArrayOutputStream()
        pdfDocument.writeTo(out)
        pdfDocument.close()
        return out.toByteArray()
    }

    // ================= SAVERS (For Mobile App Button Clicks) =================

    fun exportDailyToExcel(context: Context, items: List<ExpenseItem>, base: String, target: String, rate: Double) {
        if(items.isEmpty()) return
        val bytes = generateDailyCsv(items, base, target, rate)
        saveFileToDownloads(context, "Daily_Expense_${base}_${getFileNameDate()}.csv", "text/csv") { it.write(bytes) }
    }

    fun exportDailyToPdf(context: Context, items: List<ExpenseItem>, base: String, target: String, rate: Double) {
        if(items.isEmpty()) return
        val bytes = generateDailyPdf(items, base, target, rate)
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