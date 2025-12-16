package com.sankalp.expensebuilder.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

enum class TransactionType {
    @SerializedName("DEBIT") DEBIT,
    @SerializedName("CREDIT") CREDIT
}

enum class UnitType {
    @SerializedName("PIECE") PIECE,
    @SerializedName("KG") KG,
    @SerializedName("GRAM") GRAM,
    @SerializedName("LITER") LITER,
    @SerializedName("ML") ML
}

@Entity(tableName = "expenses")
data class ExpenseItem(
    @PrimaryKey(autoGenerate = true)
    @SerializedName("id") val id: Int = 0,

    @SerializedName("date") val date: Long,
    @SerializedName("day") val day: String,
    @SerializedName("personName") val personName: String,

    @SerializedName("openingCash") val openingCash: Double,
    @SerializedName("openingCheque") val openingCheque: Double,
    @SerializedName("openingCard") val openingCard: Double,

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

    // FROM details
    @SerializedName("accountHolder") val accountHolder: String,
    @SerializedName("bankName") val bankName: String,
    @SerializedName("accountNumber") val accountNumber: String,

    // TO details
    @SerializedName("beneficiaryName") val beneficiaryName: String,
    @SerializedName("toBankName") val toBankName: String,
    @SerializedName("toAccountNumber") val toAccountNumber: String,

    @SerializedName("amount") val amount: Double,
    @SerializedName("type") val type: TransactionType,
    @SerializedName("paymentMode") val paymentMode: String
)