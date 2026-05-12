package com.obdreader.app.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.obdreader.app.auth.AuthManager

// ─── Pełny ekran zarządzania pojazdami ────────────────────────────────────────

@Composable
fun VehiclesScreen(
    vehicles: List<AuthManager.Vehicle>,
    selectedVehicleId: Int?,
    onSelectVehicle: (Int) -> Unit,
    isLoading: Boolean,
    error: String?,
    isAddingVehicle: Boolean,
    addVehicleError: String?,
    showAddDialog: Boolean,
    vehicleToDelete: AuthManager.Vehicle?,
    isDeletingVehicle: Boolean,
    // Edycja
    vehicleToEdit: AuthManager.Vehicle?,
    isEditingVehicle: Boolean,
    editVehicleError: String?,
    showEditDialog: Boolean,
    onEditRequest: (AuthManager.Vehicle) -> Unit,
    onEditVehicle: (id: Int, name: String, make: String, model: String, year: Int,
                    fuelType: String, engineDisplacementL: Double,
                    cylinderCount: Int, tankCapacityL: Int, vehicleMassKg: Int) -> Unit,
    onDismissEdit: () -> Unit,
    // Dodawanie
    onAddClick: () -> Unit,
    onAddVehicle: (name: String, make: String, model: String, year: Int,
                   fuelType: String, engineDisplacementL: Double,
                   cylinderCount: Int, tankCapacityL: Int, vehicleMassKg: Int) -> Unit,
    onDismissAdd: () -> Unit,
    onDeleteRequest: (AuthManager.Vehicle) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteCancel: () -> Unit,
    onRefresh: () -> Unit
) {
    LaunchedEffect(Unit) { onRefresh() }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && vehicles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentGreen, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Ładowanie pojazdów...", color = TextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Moje pojazdy", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("${vehicles.size} ${vehicleCountLabel(vehicles.size)}", fontSize = 13.sp, color = TextSecondary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier.size(38.dp).clip(CircleShape).border(1.dp, TextSecondary.copy(0.25f), CircleShape)
                            ) {
                                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AccentGreen, strokeWidth = 2.dp)
                                else Icon(Icons.Default.Refresh, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = onAddClick,
                                modifier = Modifier.size(38.dp).clip(CircleShape).background(AccentGreen.copy(0.15f)).border(1.dp, AccentGreen.copy(0.5f), CircleShape)
                            ) {
                                Icon(Icons.Default.Add, null, tint = AccentGreen, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }

                if (vehicles.isNotEmpty()) {
                    item {
                        val activeName = vehicles.firstOrNull { it.id == selectedVehicleId }
                            ?.let { it.name.ifBlank { "${it.make} ${it.model}".trim() } }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentGreen.copy(0.06f))
                                .border(1.dp, AccentGreen.copy(0.2f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.TouchApp, null, tint = AccentGreen.copy(0.7f), modifier = Modifier.size(15.dp))
                            Text(
                                if (activeName != null) "Aktywny pojazd: $activeName"
                                else "Dotknij pojazd, aby go wybrać przed połączeniem",
                                fontSize = 12.sp,
                                color = if (activeName != null) AccentGreen else TextSecondary
                            )
                        }
                    }
                }

                if (error != null) item { ErrorBanner(message = error) }

                if (vehicles.isEmpty() && !isLoading) item { EmptyVehiclesPlaceholder(onAddClick = onAddClick) }

                items(vehicles, key = { it.id }) { vehicle ->
                    VehicleItemCard(
                        vehicle = vehicle,
                        isSelected = vehicle.id == selectedVehicleId,
                        onSelect = { onSelectVehicle(vehicle.id) },
                        onEditRequest = { onEditRequest(vehicle) },
                        onDeleteRequest = { onDeleteRequest(vehicle) }
                    )
                }

                if (vehicles.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = onAddClick, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, AccentGreen.copy(0.35f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen)
                        ) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Dodaj kolejny pojazd", fontSize = 14.sp)
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }

        if (showAddDialog) {
            AddVehicleDialog(isLoading = isAddingVehicle, errorMessage = addVehicleError, onAdd = onAddVehicle, onDismiss = onDismissAdd)
        }

        if (showEditDialog && vehicleToEdit != null) {
            EditVehicleDialog(
                vehicle = vehicleToEdit,
                isLoading = isEditingVehicle,
                errorMessage = editVehicleError,
                onEdit = onEditVehicle,
                onDismiss = onDismissEdit
            )
        }

        vehicleToDelete?.let { vehicle ->
            DeleteVehicleDialog(vehicle = vehicle, isDeleting = isDeletingVehicle, onConfirm = onDeleteConfirm, onDismiss = onDeleteCancel)
        }
    }
}

// ─── Karta pojedynczego pojazdu ───────────────────────────────────────────────

@Composable
fun VehicleItemCard(
    vehicle: AuthManager.Vehicle,
    isSelected: Boolean = false,
    onSelect: () -> Unit = {},
    onEditRequest: () -> Unit = {},
    onDeleteRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onSelect() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) AccentGreen.copy(0.08f) else CardBackground),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) AccentGreen.copy(0.5f) else AccentGreen.copy(0.12f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.radialGradient(listOf(
                        if (isSelected) AccentGreen.copy(0.3f) else AccentGreen.copy(0.2f),
                        AccentGreen.copy(0.05f)
                    ))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DirectionsCar, null, tint = if (isSelected) AccentGreen else AccentGreen.copy(0.7f), modifier = Modifier.size(26.dp))
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        vehicle.name.ifBlank { "${vehicle.make} ${vehicle.model}".trim() },
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isSelected) {
                        Text(
                            "Aktywny", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AccentGreen,
                            modifier = Modifier.background(AccentGreen.copy(0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                val subtitle = buildList {
                    if (vehicle.make.isNotBlank()) add(vehicle.make)
                    if (vehicle.model.isNotBlank()) add(vehicle.model)
                }.joinToString(" ")
                if (subtitle.isNotBlank()) Text(subtitle, fontSize = 13.sp, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)

                val metaLine = buildList {
                    if (vehicle.yearLabel.isNotBlank()) add(vehicle.yearLabel)
                    if (vehicle.fuelType.isNotBlank()) {
                        val fuelLabel = listOf("petrol" to "Benzyna", "diesel" to "Diesel", "lpg" to "LPG", "hybrid" to "Hybryda")
                            .find { it.first == vehicle.fuelType }?.second ?: vehicle.fuelType
                        add(fuelLabel)
                    }
                    if (vehicle.engineDisplacementL > 0.0) add("%.1f L".format(vehicle.engineDisplacementL))
                }.joinToString(" • ")
                if (metaLine.isNotBlank()) Text(metaLine, fontSize = 12.sp, color = AccentBlue.copy(0.7f))
            }

            // Przyciski akcji po prawej
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("#${vehicle.id}", fontSize = 11.sp, color = TextSecondary.copy(0.5f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = onEditRequest,
                        modifier = Modifier.size(32.dp).clip(CircleShape)
                            .background(AccentBlue.copy(0.08f))
                            .border(1.dp, AccentBlue.copy(0.25f), CircleShape)
                    ) {
                        Icon(Icons.Default.Edit, null, tint = AccentBlue.copy(0.8f), modifier = Modifier.size(15.dp))
                    }
                    IconButton(
                        onClick = onDeleteRequest,
                        modifier = Modifier.size(32.dp).clip(CircleShape)
                            .background(AccentRed.copy(0.08f))
                            .border(1.dp, AccentRed.copy(0.25f), CircleShape)
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, tint = AccentRed.copy(0.8f), modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

// ─── Dialog edycji pojazdu ────────────────────────────────────────────────────

private val EDIT_FUEL_OPTIONS = listOf(
    Triple("petrol", "Benzyna", "⛽"),
    Triple("diesel", "Diesel", "🛢"),
    Triple("lpg", "LPG", "🔵"),
    Triple("hybrid", "Hybryda", "🔋")
)

@Composable
fun EditVehicleDialog(
    vehicle: AuthManager.Vehicle,
    isLoading: Boolean,
    errorMessage: String?,
    onEdit: (id: Int, name: String, make: String, model: String, year: Int,
             fuelType: String, engineDisplacementL: Double,
             cylinderCount: Int, tankCapacityL: Int, vehicleMassKg: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var name  by remember(vehicle.id) { mutableStateOf(vehicle.name) }
    var make  by remember(vehicle.id) { mutableStateOf(vehicle.make) }
    var model by remember(vehicle.id) { mutableStateOf(vehicle.model) }
    var year  by remember(vehicle.id) { mutableStateOf(if (vehicle.year > 0) vehicle.year.toString() else "") }
    var selectedFuel by remember(vehicle.id) {
        mutableStateOf(EDIT_FUEL_OPTIONS.find { it.first == vehicle.fuelType } ?: EDIT_FUEL_OPTIONS[0])
    }
    var engineDispl  by remember(vehicle.id) {
        mutableStateOf(if (vehicle.engineDisplacementL > 0.0) "%.1f".format(vehicle.engineDisplacementL) else "")
    }
    var cylinders    by remember(vehicle.id) { mutableStateOf(if (vehicle.cylinderCount > 0) vehicle.cylinderCount.toString() else "") }
    var tankCapacity by remember(vehicle.id) { mutableStateOf(if (vehicle.tankCapacityL > 0) vehicle.tankCapacityL.toString() else "") }
    var vehicleMass  by remember(vehicle.id) { mutableStateOf(if (vehicle.vehicleMassKg > 0) vehicle.vehicleMassKg.toString() else "") }
    var showTechnical by remember { mutableStateOf(vehicle.engineDisplacementL > 0.0 || vehicle.cylinderCount > 0 || vehicle.tankCapacityL > 0 || vehicle.vehicleMassKg > 0) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var yearError by remember { mutableStateOf<String?>(null) }

    fun validate(): Boolean {
        nameError = null; yearError = null; var ok = true
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
            border = BorderStroke(1.dp, AccentBlue.copy(0.25f))
        ) {
            Column(
                modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Nagłówek
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(AccentBlue.copy(0.12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Edit, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Edytuj pojazd", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            vehicle.name.ifBlank { "${vehicle.make} ${vehicle.model}".trim() }.ifBlank { "#${vehicle.id}" },
                            fontSize = 12.sp, color = TextSecondary
                        )
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
                    EditFuelTypePicker(options = EDIT_FUEL_OPTIONS, selected = selectedFuel, onSelect = { selectedFuel = it })
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
                    }
                }

                if (errorMessage != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AccentRed.copy(0.1f))
                            .border(1.dp, AccentRed.copy(0.3f), RoundedCornerShape(8.dp)).padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = AccentRed, modifier = Modifier.size(14.dp))
                        Text(errorMessage, fontSize = 12.sp, color = AccentRed)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { if (!isLoading) onDismiss() }, modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, TextSecondary.copy(0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                    ) { Text("Anuluj") }
                    Button(
                        onClick = {
                            if (validate()) onEdit(
                                vehicle.id, name.trim(), make.trim(), model.trim(),
                                year.toIntOrNull() ?: 0, selectedFuel.first,
                                engineDispl.replace(",", ".").toDoubleOrNull() ?: 0.0,
                                cylinders.toIntOrNull() ?: 0, tankCapacity.toIntOrNull() ?: 0, vehicleMass.toIntOrNull() ?: 0
                            )
                        },
                        enabled = !isLoading, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentBlue, disabledContainerColor = AccentBlue.copy(0.4f))
                    ) {
                        if (isLoading) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Zapisz", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditFuelTypePicker(
    options: List<Triple<String, String, String>>,
    selected: Triple<String, String, String>,
    onSelect: (Triple<String, String, String>) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { option ->
                    val isSelected = option == selected
                    Box(
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                            .background(if (isSelected) AccentBlue.copy(0.15f) else CardBackground)
                            .border(1.dp, if (isSelected) AccentBlue.copy(0.6f) else TextSecondary.copy(0.2f), RoundedCornerShape(9.dp))
                            .clickable { onSelect(option) }.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(option.third, fontSize = 16.sp)
                            Text(option.second, fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) AccentBlue else TextSecondary)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

// ─── Dialog potwierdzenia usunięcia ──────────────────────────────────────────

@Composable
fun DeleteVehicleDialog(vehicle: AuthManager.Vehicle, isDeleting: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        containerColor = CardBackground, shape = RoundedCornerShape(20.dp),
        icon = {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(AccentRed.copy(0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DeleteOutline, null, tint = AccentRed, modifier = Modifier.size(24.dp))
            }
        },
        title = { Text("Usuń pojazd", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Czy na pewno chcesz usunąć:", color = TextSecondary, fontSize = 13.sp)
                Text(vehicle.name.ifBlank { "${vehicle.make} ${vehicle.model}".trim() }, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
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

@Composable
private fun EmptyVehiclesPlaceholder(onAddClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onAddClick() }, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground), border = BorderStroke(1.dp, AccentGreen.copy(0.2f))) {
        Column(modifier = Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(AccentGreen.copy(0.08f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.DirectionsCar, null, tint = AccentGreen.copy(0.5f), modifier = Modifier.size(36.dp))
            }
            Text("Brak pojazdów", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
            Text("Dodaj swój pierwszy pojazd,\naby powiązać dane OBD2 z samochodem", fontSize = 13.sp, color = TextSecondary.copy(0.6f), textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Button(onClick = onAddClick, shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Dodaj pojazd", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(AccentRed.copy(0.1f))
            .border(1.dp, AccentRed.copy(0.35f), RoundedCornerShape(10.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.ErrorOutline, null, tint = AccentRed, modifier = Modifier.size(18.dp))
        Text(message, fontSize = 13.sp, color = AccentRed, modifier = Modifier.weight(1f))
    }
}

private fun vehicleCountLabel(count: Int): String = when {
    count == 1 -> "pojazd"
    count in 2..4 -> "pojazdy"
    else -> "pojazdów"
}