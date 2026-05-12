package com.obdreader.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.obdreader.app.auth.AuthManager

private data class FuelOption(val apiValue: String, val label: String, val icon: String)

private val FUEL_OPTIONS = listOf(
    FuelOption("petrol",   "Benzyna",     "⛽"),
    FuelOption("diesel",   "Diesel",      "🛢"),
    FuelOption("lpg",      "LPG",         "🔵"),
    FuelOption("hybrid",   "Hybryda",     "🔋")
)

@Composable
fun AddVehicleDialog(
    isLoading: Boolean,
    errorMessage: String?,
    onAdd: (name: String, make: String, model: String, year: Int,
            fuelType: String, engineDisplacementL: Double,
            cylinderCount: Int, tankCapacityL: Int, vehicleMassKg: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name  by remember { mutableStateOf("") }
    var make  by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var year  by remember { mutableStateOf("") }
    var selectedFuel by remember { mutableStateOf(FUEL_OPTIONS[0]) }
    var engineDispl  by remember { mutableStateOf("") }
    var cylinders    by remember { mutableStateOf("") }
    var tankCapacity by remember { mutableStateOf("") }
    var vehicleMass  by remember { mutableStateOf("") }
    var showTechnical by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var yearError by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        nameError = null; yearError = null
        var ok = true
        if (name.isBlank()) { nameError = "Nazwa jest wymagana"; ok = false }
        if (year.isNotBlank()) {
            val y = year.toIntOrNull()
            if (y == null || y < 1900 || y > 2100) { yearError = "Podaj prawidłowy rok (1900–2100)"; ok = false }
        }
        return ok
    }

    Dialog(onDismissRequest = { if (!isLoading) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground),
            border = BorderStroke(1.dp, AccentGreen.copy(0.25f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(AccentGreen.copy(0.12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DirectionsCar, null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Dodaj pojazd", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Nowy pojazd w Twoim garażu", fontSize = 12.sp, color = TextSecondary)
                    }
                }

                Divider(color = TextSecondary.copy(0.1f))

                AuthTextField(
                    value = name, onValueChange = { name = it; nameError = null },
                    label = "Nazwa pojazdu *", error = nameError,
                    leadingIcon = { Icon(Icons.Default.Label, null, tint = TextSecondary, modifier = Modifier.size(16.dp)) }
                )

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AuthTextField(value = make, onValueChange = { make = it }, label = "Marka", modifier = Modifier.weight(1f),
                        leadingIcon = { Icon(Icons.Default.DirectionsCar, null, tint = TextSecondary, modifier = Modifier.size(16.dp)) })
                    AuthTextField(value = model, onValueChange = { model = it }, label = "Model", modifier = Modifier.weight(1f))
                }

                AuthTextField(
                    value = year, onValueChange = { if (it.length <= 4) { year = it; yearError = null } },
                    label = "Rok produkcji", error = yearError, keyboardType = KeyboardType.Number,
                    leadingIcon = { Icon(Icons.Default.CalendarToday, null, tint = TextSecondary, modifier = Modifier.size(16.dp)) }
                )

                Column {
                    Text("Typ paliwa", fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                    FuelTypePicker(options = FUEL_OPTIONS, selected = selectedFuel, onSelect = { selectedFuel = it })
                }

                Box(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(TextSecondary.copy(0.05f))
                        .border(1.dp, TextSecondary.copy(0.12f), RoundedCornerShape(10.dp))
                        .clickable { showTechnical = !showTechnical }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Settings, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            Text("Dane techniczne (opcjonalne)", fontSize = 13.sp, color = TextSecondary)
                        }
                        Icon(if (showTechnical) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                AnimatedVisibility(visible = showTechnical, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AuthTextField(value = engineDispl, onValueChange = { engineDispl = it }, label = "Pojemność (L)", modifier = Modifier.weight(1f),
                                keyboardType = KeyboardType.Decimal, leadingIcon = { Icon(Icons.Default.Speed, null, tint = TextSecondary, modifier = Modifier.size(16.dp)) })
                            AuthTextField(value = cylinders, onValueChange = { if (it.length <= 2) cylinders = it }, label = "Cylindry", modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AuthTextField(value = tankCapacity, onValueChange = { if (it.length <= 4) tankCapacity = it }, label = "Bak (L)", modifier = Modifier.weight(1f),
                                keyboardType = KeyboardType.Number, leadingIcon = { Icon(Icons.Default.LocalGasStation, null, tint = TextSecondary, modifier = Modifier.size(16.dp)) })
                            AuthTextField(value = vehicleMass, onValueChange = { if (it.length <= 5) vehicleMass = it }, label = "Masa (kg)", modifier = Modifier.weight(1f), keyboardType = KeyboardType.Number)
                        }
                        Text("Dane techniczne pomagają w dokładniejszej analizie telemetrii", fontSize = 11.sp, color = TextSecondary.copy(0.5f), modifier = Modifier.padding(horizontal = 2.dp))
                    }
                }

                if (errorMessage != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AccentRed.copy(0.1f)).border(1.dp, AccentRed.copy(0.3f), RoundedCornerShape(8.dp)).padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = AccentRed, modifier = Modifier.size(14.dp))
                        Text(errorMessage, fontSize = 12.sp, color = AccentRed)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { if (!isLoading) onDismiss() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, TextSecondary.copy(0.3f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)) { Text("Anuluj") }
                    Button(
                        onClick = {
                            if (validate()) onAdd(name.trim(), make.trim(), model.trim(), year.toIntOrNull() ?: 0,
                                selectedFuel.apiValue, engineDispl.replace(",", ".").toDoubleOrNull() ?: 0.0,
                                cylinders.toIntOrNull() ?: 0, tankCapacity.toIntOrNull() ?: 0, vehicleMass.toIntOrNull() ?: 0)
                        },
                        enabled = !isLoading, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen, disabledContainerColor = AccentGreen.copy(0.4f))
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                        else Text("Dodaj", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FuelTypePicker(options: List<FuelOption>, selected: FuelOption, onSelect: (FuelOption) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { option ->
                    val isSelected = option == selected
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) AccentGreen.copy(0.15f) else CardBackground)
                            .border(1.dp, if (isSelected) AccentGreen.copy(0.6f) else TextSecondary.copy(0.2f), RoundedCornerShape(9.dp))
                            .clickable { onSelect(option) }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(option.icon, fontSize = 16.sp)
                            Text(option.label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) AccentGreen else TextSecondary)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun VehiclesSection(
    vehicles: List<AuthManager.Vehicle>,
    isLoggedIn: Boolean,
    isLoadingVehicles: Boolean,
    vehicleError: String?,
    onAddVehicleClick: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("MOJE POJAZDY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 1.5.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isLoggedIn) {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Refresh, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onAddVehicleClick, modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(AccentGreen.copy(0.15f))) {
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
            isLoadingVehicles -> Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentGreen, strokeWidth = 2.dp)
            }
            vehicleError != null -> Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = AccentRed.copy(0.08f)), border = BorderStroke(1.dp, AccentRed.copy(0.3f))) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.ErrorOutline, null, tint = AccentRed, modifier = Modifier.size(18.dp))
                    Text(vehicleError, fontSize = 13.sp, color = AccentRed)
                }
            }
            vehicles.isEmpty() -> Card(modifier = Modifier.fillMaxWidth().clickable { onAddVehicleClick() }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), border = BorderStroke(1.dp, AccentGreen.copy(0.2f))) {
                Column(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.DirectionsCar, null, tint = AccentGreen.copy(0.4f), modifier = Modifier.size(32.dp))
                    Text("Brak pojazdów", fontSize = 14.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                    Text("Dotknij aby dodać pierwszy pojazd", fontSize = 12.sp, color = TextSecondary.copy(0.6f))
                }
            }
            else -> {
                vehicles.forEach { VehicleCard(vehicle = it) }
                OutlinedButton(onClick = onAddVehicleClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, AccentGreen.copy(0.3f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Dodaj pojazd", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun VehicleCard(vehicle: AuthManager.Vehicle) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = CardBackground), border = BorderStroke(1.dp, AccentGreen.copy(0.15f))) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(AccentGreen.copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DirectionsCar, null, tint = AccentGreen, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(vehicle.name.ifBlank { "${vehicle.make} ${vehicle.model}".trim() }, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = buildString {
                    if (vehicle.make.isNotBlank()) append(vehicle.make)
                    if (vehicle.model.isNotBlank()) { if (isNotEmpty()) append(" "); append(vehicle.model) }
                    if (vehicle.yearLabel.isNotBlank()) { if (isNotEmpty()) append(" • "); append(vehicle.yearLabel) }
                }
                if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = TextSecondary)
                if (vehicle.fuelType.isNotBlank()) {
                    val fuelLabel = FUEL_OPTIONS.find { it.apiValue == vehicle.fuelType }?.let { "${it.icon} ${it.label}" } ?: vehicle.fuelType
                    Text(fuelLabel, fontSize = 11.sp, color = AccentBlue.copy(0.7f))
                }
            }
            Text("#${vehicle.id}", fontSize = 11.sp, color = AccentBlue.copy(0.6f))
        }
    }
}