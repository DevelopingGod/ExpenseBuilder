package com.sankalp.expensebuilder.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Database
import androidx.room.RoomDatabase
import com.google.gson.annotations.SerializedName

enum class TransactionType {
    @SerializedName("DEBIT") DEBIT,
    @SerializedName("CREDIT") CREDIT
}

enum class UnitType {
    @SerializedName("NOT APPLICABLE") NOT_APPLICABLE,
    @SerializedName("NOT AVAILABLE") NOT_AVAILABLE,
    @SerializedName("PIECE") PIECE,
    @SerializedName("KG") KG,
    @SerializedName("GRAM") GRAM,
    @SerializedName("LITER") LITER,
    @SerializedName("ML") ML
}

// NEW: Stores opening balance for a specific bank on a specific date
@Entity(tableName = "bank_balances", primaryKeys = ["date", "bankName"])
data class DailyBankBalance(
    @SerializedName("date") val date: Long,
    @SerializedName("bankName") val bankName: String,
    @SerializedName("openingCash") val openingCash: Double,
    @SerializedName("openingCheque") val openingCheque: Double,
    @SerializedName("openingCard") val openingCard: Double
)

@Entity(tableName = "expenses")
data class ExpenseItem(
    @PrimaryKey(autoGenerate = true)
    @SerializedName("id") val id: Int = 0,

    @SerializedName("date") val date: Long,
    @SerializedName("day") val day: String,
    @SerializedName("personName") val personName: String,

    // NEW: Expenses are now tied to a bank
    @SerializedName("bankName") val bankName: String,

    // NEW: Additional Info
    @SerializedName("additionalInfo") val additionalInfo: String = "",

    // Kept for backward compatibility, but we will mostly rely on DailyBankBalance table now
    @SerializedName("openingCash") val openingCash: Double = 0.0,
    @SerializedName("openingCheque") val openingCheque: Double = 0.0,
    @SerializedName("openingCard") val openingCard: Double = 0.0,

    @SerializedName("category") val category: String,
    @SerializedName("itemName") val itemName: String,
    @SerializedName("quantity") val quantity: Double,
    @SerializedName("unit") val unit: UnitType,
    @SerializedName("pricePerUnit") val pricePerUnit: Double,
    @SerializedName("totalPrice") val totalPrice: Double,
    @SerializedName("type") val type: TransactionType,
    @SerializedName("paymentMode") val paymentMode: String
)

@Entity(tableName = "accounts")
data class AccountTransaction(
    @PrimaryKey(autoGenerate = true)
    @SerializedName("id") val id: Int = 0,
    @SerializedName("date") val date: Long,
    @SerializedName("day") val day: String,
    @SerializedName("accountHolder") val accountHolder: String,
    @SerializedName("bankName") val bankName: String,
    @SerializedName("accountNumber") val accountNumber: String,
    @SerializedName("beneficiaryName") val beneficiaryName: String,
    @SerializedName("toBankName") val toBankName: String,
    @SerializedName("toAccountNumber") val toAccountNumber: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("type") val type: TransactionType,
    @SerializedName("paymentMode") val paymentMode: String
)