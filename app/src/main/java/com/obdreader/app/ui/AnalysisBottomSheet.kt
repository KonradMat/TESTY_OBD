package com.obdreader.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdreader.app.viewmodel.AnalysisIssue
import com.obdreader.app.viewmodel.AnalysisState
import com.obdreader.app.viewmodel.SessionAnalysis

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisBottomSheet(
    state: AnalysisState,
    onDismiss: () -> Unit,
    onDeleteAnalysis: () -> Unit,
    onRetry: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest  = onDismiss,
        sheetState        = sheetState,
        containerColor    = Color(0xFF0D1320),
        dragHandle        = {
            Box(
                modifier         = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(TextSecondary.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (state) {
                is AnalysisState.Loading -> AnalysisLoadingContent(sessionId = state.sessionId)
                is AnalysisState.Success -> AnalysisSuccessContent(
                    analysis         = state.analysis,
                    onDeleteAnalysis = onDeleteAnalysis
                )
                is AnalysisState.Error   -> AnalysisErrorContent(
                    message  = state.message,
                    onRetry  = onRetry,
                    onDismiss = onDismiss
                )
                is AnalysisState.Idle    -> { /* nie powinno się otworzyć */ }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// LOADING
// ─────────────────────────────────────────────────────────────

@Composable
private fun AnalysisLoadingContent(sessionId: String) {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator(color = AccentGreen, modifier = Modifier.size(48.dp), strokeWidth = 3.dp)
        Text("Analizuję dane sesji…", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Text(sessionId, fontSize = 11.sp, color = TextSecondary)
        Text(
            "AI przegląda dane OBD2 i szuka nieprawidłowości.\nTo może potrwać kilka sekund.",
            fontSize = 13.sp, color = TextSecondary, lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────
// SUCCESS
// ─────────────────────────────────────────────────────────────

@Composable
private fun AnalysisSuccessContent(
    analysis: SessionAnalysis,
    onDeleteAnalysis: () -> Unit
) {
    val dateFormatted = remember(analysis.createdAt) {
        try { analysis.createdAt.take(16).replace("T", " ") } catch (e: Exception) { analysis.createdAt }
    }

    val errorCount   = analysis.issues.count { it.severity.uppercase() == "ERROR" }
    val warningCount = analysis.issues.count { it.severity.uppercase() == "WARNING" }
    val okCount      = analysis.issues.count { it.severity.uppercase() !in setOf("ERROR", "WARNING") }

    // Nagłówek
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier         = Modifier.size(40.dp).clip(CircleShape).background(AccentGreen.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ManageSearch, null, tint = AccentGreen, modifier = Modifier.size(22.dp))
            }
            Column {
                Text("Analiza AI", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(dateFormatted, fontSize = 11.sp, color = TextSecondary)
            }
        }
        // Przycisk usuń
        IconButton(
            onClick  = onDeleteAnalysis,
            modifier = Modifier.size(36.dp).clip(CircleShape)
                .background(AccentRed.copy(0.08f))
                .border(1.dp, AccentRed.copy(0.25f), CircleShape)
        ) {
            Icon(Icons.Default.Delete, null, tint = AccentRed, modifier = Modifier.size(16.dp))
        }
    }

    // Podsumowanie liczb
    if (analysis.issues.isNotEmpty()) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (errorCount > 0)   SeverityBadge(count = errorCount,   label = "Błędy",      color = AccentRed,    icon = Icons.Default.Error,        modifier = Modifier.weight(1f))
            if (warningCount > 0) SeverityBadge(count = warningCount, label = "Ostrzeżenia", color = AccentOrange, icon = Icons.Default.Warning,       modifier = Modifier.weight(1f))
            if (okCount > 0)      SeverityBadge(count = okCount,      label = "OK",          color = AccentGreen,  icon = Icons.Default.CheckCircle,   modifier = Modifier.weight(1f))
        }
    }

    Divider(color = TextSecondary.copy(0.1f))

    // Podsumowanie tekstowe
    Text("PODSUMOWANIE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.2.sp)
    Text(
        analysis.summary,
        fontSize   = 13.sp,
        color      = TextPrimary.copy(alpha = 0.9f),
        lineHeight = 20.sp
    )

    // Issues
    if (analysis.issues.isNotEmpty()) {
        Divider(color = TextSecondary.copy(0.1f))
        Text("WYNIKI DIAGNOSTYKI", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.2.sp)
        analysis.issues.forEach { issue ->
            AnalysisIssueCard(issue = issue)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AccentGreen.copy(0.07f))
                .border(1.dp, AccentGreen.copy(0.25f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(24.dp))
            Text("Nie wykryto żadnych problemów", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AccentGreen)
        }
    }
}

@Composable
private fun SeverityBadge(
    count: Int,
    label: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(0.08f))
            .border(1.dp, color.copy(0.3f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Column {
            Text("$count", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 10.sp, color = color.copy(0.8f))
        }
    }
}

@Composable
private fun AnalysisIssueCard(issue: AnalysisIssue) {
    val (color, icon, severityLabel) = when (issue.severity.uppercase()) {
        "ERROR"   -> Triple(AccentRed,    Icons.Default.Error,        "BŁĄD")
        "WARNING" -> Triple(AccentOrange, Icons.Default.Warning,      "OSTRZEŻENIE")
        else      -> Triple(AccentGreen,  Icons.Default.CheckCircle,  "OK")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.06f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Text(issue.component, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1f))
            Text(
                severityLabel,
                fontSize   = 10.sp,
                fontWeight = FontWeight.Bold,
                color      = color,
                modifier   = Modifier
                    .background(color.copy(0.12f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Text(issue.description, fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp)
    }
}

// ─────────────────────────────────────────────────────────────
// ERROR
// ─────────────────────────────────────────────────────────────

@Composable
private fun AnalysisErrorContent(
    message: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier         = Modifier.size(56.dp).clip(CircleShape).background(AccentRed.copy(0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.ErrorOutline, null, tint = AccentRed, modifier = Modifier.size(28.dp))
        }
        Text("Błąd analizy", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        Text(message, fontSize = 13.sp, color = TextSecondary, lineHeight = 20.sp)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onDismiss,
                shape   = RoundedCornerShape(10.dp),
                border  = BorderStroke(1.dp, TextSecondary.copy(0.3f)),
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
            ) { Text("Zamknij") }
            Button(
                onClick = onRetry,
                shape   = RoundedCornerShape(10.dp),
                colors  = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp), tint = Color.Black)
                Spacer(Modifier.width(6.dp))
                Text("Spróbuj ponownie", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}