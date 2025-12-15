package com.example.expensebuilder.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    // --- Daily Expense Operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseItem)

    @Delete
    suspend fun deleteExpense(expense: ExpenseItem)

    // Get expenses for a specific date
    @Query("SELECT * FROM expenses WHERE date = :date ORDER BY id DESC")
    fun getExpensesByDate(date: Long): Flow<List<ExpenseItem>>

    // "LEARNING" FEATURES:
    // 1. Get all unique categories ever entered (for the dropdown)
    @Query("SELECT DISTINCT category FROM expenses ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    // 2. Get unique item names based on a category (Auto-suggestion logic)
    @Query("SELECT DISTINCT itemName FROM expenses WHERE category = :category AND itemName LIKE '%' || :query || '%'")
    suspend fun getItemSuggestions(category: String, query: String): List<String>

    // --- Account Transaction Operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccountTx(tx: AccountTransaction)

    // --- NEW: Delete Account Transaction ---
    @Delete
    suspend fun deleteAccountTx(tx: AccountTransaction)

    @Query("SELECT * FROM accounts WHERE date = :date ORDER BY id DESC")
    fun getAccountTxByDate(date: Long): Flow<List<AccountTransaction>>
}