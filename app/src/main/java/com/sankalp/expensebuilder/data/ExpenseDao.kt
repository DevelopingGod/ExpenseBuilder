package com.sankalp.expensebuilder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    // --- Expenses ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseItem)

    @Delete
    suspend fun deleteExpense(expense: ExpenseItem)

    @Query("SELECT * FROM expenses WHERE date = :date ORDER BY id DESC")
    fun getExpensesByDate(date: Long): Flow<List<ExpenseItem>>

    @Query("SELECT * FROM expenses WHERE date = :date ORDER BY id DESC")
    suspend fun getExpensesByDateSync(date: Long): List<ExpenseItem>

    // History Tab Range Query
    @Query("SELECT * FROM expenses WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC, id DESC")
    fun getExpensesByDateRange(startDate: Long, endDate: Long): Flow<List<ExpenseItem>>

    // --- Categories ---
    @Query("SELECT DISTINCT category FROM expenses ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    @Query("SELECT DISTINCT category FROM expenses ORDER BY category ASC")
    suspend fun getAllCategoriesSync(): List<String>

    @Query("SELECT DISTINCT itemName FROM expenses WHERE category = :category AND itemName LIKE '%' || :query || '%'")
    suspend fun getItemSuggestions(category: String, query: String): List<String>

    // --- Bank Balances ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBankBalance(balance: DailyBankBalance)

    @Delete
    suspend fun deleteBankBalance(balance: DailyBankBalance)

    @Query("SELECT * FROM bank_balances WHERE date = :date ORDER BY bankName ASC")
    fun getBankBalancesByDate(date: Long): Flow<List<DailyBankBalance>>

    @Query("SELECT * FROM bank_balances WHERE date = :date ORDER BY bankName ASC")
    suspend fun getBankBalancesByDateSync(date: Long): List<DailyBankBalance>

    // --- Account Tx ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccountTx(tx: AccountTransaction)

    @Delete
    suspend fun deleteAccountTx(tx: AccountTransaction)

    @Query("SELECT * FROM accounts WHERE date = :date ORDER BY id DESC")
    fun getAccountTxByDate(date: Long): Flow<List<AccountTransaction>>

    @Query("SELECT * FROM accounts WHERE date = :date ORDER BY id DESC")
    suspend fun getAccountTxByDateSync(date: Long): List<AccountTransaction>

    // NEW: For History Tab (Range Query for Accounts)
    @Query("SELECT * FROM accounts WHERE date BETWEEN :startDate AND :endDate ORDER BY date DESC, id DESC")
    fun getAccountTxByDateRange(startDate: Long, endDate: Long): Flow<List<AccountTransaction>>
}