package com.obdreader.app.ui

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
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
import com.obdreader.app.bluetooth.ObdBluetoothManager
import com.obdreader.app.obd.ObdCategory
import com.obdreader.app.obd.ObdCommand
import com.obdreader.app.obd.ObdResponseParser
import com.obdreader.app.obd.TelemetryUploader
import com.obdreader.app.viewmodel.AnalysisState
import com.obdreader.app.viewmodel.MergedSession
import com.obdreader.app.viewmodel.ObdViewModel

// ─────────────────────────────────────────────────────────────
// MAIN SCREEN
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObdMainScreen(
    viewModel: ObdViewModel,
    isGuest: Boolean,
    onConnectRequest: (BluetoothDevice) -> Unit
) {
    val connectionState   by viewModel.connectionState.collectAsState()
    val isConnected = connectionState is ObdBluetoothManager.ConnectionState.Connected

    val selectedVehicleId  by viewModel.selectedVehicleId.collectAsState()
    val sensorData         by viewModel.sensorData.collectAsState()
    val supportedCommands  by viewModel.supportedCommands.collectAsState()
    val isScanning         by viewModel.isScanning.collectAsState()
    val isLogging          by viewModel.isLogging.collectAsState()
    val vinInfo            by viewModel.vinInfo.collectAsState()
    val logMessages        by viewModel.logMessages.collectAsState()
    val selectedCategory   by viewModel.selectedCategory.collectAsState()
    val uploadStatus       by viewModel.uploadStatus.collectAsState()
    val analysisState      by viewModel.analysisState.collectAsState()
    val showAnalysisSheet  by viewModel.showAnalysisSheet.collectAsState()

    // Sessions
    val mergedSessions     by viewModel.mergedSessions.collectAsState()
    val isLoadingSessions  by viewModel.isLoadingSessions.collectAsState()
    val sessionToDelete    by viewModel.sessionToDelete.collectAsState()
    val isDeletingSession  by viewModel.isDeletingSession.collectAsState()

    // Vehicles
    val vehicles           by viewModel.vehicles.collectAsState()
    val isLoadingVehicles  by viewModel.isLoadingVehicles.collectAsState()
    val vehicleError       by viewModel.vehicleError.collectAsState()
    val isAddingVehicle    by viewModel.isAddingVehicle.collectAsState()
    val addVehicleError    by viewModel.addVehicleError.collectAsState()
    val showAddDialog      by viewModel.showAddVehicleDialog.collectAsState()
    val vehicleToDelete    by viewModel.vehicleToDelete.collectAsState()
    val isDeletingVehicle  by viewModel.isDeletingVehicle.collectAsState()
    val vehicleToEdit      by viewModel.vehicleToEdit.collectAsState()
    val isEditingVehicle   by viewModel.isEditingVehicle.collectAsState()
    val editVehicleError   by viewModel.editVehicleError.collectAsState()
    val showEditDialog     by viewModel.showEditVehicleDialog.collectAsState()

    val backendUrl         = remember { mutableStateOf(viewModel.uploader.backendUrl) }
    val pendingRetryCount  = remember { mutableStateOf(viewModel.uploader.pendingRetryCount()) }

    var activeTab by remember { mutableStateOf(0) }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ObdHeader(
                connectionState = connectionState,
                vinInfo         = vinInfo,
                isScanning      = isScanning,
                isLoggedIn      = viewModel.authManager.isLoggedIn,
                userEmail       = viewModel.authManager.savedEmail,
                isGuest         = isGuest,
                onDisconnect    = { viewModel.disconnect() },
                onToggleScan    = { if (isScanning) viewModel.stopScanning() else viewModel.startScanning() },
                onLogout        = { viewModel.logout() }
            )

            AnimatedContent(
                targetState    = isConnected,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label          = "connection_content"
            ) { connected ->
                if (!connected) {
                    if (isGuest) {
                        GuestPreConnectScreen(connectionState = connectionState, onConnect = onConnectRequest)
                    } else {
                        PreConnectTabs(
                            activeTab            = activeTab,
                            onTabChange          = { activeTab = it },
                            connectionState      = connectionState,
                            onConnect            = onConnectRequest,
                            // Sessions
                            mergedSessions       = mergedSessions,
                            isLoadingSessions    = isLoadingSessions,
                            isLogging            = isLogging,
                            uploadStatus         = uploadStatus,
                            backendUrl           = backendUrl.value,
                            pendingRetryCount    = pendingRetryCount.value,
                            isLoggedIn           = viewModel.authManager.isLoggedIn,
                            analysisState        = analysisState,
                            showAnalysisSheet    = showAnalysisSheet,
                            sessionToDelete      = sessionToDelete,
                            isDeletingSession    = isDeletingSession,
                            onStopLogging        = { viewModel.stopLogging() },
                            onRetryUploads       = { viewModel.retryPendingUploads(); pendingRetryCount.value = viewModel.uploader.pendingRetryCount() },
                            onUrlChange          = { url -> viewModel.setBackendUrl(url); backendUrl.value = url },
                            onRefreshSessions    = { viewModel.refreshSessions() },
                            onAnalyze            = { viewModel.fetchOrTriggerAnalysis(it) },
                            onDismissAnalysis    = { viewModel.dismissAnalysisSheet() },
                            onDeleteAnalysis     = { viewModel.deleteAnalysis(it) },
                            onDeleteSessionRequest = { viewModel.requestDeleteSession(it) },
                            onDeleteSessionConfirm = { viewModel.confirmDeleteSession() },
                            onDeleteSessionCancel  = { viewModel.cancelDeleteSession() },
                            // Vehicles
                            vehicles             = vehicles,
                            selectedVehicleId    = selectedVehicleId,
                            isLoadingVehicles    = isLoadingVehicles,
                            vehicleError         = vehicleError,
                            isAddingVehicle      = isAddingVehicle,
                            addVehicleError      = addVehicleError,
                            showAddDialog        = showAddDialog,
                            vehicleToDelete      = vehicleToDelete,
                            isDeletingVehicle    = isDeletingVehicle,
                            vehicleToEdit        = vehicleToEdit,
                            isEditingVehicle     = isEditingVehicle,
                            editVehicleError     = editVehicleError,
                            showEditDialog       = showEditDialog,
                            onSelectVehicle      = { viewModel.selectVehicle(it) },
                            onEditRequest        = { viewModel.requestEditVehicle(it) },
                            onEditVehicle        = { id, name, make, model, year, fuel, displ, cyl, tank, mass -> viewModel.editVehicle(id, name, make, model, year, fuel, displ, cyl, tank, mass) },
                            onDismissEdit        = { viewModel.hideEditVehicle() },
                            onAddVehicleClick    = { viewModel.showAddVehicle() },
                            onAddVehicle         = { name, make, model, year, fuelType, engineDispl, cylinders, tank, mass -> viewModel.addVehicle(name, make, model, year, fuelType, engineDispl, cylinders, tank, mass) },
                            onDismissAdd         = { viewModel.hideAddVehicle() },
                            onDeleteRequest      = { viewModel.requestDeleteVehicle(it) },
                            onDeleteConfirm      = { viewModel.confirmDeleteVehicle() },
                            onDeleteCancel       = { viewModel.cancelDeleteVehicle() },
                            onRefreshVehicles    = { viewModel.loadVehicles() }
                        )
                    }
                } else {
                    PostConnectContent(
                        viewModel            = viewModel,
                        isGuest              = isGuest,
                        activeTab            = activeTab,
                        onTabChange          = { activeTab = it },
                        sensorData           = sensorData,
                        supportedCommands    = supportedCommands,
                        selectedCategory     = selectedCategory,
                        logMessages          = logMessages,
                        isLogging            = isLogging,
                        mergedSessions       = mergedSessions,
                        isLoadingSessions    = isLoadingSessions,
                        uploadStatus         = uploadStatus,
                        backendUrl           = backendUrl.value,
                        pendingRetryCount    = pendingRetryCount.value,
                        vehicles             = vehicles,
                        selectedVehicleId    = selectedVehicleId,
                        isLoadingVehicles    = isLoadingVehicles,
                        vehicleError         = vehicleError,
                        analysisState        = analysisState,
                        showAnalysisSheet    = showAnalysisSheet,
                        sessionToDelete      = sessionToDelete,
                        isDeletingSession    = isDeletingSession,
                        isAddingVehicle      = isAddingVehicle,
                        addVehicleError      = addVehicleError,
                        showAddDialog        = showAddDialog,
                        vehicleToDelete      = vehicleToDelete,
                        isDeletingVehicle    = isDeletingVehicle,
                        vehicleToEdit        = vehicleToEdit,
                        isEditingVehicle     = isEditingVehicle,
                        editVehicleError     = editVehicleError,
                        showEditDialog       = showEditDialog,
                        onSelectVehicle      = { viewModel.selectVehicle(it) },
                        onEditRequest        = { viewModel.requestEditVehicle(it) },
                        onEditVehicle        = { id, name, make, model, year, fuel, displ, cyl, tank, mass -> viewModel.editVehicle(id, name, make, model, year, fuel, displ, cyl, tank, mass) },
                        onDismissEdit        = { viewModel.hideEditVehicle() },
                        onUrlChange          = { url -> viewModel.setBackendUrl(url); backendUrl.value = url },
                        onRetryUploads       = { viewModel.retryPendingUploads(); pendingRetryCount.value = viewModel.uploader.pendingRetryCount() },
                        onStopLogging        = { viewModel.stopLogging() },
                        onRefreshSessions    = { viewModel.refreshSessions() },
                        onAddVehicleClick    = { viewModel.showAddVehicle() },
                        onAddVehicle         = { name, make, model, year, fuelType, engineDispl, cylinders, tank, mass -> viewModel.addVehicle(name, make, model, year, fuelType, engineDispl, cylinders, tank, mass) },
                        onDismissAdd         = { viewModel.hideAddVehicle() },
                        onDeleteRequest      = { viewModel.requestDeleteVehicle(it) },
                        onDeleteConfirm      = { viewModel.confirmDeleteVehicle() },
                        onDeleteCancel       = { viewModel.cancelDeleteVehicle() },
                        onRefreshVehicles    = { viewModel.loadVehicles() },
                        onAnalyze            = { viewModel.fetchOrTriggerAnalysis(it) },
                        onDismissAnalysis    = { viewModel.dismissAnalysisSheet() },
                        onDeleteAnalysis     = { viewModel.deleteAnalysis(it) },
                        onDeleteSessionRequest = { viewModel.requestDeleteSession(it) },
                        onDeleteSessionConfirm = { viewModel.confirmDeleteSession() },
                        onDeleteSessionCancel  = { viewModel.cancelDeleteSession() }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// HEADER
// ─────────────────────────────────────────────────────────────

@Composable
fun ObdHeader(
    connectionState: ObdBluetoothManager.ConnectionState,
    vinInfo: String, isScanning: Boolean, isLoggedIn: Boolean,
    userEmail: String?, isGuest: Boolean,
    onDisconnect: () -> Unit, onToggleScan: () -> Unit, onLogout: () -> Unit
) {
    val isConnected = connectionState is ObdBluetoothManager.ConnectionState.Connected
    val deviceName  = (connectionState as? ObdBluetoothManager.ConnectionState.Connected)?.deviceName ?: ""
    val cleanVin    = vinInfo.filter { it.isLetterOrDigit() }.uppercase()
    val displayVin  = if (cleanVin.length in 10..17) cleanVin else ""
    var showLogout  by remember { mutableStateOf(false) }

    if (showLogout) {
        AlertDialog(
            onDismissRequest = { showLogout = false }, containerColor = CardBackground,
            title = { Text("Wyloguj się?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text  = { Text("Sesja zostanie zakończona. Niezapisane dane mogą zostać utracone.", color = TextSecondary, fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { showLogout = false; onLogout() }) { Text("Wyloguj", color = AccentRed, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { showLogout = false }) { Text("Anuluj", color = TextSecondary) } }
        )
    }

    Box(modifier = Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color(0xFF0D1B2A), CardBackground))).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text("OBDamy", fontSize = 22.sp, fontWeight = FontWeight.Black, color = TextPrimary, letterSpacing = 0.5.sp, maxLines = 1)
                if (isConnected) Text(deviceName, fontSize = 12.sp, color = AccentGreen, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (displayVin.isNotBlank()) Text("VIN: $displayVin", fontSize = 10.sp, color = TextSecondary, fontFamily = FontFamily.Monospace, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    when {
                        isGuest -> { Icon(Icons.Default.PersonOutline, null, tint = AccentOrange.copy(0.7f), modifier = Modifier.size(10.dp)); Text("Tryb gościa", fontSize = 10.sp, color = AccentOrange.copy(0.7f)) }
                        isLoggedIn && !userEmail.isNullOrBlank() -> { Icon(Icons.Default.Person, null, tint = AccentBlue.copy(0.7f), modifier = Modifier.size(10.dp)); Text(userEmail, fontSize = 10.sp, color = AccentBlue.copy(0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isConnected) {
                    IconButton(onClick = onToggleScan, modifier = Modifier.size(38.dp).clip(CircleShape).background(if (isScanning) AccentGreen.copy(0.15f) else Color.Transparent).border(1.dp, if (isScanning) AccentGreen else TextSecondary, CircleShape)) {
                        Icon(if (isScanning) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = if (isScanning) AccentGreen else TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDisconnect, modifier = Modifier.size(38.dp).clip(CircleShape).border(1.dp, AccentRed.copy(0.5f), CircleShape)) {
                        Icon(Icons.Default.BluetoothDisabled, null, tint = AccentRed, modifier = Modifier.size(18.dp))
                    }
                }
                val bg     = when { isGuest -> AccentOrange.copy(0.1f); isLoggedIn -> AccentBlue.copy(0.1f); else -> Color.Transparent }
                val border = when { isGuest -> AccentOrange.copy(0.4f); isLoggedIn -> AccentBlue.copy(0.4f); else -> TextSecondary.copy(0.3f) }
                val tint   = when { isGuest -> AccentOrange; isLoggedIn -> AccentBlue; else -> TextSecondary }
                IconButton(onClick = { showLogout = true }, modifier = Modifier.size(38.dp).clip(CircleShape).background(bg).border(1.dp, border, CircleShape)) {
                    Icon(Icons.Default.Logout, null, tint = tint, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// GUEST PRE-CONNECT
// ─────────────────────────────────────────────────────────────

@Composable
fun GuestPreConnectScreen(connectionState: ObdBluetoothManager.ConnectionState, onConnect: (BluetoothDevice) -> Unit) {
    when (connectionState) {
        is ObdBluetoothManager.ConnectionState.Connecting -> ConnectingScreen(message = connectionState.message)
        is ObdBluetoothManager.ConnectionState.Error      -> DeviceSelectionScreen(onConnect = onConnect, errorBanner = connectionState.message)
        else                                              -> DeviceSelectionScreen(onConnect = onConnect)
    }
}

// ─────────────────────────────────────────────────────────────
// PRE-CONNECT TABS  [Połącz | Sesje]
// ─────────────────────────────────────────────────────────────

@Composable
fun PreConnectTabs(
    activeTab: Int, onTabChange: (Int) -> Unit,
    connectionState: ObdBluetoothManager.ConnectionState, onConnect: (BluetoothDevice) -> Unit,
    // Sessions
    mergedSessions: List<MergedSession>, isLoadingSessions: Boolean, isLogging: Boolean,
    uploadStatus: TelemetryUploader.UploadStatus, backendUrl: String, pendingRetryCount: Int,
    isLoggedIn: Boolean, analysisState: AnalysisState, showAnalysisSheet: Boolean,
    sessionToDelete: MergedSession?, isDeletingSession: Boolean,
    onStopLogging: () -> Unit, onRetryUploads: () -> Unit, onUrlChange: (String) -> Unit,
    onRefreshSessions: () -> Unit, onAnalyze: (MergedSession) -> Unit,
    onDismissAnalysis: () -> Unit, onDeleteAnalysis: (MergedSession) -> Unit,
    onDeleteSessionRequest: (MergedSession) -> Unit, onDeleteSessionConfirm: () -> Unit, onDeleteSessionCancel: () -> Unit,
    // Vehicles
    vehicles: List<AuthManager.Vehicle>, selectedVehicleId: Int?,
    isLoadingVehicles: Boolean, vehicleError: String?,
    isAddingVehicle: Boolean, addVehicleError: String?, showAddDialog: Boolean,
    vehicleToDelete: AuthManager.Vehicle?, isDeletingVehicle: Boolean,
    vehicleToEdit: AuthManager.Vehicle?, isEditingVehicle: Boolean, editVehicleError: String?, showEditDialog: Boolean,
    onSelectVehicle: (Int) -> Unit, onEditRequest: (AuthManager.Vehicle) -> Unit,
    onEditVehicle: (Int, String, String, String, Int, String, Double, Int, Int, Int) -> Unit,
    onDismissEdit: () -> Unit, onAddVehicleClick: () -> Unit,
    onAddVehicle: (String, String, String, Int, String, Double, Int, Int, Int) -> Unit,
    onDismissAdd: () -> Unit, onDeleteRequest: (AuthManager.Vehicle) -> Unit,
    onDeleteConfirm: () -> Unit, onDeleteCancel: () -> Unit, onRefreshVehicles: () -> Unit
) {
    val safeTab = activeTab.coerceIn(0, 1)
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = safeTab, containerColor = CardBackground, contentColor = AccentGreen,
            indicator = { tabs -> TabRowDefaults.Indicator(modifier = Modifier.tabIndicatorOffset(tabs[safeTab]), color = AccentGreen, height = 2.dp) }) {
            listOf(Icons.Default.Bluetooth to "Połącz", Icons.Default.Storage to "Sesje").forEachIndexed { index, (icon, label) ->
                Tab(selected = safeTab == index, onClick = { onTabChange(index) }, modifier = Modifier.height(48.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(icon, null, modifier = Modifier.size(16.dp), tint = if (safeTab == index) AccentGreen else TextSecondary)
                        Text(label, color = if (safeTab == index) AccentGreen else TextSecondary, fontWeight = if (safeTab == index) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
                    }
                }
            }
        }
        when (safeTab) {
            0 -> when (connectionState) {
                is ObdBluetoothManager.ConnectionState.Connecting -> ConnectingScreen(message = connectionState.message)
                is ObdBluetoothManager.ConnectionState.Error      -> DeviceSelectionScreen(onConnect = onConnect, errorBanner = connectionState.message)
                else -> DeviceSelectionScreen(onConnect = onConnect)
            }
            1 -> SessionsTab(
                mergedSessions = mergedSessions, isLoadingSessions = isLoadingSessions, isLogging = isLogging,
                uploadStatus = uploadStatus, backendUrl = backendUrl, pendingRetryCount = pendingRetryCount,
                vehicles = vehicles, selectedVehicleId = selectedVehicleId, isLoggedIn = isLoggedIn,
                isLoadingVehicles = isLoadingVehicles, vehicleError = vehicleError,
                isAddingVehicle = isAddingVehicle, addVehicleError = addVehicleError, showAddDialog = showAddDialog,
                vehicleToDelete = vehicleToDelete, isDeletingVehicle = isDeletingVehicle,
                vehicleToEdit = vehicleToEdit, isEditingVehicle = isEditingVehicle, editVehicleError = editVehicleError, showEditDialog = showEditDialog,
                onSelectVehicle = onSelectVehicle, onEditRequest = onEditRequest, onEditVehicle = onEditVehicle, onDismissEdit = onDismissEdit,
                onAddVehicleClick = onAddVehicleClick, onAddVehicle = onAddVehicle, onDismissAdd = onDismissAdd,
                onDeleteRequest = onDeleteRequest, onDeleteConfirm = onDeleteConfirm, onDeleteCancel = onDeleteCancel, onRefreshVehicles = onRefreshVehicles,
                sessionToDelete = sessionToDelete, isDeletingSession = isDeletingSession,
                onDeleteSessionRequest = onDeleteSessionRequest, onDeleteSessionConfirm = onDeleteSessionConfirm, onDeleteSessionCancel = onDeleteSessionCancel,
                analysisState = analysisState, showAnalysisSheet = showAnalysisSheet,
                onAnalyze = onAnalyze, onDismissAnalysis = onDismissAnalysis, onDeleteAnalysis = onDeleteAnalysis,
                onStopLogging = onStopLogging, onRefresh = onRefreshSessions, onRetryUploads = onRetryUploads, onUrlChange = onUrlChange
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// POST-CONNECT CONTENT  [Dashboard | Czujniki | Logi | Sesje]
// ─────────────────────────────────────────────────────────────

@Composable
fun PostConnectContent(
    viewModel: ObdViewModel, isGuest: Boolean, activeTab: Int, onTabChange: (Int) -> Unit,
    sensorData: Map<ObdCommand, ObdResponseParser.ParsedValue>, supportedCommands: List<ObdCommand>,
    selectedCategory: ObdCategory?, logMessages: List<String>, isLogging: Boolean,
    mergedSessions: List<MergedSession>, isLoadingSessions: Boolean,
    uploadStatus: TelemetryUploader.UploadStatus, backendUrl: String, pendingRetryCount: Int,
    vehicles: List<AuthManager.Vehicle>, selectedVehicleId: Int?,
    isLoadingVehicles: Boolean, vehicleError: String?, analysisState: AnalysisState,
    showAnalysisSheet: Boolean, sessionToDelete: MergedSession?, isDeletingSession: Boolean,
    isAddingVehicle: Boolean, addVehicleError: String?, showAddDialog: Boolean,
    vehicleToDelete: AuthManager.Vehicle?, isDeletingVehicle: Boolean,
    vehicleToEdit: AuthManager.Vehicle?, isEditingVehicle: Boolean, editVehicleError: String?, showEditDialog: Boolean,
    onSelectVehicle: (Int) -> Unit, onEditRequest: (AuthManager.Vehicle) -> Unit,
    onEditVehicle: (Int, String, String, String, Int, String, Double, Int, Int, Int) -> Unit,
    onDismissEdit: () -> Unit, onUrlChange: (String) -> Unit, onRetryUploads: () -> Unit,
    onStopLogging: () -> Unit, onRefreshSessions: () -> Unit, onAddVehicleClick: () -> Unit,
    onAddVehicle: (String, String, String, Int, String, Double, Int, Int, Int) -> Unit,
    onDismissAdd: () -> Unit, onDeleteRequest: (AuthManager.Vehicle) -> Unit,
    onDeleteConfirm: () -> Unit, onDeleteCancel: () -> Unit, onRefreshVehicles: () -> Unit,
    onAnalyze: (MergedSession) -> Unit, onDismissAnalysis: () -> Unit, onDeleteAnalysis: (MergedSession) -> Unit,
    onDeleteSessionRequest: (MergedSession) -> Unit, onDeleteSessionConfirm: () -> Unit, onDeleteSessionCancel: () -> Unit
) {
    val tabs    = if (isGuest) listOf("Dashboard", "Czujniki", "Logi") else listOf("Dashboard", "Czujniki", "Logi", "Sesje")
    val safeTab = activeTab.coerceIn(0, tabs.lastIndex)

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = safeTab, containerColor = CardBackground, contentColor = AccentGreen,
            indicator = { tabs -> TabRowDefaults.Indicator(modifier = Modifier.tabIndicatorOffset(tabs[safeTab]), color = AccentGreen, height = 2.dp) }) {
            tabs.forEachIndexed { index, title ->
                Tab(selected = safeTab == index, onClick = { onTabChange(index) }, modifier = Modifier.height(48.dp)) {
                    Text(title, color = if (safeTab == index) AccentGreen else TextSecondary, fontWeight = if (safeTab == index) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp, maxLines = 1, softWrap = false)
                }
            }
        }
        when (safeTab) {
            0 -> DashboardTab(sensorData = sensorData)
            1 -> SensorsTab(sensorData = sensorData, supportedCommands = supportedCommands, selectedCategory = selectedCategory, onCategorySelect = { viewModel.selectCategory(it) })
            2 -> LogsTab(logMessages = logMessages)
            3 -> if (!isGuest) {
                SessionsTab(
                    mergedSessions = mergedSessions, isLoadingSessions = isLoadingSessions, isLogging = isLogging,
                    uploadStatus = uploadStatus, backendUrl = backendUrl, pendingRetryCount = pendingRetryCount,
                    vehicles = vehicles, selectedVehicleId = selectedVehicleId, isLoggedIn = viewModel.authManager.isLoggedIn,
                    isLoadingVehicles = isLoadingVehicles, vehicleError = vehicleError,
                    isAddingVehicle = isAddingVehicle, addVehicleError = addVehicleError, showAddDialog = showAddDialog,
                    vehicleToDelete = vehicleToDelete, isDeletingVehicle = isDeletingVehicle,
                    vehicleToEdit = vehicleToEdit, isEditingVehicle = isEditingVehicle, editVehicleError = editVehicleError, showEditDialog = showEditDialog,
                    onSelectVehicle = onSelectVehicle, onEditRequest = onEditRequest, onEditVehicle = onEditVehicle, onDismissEdit = onDismissEdit,
                    onAddVehicleClick = onAddVehicleClick, onAddVehicle = onAddVehicle, onDismissAdd = onDismissAdd,
                    onDeleteRequest = onDeleteRequest, onDeleteConfirm = onDeleteConfirm, onDeleteCancel = onDeleteCancel, onRefreshVehicles = onRefreshVehicles,
                    sessionToDelete = sessionToDelete, isDeletingSession = isDeletingSession,
                    onDeleteSessionRequest = onDeleteSessionRequest, onDeleteSessionConfirm = onDeleteSessionConfirm, onDeleteSessionCancel = onDeleteSessionCancel,
                    analysisState = analysisState, showAnalysisSheet = showAnalysisSheet,
                    onAnalyze = onAnalyze, onDismissAnalysis = onDismissAnalysis, onDeleteAnalysis = onDeleteAnalysis,
                    onStopLogging = onStopLogging, onRefresh = onRefreshSessions, onRetryUploads = onRetryUploads, onUrlChange = onUrlChange
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// UTILITY
// ─────────────────────────────────────────────────────────────

fun Modifier.tabIndicatorOffset(currentTabPosition: TabPosition): Modifier =
    this.then(Modifier.wrapContentSize(align = Alignment.BottomStart).offset(x = currentTabPosition.left).width(currentTabPosition.width))