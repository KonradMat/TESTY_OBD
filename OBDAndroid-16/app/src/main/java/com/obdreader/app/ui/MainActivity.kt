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
import com.obdreader.app.viewmodel.ObdViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ObdViewModel by viewModels { androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory(application) }

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
                ObdMainScreen(viewModel = viewModel, onConnectRequest = { device ->
                    viewModel.connect(device)
                })
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

// ─── Główny ekran ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObdMainScreen(
    viewModel: ObdViewModel,
    onConnectRequest: (BluetoothDevice) -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val sensorData by viewModel.sensorData.collectAsState()
    val supportedCommands by viewModel.supportedCommands.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isLogging by viewModel.isLogging.collectAsState()
    val vinInfo by viewModel.vinInfo.collectAsState()
    val logMessages by viewModel.logMessages.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val sessionFiles by viewModel.sessionFiles.collectAsState()
    val uploadStatus by viewModel.uploadStatus.collectAsState()
    val backendUrl = remember { mutableStateOf(viewModel.uploader.backendUrl) }
    val pendingRetryCount = remember { mutableStateOf(viewModel.uploader.pendingRetryCount()) }

    var activeTab by remember { mutableStateOf(0) } // 0=Dashboard, 1=Sensory, 2=Logi, 3=Sesje

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ─ Header ─
            ObdHeader(
                connectionState = connectionState,
                vinInfo = vinInfo,
                isScanning = isScanning,
                onDisconnect = { viewModel.disconnect() },
                onToggleScan = {
                    if (isScanning) viewModel.stopScanning()
                    else viewModel.startScanning()
                }
            )

            // ─ Tab bar ─
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
                listOf("Dashboard", "Czujniki", "Logi", "Sesje").forEachIndexed { index, title ->
                    Tab(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text(
                            title,
                            color = if (activeTab == index) AccentGreen else TextSecondary,
                            fontWeight = if (activeTab == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // ─ Content ─
            when {
                connectionState is ObdBluetoothManager.ConnectionState.Disconnected ||
                        connectionState is ObdBluetoothManager.ConnectionState.Error -> {
                    DeviceSelectionScreen(onConnect = onConnectRequest)
                }

                connectionState is ObdBluetoothManager.ConnectionState.Connecting -> {
                    ConnectingScreen(message = (connectionState as ObdBluetoothManager.ConnectionState.Connecting).message)
                }

                else -> {
                    when (activeTab) {
                        0 -> DashboardTab(sensorData = sensorData)
                        1 -> SensorsTab(
                            sensorData = sensorData,
                            supportedCommands = supportedCommands,
                            selectedCategory = selectedCategory,
                            onCategorySelect = { viewModel.selectCategory(it) }
                        )
                        2 -> LogsTab(logMessages = logMessages)
                        3 -> SessionsTab(
                            sessions = sessionFiles,
                            isLogging = isLogging,
                            uploadStatus = uploadStatus,
                            backendUrl = backendUrl.value,
                            pendingRetryCount = pendingRetryCount.value,
                            onStopLogging = { viewModel.stopLogging() },
                            onRefresh = {
                                viewModel.refreshSessionList()
                                pendingRetryCount.value = viewModel.uploader.pendingRetryCount()
                            },
                            onRetryUploads = {
                                viewModel.retryPendingUploads()
                                pendingRetryCount.value = viewModel.uploader.pendingRetryCount()
                            },
                            onUrlChange = { url ->
                                viewModel.setBackendUrl(url)
                                backendUrl.value = url
                            }
                        )
                    }
                }
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
    onDisconnect: () -> Unit,
    onToggleScan: () -> Unit
) {
    val isConnected = connectionState is ObdBluetoothManager.ConnectionState.Connected
    val deviceName = (connectionState as? ObdBluetoothManager.ConnectionState.Connected)?.deviceName ?: ""
    // Pokaż VIN tylko jeśli wygląda jak poprawny VIN (17 znaków alfanum.)
    val cleanVin = vinInfo.filter { it.isLetterOrDigit() }.uppercase()
    val displayVin = if (cleanVin.length in 10..17) cleanVin else ""

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
            // Lewa strona: tytuł + info
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
            }

            // Prawa strona: przyciski
            if (isConnected) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    IconButton(
                        onClick = onToggleScan,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (isScanning) AccentGreen.copy(alpha = 0.15f) else Color.Transparent)
                            .border(1.dp, if (isScanning) AccentGreen else TextSecondary, CircleShape)
                    ) {
                        Icon(
                            if (isScanning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (isScanning) AccentGreen else TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onDisconnect,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .border(1.dp, AccentRed.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.BluetoothDisabled,
                            contentDescription = null,
                            tint = AccentRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─── Ekran parowania ──────────────────────────────────────────────────────────

@Composable
fun DeviceSelectionScreen(onConnect: (BluetoothDevice) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Sprawdź uprawnienia runtime przed wywołaniem getBondedDevices()
    // Na Android 12+ (API 31+) BLUETOOTH_CONNECT musi być przyznane przez użytkownika
    val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ActivityCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ActivityCompat.checkSelfPermission(
            context, Manifest.permission.BLUETOOTH
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Poproś o uprawnienie jeśli nie ma
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* odświeżenie nastąpi automatycznie przez rekompozyję */ }

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
        } catch (e: SecurityException) {
            emptyList()
        }
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
                "Wybierz urządzenie OBD2",
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
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BluetoothDisabled, contentDescription = null,
                            tint = AccentOrange, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Brak uprawnienia Bluetooth", color = TextPrimary,
                            fontWeight = FontWeight.Bold)
                        Text(
                            "Aplikacja potrzebuje dostępu do Bluetooth. Zaakceptuj uprawnienie w oknie systemowym.",
                            color = TextSecondary, fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                            Text("Przyznaj uprawnienie", color = Color.Black,
                                fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else if (pairedDevices.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Bluetooth, contentDescription = null,
                            tint = TextSecondary, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Brak sparowanych urządzeń", color = TextSecondary)
                        Text("Sparuj kostkę OBD2 w Ustawienia → Bluetooth",
                            color = TextSecondary, fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(device: BluetoothDevice, isObd: Boolean, onConnect: (BluetoothDevice) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onConnect(device) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isObd) AccentGreen.copy(alpha = 0.08f) else CardBackground
        ),
        border = if (isObd) BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f)) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Bluetooth,
                contentDescription = null,
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
                    device.address,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (isObd) {
                Text(
                    "OBD",
                    fontSize = 11.sp,
                    color = AccentGreen,
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
        // Dwie duże karty RPM + Prędkość
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BigMetricCard(ObdCommand.ENGINE_RPM,  sensorData[ObdCommand.ENGINE_RPM],  Modifier.weight(1f), AccentGreen)
                BigMetricCard(ObdCommand.VEHICLE_SPEED, sensorData[ObdCommand.VEHICLE_SPEED], Modifier.weight(1f), AccentBlue)
            }
        }

        // Siatka 2×N pozostałych metryk
        val secondaryMetrics = listOf(
            ObdCommand.COOLANT_TEMP, ObdCommand.FUEL_LEVEL,
            ObdCommand.ENGINE_LOAD,  ObdCommand.INTAKE_TEMP,
            //ObdCommand.OIL_TEMP,     ObdCommand.CONTROL_MODULE_VOLTAGE
        )
        secondaryMetrics.chunked(2).forEach { pair ->
            item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { cmd ->
                    SmallMetricCard(cmd, sensorData[cmd], Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            }
        }

        // Status OBD
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
                .background(Brush.verticalGradient(
                    listOf(accentColor.copy(alpha = if (hasValue) 0.08f else 0.02f), Color.Transparent)
                ))
                .padding(14.dp)
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                Text(
                    command.cmdName.uppercase(),
                    fontSize = 11.sp, color = accentColor,
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
                        command.unit,
                        fontSize = 12.sp, color = accentColor.copy(alpha = 0.8f),
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
        //ObdCommand.COOLANT_TEMP, ObdCommand.OIL_TEMP, ObdCommand.INTAKE_TEMP -> AccentOrange
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
        // Category chips
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

        // Sensor list
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
            .background(
                if (selected) AccentGreen.copy(alpha = 0.2f)
                else CardBackground
            )
            .border(
                1.dp,
                if (selected) AccentGreen else TextSecondary.copy(alpha = 0.3f),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    command.cmdName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isSupported) TextPrimary else TextSecondary
                )
                Text(
                    "PID: ${command.mode}${command.pid} • ${command.description}",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    parsed?.displayValue ?: if (isSupported) "..." else "N/A",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
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
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
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

// Potrzebna funkcja extension
fun Modifier.tabIndicatorOffset(currentTabPosition: androidx.compose.material3.TabPosition): Modifier {
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
    uploadStatus: com.obdreader.app.obd.TelemetryUploader.UploadStatus,
    backendUrl: String,
    pendingRetryCount: Int,
    onStopLogging: () -> Unit,
    onRefresh: () -> Unit,
    onRetryUploads: () -> Unit,
    onUrlChange: (String) -> Unit
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
                        placeholder = { Text("http://192.168.1.x:5032/api/...", fontSize = 12.sp) }
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Emulator: 10.0.2.2 = localhost hosta Fizyczne urządzenie: adres IP serwera w sieci lokalnej",
                        fontSize = 11.sp, color = TextSecondary
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── Status uploadu ──
        item {
            val (uploadColor, uploadText, uploadSub) = when (val s = uploadStatus) {
                is com.obdreader.app.obd.TelemetryUploader.UploadStatus.IDLE ->
                    Triple(TextSecondary, "Oczekiwanie", "Nie wysłano jeszcze danych")
                is com.obdreader.app.obd.TelemetryUploader.UploadStatus.UPLOADING ->
                    Triple(AccentBlue, "Wysyłanie...", "Trwa transfer danych")
                is com.obdreader.app.obd.TelemetryUploader.UploadStatus.SUCCESS ->
                    Triple(AccentGreen, "✓ Wysłano ${s.recordsSent} rekordów", s.timestamp.take(19).replace("T"," "))
                is com.obdreader.app.obd.TelemetryUploader.UploadStatus.FAILED ->
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
                            Text("STATUS UPLOADU", fontSize = 10.sp, color = TextSecondary, letterSpacing = 1.sp)
                            Text(uploadText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = uploadColor)
                            if (uploadSub.isNotBlank())
                                Text(uploadSub, fontSize = 11.sp, color = TextSecondary)
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
                modifier = Modifier.fillMaxWidth().clickable { showUrlDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("ENDPOINT BACKENDU", fontSize = 10.sp, color = TextSecondary, letterSpacing = 1.sp)
                        Text(
                            backendUrl,
                            fontSize = 12.sp, color = AccentBlue,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 2, overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Default.Edit, contentDescription = null,
                        tint = TextSecondary, modifier = Modifier.size(18.dp).padding(start = 4.dp))
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
                border = BorderStroke(1.dp, if (isLogging) AccentGreen.copy(0.4f) else TextSecondary.copy(0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape)
                            .background(if (isLogging) AccentGreen else TextSecondary))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                if (isLogging) "Logowanie aktywne" else "Logowanie nieaktywne",
                                fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                color = if (isLogging) AccentGreen else TextSecondary
                            )
                            Text("Dane zapisywane do JSON + wysyłane na backend",
                                fontSize = 11.sp, color = TextSecondary)
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

        item {
            Text("ZAPISANE SESJE (${sessions.size})", fontSize = 10.sp,
                fontWeight = FontWeight.Bold, color = TextSecondary,
                letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 4.dp))
        }

        if (sessions.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center) {
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
                        Text(session.sessionId, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold, color = TextPrimary,
                            fontFamily = FontFamily.Monospace)
                        Text("${session.sizeKb} KB", fontSize = 12.sp, color = AccentBlue)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("${session.recordCount} rek.", fontSize = 11.sp, color = TextSecondary)
                        if (session.vin.isNotBlank())
                            Text("VIN: ${session.vin.take(17)}", fontSize = 11.sp,
                                color = TextSecondary, fontFamily = FontFamily.Monospace)
                    }
                    Text(session.file.absolutePath, fontSize = 10.sp,
                        color = TextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
