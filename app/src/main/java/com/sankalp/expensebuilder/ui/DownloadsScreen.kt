package com.sankalp.expensebuilder.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sankalp.expensebuilder.viewmodel.DownloadedFile
import com.sankalp.expensebuilder.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DownloadsScreen(viewModel: ExpenseViewModel) {
    val allFiles by viewModel.downloadedFiles.collectAsState()
    val context = LocalContext.current

    // 0: Daily, 1: Accounts, 2: Others (Monthly)
    var selectedCategory by remember { mutableIntStateOf(0) }
    var showRenameDialog by remember { mutableStateOf<DownloadedFile?>(null) }

    // Auto-refresh list when screen opens
    LaunchedEffect(Unit) {
        viewModel.fetchDownloadedFiles()
    }

    // Filter files based on the selected tab
    val displayFiles = allFiles.filter {
        when(selectedCategory) {
            0 -> it.name.startsWith("Daily_", ignoreCase = true)
            1 -> it.name.startsWith("Accounts_", ignoreCase = true)
            // Others includes Monthly reports and anything else not matching the above
            else -> !it.name.startsWith("Daily_", ignoreCase = true) && !it.name.startsWith("Accounts_", ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Downloads",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            // FIX 3: Clear records ONLY for the currently selected tab
            TextButton(onClick = {
                val prefix = when(selectedCategory) {
                    0 -> "Daily_"
                    1 -> "Accounts_"
                    else -> "Others"
                }
                viewModel.clearTabRecords(prefix)
            }) {
                Text("Clear List (Hide)")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Category Tabs
        TabRow(selectedTabIndex = selectedCategory) {
            Tab(selected = selectedCategory == 0, onClick = { selectedCategory = 0 }, text = { Text("Daily") })
            Tab(selected = selectedCategory == 1, onClick = { selectedCategory = 1 }, text = { Text("Accounts") })
            Tab(selected = selectedCategory == 2, onClick = { selectedCategory = 2 }, text = { Text("Others") })
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (displayFiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FilePresent, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(8.dp))
                    Text("No files in this category.", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                }
            }
        } else {
            LazyColumn {
                items(displayFiles) { file ->
                    FileRow(
                        file = file,
                        onOpen = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    // Robust MIME type check for PDF and CSV/Excel
                                    val mimeType = if(file.name.endsWith(".pdf", true)) "application/pdf" else "text/csv"
                                    setDataAndType(file.uri, mimeType)
                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NO_HISTORY
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onRename = { showRenameDialog = file },
                        onDelete = { viewModel.deleteFile(file) }, // Actual Delete from phone
                        onHide = { viewModel.hideFileRecord(file) } // Clear record from list
                    )
                }
                // Spacer at bottom to avoid FAB overlap
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // Rename Dialog Overlay
    if (showRenameDialog != null) {
        RenameDialog(
            file = showRenameDialog!!,
            currentTab = selectedCategory, // Pass current tab index to enforce prefix
            onDismiss = { showRenameDialog = null },
            onConfirm = { newName ->
                viewModel.renameFile(showRenameDialog!!, newName)
                showRenameDialog = null
            }
        )
    }
}

@Composable
fun FileRow(file: DownloadedFile, onOpen: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit, onHide: () -> Unit) {
    val dateStr = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(file.dateAdded))

    // Choose icon based on file type
    val icon = if (file.name.endsWith(".pdf", true)) Icons.Default.Description else Icons.Default.TableChart
    val iconColor = if (file.name.endsWith(".pdf", true)) Color(0xFFD32F2F) else Color(0xFF388E3C) // Red for PDF, Green for CSV

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onOpen() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(file.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(dateStr, style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
            }

            // Actions Row
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, "Rename", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
            IconButton(onClick = onHide) {
                Icon(Icons.Default.VisibilityOff, "Hide", tint = Color.Gray)
            }
        }
    }
}

// FIX 2: Smart Rename (Sticky Tabs)
@Composable
fun RenameDialog(file: DownloadedFile, currentTab: Int, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var newName by remember { mutableStateOf(file.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename File") },
        text = {
            Column {
                Text("Enter new name:", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Note: Tab prefix (e.g., 'Daily_') will be auto-added if missing to keep the file in this tab.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (newName.isNotBlank()) {
                    // Logic to ensure file stays in the current tab
                    var finalName = newName
                    val prefix = when(currentTab) {
                        0 -> "Daily_"
                        1 -> "Accounts_"
                        // For "Others", we primarily check for Monthly reports
                        2 -> if(file.name.startsWith("Monthly_", true)) "Monthly_" else ""
                        else -> ""
                    }

                    // If user removed the prefix, put it back
                    if (prefix.isNotEmpty() && !finalName.startsWith(prefix, true)) {
                        finalName = prefix + finalName
                    }

                    // Maintain original extension
                    if (file.name.endsWith(".pdf", true) && !finalName.endsWith(".pdf", true)) {
                        finalName += ".pdf"
                    } else if (file.name.endsWith(".csv", true) && !finalName.endsWith(".csv", true)) {
                        finalName += ".csv"
                    }

                    onConfirm(finalName)
                }
            }) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}