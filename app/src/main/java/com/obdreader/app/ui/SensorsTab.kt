package com.obdreader.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdreader.app.obd.ObdCategory
import com.obdreader.app.obd.ObdCommand
import com.obdreader.app.obd.ObdResponseParser
import com.obdreader.app.obd.ReadPriority

@Composable
fun SensorsTab(
    sensorData: Map<ObdCommand, ObdResponseParser.ParsedValue>,
    supportedCommands: List<ObdCommand>,
    selectedCategory: ObdCategory?,
    onCategorySelect: (ObdCategory?) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                CategoryChip(
                    label    = "Wszystkie",
                    selected = selectedCategory == null,
                    onClick  = { onCategorySelect(null) }
                )
            }
            items(ObdCategory.values().toList()) { category ->
                CategoryChip(
                    label    = category.displayName,
                    selected = selectedCategory == category,
                    onClick  = { onCategorySelect(category) }
                )
            }
        }

        val displayCommands = remember(selectedCategory, supportedCommands) {
            if (selectedCategory != null) {
                selectedCategory.pids.filter {
                    it.priority == ReadPriority.VIRTUAL || it in supportedCommands
                }
            } else {
                ObdCommand.values().filter {
                    it.priority == ReadPriority.VIRTUAL || it in supportedCommands
                }
            }
        }

        LazyColumn(
            modifier            = Modifier.fillMaxSize(),
            contentPadding      = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(displayCommands, key = { it.cmdName }) { cmd ->
                SensorRow(
                    command     = cmd,
                    parsed      = sensorData[cmd],
                    isSupported = cmd in supportedCommands || cmd.priority == ReadPriority.VIRTUAL
                )
            }
        }
    }
}

@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
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
            label,
            fontSize   = 13.sp,
            color      = if (selected) AccentGreen else TextSecondary,
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
        shape    = RoundedCornerShape(10.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (isSupported) CardBackground else CardBackground.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    command.cmdName,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color      = if (isSupported) TextPrimary else TextSecondary
                )
                Text(
                    "PID: ${command.mode}${command.pid} • ${command.description}",
                    fontSize = 11.sp,
                    color    = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    parsed?.displayValue ?: if (isSupported) "..." else "N/A",
                    fontSize   = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color      = when {
                        parsed == null                        -> TextSecondary
                        parsed.displayValue == "Brak danych" -> TextSecondary
                        else                                  -> AccentGreen
                    }
                )
                if (command.unit.isNotBlank() && command.unit != "-") {
                    Text(command.unit, fontSize = 11.sp, color = TextSecondary)
                }
            }
        }
    }
}