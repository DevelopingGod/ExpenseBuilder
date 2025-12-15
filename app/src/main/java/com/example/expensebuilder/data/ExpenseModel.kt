package com.example.expensebuilder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TransactionType {
    DEBIT, CREDIT
}

enum class UnitType {
    PIECE, KG, GRAM, LITER, ML
}

@Entity(tableName = "expenses")
data class ExpenseItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val day: String,
    val personName: String,
    val openingBalance: Double,
    val category: String,
    val itemName: String,
    val quantity: Double,
    val unit: UnitType,
    val pricePerUnit: Double,
    val totalPrice: Double,
    val type: TransactionType,
    val paymentMode: String // "Cash", "Cheque", or "Card/UPI"
)

@Entity(tableName = "accounts")
data class AccountTransaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val day: String,

    // FROM details
    val accountHolder: String,
    val bankName: String,
    val accountNumber: String,

    // TO details (New Fields)
    val beneficiaryName: String,
    val toBankName: String,
    val toAccountNumber: String,

    val amount: Double,
    val type: TransactionType,

    val paymentMode: String // "Cash", "Cheque", or "Card/UPI"
)