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

    // ================= GENERATORS =================

    // --- UPDATED: Multi-Bank CSV Generation ---
    fun generateDailyCsv(items: List<ExpenseItem>, banks: List<DailyBankBalance>, baseCurr: String, targetCurr: String, rate: Double): ByteArray {
        val sb = StringBuilder()
        val name = getPersonName(items)
        val dateStr = convertDate(items.firstOrNull()?.date ?: System.currentTimeMillis())

        sb.append("Daily Expense Report\n")
        sb.append("Person Name,$name\n")
        sb.append("Date,$dateStr\n")
        sb.append("Rate,1 $baseCurr = $rate $targetCurr\n\n")

        // LOOP THROUGH EACH BANK
        banks.forEach { bank ->
            sb.append("BANK SOURCE: ${bank.bankName}\n")
            sb.append("Opening Cash,${bank.openingCash}\n")
            sb.append("Opening Cheque,${bank.openingCheque}\n")
            sb.append("Opening Card,${bank.openingCard}\n")

            // Added "Additional Info" column
            sb.append("Category,Item Name,Additional Info,Qty,Unit,Price ($baseCurr),Price ($targetCurr),Type,Mode\n")

            // Filter items belonging ONLY to this bank
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

    // --- UPDATED: Multi-Bank PDF with Margins & Wrapping ---
    fun generateDailyPdf(items: List<ExpenseItem>, banks: List<DailyBankBalance>, baseCurr: String, targetCurr: String, rate: Double): ByteArray {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas
        val paint = Paint()

        // MARGIN SETTINGS (A4 width is roughly 595)
        val marginLeft = 40f
        val marginRight = 550f
        val contentWidth = marginRight - marginLeft
        var y = 50f

        fun checkPageBreak() {
            if (y > 780f) {
                pdfDocument.finishPage(page)
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                y = 50f
            }
        }

        // HELPER: Wraps text if it exceeds width
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

        y = drawMultiLineText("Daily Report ($baseCurr)", marginLeft, contentWidth, Color.BLACK, 16f, true)
        y += 10f
        y = drawMultiLineText("Name: $name | Date: $dateStr", marginLeft, contentWidth, Color.BLACK, 12f, false)
        y = drawMultiLineText("Rate: 1 $baseCurr = $rate $targetCurr", marginLeft, contentWidth, Color.DKGRAY, 12f, false)
        y += 20f

        // LOOP THROUGH BANKS
        banks.forEach { bank ->
            checkPageBreak()

            // Bank Header
            paint.color = Color.parseColor("#00008B") // Dark Blue
            paint.strokeWidth = 2f
            canvas.drawLine(marginLeft, y, marginRight, y, paint)
            y += 15f

            y = drawMultiLineText("BANK: ${bank.bankName}", marginLeft, contentWidth, Color.parseColor("#00008B"), 14f, true)
            y = drawMultiLineText("Op Cash: ${bank.openingCash} | Op Chq: ${bank.openingCheque} | Op Card: ${bank.openingCard}", marginLeft, contentWidth, Color.BLACK, 11f, false)
            y += 15f

            val bankItems = items.filter { it.bankName == bank.bankName }
            var cCr = 0.0; var cDr = 0.0; var qCr = 0.0; var qDr = 0.0; var dCr = 0.0; var dDr = 0.0

            bankItems.groupBy { it.category }.forEach { (category, catItems) ->
                checkPageBreak()
                y = drawMultiLineText(category, marginLeft, contentWidth, Color.BLUE, 13f, true)

                catItems.forEach { item ->
                    checkPageBreak()
                    val converted = item.totalPrice * rate
                    val sym = if(item.type == TransactionType.CREDIT) "(+)" else "(-)"

                    // Format: ItemName (Info) | Qty Unit | Mode
                    val infoStr = if(item.additionalInfo.isNotBlank()) "(${item.additionalInfo})" else ""
                    val mainText = "${item.itemName} $infoStr | ${item.quantity} ${item.unit} | [${item.paymentMode}]"
                    val priceText = "$baseCurr ${item.totalPrice}  /  $targetCurr ${String.format("%.2f", converted)} $sym"

                    // Draw Main Text (Wrapped)
                    y = drawMultiLineText(mainText, marginLeft + 10f, 350f, Color.BLACK, 10f, false)

                    // Draw Price (Aligned to right side manually)
                    paint.color = if(item.type == TransactionType.CREDIT) Color.parseColor("#006400") else Color.RED
                    paint.textAlign = Paint.Align.RIGHT
                    canvas.drawText(priceText, marginRight, y - 5f, paint) // Draw at end of previous line height
                    paint.textAlign = Paint.Align.LEFT // Reset

                    y += 5f

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
            canvas.drawLine(marginLeft, y, marginRight, y, paint)
            y += 15f

            y = drawMultiLineText("CLOSING SUMMARY (${bank.bankName}):", marginLeft, contentWidth, Color.BLACK, 12f, true)
            y = drawMultiLineText("Cash: $clCash", marginLeft, contentWidth, Color.BLACK, 11f, false)
            y = drawMultiLineText("Cheque: $clChq", marginLeft, contentWidth, Color.BLACK, 11f, false)
            y = drawMultiLineText("Card: $clCard", marginLeft, contentWidth, Color.BLACK, 11f, false)
            y += 30f // Space between banks
        }

        pdfDocument.finishPage(page)
        val out = ByteArrayOutputStream()
        pdfDocument.writeTo(out)
        pdfDocument.close()
        return out.toByteArray()
    }

    // --- Account Exports (Standard) ---
    // No specific multi-bank grouping needed here as Account Transactions already have explicit "From Bank" and "To Bank" fields.

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

    // ================= SAVERS (Updated to accept Bank List) =================

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