package com.obdreader.app.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.core.app.ActivityCompat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
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
import com.obdreader.app.bluetooth.ObdBluetoothManager
import com.obdreader.app.obd.ObdCategory
import com.obdreader.app.obd.ObdCommand
import com.obdreader.app.obd.ObdResponseParser
import com.obdreader.app.obd.TelemetryUploader
import com.obdreader.app.viewmodel.ObdViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ObdViewModel by viewModels {
        androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(application)
    }

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val enableBtLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            Toast.makeText(this, "Bluetooth jest wymagany!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestBluetoothPermissions()

        setContent {
            ObdAppTheme {
                AppRoot(viewModel = viewModel)
            }
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            if (permissions.any {
                    ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }) {
                ActivityCompat.requestPermissions(this, permissions, 1001)
            }
        } else {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            if (permissions.any {
                    ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
                }) {
                ActivityCompat.requestPermissions(this, permissions, 1001)
            }
        }

        if (bluetoothAdapter?.isEnabled == false) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }
}

// ─── Korzeń aplikacji ─────────────────────────────────────────────────────────

@Composable
fun AppRoot(viewModel: ObdViewModel) {
    val authPassed by viewModel.authPassed.collectAsState()
    val isAuthLoading by viewModel.isAuthLoading.collectAsState()
    val authError by viewModel.authError.collectAsState()
    val isGuest by viewModel.isGuest.collectAsState()

    // Dialogi pojazdów – renderowane nad całością
    val showAddVehicle by viewModel.showAddVehicleDialog.collectAsState()
    val isAddingVehicle by viewModel.isAddingVehicle.collectAsState()
    val addVehicleError by viewModel.addVehicleError.collectAsState()

    AnimatedContent(
        targetState = authPassed,
        transitionSpec = {
            if (targetState) {
                (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
            } else {
                (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
            }
        },
        label = "auth_transition"
    ) { passed ->
        if (!passed) {
            AuthScreen(
                isLoading = isAuthLoading,
                errorMessage = authError,
                onLogin = { email, password ->
                    viewModel.clearAuthError()
                    viewModel.login(email, password)
                },
                onRegister = { email, firstName, lastName, password ->
                    viewModel.clearAuthError()
                    viewModel.register(email, firstName, lastName, password)
                },
                onGuestContinue = { viewModel.continueAsGuest() }
            )
        } else {
            ObdMainScreen(
                viewModel = viewModel,
                isGuest = isGuest,
                onConnectRequest = { device -> viewModel.connect(device) }
            )
        }
    }

    // Dialog dodawania pojazdu (może być widoczny z ekranu pojazdów i sesji)
    if (showAddVehicle) {
        AddVehicleDialog(
            isLoading = isAddingVehicle,
            errorMessage = addVehicleError,
            onAdd = { name, make, model, year ->
                viewModel.addVehicle(name, make, model, year)
            },
            onDismiss = { viewModel.hideAddVehicle() }
        )
    }
}

// ─── Motyw ────────────────────────────────────────────────────────────────────

val DarkBackground = Color(0xFF0A0E1A)
val CardBackground = Color(0xFF111827)
val AccentGreen = Color(0xFF00FF88)
val AccentBlue = Color(0xFF00B4FF)
val AccentOrange = Color(0xFFFF6B35)
val AccentRed = Color(0xFFFF3B5C)
val TextPrimary = Color(0xFFE8F0FE)
val TextSecondary = Color(0xFF8899AA)

@Composable
fun ObdAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = DarkBackground,
            surface = CardBackground,
            primary = AccentGreen,
            secondary = AccentBlue,
            error = AccentRed,
            onBackground = TextPrimary,
            onSurface = TextPrimary
        ),
        content = content
    )
}

// ─── Główny ekran aplikacji ────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObdMainScreen(
    viewModel: ObdViewModel,
    isGuest: Boolean,
    onConnectRequest: (BluetoothDevice) -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isConnected = connectionState is ObdBluetoothManager.ConnectionState.Connected

    val sensorData by viewModel.sensorData.collectAsState()
    val supportedCommands by viewModel.supportedCommands.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isLogging by viewModel.isLogging.collectAsState()
    val vinInfo by viewModel.vinInfo.collectAsState()
    val logMessages by viewModel.logMessages.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val sessionFiles by viewModel.sessionFiles.collectAsState()
    val uploadStatus by viewModel.uploadStatus.collectAsState()

    // Pojazdy
    val vehicles by viewModel.vehicles.collectAsState()
    val isLoadingVehicles by viewModel.isLoadingVehicles.collectAsState()
    val vehicleError by viewModel.vehicleError.collectAsState()
    val isAddingVehicle by viewModel.isAddingVehicle.collectAsState()
    val addVehicleError by viewModel.addVehicleError.collectAsState()
    val showAddDialog by viewModel.showAddVehicleDialog.collectAsState()
    val vehicleToDelete by viewModel.vehicleToDelete.collectAsState()
    val isDeletingVehicle by viewModel.isDeletingVehicle.collectAsState()

    val backendUrl = remember { mutableStateOf(viewModel.uploader.backendUrl) }
    val pendingRetryCount = remember { mutableStateOf(viewModel.uploader.pendingRetryCount()) }

    // Aktywna zakładka – indeks w zależności od stanu połączenia
    var activeTab by remember(isConnected) { mutableStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ─ Header ─────────────────────────────────────────────────────────
            ObdHeader(
                connectionState = connectionState,
                vinInfo = vinInfo,
                isScanning = isScanning,
                isLoggedIn = viewModel.authManager.isLoggedIn,
                userEmail = viewModel.authManager.savedEmail,
                isGuest = isGuest,
                onDisconnect = { viewModel.disconnect() },
                onToggleScan = {
                    if (isScanning) viewModel.stopScanning()
                    else viewModel.startScanning()
                },
                onLogout = { viewModel.logout() }
            )

            // ─ Zawartość ekranu ───────────────────────────────────────────────
            AnimatedContent(
                targetState = isConnected,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "connection_content"
            ) { connected ->
                if (!connected) {
                    // ══ PRZED POŁĄCZENIEM ══════════════════════════════════════
                    if (isGuest) {
                        // Gość: tylko ekran BT, bez zakładek
                        GuestPreConnectScreen(
                            connectionState = connectionState,
                            onConnect = onConnectRequest
                        )
                    } else {
                        // Zalogowany: zakładki Moje pojazdy | Połącz z OBD
                        PreConnectTabs(
                            activeTab = activeTab,
                            onTabChange = { activeTab = it },
                            // Zakładka Pojazdy
                            vehicles = vehicles,
                            isLoadingVehicles = isLoadingVehicles,
                            vehicleError = vehicleError,
                            isAddingVehicle = isAddingVehicle,
                            addVehicleError = addVehicleError,
                            showAddDialog = showAddDialog,
                            vehicleToDelete = vehicleToDelete,
                            isDeletingVehicle = isDeletingVehicle,
                            onAddVehicleClick = { viewModel.showAddVehicle() },
                            onAddVehicle = { name, make, model, year ->
                                viewModel.addVehicle(name, make, model, year)
                            },
                            onDismissAdd = { viewModel.hideAddVehicle() },
                            onDeleteRequest = { viewModel.requestDeleteVehicle(it) },
                            onDeleteConfirm = { viewModel.confirmDeleteVehicle() },
                            onDeleteCancel = { viewModel.cancelDeleteVehicle() },
                            onRefreshVehicles = { viewModel.loadVehicles() },
                            // Zakładka BT
                            connectionState = connectionState,
                            onConnect = onConnectRequest
                        )
                    }
                } else {
                    // ══ PO POŁĄCZENIU ══════════════════════════════════════════
                    PostConnectContent(
                        viewModel = viewModel,
                        isGuest = isGuest,
                        activeTab = activeTab,
                        onTabChange = { activeTab = it },
                        sensorData = sensorData,
                        supportedCommands = supportedCommands,
                        selectedCategory = selectedCategory,
                        logMessages = logMessages,
                        isLogging = isLogging,
                        sessionFiles = sessionFiles,
                        uploadStatus = uploadStatus,
                        backendUrl = backendUrl.value,
                        pendingRetryCount = pendingRetryCount.value,
                        vehicles = vehicles,
                        isLoadingVehicles = isLoadingVehicles,
                        vehicleError = vehicleError,
                        onUrlChange = { url ->
                            viewModel.setBackendUrl(url)
                            backendUrl.value = url
                        },
                        onRetryUploads = {
                            viewModel.retryPendingUploads()
                            pendingRetryCount.value = viewModel.uploader.pendingRetryCount()
                        },
                        onStopLogging = { viewModel.stopLogging() },
                        onRefreshSessions = {
                            viewModel.refreshSessionList()
                            viewModel.loadVehicles()
                            pendingRetryCount.value = viewModel.uploader.pendingRetryCount()
                        },
                        onAddVehicleClick = { viewModel.showAddVehicle() },
                        onRefreshVehicles = { viewModel.loadVehicles() }
                    )
                }
            }
        }
    }
}

// ─── Ekran przed połączeniem dla gościa ──────────────────────────────────────

@Composable
fun GuestPreConnectScreen(
    connectionState: ObdBluetoothManager.ConnectionState,
    onConnect: (BluetoothDevice) -> Unit
) {
    when (connectionState) {
        is ObdBluetoothManager.ConnectionState.Connecting ->
            ConnectingScreen(message = connectionState.message)
        is ObdBluetoothManager.ConnectionState.Error ->
            DeviceSelectionScreen(
                onConnect = onConnect,
                errorBanner = connectionState.message
            )
        else -> DeviceSelectionScreen(onConnect = onConnect)
    }
}

// ─── Zakładki PRZED połączeniem (zalogowany) ──────────────────────────────────

@Composable
fun PreConnectTabs(
    activeTab: Int,
    onTabChange: (Int) -> Unit,
    // Pojazdy
    vehicles: List<com.obdreader.app.auth.AuthManager.Vehicle>,
    isLoadingVehicles: Boolean,
    vehicleError: String?,
    isAddingVehicle: Boolean,
    addVehicleError: String?,
    showAddDialog: Boolean,
    vehicleToDelete: com.obdreader.app.auth.AuthManager.Vehicle?,
    isDeletingVehicle: Boolean,
    onAddVehicleClick: () -> Unit,
    onAddVehicle: (String, String, String, String) -> Unit,
    onDismissAdd: () -> Unit,
    onDeleteRequest: (com.obdreader.app.auth.AuthManager.Vehicle) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteCancel: () -> Unit,
    onRefreshVehicles: () -> Unit,
    // BT
    connectionState: ObdBluetoothManager.ConnectionState,
    onConnect: (BluetoothDevice) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Pasek zakładek
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = CardBackground,
            contentColor = AccentGreen,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = AccentGreen,
                    height = 2.dp
                )
            }
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { onTabChange(0) },
                modifier = Modifier.height(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.DirectionsCar, null,
                        modifier = Modifier.size(16.dp),
                        tint = if (activeTab == 0) AccentGreen else TextSecondary
                    )
                    Text(
                        "Moje pojazdy",
                        color = if (activeTab == 0) AccentGreen else TextSecondary,
                        fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
            }
            Tab(
                selected = activeTab == 1,
                onClick = { onTabChange(1) },
                modifier = Modifier.height(48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Bluetooth, null,
                        modifier = Modifier.size(16.dp),
                        tint = if (activeTab == 1) AccentGreen else TextSecondary
                    )
                    Text(
                        "Połącz z OBD",
                        color = if (activeTab == 1) AccentGreen else TextSecondary,
                        fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Treść zakładki
        when (activeTab) {
            0 -> VehiclesScreen(
                vehicles = vehicles,
                isLoading = isLoadingVehicles,
                error = vehicleError,
                isAddingVehicle = isAddingVehicle,
                addVehicleError = addVehicleError,
                showAddDialog = showAddDialog,
                vehicleToDelete = vehicleToDelete,
                isDeletingVehicle = isDeletingVehicle,
                onAddClick = onAddVehicleClick,
                onAddVehicle = onAddVehicle,
                onDismissAdd = onDismissAdd,
                onDeleteRequest = onDeleteRequest,
                onDeleteConfirm = onDeleteConfirm,
                onDeleteCancel = onDeleteCancel,
                onRefresh = onRefreshVehicles
            )
            1 -> when (connectionState) {
                is ObdBluetoothManager.ConnectionState.Connecting ->
                    ConnectingScreen(message = connectionState.message)
                is ObdBluetoothManager.ConnectionState.Error ->
                    DeviceSelectionScreen(
                        onConnect = onConnect,
                        errorBanner = connectionState.message
                    )
                else -> DeviceSelectionScreen(onConnect = onConnect)
            }
        }
    }
}

// ─── Zawartość PO połączeniu ──────────────────────────────────────────────────

@Composable
fun PostConnectContent(
    viewModel: ObdViewModel,
    isGuest: Boolean,
    activeTab: Int,
    onTabChange: (Int) -> Unit,
    sensorData: Map<ObdCommand, ObdResponseParser.ParsedValue>,
    supportedCommands: List<ObdCommand>,
    selectedCategory: ObdCategory?,
    logMessages: List<String>,
    isLogging: Boolean,
    sessionFiles: List<com.obdreader.app.obd.ObdDataLogger.SessionInfo>,
    uploadStatus: TelemetryUploader.UploadStatus,
    backendUrl: String,
    pendingRetryCount: Int,
    vehicles: List<com.obdreader.app.auth.AuthManager.Vehicle>,
    isLoadingVehicles: Boolean,
    vehicleError: String?,
    onUrlChange: (String) -> Unit,
    onRetryUploads: () -> Unit,
    onStopLogging: () -> Unit,
    onRefreshSessions: () -> Unit,
    onAddVehicleClick: () -> Unit,
    onRefreshVehicles: () -> Unit
) {
    // Zakładki zależne od trybu:
    // Zalogowany: Dashboard | Czujniki | Logi | Sesje
    // Gość:       Dashboard | Czujniki | Logi
    val tabs = if (isGuest) {
        listOf("Dashboard", "Czujniki", "Logi")
    } else {
        listOf("Dashboard", "Czujniki", "Logi", "Sesje")
    }

    // Upewnij się, że activeTab nie wychodzi poza zakres
    val safeTab = activeTab.coerceIn(0, tabs.lastIndex)

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = safeTab,
            containerColor = CardBackground,
            contentColor = AccentGreen,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[safeTab]),
                    color = AccentGreen,
                    height = 2.dp
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = safeTab == index,
                    onClick = { onTabChange(index) },
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        title,
                        color = if (safeTab == index) AccentGreen else TextSecondary,
                        fontWeight = if (safeTab == index) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        when (safeTab) {
            0 -> DashboardTab(sensorData = sensorData)
            1 -> SensorsTab(
                sensorData = sensorData,
                supportedCommands = supportedCommands,
                selectedCategory = selectedCategory,
                onCategorySelect = { viewModel.selectCategory(it) }
            )
            2 -> LogsTab(logMessages = logMessages)
            3 -> if (!isGuest) {
                SessionsTab(
                    sessions = sessionFiles,
                    isLogging = isLogging,
                    uploadStatus = uploadStatus,
                    backendUrl = backendUrl,
                    pendingRetryCount = pendingRetryCount,
                    vehicles = vehicles,
                    isLoggedIn = viewModel.authManager.isLoggedIn,
                    isLoadingVehicles = isLoadingVehicles,
                    vehicleError = vehicleError,
                    onStopLogging = onStopLogging,
                    onRefresh = onRefreshSessions,
                    onRetryUploads = onRetryUploads,
                    onUrlChange = onUrlChange,
                    onAddVehicleClick = onAddVehicleClick,
                    onRefreshVehicles = onRefreshVehicles
                )
            }
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────

@Composable
fun ObdHeader(
    connectionState: ObdBluetoothManager.ConnectionState,
    vinInfo: String,
    isScanning: Boolean,
    isLoggedIn: Boolean,
    userEmail: String?,
    isGuest: Boolean,
    onDisconnect: () -> Unit,
    onToggleScan: () -> Unit,
    onLogout: () -> Unit
) {
    val isConnected = connectionState is ObdBluetoothManager.ConnectionState.Connected
    val deviceName = (connectionState as? ObdBluetoothManager.ConnectionState.Connected)?.deviceName ?: ""
    val cleanVin = vinInfo.filter { it.isLetterOrDigit() }.uppercase()
    val displayVin = if (cleanVin.length in 10..17) cleanVin else ""

    var showLogoutConfirm by remember { mutableStateOf(false) }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            containerColor = CardBackground,
            title = { Text("Wyloguj się?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Sesja zostanie zakończona. Niezapisane dane mogą zostać utracone.",
                    color = TextSecondary, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showLogoutConfirm = false; onLogout() }) {
                    Text("Wyloguj", color = AccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Anuluj", color = TextSecondary)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(colors = listOf(Color(0xFF0D1B2A), CardBackground)))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Lewa strona
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "OBD2 Reader",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    letterSpacing = 0.5.sp,
                    maxLines = 1
                )
                if (isConnected) {
                    Text(
                        deviceName,
                        fontSize = 12.sp,
                        color = AccentGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (displayVin.isNotBlank()) {
                    Text(
                        "VIN: $displayVin",
                        fontSize = 10.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                }
                // Status konta
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isGuest) {
                        Icon(
                            Icons.Default.PersonOutline,
                            contentDescription = null,
                            tint = AccentOrange.copy(0.7f),
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            "Tryb gościa",
                            fontSize = 10.sp,
                            color = AccentOrange.copy(0.7f)
                        )
                    } else if (isLoggedIn && !userEmail.isNullOrBlank()) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = AccentBlue.copy(0.7f),
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            userEmail,
                            fontSize = 10.sp,
                            color = AccentBlue.copy(0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Prawa strona: przyciski
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isConnected) {
                    // Pauza / Start skanowania
                    IconButton(
                        onClick = onToggleScan,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isScanning) AccentGreen.copy(alpha = 0.15f) else Color.Transparent)
                            .border(1.dp, if (isScanning) AccentGreen else TextSecondary, CircleShape)
                    ) {
                        Icon(
                            if (isScanning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isScanning) AccentGreen else TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    // Rozłącz
                    IconButton(
                        onClick = onDisconnect,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.dp, AccentRed.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.BluetoothDisabled,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Wyloguj / wróć do logowania
                IconButton(
                    onClick = { showLogoutConfirm = true },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isGuest -> AccentOrange.copy(0.1f)
                                isLoggedIn -> AccentBlue.copy(0.1f)
                                else -> Color.Transparent
                            }
                        )
                        .border(
                            1.dp,
                            when {
                                isGuest -> AccentOrange.copy(0.4f)
                                isLoggedIn -> AccentBlue.copy(0.4f)
                                else -> TextSecondary.copy(0.3f)
                            },
                            CircleShape
                        )
                ) {
                    Icon(
                        Icons.Default.Logout,
                        contentDescription = "Wyloguj / zmień konto",
                        tint = when {
                            isGuest -> AccentOrange
                            isLoggedIn -> AccentBlue
                            else -> TextSecondary
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ─── Ekran parowania (zaktualizowany o opcjonalny baner błędu) ────────────────

@Composable
fun DeviceSelectionScreen(
    onConnect: (BluetoothDevice) -> Unit,
    errorBanner: String? = null
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ActivityCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ActivityCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH
        ) == PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    if (!hasPermission) {
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
    }

    val pairedDevices = remember(hasPermission) {
        if (!hasPermission) return@remember emptyList()
        try {
            BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) { emptyList() }
    }
    val obdDevices = pairedDevices.filter { device ->
        try {
            val name = device.name?.lowercase() ?: ""
            name.contains("obd") || name.contains("elm") || name.contains("vlink") ||
                    name.contains("icar") || name.contains("obdii") || name.contains("v-link")
        } catch (e: SecurityException) { false }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Połącz z kostką OBD2",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                "Sparuj kostkę OBD2 w ustawieniach Bluetooth, potem wróć tutaj",
                fontSize = 13.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Baner błędu połączenia
        if (errorBanner != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentRed.copy(0.1f))
                        .border(1.dp, AccentRed.copy(0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = AccentRed, modifier = Modifier.size(18.dp))
                    Text(errorBanner, fontSize = 13.sp, color = AccentRed)
                }
            }
        }

        if (obdDevices.isNotEmpty()) {
            item {
                Text(
                    "WYKRYTE KOSTKI OBD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentGreen,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(obdDevices) { device ->
                DeviceCard(device = device, isObd = true, onConnect = onConnect)
            }
        }

        val otherDevices = pairedDevices - obdDevices.toSet()
        if (otherDevices.isNotEmpty()) {
            item {
                Text(
                    "INNE SPAROWANE URZĄDZENIA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(otherDevices) { device ->
                DeviceCard(device = device, isObd = false, onConnect = onConnect)
            }
        }

        if (!hasPermission) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.BluetoothDisabled, contentDescription = null,
                            tint = AccentOrange, modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Brak uprawnienia Bluetooth", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            "Aplikacja potrzebuje dostępu do Bluetooth.",
                            color = TextSecondary, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                        ) {
                            Text("Przyznaj uprawnienie", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (pairedDevices.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Bluetooth, contentDescription = null,
                            tint = TextSecondary, modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Brak sparowanych urządzeń", color = TextSecondary)
                        Text(
                            "Sparuj kostkę OBD2 w Ustawienia → Bluetooth",
                            color = TextSecondary, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(device: BluetoothDevice, isObd: Boolean, onConnect: (BluetoothDevice) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onConnect(device) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isObd) AccentGreen.copy(alpha = 0.08f) else CardBackground
        ),
        border = if (isObd) BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f)) else null
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Bluetooth, contentDescription = null,
                tint = if (isObd) AccentGreen else AccentBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.name ?: "Nieznane urządzenie",
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    device.address, fontSize = 12.sp,
                    color = TextSecondary, fontFamily = FontFamily.Monospace
                )
            }
            if (isObd) {
                Text(
                    "OBD", fontSize = 11.sp, color = AccentGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(AccentGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}

// ─── Ekran łączenia ───────────────────────────────────────────────────────────

@Composable
fun ConnectingScreen(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AccentGreen, modifier = Modifier.size(56.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(message, color = TextPrimary, fontSize = 16.sp)
        }
    }
}

// ─── Dashboard Tab ────────────────────────────────────────────────────────────

@Composable
fun DashboardTab(sensorData: Map<ObdCommand, ObdResponseParser.ParsedValue>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigMetricCard(ObdCommand.ENGINE_RPM, sensorData[ObdCommand.ENGINE_RPM], Modifier.weight(1f), AccentGreen)
                BigMetricCard(ObdCommand.VEHICLE_SPEED, sensorData[ObdCommand.VEHICLE_SPEED], Modifier.weight(1f), AccentBlue)
            }
        }

        val secondaryMetrics = listOf(
            ObdCommand.COOLANT_TEMP, ObdCommand.FUEL_LEVEL,
            ObdCommand.ENGINE_LOAD,  ObdCommand.INTAKE_TEMP,
        )
        secondaryMetrics.chunked(2).forEach { pair ->
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { cmd -> SmallMetricCard(cmd, sensorData[cmd], Modifier.weight(1f)) }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        val statusParsed = sensorData[ObdCommand.STATUS]
        if (statusParsed != null) {
            item { StatusCard(statusParsed) }
        }
    }
}

@Composable
fun BigMetricCard(
    command: ObdCommand,
    parsed: ObdResponseParser.ParsedValue?,
    modifier: Modifier = Modifier,
    accentColor: Color = AccentGreen
) {
    val hasValue = parsed != null && parsed.displayValue != "--" && parsed.displayValue != "Brak danych"
    Card(
        modifier = modifier.height(130.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, accentColor.copy(alpha = if (hasValue) 0.35f else 0.12f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(accentColor.copy(alpha = if (hasValue) 0.08f else 0.02f), Color.Transparent)
                    )
                )
                .padding(14.dp)
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(
                    command.cmdName.uppercase(), fontSize = 11.sp, color = accentColor,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1
                )
                Column {
                    Text(
                        if (hasValue) parsed!!.displayValue else "--",
                        fontSize = 38.sp, fontWeight = FontWeight.Black,
                        color = if (hasValue) TextPrimary else TextSecondary,
                        lineHeight = 38.sp, maxLines = 1, softWrap = false
                    )
                    Text(
                        command.unit, fontSize = 12.sp, color = accentColor.copy(alpha = 0.8f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun SmallMetricCard(
    command: ObdCommand,
    parsed: ObdResponseParser.ParsedValue?,
    modifier: Modifier = Modifier
) {
    val accentColor = when (command) {
        ObdCommand.FUEL_LEVEL -> AccentBlue
        ObdCommand.CONTROL_MODULE_VOLTAGE -> Color(0xFFFFD700)
        else -> TextSecondary
    }
    val hasValue = parsed != null && parsed.displayValue != "--" && parsed.displayValue != "Brak danych"

    Card(
        modifier = modifier.height(86.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                command.cmdName, fontSize = 11.sp, color = TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (hasValue) parsed!!.displayValue else "--",
                    fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = if (hasValue) TextPrimary else TextSecondary,
                    maxLines = 1, softWrap = false
                )
                if (command.unit.isNotBlank() && command.unit != "-") {
                    Text(
                        command.unit, fontSize = 11.sp, color = accentColor,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StatusCard(parsed: ObdResponseParser.ParsedValue) {
    val isMilOn = parsed.displayValue.contains("MIL ON")
    val bg = if (isMilOn) AccentRed.copy(alpha = 0.12f) else AccentGreen.copy(alpha = 0.07f)
    val border = if (isMilOn) AccentRed.copy(alpha = 0.5f) else AccentGreen.copy(alpha = 0.3f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, border)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isMilOn) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isMilOn) AccentRed else AccentGreen,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Status OBD", fontSize = 11.sp, color = TextSecondary)
                Text(
                    parsed.displayValue, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    color = if (isMilOn) AccentRed else AccentGreen
                )
            }
        }
    }
}

// ─── Sensors Tab ──────────────────────────────────────────────────────────────

@Composable
fun SensorsTab(
    sensorData: Map<ObdCommand, ObdResponseParser.ParsedValue>,
    supportedCommands: List<ObdCommand>,
    selectedCategory: ObdCategory?,
    onCategorySelect: (ObdCategory?) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                CategoryChip(
                    label = "Wszystkie",
                    selected = selectedCategory == null,
                    onClick = { onCategorySelect(null) }
                )
            }
            items(ObdCategory.values().toList()) { category ->
                CategoryChip(
                    label = category.displayName,
                    selected = selectedCategory == category,
                    onClick = { onCategorySelect(category) }
                )
            }
        }

        val displayCommands = if (selectedCategory != null) {
            supportedCommands.filter { it in selectedCategory.pids }
                .ifEmpty { selectedCategory.pids }
        } else {
            supportedCommands.ifEmpty { ObdCommand.values().toList() }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(displayCommands, key = { it.cmdName }) { cmd ->
                SensorRow(
                    command = cmd,
                    parsed = sensorData[cmd],
                    isSupported = cmd in supportedCommands
                )
            }
        }
    }
}

@Composable
fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) AccentGreen.copy(alpha = 0.2f) else CardBackground)
            .border(
                1.dp,
                if (selected) AccentGreen else TextSecondary.copy(alpha = 0.3f),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label, fontSize = 13.sp,
            color = if (selected) AccentGreen else TextSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun SensorRow(
    command: ObdCommand,
    parsed: ObdResponseParser.ParsedValue?,
    isSupported: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSupported) CardBackground else CardBackground.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    command.cmdName, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    color = if (isSupported) TextPrimary else TextSecondary
                )
                Text(
                    "PID: ${command.mode}${command.pid} • ${command.description}",
                    fontSize = 11.sp, color = TextSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    parsed?.displayValue ?: if (isSupported) "..." else "N/A",
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = when {
                        parsed == null -> TextSecondary
                        parsed.displayValue == "Brak danych" -> TextSecondary
                        else -> AccentGreen
                    }
                )
                if (command.unit.isNotBlank() && command.unit != "-") {
                    Text(command.unit, fontSize = 11.sp, color = TextSecondary)
                }
            }
        }
    }
}

// ─── Logs Tab ─────────────────────────────────────────────────────────────────

@Composable
fun LogsTab(logMessages: List<String>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (logMessages.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Brak wpisów w logu", color = TextSecondary)
                }
            }
        }
        items(logMessages) { msg ->
            Text(
                msg,
                fontSize = 12.sp,
                color = when {
                    msg.contains("Błąd") || msg.contains("Nie udało") -> AccentRed
                    msg.contains("Połączono") || msg.contains("SUKCES") -> AccentGreen
                    msg.contains("Skanowanie") -> AccentBlue
                    else -> TextSecondary
                },
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

fun Modifier.tabIndicatorOffset(currentTabPosition: TabPosition): Modifier {
    return this.then(
        Modifier
            .wrapContentSize(align = Alignment.BottomStart)
            .offset(x = currentTabPosition.left)
            .width(currentTabPosition.width)
    )
}

// ─── Sessions Tab ─────────────────────────────────────────────────────────────

@Composable
fun SessionsTab(
    sessions: List<com.obdreader.app.obd.ObdDataLogger.SessionInfo>,
    isLogging: Boolean,
    uploadStatus: TelemetryUploader.UploadStatus,
    backendUrl: String,
    pendingRetryCount: Int,
    vehicles: List<com.obdreader.app.auth.AuthManager.Vehicle>,
    isLoggedIn: Boolean,
    isLoadingVehicles: Boolean,
    vehicleError: String?,
    onStopLogging: () -> Unit,
    onRefresh: () -> Unit,
    onRetryUploads: () -> Unit,
    onUrlChange: (String) -> Unit,
    onAddVehicleClick: () -> Unit,
    onRefreshVehicles: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }
    var showUrlDialog by remember { mutableStateOf(false) }
    var urlInput by remember { mutableStateOf(backendUrl) }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            containerColor = CardBackground,
            title = { Text("URL backendu", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Adres endpointu POST:", fontSize = 12.sp, color = TextSecondary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGreen,
                            unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f)
                        ),
                        placeholder = { Text("https://api.example.com/api/...", fontSize = 12.sp) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onUrlChange(urlInput); showUrlDialog = false }) {
                    Text("Zapisz", color = AccentGreen, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Anuluj", color = TextSecondary)
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Status uploadu ──
        item {
            val (uploadColor, uploadText, uploadSub) = when (val s = uploadStatus) {
                is TelemetryUploader.UploadStatus.IDLE ->
                    Triple(TextSecondary, "Oczekiwanie", "Nie wysłano jeszcze danych")
                is TelemetryUploader.UploadStatus.UPLOADING ->
                    Triple(AccentBlue, "Wysyłanie...", "Trwa transfer danych")
                is TelemetryUploader.UploadStatus.SUCCESS ->
                    Triple(AccentGreen, "✓ Wysłano ${s.recordsSent} rekordów", s.timestamp.take(19).replace("T", " "))
                is TelemetryUploader.UploadStatus.FAILED ->
                    Triple(AccentRed, "✗ Błąd: ${s.error}", if (s.willRetry) "Zapisano do retry" else "")
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = uploadColor.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, uploadColor.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "STATUS UPLOADU", fontSize = 10.sp,
                                color = TextSecondary, letterSpacing = 1.sp
                            )
                            Text(
                                uploadText, fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold, color = uploadColor
                            )
                            if (uploadSub.isNotBlank()) {
                                Text(uploadSub, fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                        if (pendingRetryCount > 0) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text("$pendingRetryCount do retry", fontSize = 11.sp, color = AccentOrange)
                                TextButton(onClick = onRetryUploads) {
                                    Text("Wyślij teraz", color = AccentOrange, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── URL backendu ──
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showUrlDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "ENDPOINT BACKENDU", fontSize = 10.sp,
                            color = TextSecondary, letterSpacing = 1.sp
                        )
                        Text(
                            backendUrl, fontSize = 12.sp, color = AccentBlue,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.Default.Edit, contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(start = 4.dp)
                    )
                }
            }
        }

        // ── Status logowania ──
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isLogging) AccentGreen.copy(alpha = 0.08f) else CardBackground
                ),
                border = BorderStroke(
                    1.dp,
                    if (isLogging) AccentGreen.copy(0.4f) else TextSecondary.copy(0.15f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isLogging) AccentGreen else TextSecondary)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                if (isLogging) "Logowanie aktywne" else "Logowanie nieaktywne",
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                color = if (isLogging) AccentGreen else TextSecondary
                            )
                            Text(
                                "Dane zapisywane do JSON + wysyłane na backend",
                                fontSize = 11.sp, color = TextSecondary
                            )
                        }
                    }
                    if (isLogging) {
                        TextButton(onClick = onStopLogging) {
                            Text("Zatrzymaj", color = AccentRed, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // ── Pojazdy (skrócony widok w zakładce Sesje) ──
        item {
            VehiclesSection(
                vehicles = vehicles,
                isLoggedIn = isLoggedIn,
                isLoadingVehicles = isLoadingVehicles,
                vehicleError = vehicleError,
                onAddVehicleClick = onAddVehicleClick,
                onRefresh = onRefreshVehicles
            )
        }

        // ── Zapisane sesje ──
        item {
            Text(
                "ZAPISANE SESJE (${sessions.size})",
                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = TextSecondary, letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (sessions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Brak zapisanych sesji", color = TextSecondary)
                }
            }
        }

        items(sessions) { session ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            session.sessionId, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                        Text("${session.sizeKb} KB", fontSize = 12.sp, color = AccentBlue)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${session.recordCount} rek.", fontSize = 11.sp, color = TextSecondary)
                        if (session.vin.isNotBlank()) {
                            Text(
                                "VIN: ${session.vin.take(17)}", fontSize = 11.sp,
                                color = TextSecondary, fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Text(
                        session.file.absolutePath, fontSize = 10.sp,
                        color = TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

// ─── VehiclesSection (skrócony widok w SessionsTab) ───────────────────────────

//@Composable
//fun VehiclesSection(
//    vehicles: List<com.obdreader.app.auth.AuthManager.Vehicle>,
//    isLoggedIn: Boolean,
//    isLoadingVehicles: Boolean,
//    vehicleError: String?,
//    onAddVehicleClick: () -> Unit,
//    onRefresh: () -> Unit
//) {
//    Column(
//        modifier = Modifier.fillMaxWidth(),
//        verticalArrangement = Arrangement.spacedBy(8.dp)
//    ) {
//        Row(
//            modifier = Modifier.fillMaxWidth(),
//            verticalAlignment = Alignment.CenterVertically,
//            horizontalArrangement = Arrangement.SpaceBetween
//        ) {
//            Text(
//                "MOJE POJAZDY",
//                fontSize = 10.sp, fontWeight = FontWeight.Bold,
//                color = TextSecondary, letterSpacing = 1.5.sp
//            )
//            if (isLoggedIn) {
//                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
//                    IconButton(
//                        onClick = onRefresh,
//                        modifier = Modifier.size(28.dp)
//                    ) {
//                        Icon(Icons.Default.Refresh, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
//                    }
//                    IconButton(
//                        onClick = onAddVehicleClick,
//                        modifier = Modifier
//                            .size(28.dp)
//                            .clip(RoundedCornerShape(6.dp))
//                            .background(AccentGreen.copy(0.15f))
//                    ) {
//                        Icon(Icons.Default.Add, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
//                    }
//                }
//            }
//        }
//
//        when {
//            !isLoggedIn -> {
//                Card(
//                    modifier = Modifier.fillMaxWidth(),
//                    shape = RoundedCornerShape(12.dp),
//                    colors = CardDefaults.cardColors(containerColor = CardBackground),
//                    border = BorderStroke(1.dp, TextSecondary.copy(0.15f))
//                ) {
//                    Row(
//                        modifier = Modifier.padding(14.dp),
//                        verticalAlignment = Alignment.CenterVertically,
//                        horizontalArrangement = Arrangement.spacedBy(10.dp)
//                    ) {
//                        Icon(Icons.Default.Lock, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
//                        Text("Zaloguj się, aby zarządzać pojazdami", fontSize = 13.sp, color = TextSecondary)
//                    }
//                }
//            }
//            isLoadingVehicles -> {
//                Box(
//                    modifier = Modifier.fillMaxWidth().padding(16.dp),
//                    contentAlignment = Alignment.Center
//                ) {
//                    CircularProgressIndicator(
//                        modifier = Modifier.size(24.dp),
//                        color = AccentGreen,
//                        strokeWidth = 2.dp
//                    )
//                }
//            }
//            vehicleError != null -> {
//                Card(
//                    modifier = Modifier.fillMaxWidth(),
//                    shape = RoundedCornerShape(12.dp),
//                    colors = CardDefaults.cardColors(containerColor = AccentRed.copy(0.08f)),
//                    border = BorderStroke(1.dp, AccentRed.copy(0.3f))
//                ) {
//                    Row(
//                        modifier = Modifier.padding(14.dp),
//                        verticalAlignment = Alignment.CenterVertically,
//                        horizontalArrangement = Arrangement.spacedBy(10.dp)
//                    ) {
//                        Icon(Icons.Default.ErrorOutline, null, tint = AccentRed, modifier = Modifier.size(18.dp))
//                        Text(vehicleError, fontSize = 13.sp, color = AccentRed)
//                    }
//                }
//            }
//            vehicles.isEmpty() -> {
//                Card(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .clickable { onAddVehicleClick() },
//                    shape = RoundedCornerShape(12.dp),
//                    colors = CardDefaults.cardColors(containerColor = CardBackground),
//                    border = BorderStroke(1.dp, AccentGreen.copy(0.2f))
//                ) {
//                    Column(
//                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
//                        horizontalAlignment = Alignment.CenterHorizontally,
//                        verticalArrangement = Arrangement.spacedBy(8.dp)
//                    ) {
//                        Icon(
//                            Icons.Default.DirectionsCar, null,
//                            tint = AccentGreen.copy(0.4f),
//                            modifier = Modifier.size(32.dp)
//                        )
//                        Text("Brak pojazdów", fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
//                        Text(
//                            "Dotknij aby dodać pierwszy pojazd",
//                            fontSize = 12.sp, color = TextSecondary.copy(0.6f)
//                        )
//                    }
//                }
//            }
//            else -> {
//                vehicles.forEach { vehicle ->
//                    SessionVehicleChip(vehicle = vehicle)
//                }
//                OutlinedButton(
//                    onClick = onAddVehicleClick,
//                    modifier = Modifier.fillMaxWidth(),
//                    shape = RoundedCornerShape(10.dp),
//                    border = BorderStroke(1.dp, AccentGreen.copy(0.3f)),
//                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen)
//                ) {
//                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
//                    Spacer(Modifier.width(6.dp))
//                    Text("Dodaj pojazd", fontSize = 13.sp)
//                }
//            }
//        }
//    }
//}

// Kompaktowa karta pojazdu do widoku w SessionsTab
@Composable
private fun SessionVehicleChip(vehicle: com.obdreader.app.auth.AuthManager.Vehicle) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, AccentGreen.copy(0.12f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.DirectionsCar, null,
                tint = AccentGreen, modifier = Modifier.size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    vehicle.name.ifBlank { "${vehicle.make} ${vehicle.model}".trim() },
                    fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val sub = buildList {
                    if (vehicle.make.isNotBlank()) add(vehicle.make)
                    if (vehicle.model.isNotBlank()) add(vehicle.model)
                    if (vehicle.year.isNotBlank()) add(vehicle.year)
                }.joinToString(" • ")
                if (sub.isNotBlank()) {
                    Text(sub, fontSize = 11.sp, color = TextSecondary, maxLines = 1)
                }
            }
            Text("#${vehicle.id}", fontSize = 11.sp, color = AccentBlue.copy(0.5f))
        }
    }
}