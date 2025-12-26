package com.sankalp.expensebuilder.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppGuideScreen() {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "User Guide & Tips",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- SECTION 1 ---
        item {
            GuideSection(
                title = "1. Getting Started: Banks",
                icon = Icons.Default.AccountBalance,
                content = buildAnnotatedString {
                    append("Before adding expenses, you must add at least one ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Bank or Money Source") }
                    append(" (e.g., 'HDFC', 'Wallet Cash').\n\n")
                    append("• Go to ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Daily Expense") }
                    append(" tab.\n")
                    append("• Click ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("'+ Add Bank'") }
                    append(".\n")
                    append("• Enter the ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Opening Balance") }
                    append(" for that source.\n\n")

                    // Security Warning
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)) {
                        append("⚠️ IMPORTANT: ")
                    }
                    append("Add at least ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("1 entry") }
                    append(" through the mobile UI first. This initializes the database security so the Web UI can work.")
                }
            )
        }

        // --- SECTION 2 ---
        item {
            GuideSection(
                title = "2. Credit vs Debit",
                icon = Icons.Default.CurrencyExchange,
                content = buildAnnotatedString {
                    append("• ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))) { append("Credit (+)") } // Green
                    append(": Money deposited, Refunded, or Received.\n")
                    append("• ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFC62828))) { append("Debit (-)") } // Red
                    append(": Money deducted, Paid, or Spent.")
                }
            )
        }

        // --- SECTION 3 ---
        item {
            GuideSection(
                title = "3. Daily Expenses",
                icon = Icons.Default.ShoppingCart,
                content = buildAnnotatedString {
                    append("• Select the specific ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Bank Source") }
                    append(" for every expense.\n")
                    append("• Use ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("'Additional Info'") }
                    append(" (e.g., 'Dinner with team') for better tracking.\n")
                    append("• ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Quantity") }
                    append(" and ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Unit") }
                    append(" are optional but helpful for inventory.")
                }
            )
        }

        // --- SECTION 4 ---
        item {
            GuideSection(
                title = "4. Currency & Reports",
                icon = Icons.Default.Public,
                content = buildAnnotatedString {
                    append("• The app supports ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("live currency conversion") }
                    append(" (Rates update every 24 hours).\n")
                    append("• ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("CRUCIAL: ") }
                    append("Reports are generated based on the ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)) { append("currently selected Base Currency") }
                    append(".\n")
                    append("• ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("TIP: ") }
                    append("If you maintain accounts in INR and SGD, switch the Base Currency to INR before exporting INR reports, and vice-versa.")
                }
            )
        }

        // --- SECTION 5 ---
        item {
            GuideSection(
                title = "5. WiFi PC Dashboard",
                icon = Icons.Default.Wifi,
                content = buildAnnotatedString {
                    append("• Click the ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("WiFi icon") }
                    append(" to start the server.\n")
                    append("• Type the ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("IP address") }
                    append(" in your laptop browser (Phone & Laptop must be on ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("same WiFi") }
                    append(").\n")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)) {
                        append("• WARNING: ")
                    }
                    append("Changing currency on Web UI will ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("refresh the page") }
                    append(". Save unsaved data first.")
                }
            )
        }

        // --- SECTION 6 ---
        item {
            GuideSection(
                title = "6. Exports",
                icon = Icons.Default.FileDownload,
                content = buildAnnotatedString {
                    append("• Download ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Excel (.csv)") }
                    append(" or ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("PDF") }
                    append(" reports.\n")
                    append("• Reports include a ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Closing Summary") }
                    append(" calculated per Bank.")
                }
            )
        }

        // --- FOOTER & LINKS ---
        item {
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(20.dp))

            Text("Developer Links [Sankalp S. Indish]", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            // LinkedIn Button
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.linkedin.com/in/sankalp-indish/"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0077B5)) // LinkedIn Blue
            ) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect on LinkedIn", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // GitHub Button
            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/DevelopingGod"))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF24292E)) // GitHub Black
            ) {
                Icon(Icons.Default.Code, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Check code on GitHub", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(50.dp))
        }
    }
}

@Composable
fun GuideSection(title: String, icon: ImageVector, content: AnnotatedString) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(content, style = MaterialTheme.typography.bodyMedium, lineHeight = 22.sp)
        }
    }
}