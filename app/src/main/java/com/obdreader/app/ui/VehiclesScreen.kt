package com.obdreader.app.ui

import androidx.compose.animation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.obdreader.app.auth.AuthManager

// ─── Pełny ekran zarządzania pojazdami ────────────────────────────────────────

@Composable
fun VehiclesScreen(
    vehicles: List<AuthManager.Vehicle>,
    isLoading: Boolean,
    error: String?,
    isAddingVehicle: Boolean,
    addVehicleError: String?,
    showAddDialog: Boolean,
    vehicleToDelete: AuthManager.Vehicle?,
    isDeletingVehicle: Boolean,
    onAddClick: () -> Unit,
    onAddVehicle: (name: String, make: String, model: String, year: String) -> Unit,
    onDismissAdd: () -> Unit,
    onDeleteRequest: (AuthManager.Vehicle) -> Unit,
    onDeleteConfirm: () -> Unit,
    onDeleteCancel: () -> Unit,
    onRefresh: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading && vehicles.isEmpty()) {
            // Stan ładowania (pierwsze wejście)
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
                // Nagłówek sekcji
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Moje pojazdy",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                "${vehicles.size} ${vehicleCountLabel(vehicles.size)}",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Odśwież
                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .border(1.dp, TextSecondary.copy(0.25f), CircleShape)
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = AccentGreen,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Refresh, null,
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            // Dodaj
                            IconButton(
                                onClick = onAddClick,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(AccentGreen.copy(0.15f))
                                    .border(1.dp, AccentGreen.copy(0.5f), CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Add, null,
                                    tint = AccentGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                // Błąd
                if (error != null) {
                    item {
                        ErrorBanner(message = error)
                    }
                }

                // Lista pusta
                if (vehicles.isEmpty() && !isLoading) {
                    item {
                        EmptyVehiclesPlaceholder(onAddClick = onAddClick)
                    }
                }

                // Karty pojazdów
                items(vehicles, key = { it.id }) { vehicle ->
                    VehicleItemCard(
                        vehicle = vehicle,
                        onDeleteRequest = { onDeleteRequest(vehicle) }
                    )
                }

                // Przycisk dodaj na dole (gdy jest już kilka pojazdów)
                if (vehicles.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = onAddClick,
                            modifier = Modifier.fillMaxWidth(),
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

        // Dialog dodawania
        if (showAddDialog) {
            AddVehicleDialog(
                isLoading = isAddingVehicle,
                errorMessage = addVehicleError,
                onAdd = onAddVehicle,
                onDismiss = onDismissAdd
            )
        }

        // Dialog potwierdzenia usunięcia
        vehicleToDelete?.let { vehicle ->
            DeleteVehicleDialog(
                vehicle = vehicle,
                isDeleting = isDeletingVehicle,
                onConfirm = onDeleteConfirm,
                onDismiss = onDeleteCancel
            )
        }
    }
}

// ─── Karta pojedynczego pojazdu ───────────────────────────────────────────────

@Composable
fun VehicleItemCard(
    vehicle: AuthManager.Vehicle,
    onDeleteRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, AccentGreen.copy(0.12f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Ikona pojazdu
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(AccentGreen.copy(0.2f), AccentGreen.copy(0.05f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DirectionsCar, null,
                    tint = AccentGreen,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Informacje
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    vehicle.name.ifBlank { "${vehicle.make} ${vehicle.model}".trim() },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = buildList {
                    if (vehicle.make.isNotBlank()) add(vehicle.make)
                    if (vehicle.model.isNotBlank()) add(vehicle.model)
                }.joinToString(" ")
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        fontSize = 13.sp,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (vehicle.year.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            Icons.Default.CalendarToday, null,
                            tint = AccentBlue.copy(0.6f),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            vehicle.year,
                            fontSize = 12.sp,
                            color = AccentBlue.copy(0.7f)
                        )
                    }
                }
            }

            // ID chip + usuń
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "#${vehicle.id}",
                    fontSize = 11.sp,
                    color = TextSecondary.copy(0.5f)
                )
                IconButton(
                    onClick = onDeleteRequest,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(AccentRed.copy(0.08f))
                        .border(1.dp, AccentRed.copy(0.25f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline, null,
                        tint = AccentRed.copy(0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// ─── Dialog potwierdzenia usunięcia ──────────────────────────────────────────

@Composable
fun DeleteVehicleDialog(
    vehicle: AuthManager.Vehicle,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        containerColor = CardBackground,
        shape = RoundedCornerShape(20.dp),
        icon = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AccentRed.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DeleteOutline, null,
                    tint = AccentRed,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Text(
                "Usuń pojazd",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Czy na pewno chcesz usunąć:",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Text(
                    vehicle.name.ifBlank { "${vehicle.make} ${vehicle.model}".trim() },
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
                Text(
                    "Tej operacji nie można cofnąć.",
                    color = AccentRed.copy(0.8f),
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeleting,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentRed,
                    disabledContainerColor = AccentRed.copy(0.4f)
                )
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Usuń", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeleting
            ) {
                Text("Anuluj", color = TextSecondary)
            }
        }
    )
}

// ─── Placeholder: brak pojazdów ───────────────────────────────────────────────

@Composable
private fun EmptyVehiclesPlaceholder(onAddClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAddClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, AccentGreen.copy(0.2f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(AccentGreen.copy(0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.DirectionsCar, null,
                    tint = AccentGreen.copy(0.5f),
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                "Brak pojazdów",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Text(
                "Dodaj swój pierwszy pojazd,\naby powiązać dane OBD2 z samochodem",
                fontSize = 13.sp,
                color = TextSecondary.copy(0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Dodaj pojazd", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Baner błędu ──────────────────────────────────────────────────────────────

@Composable
private fun ErrorBanner(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AccentRed.copy(0.1f))
            .border(1.dp, AccentRed.copy(0.35f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.ErrorOutline, null, tint = AccentRed, modifier = Modifier.size(18.dp))
        Text(message, fontSize = 13.sp, color = AccentRed, modifier = Modifier.weight(1f))
    }
}

// ─── Helper ───────────────────────────────────────────────────────────────────

private fun vehicleCountLabel(count: Int): String = when {
    count == 1 -> "pojazd"
    count in 2..4 -> "pojazdy"
    else -> "pojazdów"
}