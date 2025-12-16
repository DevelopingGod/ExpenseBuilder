package com.sankalp.expensebuilder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.sankalp.expensebuilder.ui.MainScreen
import com.sankalp.expensebuilder.viewmodel.ExpenseViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val viewModel: ExpenseViewModel by viewModels()
        setContent {
            MainScreen(viewModel = viewModel)
        }
    }
}