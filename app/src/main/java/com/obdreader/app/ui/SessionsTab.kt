package com.obdreader.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdreader.app.auth.AuthManager
import com.obdreader.app.obd.TelemetryUploader
import com.obdreader.app.viewmodel.AnalysisState
import com.obdreader.app.viewmodel.MergedSession

// ─────────────────────────────────────────────────────────────
// SESSIONS TAB
// ─────────────────────────────────────────────────────────────

@Composable
fun SessionsTab(
    mergedSessions: List<MergedSession>,
    isLoadingSessions: Boolean,
    isLogging: Boolean,
    uploadStatus: TelemetryUploader.UploadStatus,
    backendUrl: String,
    pendingRetryCount: Int,
    // Vehicle management
    vehicles: List<AuthManager.Vehicle>,
    selectedVehicleId: Int?,
    isLoggedIn: Boolean,
    isLoadingVehicles: Boolean,
    vehicleError: String?,
    isAddingVehicle: Boolean,
    addVehicleError: String?,
    showAddDialog: Boolean,
    vehicleToDelete: AuthManager.Vehicle?,
    isDeletingVehicle: Boolean,
    vehicleToEdit: AuthManager.Vehicle?,
    isEditingVehicle: Boolean,
    editVehicleError: String?,
    showEditDialog: Boolean,
    onSelectVehicle: (Int) -> Unit,
    onEditRequest: (AuthManager.Vehicle) -> Unit,
    onEditVehicle: (Int, String, String, String, Int, String, Double, Int, Int, Int) -> Unit,
    onDismissEdit: () -> Unit,
    onAddVehicleClick: () -> Unit,
    onAddVehicle: (String, String, String, Int, String, Double, Int, Int, Int) -> Unit,
    onDismissAdd: () -> Unit,
    onDeleteRequest: (AuthManager.Vehicle) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteCancel: () -> Unit,
    onRefreshVehicles: () -> Unit,
    // Session actions
    sessionToDelete: MergedSession?,
    isDeletingSession: Boolean,
    onDeleteSessionRequest: (MergedSession) -> Unit,
    onDeleteSessionConfirm: () -> Unit,
    onDeleteSessionCancel: () -> Unit,
    // Analysis
    analysisState: AnalysisState,
    showAnalysisSheet: Boolean,
    onAnalyze: (MergedSession) -> Unit,
    onDismissAnalysis: () -> Unit,
    onDeleteAnalysis: (MergedSession) -> Unit,
    // Misc
    onStopLogging: () -> Unit,
    onRefresh: () -> Unit,
    onRetryUploads: () -> Unit,
    onUrlChange: (String) -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    // Dialogi pojazdów
    if (showAddDialog) {
        AddVehicleDialog(isLoading = isAddingVehicle, errorMessage = addVehicleError, onAdd = onAddVehicle, onDismiss = onDismissAdd)
    }
    if (showEditDialog && vehicleToEdit != null) {
        EditVehicleDialog(vehicle = vehicleToEdit, isLoading = isEditingVehicle, errorMessage = editVehicleError, onEdit = onEditVehicle, onDismiss = onDismissEdit)
    }
    vehicleToDelete?.let { v ->
        DeleteVehicleDialog(vehicle = v, isDeleting = isDeletingVehicle, onConfirm = onDeleteConfirm, onDismiss = onDeleteCancel)
    }

    // Dialog potwierdzenia usunięcia sesji
    sessionToDelete?.let { session ->
        DeleteSessionDialog(session = session, isDeleting = isDeletingSession, onConfirm = onDeleteSessionConfirm, onDismiss = onDeleteSessionCancel)
    }

    // Bottom sheet analizy
    if (showAnalysisSheet && analysisState !is AnalysisState.Idle) {
        val currentSession = (analysisState as? AnalysisState.Success)?.let { s ->
            mergedSessions.find { it.sessionId == s.sessionId }
        } ?: (analysisState as? AnalysisState.Loading)?.let { l ->
            mergedSessions.find { it.sessionId == l.sessionId }
        } ?: (analysisState as? AnalysisState.Error)?.let { e ->
            mergedSessions.find { it.sessionId == e.sessionId }
        }

        AnalysisBottomSheet(
            state            = analysisState,
            onDismiss        = onDismissAnalysis,
            onDeleteAnalysis = { currentSession?.let { onDeleteAnalysis(it) } },
            onRetry          = { currentSession?.let { onAnalyze(it) } }
        )
    }

    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInput      by remember { mutableStateOf(backendUrl) }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            containerColor   = CardBackground,
            title            = { Text("URL backendu", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text             = {
                Column {
                    Text("Adres endpointu POST:", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = urlInput, onValueChange = { urlInput = it }, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                        colors    = OutlinedTextFieldDefaults.colors(focusedBorderColor = AccentGreen, unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)),
                        placeholder = { Text("https://api.example.com/api/...", fontSize = 12.sp) }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { onUrlChange(urlInput); showUrlDialog = false }) { Text("Zapisz", color = AccentGreen, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showUrlDialog = false }) { Text("Anuluj", color = TextSecondary) } }
        )
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding      = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        // ── Wybór pojazdu ───────────────────────────────────────────────────
        item {
            VehiclePickerSection(
                vehicles          = vehicles,
                selectedVehicleId = selectedVehicleId,
                isLoggedIn        = isLoggedIn,
                isLoading         = isLoadingVehicles,
                error             = vehicleError,
                onSelectVehicle   = onSelectVehicle,
                onAddClick        = onAddVehicleClick,
                onEditRequest     = onEditRequest,
                onDeleteRequest   = onDeleteRequest,
                onRefresh         = onRefreshVehicles
            )
        }

        item { Divider(color = TextSecondary.copy(0.08f), modifier = Modifier.padding(vertical = 4.dp)) }

        // ── Upload status ───────────────────────────────────────────────────
        item { UploadStatusCard(uploadStatus = uploadStatus, pendingRetryCount = pendingRetryCount, onRetryUploads = onRetryUploads) }

        // ── Backend URL ─────────────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth().clickable { showUrlDialog = true },
                shape    = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("ENDPOINT BACKENDU", fontSize = 10.sp, color = TextSecondary, letterSpacing = 1.sp)
                        Text(backendUrl, fontSize = 12.sp, color = AccentBlue, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.Edit, null, tint = TextSecondary, modifier = Modifier.size(18.dp).padding(start = 4.dp))
                }
            }
        }

        // ── Logging status ──────────────────────────────────────────────────
        item {
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors   = CardDefaults.cardColors(containerColor = if (isLogging) AccentGreen.copy(alpha = 0.08f) else CardBackground),
                border   = BorderStroke(1.dp, if (isLogging) AccentGreen.copy(0.4f) else TextSecondary.copy(0.15f))
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (isLogging) AccentGreen else TextSecondary))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(if (isLogging) "Logowanie aktywne" else "Logowanie nieaktywne", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = if (isLogging) AccentGreen else TextSecondary)
                            Text("Dane zapisywane do JSON + wysyłane na backend", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                    if (isLogging) TextButton(onClick = onStopLogging) { Text("Zatrzymaj", color = AccentRed, fontSize = 13.sp) }
                }
            }
        }

        // ── Nagłówek sesji ──────────────────────────────────────────────────
        item {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "SESJE (${mergedSessions.size})",
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.5.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (isLoadingSessions) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = AccentGreen, strokeWidth = 2.dp)
                    }
                    IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (mergedSessions.isEmpty() && !isLoadingSessions) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (selectedVehicleId != null) "Brak sesji dla wybranego pojazdu"
                        else "Brak sesji",
                        color = TextSecondary
                    )
                }
            }
        }

        items(mergedSessions, key = { it.sessionId }) { session ->
            SessionCard(
                session         = session,
                analysisState   = analysisState,
                onAnalyze       = { onAnalyze(session) },
                onDeleteRequest = { onDeleteSessionRequest(session) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// UPLOAD STATUS CARD (wydzielony dla czytelności)
// ─────────────────────────────────────────────────────────────

@Composable
private fun UploadStatusCard(
    uploadStatus: TelemetryUploader.UploadStatus,
    pendingRetryCount: Int,
    onRetryUploads: () -> Unit
) {
    val (color, text, sub) = when (val s = uploadStatus) {
        is TelemetryUploader.UploadStatus.IDLE      -> Triple(TextSecondary, "Oczekiwanie", "Nie wysłano jeszcze danych")
        is TelemetryUploader.UploadStatus.UPLOADING -> Triple(AccentBlue, "Wysyłanie…", "Trwa transfer danych")
        is TelemetryUploader.UploadStatus.SUCCESS   -> Triple(AccentGreen, "✓ Wysłano ${s.recordsSent} rekordów", s.timestamp.take(19).replace("T", " "))
        is TelemetryUploader.UploadStatus.FAILED    -> Triple(AccentRed, "✗ Błąd: ${s.error}", if (s.willRetry) "Zapisano do retry" else "")
    }
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f)),
        border   = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("STATUS UPLOADU", fontSize = 10.sp, color = TextSecondary, letterSpacing = 1.sp)
                Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = color)
                if (sub.isNotBlank()) Text(sub, fontSize = 11.sp, color = TextSecondary)
            }
            if (pendingRetryCount > 0) {
                Column(horizontalAlignment = Alignment.End) {
                    Text("$pendingRetryCount do retry", fontSize = 11.sp, color = AccentOrange)
                    TextButton(onClick = onRetryUploads) { Text("Wyślij teraz", color = AccentOrange, fontSize = 12.sp) }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// SESSION CARD
// ─────────────────────────────────────────────────────────────

@Composable
fun SessionCard(
    session: MergedSession,
    analysisState: AnalysisState,
    onAnalyze: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val isThisSession = when (analysisState) {
        is AnalysisState.Success -> analysisState.sessionId == session.sessionId
        is AnalysisState.Loading -> analysisState.sessionId == session.sessionId
        is AnalysisState.Error   -> analysisState.sessionId == session.sessionId
        else -> false
    }
    val hasAnalysis = isThisSession && analysisState is AnalysisState.Success
    val isLoading   = isThisSession && analysisState is AnalysisState.Loading

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBackground),
        border   = if (hasAnalysis) BorderStroke(1.dp, AccentGreen.copy(0.2f)) else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // ── Nagłówek: nazwa + badge online ───────────────────────────────
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            session.displayName,
                            fontSize   = 13.sp, fontWeight = FontWeight.SemiBold,
                            color      = TextPrimary, fontFamily = FontFamily.Monospace,
                            maxLines   = 1, overflow = TextOverflow.Ellipsis,
                            modifier   = Modifier.weight(1f, fill = false)
                        )
                        // Badge: Online / Lokalnie
                        when {
                            session.isOnlineOnly -> Badge(label = "Online", color = AccentBlue)
                            session.isLocalOnly  -> Badge(label = "Lokalnie", color = TextSecondary)
                            else                 -> Badge(label = "Zsynchronizowana", color = AccentGreen)
                        }
                    }
                    // Metadane
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 3.dp)) {
                        Text("${session.recordCount} rek.", fontSize = 11.sp, color = TextSecondary)
                        if (session.sizeKb > 0) Text("${session.sizeKb} KB", fontSize = 11.sp, color = TextSecondary)
                        if (session.vehicleName.isNotBlank()) Text(session.vehicleName, fontSize = 11.sp, color = AccentBlue.copy(0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (session.vin.isNotBlank()) {
                        Text("VIN: ${session.vin.take(17)}", fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 1.dp))
                    }
                    if (session.closedAt.isNotBlank()) {
                        val dateStr = remember(session.closedAt) {
                            try { session.closedAt.take(16).replace("T", " ") } catch (e: Exception) { "" }
                        }
                        if (dateStr.isNotBlank()) Text(dateStr, fontSize = 10.sp, color = TextSecondary.copy(0.6f), modifier = Modifier.padding(top = 1.dp))
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Przyciski akcji ──────────────────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Analizuj (tylko jeśli sesja jest na serwerze)
                if (session.backendId != null) {
                    Button(
                        onClick        = onAnalyze,
                        enabled        = !isLoading,
                        shape          = RoundedCornerShape(8.dp),
                        colors         = ButtonDefaults.buttonColors(
                            containerColor         = AccentGreen.copy(0.15f),
                            disabledContainerColor = AccentGreen.copy(0.07f)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier       = Modifier.height(34.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = AccentGreen, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Analizuję…", fontSize = 12.sp, color = AccentGreen)
                        } else {
                            Icon(
                                if (hasAnalysis) Icons.Default.Visibility else Icons.Default.Search,
                                null, tint = AccentGreen, modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (hasAnalysis) "Pokaż analizę" else "Analizuj",
                                fontSize = 12.sp, color = AccentGreen, fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Usuń sesję
                IconButton(
                    onClick  = onDeleteRequest,
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                        .background(AccentRed.copy(0.08f))
                        .border(1.dp, AccentRed.copy(0.3f), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.DeleteOutline, null, tint = AccentRed, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// BADGE pomocniczy
// ─────────────────────────────────────────────────────────────

@Composable
private fun Badge(label: String, color: Color) {
    Text(
        label,
        fontSize   = 9.sp,
        fontWeight = FontWeight.Bold,
        color      = color,
        modifier   = Modifier
            .background(color.copy(0.12f), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}

// ─────────────────────────────────────────────────────────────
// DELETE SESSION DIALOG
// ─────────────────────────────────────────────────────────────

@Composable
fun DeleteSessionDialog(
    session: MergedSession,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        containerColor   = CardBackground,
        shape            = RoundedCornerShape(20.dp),
        icon = {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(AccentRed.copy(0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DeleteOutline, null, tint = AccentRed, modifier = Modifier.size(24.dp))
            }
        },
        title = { Text("Usuń sesję", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(session.sessionId, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                if (session.backendId != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Cloud, null, tint = AccentBlue, modifier = Modifier.size(14.dp))
                        Text("Zostanie usunięta lokalnie i z serwera.", color = TextSecondary, fontSize = 12.sp)
                    }
                } else {
                    Text("Zostanie usunięta tylko lokalnie.", color = TextSecondary, fontSize = 12.sp)
                }
                Text("Tej operacji nie można cofnąć.", color = AccentRed.copy(0.8f), fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !isDeleting, shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed, disabledContainerColor = AccentRed.copy(0.4f))) {
                if (isDeleting) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Usuń", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("Anuluj", color = TextSecondary) } }
    )
}

// ─────────────────────────────────────────────────────────────
// VEHICLE PICKER SECTION (bez zmian)
// ─────────────────────────────────────────────────────────────

private val FUEL_LABELS = listOf(
    "petrol" to "Benzyna", "diesel" to "Diesel",
    "lpg" to "LPG",        "hybrid" to "Hybryda"
)

@Composable
fun VehiclePickerSection(
    vehicles: List<AuthManager.Vehicle>,
    selectedVehicleId: Int?,
    isLoggedIn: Boolean,
    isLoading: Boolean,
    error: String?,
    onSelectVehicle: (Int) -> Unit,
    onAddClick: () -> Unit,
    onEditRequest: (AuthManager.Vehicle) -> Unit,
    onDeleteRequest: (AuthManager.Vehicle) -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.DirectionsCar, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                Text("AKTYWNY POJAZD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.5.sp)
            }
            if (isLoggedIn) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = AccentGreen, strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onAddClick, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(AccentGreen.copy(0.15f))) {
                        Icon(Icons.Default.Add, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        when {
            !isLoggedIn -> Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), border = BorderStroke(1.dp, TextSecondary.copy(0.15f))) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Lock, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Text("Zaloguj się, aby zarządzać pojazdami", fontSize = 13.sp, color = TextSecondary)
                }
            }
            error != null -> Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = AccentRed.copy(0.08f)), border = BorderStroke(1.dp, AccentRed.copy(0.3f))) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.ErrorOutline, null, tint = AccentRed, modifier = Modifier.size(18.dp))
                    Text(error, fontSize = 13.sp, color = AccentRed, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefresh) { Text("Odśwież", color = AccentGreen, fontSize = 12.sp) }
                }
            }
            vehicles.isEmpty() && !isLoading -> Card(modifier = Modifier.fillMaxWidth().clickable { onAddClick() }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), border = BorderStroke(1.dp, AccentGreen.copy(0.2f))) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DirectionsCar, null, tint = AccentGreen.copy(0.4f), modifier = Modifier.size(32.dp))
                    Text("Brak pojazdów", fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    Text("Dotknij, aby dodać pierwszy pojazd", fontSize = 12.sp, color = TextSecondary.copy(0.6f))
                }
            }
            else -> {
                if (selectedVehicleId == null || vehicles.none { it.id == selectedVehicleId }) {
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AccentOrange.copy(0.08f)).border(1.dp, AccentOrange.copy(0.3f), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.TouchApp, null, tint = AccentOrange, modifier = Modifier.size(15.dp))
                        Text("Wybierz pojazd przed połączeniem z OBD", fontSize = 12.sp, color = AccentOrange)
                    }
                }
                vehicles.forEach { vehicle ->
                    VehiclePickerCard(vehicle = vehicle, isSelected = vehicle.id == selectedVehicleId, onSelect = { onSelectVehicle(vehicle.id) }, onEditRequest = { onEditRequest(vehicle) }, onDeleteRequest = { onDeleteRequest(vehicle) })
                }
                OutlinedButton(onClick = onAddClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, AccentGreen.copy(0.3f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Dodaj pojazd", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun VehiclePickerCard(vehicle: AuthManager.Vehicle, isSelected: Boolean, onSelect: () -> Unit, onEditRequest: () -> Unit, onDeleteRequest: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onSelect() }, shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) AccentGreen.copy(0.08f) else CardBackground),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) AccentGreen else AccentGreen.copy(0.15f))) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Brush.radialGradient(listOf(if (isSelected) AccentGreen.copy(0.35f) else AccentGreen.copy(0.15f), AccentGreen.copy(0.04f)))), contentAlignment = Alignment.Center) {
                if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(24.dp))
                else Icon(Icons.Default.DirectionsCar, null, tint = AccentGreen.copy(0.6f), modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(vehicle.name.ifBlank { "${vehicle.make} ${vehicle.model}".trim() }, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (isSelected) Text("Aktywny", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentGreen, modifier = Modifier.background(AccentGreen.copy(0.15f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
                val subtitle = listOfNotNull(vehicle.make.takeIf { it.isNotBlank() }, vehicle.model.takeIf { it.isNotBlank() }).joinToString(" ")
                if (subtitle.isNotBlank()) Text(subtitle, fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val metaLine = buildList {
                    if (vehicle.yearLabel.isNotBlank()) add(vehicle.yearLabel)
                    FUEL_LABELS.find { it.first == vehicle.fuelType }?.second?.let { add(it) }
                    if (vehicle.engineDisplacementL > 0.0) add("%.1f L".format(vehicle.engineDisplacementL))
                }.joinToString(" • ")
                if (metaLine.isNotBlank()) Text(metaLine, fontSize = 12.sp, color = AccentBlue.copy(0.7f))
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(onClick = onEditRequest, modifier = Modifier.size(32.dp).clip(CircleShape).background(AccentBlue.copy(0.08f)).border(1.dp, AccentBlue.copy(0.25f), CircleShape)) {
                    Icon(Icons.Default.Edit, null, tint = AccentBlue.copy(0.8f), modifier = Modifier.size(15.dp))
                }
                IconButton(onClick = onDeleteRequest, modifier = Modifier.size(32.dp).clip(CircleShape).background(AccentRed.copy(0.08f)).border(1.dp, AccentRed.copy(0.25f), CircleShape)) {
                    Icon(Icons.Default.DeleteOutline, null, tint = AccentRed.copy(0.8f), modifier = Modifier.size(15.dp))
                }
            }
        }
    }
}