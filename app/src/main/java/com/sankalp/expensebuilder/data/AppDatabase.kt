package com.sankalp.expensebuilder.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.*
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Database(entities = [ExpenseItem::class, AccountTransaction::class, DailyBankBalance::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_builder_db"
                )
                    .fallbackToDestructiveMigration() // Handle version upgrade by resetting
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}