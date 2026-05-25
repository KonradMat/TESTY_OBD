package com.obdreader.app.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat

@Composable
fun DeviceSelectionScreen(
    onConnect: (BluetoothDevice) -> Unit,
    errorBanner: String? = null
) {
    val context = LocalContext.current
    val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    else
        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    if (!hasPermission) LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }

    val pairedDevices = remember(hasPermission) {
        if (!hasPermission) return@remember emptyList()
        try { BluetoothAdapter.getDefaultAdapter()?.bondedDevices?.toList() ?: emptyList() }
        catch (e: SecurityException) { emptyList() }
    }
    val obdDevices = pairedDevices.filter { device ->
        try {
            val name = device.name?.lowercase() ?: ""
            name.contains("obd") || name.contains("elm") || name.contains("vlink") ||
                    name.contains("icar") || name.contains("obdii") || name.contains("v-link")
        } catch (e: SecurityException) { false }
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Połącz z kostką OBD2",
                fontSize   = 18.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary
            )
            Text(
                "Sparuj kostkę OBD2 w ustawieniach Bluetooth, potem wróć tutaj",
                fontSize = 13.sp,
                color    = TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        if (errorBanner != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AccentRed.copy(0.1f))
                        .border(1.dp, AccentRed.copy(0.4f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment     = Alignment.CenterVertically,
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
                    fontSize      = 11.sp,
                    fontWeight    = FontWeight.Bold,
                    color         = AccentGreen,
                    letterSpacing = 1.5.sp,
                    modifier      = Modifier.padding(top = 8.dp)
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
                    fontSize      = 11.sp,
                    fontWeight    = FontWeight.Bold,
                    color         = TextSecondary,
                    letterSpacing = 1.5.sp,
                    modifier      = Modifier.padding(top = 8.dp)
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
                        Icon(
                            Icons.Default.BluetoothDisabled, null,
                            tint     = AccentOrange,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("Brak uprawnienia Bluetooth", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            "Aplikacja potrzebuje dostępu do Bluetooth.",
                            color    = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                                    permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
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
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Bluetooth, null, tint = TextSecondary, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Brak sparowanych urządzeń", color = TextSecondary)
                        Text(
                            "Sparuj kostkę OBD2 w Ustawienia → Bluetooth",
                            color    = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: BluetoothDevice,
    isObd: Boolean,
    onConnect: (BluetoothDevice) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onConnect(device) },
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (isObd) AccentGreen.copy(alpha = 0.08f) else CardBackground
        ),
        border = if (isObd) BorderStroke(1.dp, AccentGreen.copy(alpha = 0.4f)) else null
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Bluetooth, null,
                tint     = if (isObd) AccentGreen else AccentBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    device.name ?: "Nieznane urządzenie",
                    fontWeight = FontWeight.SemiBold,
                    color      = TextPrimary
                )
                Text(
                    device.address,
                    fontSize   = 12.sp,
                    color      = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }
            if (isObd) {
                Text(
                    "OBD",
                    fontSize   = 11.sp,
                    color      = AccentGreen,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier
                        .background(AccentGreen.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
        }
    }
}

@Composable
fun ConnectingScreen(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AccentGreen, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(message, color = TextPrimary, fontSize = 16.sp)
        }
    }
}