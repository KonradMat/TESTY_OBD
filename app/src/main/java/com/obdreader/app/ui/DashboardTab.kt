package com.obdreader.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdreader.app.obd.ObdCommand
import com.obdreader.app.obd.ObdResponseParser

@Composable
fun DashboardTab(sensorData: Map<ObdCommand, ObdResponseParser.ParsedValue>) {
    LazyColumn(
        modifier            = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding      = PaddingValues(top = 12.dp, bottom = 16.dp)
    ) {
        item {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BigMetricCard(
                    ObdCommand.ENGINE_RPM,
                    sensorData[ObdCommand.ENGINE_RPM],
                    Modifier.weight(1f),
                    AccentGreen
                )
                BigMetricCard(
                    ObdCommand.VEHICLE_SPEED,
                    sensorData[ObdCommand.VEHICLE_SPEED],
                    Modifier.weight(1f),
                    AccentBlue
                )
            }
        }

        val secondaryMetrics = listOf(
            ObdCommand.COOLANT_TEMP,
            ObdCommand.FUEL_LEVEL,
            ObdCommand.ENGINE_LOAD,
            ObdCommand.INTAKE_TEMP
        )
        secondaryMetrics.chunked(2).forEach { pair ->
            item {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pair.forEach { cmd ->
                        SmallMetricCard(cmd, sensorData[cmd], Modifier.weight(1f))
                    }
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
    val hasValue = parsed != null &&
            parsed.displayValue != "--" &&
            parsed.displayValue != "Brak danych"

    Card(
        modifier = modifier.height(130.dp),
        shape    = RoundedCornerShape(14.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBackground),
        border   = BorderStroke(1.dp, accentColor.copy(alpha = if (hasValue) 0.35f else 0.12f))
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
                    command.cmdName.uppercase(),
                    fontSize      = 11.sp,
                    color         = accentColor,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    maxLines      = 1
                )
                Column {
                    Text(
                        if (hasValue) parsed!!.displayValue else "--",
                        fontSize   = 38.sp,
                        fontWeight = FontWeight.Black,
                        color      = if (hasValue) TextPrimary else TextSecondary,
                        lineHeight = 38.sp,
                        maxLines   = 1,
                        softWrap   = false
                    )
                    Text(
                        command.unit,
                        fontSize   = 12.sp,
                        color      = accentColor.copy(alpha = 0.8f),
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
        ObdCommand.FUEL_LEVEL             -> AccentBlue
        ObdCommand.CONTROL_MODULE_VOLTAGE -> Color(0xFFFFD700)
        else                              -> TextSecondary
    }
    val hasValue = parsed != null &&
            parsed.displayValue != "--" &&
            parsed.displayValue != "Brak danych"

    Card(
        modifier = modifier.height(86.dp),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier            = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                command.cmdName,
                fontSize = 11.sp,
                color    = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment     = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    if (hasValue) parsed!!.displayValue else "--",
                    fontSize   = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color      = if (hasValue) TextPrimary else TextSecondary,
                    maxLines   = 1,
                    softWrap   = false
                )
                if (command.unit.isNotBlank() && command.unit != "-") {
                    Text(
                        command.unit,
                        fontSize = 11.sp,
                        color    = accentColor,
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(12.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (isMilOn) AccentRed.copy(alpha = 0.12f) else AccentGreen.copy(alpha = 0.07f)
        ),
        border = BorderStroke(
            1.dp,
            if (isMilOn) AccentRed.copy(alpha = 0.5f) else AccentGreen.copy(alpha = 0.3f)
        )
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isMilOn) Icons.Default.Warning else Icons.Default.CheckCircle,
                null,
                tint     = if (isMilOn) AccentRed else AccentGreen,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Status OBD", fontSize = 11.sp, color = TextSecondary)
                Text(
                    parsed.displayValue,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 15.sp,
                    color      = if (isMilOn) AccentRed else AccentGreen
                )
            }
        }
    }
}