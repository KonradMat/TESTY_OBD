package com.obdreader.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.obdreader.app.auth.AuthManager

// ─── Dialog dodawania pojazdu ──────────────────────────────────────────────────

@Composable
fun AddVehicleDialog(
    isLoading: Boolean,
    errorMessage: String?,
    onAdd: (name: String, make: String, model: String, year: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name  by remember { mutableStateOf("") }
    var make  by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year  by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, AccentGreen.copy(0.25f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Nagłówek
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(AccentGreen.copy(0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DirectionsCar, null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Dodaj pojazd", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Nowy pojazd w Twoim garażu", fontSize = 12.sp, color = TextSecondary)
                    }
                }

                Divider(color = TextSecondary.copy(0.1f))

                // Nazwa (wymagana)
                AuthTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = "Nazwa pojazdu *",
                    error = nameError,
                    leadingIcon = {
                        Icon(Icons.Default.Label, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                )

                // Marka + Model (wiersz)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AuthTextField(
                        value = make,
                        onValueChange = { make = it },
                        label = "Marka",
                        modifier = Modifier.weight(1f),
                        leadingIcon = {
                            Icon(Icons.Default.DirectionsCar, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                    )
                    AuthTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = "Model",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Rok
                AuthTextField(
                    value = year,
                    onValueChange = { if (it.length <= 4) year = it },
                    label = "Rok produkcji",
                    keyboardType = KeyboardType.Number,
                    leadingIcon = {
                        Icon(Icons.Default.CalendarToday, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                )

                // Błąd serwera
                if (errorMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentRed.copy(0.1f))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = AccentRed, modifier = Modifier.size(14.dp))
                        Text(errorMessage, fontSize = 12.sp, color = AccentRed)
                    }
                }

                // Przyciski
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TextSecondary.copy(0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) {
                        Text("Anuluj")
                    }
                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                nameError = "Nazwa jest wymagana"
                            } else {
                                onAdd(name, make, model, year)
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGreen,
                            disabledContainerColor = AccentGreen.copy(0.4f)
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Dodaj", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ─── Panel pojazdów w zakładce Sesje ──────────────────────────────────────────

@Composable
fun VehiclesSection(
    vehicles: List<AuthManager.Vehicle>,
    isLoggedIn: Boolean,
    isLoadingVehicles: Boolean,
    vehicleError: String?,
    onAddVehicleClick: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Nagłówek sekcji
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "MOJE POJAZDY",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.5.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isLoggedIn) {
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Refresh, null,
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onAddVehicleClick,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentGreen.copy(0.15f))
                    ) {
                        Icon(
                            Icons.Default.Add, null,
                            tint = AccentGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        if (!isLoggedIn) {
            // Info dla gości
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, TextSecondary.copy(0.15f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Lock, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    Text(
                        "Zaloguj się, aby zarządzać pojazdami",
                        fontSize = 13.sp, color = TextSecondary
                    )
                }
            }
        } else if (isLoadingVehicles) {
            Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentGreen, strokeWidth = 2.dp)
            }
        } else if (vehicleError != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AccentRed.copy(0.08f)),
                border = BorderStroke(1.dp, AccentRed.copy(0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.ErrorOutline, null, tint = AccentRed, modifier = Modifier.size(18.dp))
                    Text(vehicleError, fontSize = 13.sp, color = AccentRed)
                }
            }
        } else if (vehicles.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onAddVehicleClick() },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground),
                border = BorderStroke(1.dp, AccentGreen.copy(0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.DirectionsCar, null,
                        tint = AccentGreen.copy(0.4f),
                        modifier = Modifier.size(32.dp)
                    )
                    Text("Brak pojazdów", fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    Text(
                        "Dotknij aby dodać pierwszy pojazd",
                        fontSize = 12.sp, color = TextSecondary.copy(0.6f)
                    )
                }
            }
        } else {
            vehicles.forEach { vehicle ->
                VehicleCard(vehicle = vehicle)
            }
            // Przycisk dodaj kolejny
            OutlinedButton(
                onClick = onAddVehicleClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, AccentGreen.copy(0.3f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Dodaj pojazd", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun VehicleCard(vehicle: AuthManager.Vehicle) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, AccentGreen.copy(0.15f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AccentGreen.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DirectionsCar, null, tint = AccentGreen, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    vehicle.name.ifBlank { "${vehicle.make} ${vehicle.model}" },
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary
                )
                val sub = buildString {
                    if (vehicle.make.isNotBlank()) append(vehicle.make)
                    if (vehicle.model.isNotBlank()) { if (isNotEmpty()) append(" "); append(vehicle.model) }
                    if (vehicle.year.isNotBlank()) { if (isNotEmpty()) append(" • "); append(vehicle.year) }
                }
                if (sub.isNotBlank()) {
                    Text(sub, fontSize = 12.sp, color = TextSecondary)
                }
            }
            Text(
                "#${vehicle.id}",
                fontSize = 11.sp,
                color = AccentBlue.copy(0.6f)
            )
        }
    }
}