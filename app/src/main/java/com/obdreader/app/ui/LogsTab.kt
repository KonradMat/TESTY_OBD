package com.obdreader.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LogsTab(logMessages: List<String>) {
    LazyColumn(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding      = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (logMessages.isEmpty()) {
            item {
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Brak wpisów w logu", color = TextSecondary)
                }
            }
        }
        items(logMessages) { msg ->
            Text(
                msg,
                fontSize   = 12.sp,
                color      = when {
                    msg.contains("Błąd") || msg.contains("Nie udało") -> AccentRed
                    msg.contains("Połączono") || msg.contains("SUKCES") -> AccentGreen
                    msg.contains("Skanowanie") -> AccentBlue
                    else -> TextSecondary
                },
                fontFamily = FontFamily.Monospace,
                modifier   = Modifier.fillMaxWidth()
            )
        }
    }
}