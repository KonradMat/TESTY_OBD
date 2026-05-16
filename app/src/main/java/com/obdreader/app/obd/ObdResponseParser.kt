package com.obdreader.app.obd

object ObdResponseParser {

    data class ParsedValue(
        val raw: String,
        val value: Double?,
        val displayValue: String,
        val unit: String
    )

    fun parse(command: ObdCommand, rawResponse: String): ParsedValue {
        val cleaned = cleanResponse(rawResponse)

        if (cleaned.isEmpty() || isError(cleaned)) {
            return ParsedValue(rawResponse, null, errorMessage(cleaned), command.unit)
        }

        return try {
            when (command) {
                ObdCommand.ENGINE_RPM -> parseRpm(cleaned, command)
                ObdCommand.VEHICLE_SPEED -> parseSingleByteA(cleaned, command, 0)
                ObdCommand.COOLANT_TEMP -> parseTempA(cleaned, command)
                ObdCommand.INTAKE_TEMP -> parseTempA(cleaned, command)
                ObdCommand.AMBIENT_AIR_TEMP -> parseTempA(cleaned, command)
                //ObdCommand.OIL_TEMP -> parseTempA(cleaned, command)
                ObdCommand.ENGINE_LOAD -> parsePercentA(cleaned, command)
                ObdCommand.THROTTLE_POS -> parsePercentA(cleaned, command)
                ObdCommand.RELATIVE_THROTTLE_POS -> parsePercentA(cleaned, command)
                ObdCommand.ABSOLUTE_LOAD -> parseAbsoluteLoad(cleaned, command)
                ObdCommand.FUEL_LEVEL -> parsePercentA(cleaned, command)
                ObdCommand.EVAP_PURGE -> parsePercentA(cleaned, command)
                ObdCommand.THROTTLE_POS_B -> parsePercentA(cleaned, command)
                ObdCommand.THROTTLE_POS_C -> parsePercentA(cleaned, command)
                ObdCommand.THROTTLE_ACTUATOR -> parsePercentA(cleaned, command)
                ObdCommand.ACCELERATOR_POS_D -> parsePercentA(cleaned, command)
                ObdCommand.ACCELERATOR_POS_E -> parsePercentA(cleaned, command)
                ObdCommand.ACCELERATOR_POS_F -> parsePercentA(cleaned, command)
                ObdCommand.SHORT_FUEL_TRIM_1 -> parseFuelTrim(cleaned, command)
                ObdCommand.LONG_FUEL_TRIM_1 -> parseFuelTrim(cleaned, command)
                ObdCommand.SHORT_FUEL_TRIM_2 -> parseFuelTrim(cleaned, command)
                ObdCommand.LONG_FUEL_TRIM_2 -> parseFuelTrim(cleaned, command)
                ObdCommand.INTAKE_PRESSURE -> parseSingleByteA(cleaned, command, 0)
                ObdCommand.BARO_PRESSURE -> parseSingleByteA(cleaned, command, 0)
                ObdCommand.FUEL_PRESSURE -> parseFuelPressure(cleaned, command)
                ObdCommand.MAF -> parseMaf(cleaned, command)
                ObdCommand.TIMING_ADVANCE -> parseTimingAdvance(cleaned, command)
                ObdCommand.FUEL_INJECT_TIMING -> parseFuelInjectTiming(cleaned, command)
                ObdCommand.FUEL_RATE -> parseFuelRate(cleaned, command)
                ObdCommand.COMMANDED_EQUIV_RATIO -> parseEquivRatio(cleaned, command)
                ObdCommand.CONTROL_MODULE_VOLTAGE -> parseVoltage(cleaned, command)
                ObdCommand.RUN_TIME -> parseTwoByteA(cleaned, command, 0)
                ObdCommand.DISTANCE_W_MIL -> parseTwoByteA(cleaned, command, 0)
                ObdCommand.DISTANCE_SINCE_DTC_CLEAR -> parseTwoByteA(cleaned, command, 0)
                ObdCommand.TIME_WITH_MIL -> parseTwoByteA(cleaned, command, 0)
                ObdCommand.TIME_SINCE_DTC_CLEARED -> parseTwoByteA(cleaned, command, 0)
                ObdCommand.WARMUPS_SINCE_DTC_CLEAR -> parseSingleByteA(cleaned, command, 0)
                ObdCommand.CATALYST_TEMP_B1S1 -> parseCatalystTemp(cleaned, command)
                ObdCommand.CATALYST_TEMP_B2S1 -> parseCatalystTemp(cleaned, command)
                ObdCommand.CATALYST_TEMP_B1S2 -> parseCatalystTemp(cleaned, command)
                ObdCommand.CATALYST_TEMP_B2S2 -> parseCatalystTemp(cleaned, command)
                ObdCommand.O2_SENSOR_1_1 -> parseO2Voltage(cleaned, command)
                ObdCommand.O2_SENSOR_1_2 -> parseO2Voltage(cleaned, command)
                ObdCommand.O2_SENSOR_2_1 -> parseO2Voltage(cleaned, command)
                ObdCommand.O2_SENSOR_2_2 -> parseO2Voltage(cleaned, command)
                ObdCommand.VIN -> parseAscii(cleaned, command)
                ObdCommand.STATUS -> parseStatus(cleaned, command)
                ObdCommand.FREEZE_DTC -> ParsedValue(cleaned, null, cleaned, command.unit)
                ObdCommand.OBD_COMPLIANCE -> parseObdCompliance(cleaned, command)
                ObdCommand.FUEL_TYPE -> parseFuelType(cleaned, command)
                ObdCommand.EVAP_VAPOR_PRESSURE -> parseEvapPressure(cleaned, command)
                ObdCommand.EVAP_VAPOR_PRESSURE2 -> parseSingleByteA(cleaned, command, 0)
                ObdCommand.EGR_ERROR -> parseFuelTrim(cleaned, command)
                ObdCommand.COMMANDED_EGR -> parsePercentA(cleaned, command)
                ObdCommand.FUEL_RAIL_PRESSURE_VAC -> parseTwoByteA(cleaned, command, 0)
                ObdCommand.FUEL_RAIL_PRESSURE_DIRECT -> parseFuelRailDirect(cleaned, command)
                ObdCommand.BOOST_PRESSURE -> parseBoostPressure(cleaned, command)
                ObdCommand.O2_SENSORS -> parseSingleByteA(cleaned, command, 0)
                ObdCommand.O2_S1_WR_CURRENT -> parseWrCurrent(cleaned, command)
                ObdCommand.O2_S2_WR_CURRENT -> parseWrCurrent(cleaned, command)
                ObdCommand.O2_S3_WR_CURRENT -> parseWrCurrent(cleaned, command)
                ObdCommand.O2_S4_WR_CURRENT -> parseWrCurrent(cleaned, command)
                ObdCommand.PIDS_A, ObdCommand.PIDS_B, ObdCommand.PIDS_C,
                ObdCommand.PIDS_D, ObdCommand.PIDS_E, ObdCommand.PIDS_F, ObdCommand.PIDS_G ->
                    ParsedValue(cleaned, null, "Bitmask: $cleaned", command.unit)

                ObdCommand.PERF_TRACKING -> parsePerfTracking(cleaned, command)
                ObdCommand.CVN -> parseCvn(cleaned, command)
                ObdCommand.ESN ->
                    ParsedValue(cleaned, null, cleaned, command.unit)
                ObdCommand.ECU_NAME, ObdCommand.CALIBRATION_ID -> parseAscii(cleaned, command)

                ObdCommand.ACCELERATOR_POS_REL -> parsePercentA(cleaned, command)
                ObdCommand.HYBRID_BATTERY_LIFE -> parsePercentA(cleaned, command)
                ObdCommand.DIESEL_EXHAUST_FLUID -> parsePercentA(cleaned, command)
                ObdCommand.ETHANOL_PERCENT -> parsePercentA(cleaned, command)
                ObdCommand.COMMANDED_DPF -> parsePercentA(cleaned, command)
                ObdCommand.SHORT_FUEL_TRIM_B1 -> parseFuelTrim(cleaned, command)
                ObdCommand.LONG_FUEL_TRIM_B1 -> parseFuelTrim(cleaned, command)
                ObdCommand.SHORT_FUEL_TRIM_B2 -> parseFuelTrim(cleaned, command)
                ObdCommand.LONG_FUEL_TRIM_B2 -> parseFuelTrim(cleaned, command)

                ObdCommand.ENGINE_OIL_TEMP2 -> parseTempA(cleaned, command)
                ObdCommand.ENGINE_COOLANT_TEMP2 -> parseTempA(cleaned, command)
                ObdCommand.INTAKE_AIR_TEMP2 -> parseTempA(cleaned, command)
                ObdCommand.MANIFOLD_SURFACE_TEMP -> parseTempA(cleaned, command)
                ObdCommand.TURBO_TEMP_IN -> parseTempA(cleaned, command)
                ObdCommand.TURBO_TEMP_OUT -> parseTempA(cleaned, command)
                ObdCommand.INTERCOOLER_TEMP -> parseTempA(cleaned, command)
                ObdCommand.EGT_SENSOR_1 -> parseCatalystTemp(cleaned, command)
                ObdCommand.EGT_SENSOR_2 -> parseCatalystTemp(cleaned, command)
                ObdCommand.DPF_TEMP_IN -> parseCatalystTemp(cleaned, command)
                ObdCommand.DPF_TEMP_OUT -> parseCatalystTemp(cleaned, command)
                ObdCommand.EXHAUST_GAS_TEMP_1 -> parseCatalystTemp(cleaned, command)
                ObdCommand.EXHAUST_GAS_TEMP_2 -> parseCatalystTemp(cleaned, command)

                ObdCommand.EXHAUST_PRESSURE -> parseTwoByteA(cleaned, command, 0)
                ObdCommand.DPF_DIFFERENTIAL_PRESSURE -> parseTwoByteA(cleaned, command, 0)
                ObdCommand.INTAKE_MANIFOLD_PRESSURE -> parseTwoByteA(cleaned, command, 0)
                ObdCommand.FUEL_RAIL_PRESSURE_ABS -> parseFuelRailDirect(cleaned, command)
                ObdCommand.EVAP_VAPOR_PRESSURE3 -> parseTwoByteA(cleaned, command, 0)
                ObdCommand.EVAP_SYSTEM_VAPOR -> parseTwoByteA(cleaned, command, 0)

                ObdCommand.DRIVER_DEMAND_TORQUE -> parseSingleByteA(cleaned, command, -125)
                ObdCommand.ACTUAL_TORQUE -> parseSingleByteA(cleaned, command, -125)
                ObdCommand.ENGINE_PERCENT_TORQUE -> ParsedValue(cleaned, null, cleaned, command.unit)
                ObdCommand.REFERENCE_TORQUE -> parseTwoByteA(cleaned, command, 0)

                ObdCommand.TURBO_RPM -> parseTurboRpm(cleaned, command)

                ObdCommand.MASS_AIR_FLOW_SENSOR -> parseMaf(cleaned, command)

                ObdCommand.O2_S1_WR_LAMBDA, ObdCommand.O2_S2_WR_LAMBDA,
                ObdCommand.O2_S3_WR_LAMBDA, ObdCommand.O2_S4_WR_LAMBDA,
                ObdCommand.O2_S5_WR_LAMBDA, ObdCommand.O2_S6_WR_LAMBDA,
                ObdCommand.O2_S7_WR_LAMBDA, ObdCommand.O2_S8_WR_LAMBDA -> parseO2Lambda(cleaned, command)

                ObdCommand.O2_SENSOR_1_3, ObdCommand.O2_SENSOR_1_4,
                ObdCommand.O2_SENSOR_2_3, ObdCommand.O2_SENSOR_2_4 -> parseO2Voltage(cleaned, command)
                ObdCommand.O2_S5_WR_CURRENT, ObdCommand.O2_S6_WR_CURRENT,
                ObdCommand.O2_S7_WR_CURRENT, ObdCommand.O2_S8_WR_CURRENT -> parseWrCurrent(cleaned, command)
                ObdCommand.O2_SENSOR_WIDE -> parseWrCurrent(cleaned, command)

                ObdCommand.NOX_SENSOR -> parseTwoByteA(cleaned, command, 0)
                ObdCommand.NOX_SENSOR_CORRECTED -> parseTwoByteA(cleaned, command, 0)
                ObdCommand.PM_SENSOR -> parseTwoByteA(cleaned, command, 0)

                ObdCommand.ODOMETER -> parseFourByteA(cleaned, command)
                ObdCommand.ENGINE_RUN_TIME_EXT -> parseFourByteA(cleaned, command)
                ObdCommand.ENGINE_RUN_TIME_AECD -> parseFourByteA(cleaned, command)
                ObdCommand.ENGINE_RUN_TIME_AECD2 -> parseFourByteA(cleaned, command)

                ObdCommand.TRANSMISSION_ACTUAL_GEAR -> parseTransmissionGear(cleaned, command)
                ObdCommand.CYLINDER_FUEL_RATE -> parseTwoByteA(cleaned, command, 0)

                ObdCommand.FUEL_STATUS -> parseFuelStatus(cleaned, command)
                ObdCommand.SECONDARY_AIR_STATUS -> parseStatus(cleaned, command)
                ObdCommand.O2_SENSORS_ALT -> parseO2SensorsAlt(cleaned, command)
                ObdCommand.MAX_VALUES -> parseMaxValues(cleaned, command)
                ObdCommand.AUX_INPUT_STATUS, ObdCommand.MAX_MAF,
                ObdCommand.EMISSION_REQUIREMENTS, ObdCommand.AUX_IO_SUPPORTED,
                ObdCommand.COMMANDED_EGR2, ObdCommand.NOX_REAGENT_SYSTEM,
                ObdCommand.SCR_INDUCE_SYSTEM, ObdCommand.AECD_11_15, ObdCommand.AECD_16_20,
                ObdCommand.DIESEL_AFTERTREAT, ObdCommand.VGT_STATUS, ObdCommand.WASTEGATE_STATUS,
                ObdCommand.DPF_STATUS, ObdCommand.NOX_NTE_STATUS, ObdCommand.PM_NTE_STATUS ->
                    ParsedValue(cleaned, null, cleaned, command.unit)
                ObdCommand.MONITOR_STATUS_DRIVE -> parseMonitorStatus(cleaned, command)

                ObdCommand.FF_ENGINE_RPM -> parseRpm(cleaned, command)
                ObdCommand.FF_VEHICLE_SPEED -> parseSingleByteA(cleaned, command, 0)
                ObdCommand.FF_FUEL_LEVEL -> parsePercentA(cleaned, command)
                ObdCommand.FF_ENGINE_LOAD -> parsePercentA(cleaned, command)
                ObdCommand.FF_COOLANT_TEMP, ObdCommand.FF_INTAKE_TEMP -> parseTempA(cleaned, command)
                ObdCommand.FF_THROTTLE_POS -> parsePercentA(cleaned, command)
                ObdCommand.FF_MAF -> parseMaf(cleaned, command)
                ObdCommand.FF_SHORT_FUEL_TRIM_1, ObdCommand.FF_LONG_FUEL_TRIM_1 -> parseFuelTrim(cleaned, command)
                ObdCommand.FF_FUEL_PRESSURE -> parseFuelPressure(cleaned, command)
                ObdCommand.FF_INTAKE_PRESSURE, ObdCommand.FF_BARO_PRESSURE -> parseSingleByteA(cleaned, command, 0)
                ObdCommand.FF_TIMING_ADVANCE -> parseTimingAdvance(cleaned, command)

                ObdCommand.MON_O2_B1S1_RICH_TO_LEAN, ObdCommand.MON_O2_B1S1_LEAN_TO_RICH,
                ObdCommand.MON_O2_B2S1_RICH_TO_LEAN, ObdCommand.MON_O2_B2S1_LEAN_TO_RICH,
                ObdCommand.MON_O2_B1S2_MIN_V, ObdCommand.MON_O2_B1S2_MAX_V,
                ObdCommand.MON_O2_B2S2_MIN_V, ObdCommand.MON_O2_B2S2_MAX_V -> parseMode06O2(cleaned, command)

                ObdCommand.MON_O2_HEATER_B1S1, ObdCommand.MON_O2_HEATER_B1S2,
                ObdCommand.MON_O2_HEATER_B2S1, ObdCommand.MON_O2_HEATER_B2S2 -> parseMode06OheatCurrent(cleaned, command)

                ObdCommand.MON_EGR_FLOW_MIN, ObdCommand.MON_EGR_FLOW_MAX -> parseMode06Percent(cleaned, command)

                ObdCommand.MON_CATALYST_B1_TEMP, ObdCommand.MON_CATALYST_B2_TEMP -> parseMode06CatalystTemp(cleaned, command)

                ObdCommand.MON_EVAP_PURGE_FLOW -> parseMode06Percent(cleaned, command)
                ObdCommand.MON_EVAP_LEAK_04, ObdCommand.MON_EVAP_LEAK_020 -> parseMode06Pressure(cleaned, command)

                else -> parseRpm(cleaned, command)
            }
        } catch (e: Exception) {
            ParsedValue(rawResponse, null, "Błąd parsowania: ${e.message}", command.unit)
        }
    }

    private fun parseO2SensorsAlt(raw: String, command: ObdCommand): ParsedValue {
        val bytes = extractDataBytes(raw)
        if (bytes.isEmpty()) return ParsedValue(raw, null, "Brak danych", command.unit)
        val a = bytes[0]
        val sensors = mutableListOf<String>()
        if ((a and 0x01) != 0) sensors.add("B1S1")
        if ((a and 0x02) != 0) sensors.add("B1S2")
        if ((a and 0x04) != 0) sensors.add("B1S3")
        if ((a and 0x08) != 0) sensors.add("B1S4")
        if ((a and 0x10) != 0) sensors.add("B2S1")
        if ((a and 0x20) != 0) sensors.add("B2S2")
        if ((a and 0x40) != 0) sensors.add("B2S3")
        if ((a and 0x80) != 0) sensors.add("B2S4")
        val desc = if (sensors.isEmpty()) "Brak" else sensors.joinToString(", ")
        return ParsedValue(raw, a.toDouble(), desc, command.unit)
    }

    private fun parseMaxValues(raw: String, command: ObdCommand): ParsedValue {
        val bytes = extractDataBytes(raw)
        if (bytes.size < 4) return ParsedValue(raw, null, "Brak danych", command.unit)
        val fuelRatio = bytes[0]
        val o2Voltage = bytes[1]
        val o2Current = bytes[2]
        val pressure = bytes[3] * 10
        val desc = "AFR: $fuelRatio, O2: ${o2Voltage}V, ${o2Current}mA, P: ${pressure}kPa"
        return ParsedValue(raw, null, desc, command.unit)
    }

    private fun cleanResponse(raw: String): String {
        return raw.trim()
            .replace("\r", " ")
            .replace("\n", " ")
            .replace(">", "")
            .replace("SEARCHING...", "")
            .trim()
            .uppercase()
    }

    private fun isError(s: String) = s.contains("NO DATA") || s.contains("ERROR") ||
            s.contains("UNABLE") || s.contains("BUS INIT") ||
            s == "?" ||
            s.startsWith("7F 00") ||
            (s.startsWith("7F") && s.length <= 8)

    private fun errorMessage(s: String) = when {
        s.contains("NO DATA") -> "Brak danych"
        s.contains("ERROR") -> "Błąd"
        s.contains("UNABLE") -> "Niedostępny"
        s.contains("7F") -> "Odmowa ECU"
        else -> "N/A"
    }

    private fun extractDataBytes(response: String): List<Int> {
        val stripped = response
            .replace(Regex("\b[0-9A-Fa-f]{3}\b"), " ")
            .replace(Regex("\\d+:"), " ")

        val tokens = stripped.split(Regex("\\s+"))
            .filter { it.length == 2 && it.matches(Regex("[0-9A-Fa-f]{2}")) }
            .map { it.uppercase() }

        if (tokens.isEmpty()) return emptyList()

        val responseHeaders = setOf("41", "42", "46", "49")
        val firstHdrIdx = tokens.indexOfFirst { it in responseHeaders }

        return if (firstHdrIdx >= 0 && firstHdrIdx + 2 <= tokens.size) {
            val dataStart = firstHdrIdx + 2
            val nextHdrIdx = tokens.drop(dataStart).indexOfFirst { it in responseHeaders }
            val dataEnd = if (nextHdrIdx >= 0) dataStart + nextHdrIdx else tokens.size
            tokens.subList(dataStart, dataEnd).map { it.toInt(16) }
        } else {
            tokens.map { it.toInt(16) }
        }
    }
    private fun parseRpm(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 2) return ParsedValue(r, null, "N/A", cmd.unit)
        val rpm = ((d[0] * 256) + d[1]) / 4.0
        return ParsedValue(r, rpm, "%.0f".format(rpm), cmd.unit)
    }

    private fun parseSingleByteA(r: String, cmd: ObdCommand, offset: Int): ParsedValue {
        val d = extractDataBytes(r)
        if (d.isEmpty()) return ParsedValue(r, null, "N/A", cmd.unit)
        val v = d[0].toDouble() + offset
        return ParsedValue(r, v, "%.0f".format(v), cmd.unit)
    }

    private fun parseTwoByteA(r: String, cmd: ObdCommand, offset: Int): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 2) return ParsedValue(r, null, "N/A", cmd.unit)
        val v = (d[0] * 256 + d[1]).toDouble() + offset
        return ParsedValue(r, v, "%.0f".format(v), cmd.unit)
    }

    private fun parseTempA(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.isEmpty()) return ParsedValue(r, null, "N/A", cmd.unit)
        val temp = d[0] - 40.0
        return ParsedValue(r, temp, "%.0f".format(temp), cmd.unit)
    }

    private fun parsePercentA(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.isEmpty()) return ParsedValue(r, null, "N/A", cmd.unit)
        val pct = d[0] * 100.0 / 255.0
        return ParsedValue(r, pct, "%.1f".format(pct), cmd.unit)
    }

    private fun parseAbsoluteLoad(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 2) return ParsedValue(r, null, "N/A", cmd.unit)
        val v = (d[0] * 256 + d[1]) * 100.0 / 255.0
        return ParsedValue(r, v, "%.1f".format(v), cmd.unit)
    }

    private fun parseFuelTrim(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.isEmpty()) return ParsedValue(r, null, "N/A", cmd.unit)
        val v = (d[0] - 128) * 100.0 / 128.0
        return ParsedValue(r, v, "%.1f".format(v), cmd.unit)
    }

    private fun parseFuelPressure(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.isEmpty()) return ParsedValue(r, null, "N/A", cmd.unit)
        val kPa = d[0] * 3.0
        return ParsedValue(r, kPa, "%.0f".format(kPa), cmd.unit)
    }

    private fun parseMaf(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 2) return ParsedValue(r, null, "N/A", cmd.unit)
        val gps = (d[0] * 256 + d[1]) / 100.0
        return ParsedValue(r, gps, "%.2f".format(gps), cmd.unit)
    }

    private fun parseTimingAdvance(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.isEmpty()) return ParsedValue(r, null, "N/A", cmd.unit)
        val deg = d[0] / 2.0 - 64.0
        return ParsedValue(r, deg, "%.1f".format(deg), cmd.unit)
    }

    private fun parseFuelInjectTiming(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 2) return ParsedValue(r, null, "N/A", cmd.unit)
        val deg = (d[0] * 256 + d[1]) / 128.0 - 210.0
        return ParsedValue(r, deg, "%.1f".format(deg), cmd.unit)
    }

    private fun parseFuelRate(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 2) return ParsedValue(r, null, "N/A", cmd.unit)
        val lph = (d[0] * 256 + d[1]) * 0.05
        return ParsedValue(r, lph, "%.2f".format(lph), cmd.unit)
    }

    private fun parseEquivRatio(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 2) return ParsedValue(r, null, "N/A", cmd.unit)
        val ratio = (d[0] * 256 + d[1]) / 32768.0
        return ParsedValue(r, ratio, "%.3f".format(ratio), cmd.unit)
    }

    private fun parseVoltage(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 2) return ParsedValue(r, null, "N/A", cmd.unit)
        val v = (d[0] * 256 + d[1]) / 1000.0
        return ParsedValue(r, v, "%.2f".format(v), cmd.unit)
    }

    private fun parseCatalystTemp(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 2) return ParsedValue(r, null, "N/A", cmd.unit)
        val temp = (d[0] * 256 + d[1]) / 10.0 - 40.0
        return ParsedValue(r, temp, "%.1f".format(temp), cmd.unit)
    }

    private fun parseO2Voltage(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.isEmpty()) return ParsedValue(r, null, "N/A", cmd.unit)
        val v = d[0] / 200.0
        return ParsedValue(r, v, "%.3f".format(v), cmd.unit)
    }

    private fun parseWrCurrent(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 4) return ParsedValue(r, null, "N/A", cmd.unit)
        val ma = (d[2] * 256 + d[3]) / 256.0 - 128.0
        return ParsedValue(r, ma, "%.2f".format(ma), cmd.unit)
    }

    private fun parseEvapPressure(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 2) return ParsedValue(r, null, "N/A", cmd.unit)
        val raw = (d[0] * 256 + d[1]).toShort().toDouble()
        val pa = raw / 4.0
        return ParsedValue(r, pa, "%.1f".format(pa), cmd.unit)
    }

    private fun parseFuelRailDirect(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 2) return ParsedValue(r, null, "N/A", cmd.unit)
        val kPa = (d[0] * 256 + d[1]) * 10.0
        return ParsedValue(r, kPa, "%.0f".format(kPa), cmd.unit)
    }

    private fun parseAscii(r: String, cmd: ObdCommand): ParsedValue {
        val stripped = r
            .replace(Regex("\\d+:"), " ")
            .replace(Regex("\b[0-9A-F]{3}\b"), " ")
            .replace(Regex("[^0-9A-Fa-f ]"), " ")
            .trim()
            .uppercase()

        val tokens = stripped.split(Regex("\\s+"))
            .filter { it.length == 2 && it.matches(Regex("[0-9A-F]{2}")) }

        if (tokens.isEmpty()) return ParsedValue(r, null, "N/A", cmd.unit)

        val dataBytes = mutableListOf<String>()

        val pidByte = when {
            tokens.size >= 2 && tokens[0] == "49" -> tokens[1]
            else -> null
        }

        val multiBlock = pidByte != null &&
                tokens.zipWithNext().count { (a, b) -> a == "49" && b == pidByte } > 1

        if (multiBlock && pidByte != null) {
            var i = 0
            while (i < tokens.size) {
                if (i + 2 < tokens.size && tokens[i] == "49" && tokens[i+1] == pidByte) {
                    i += 3
                    while (i < tokens.size) {
                        val isNextHeader = i + 1 < tokens.size &&
                                tokens[i] == "49" && tokens[i+1] == pidByte
                        if (isNextHeader) break
                        dataBytes.add(tokens[i])
                        i++
                    }
                } else {
                    i++
                }
            }
        } else {
            val drop = when {
                tokens.size >= 3 && tokens[0] == "49" && tokens[1] == "02" -> 3
                tokens.size >= 3 && tokens[0] == "49" -> 3
                tokens.size >= 2 && tokens[0] == "41" -> 2
                else -> 0
            }
            dataBytes.addAll(tokens.drop(drop))
        }

        val ascii = dataBytes.joinToString("") {
            val c = it.toIntOrNull(16) ?: 0
            if (c in 32..126) c.toChar().toString() else ""
        }.trim()

        return ParsedValue(r, null, ascii.ifEmpty { "N/A" }, cmd.unit)
    }

    private fun parseStatus(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.isEmpty()) return ParsedValue(r, null, "N/A", cmd.unit)
        val milOn = (d[0] and 0x80) != 0
        val dtcCount = d[0] and 0x7F
        val status = if (milOn) "MIL ON • $dtcCount DTC" else "OK • $dtcCount DTC"
        return ParsedValue(r, dtcCount.toDouble(), status, cmd.unit)
    }

    private fun parseObdCompliance(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.isEmpty()) return ParsedValue(r, null, "N/A", cmd.unit)
        val standards = mapOf(
            1 to "OBD-II CARB", 2 to "OBD (Ford/GM)", 3 to "OBD-I", 4 to "OBD-I+II",
            5 to "OBD-II EPA", 6 to "EOBD (EU)", 9 to "JOBD (JPN)", 11 to "EMD"
        )
        val label = standards[d[0]] ?: "Typ ${d[0]}"
        return ParsedValue(r, d[0].toDouble(), label, cmd.unit)
    }

    private fun parseTurboRpm(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 2) return ParsedValue(r, null, "N/A", cmd.unit)
        val rpm = (d[0] * 256 + d[1]) * 4.0
        return ParsedValue(r, rpm, "%.0f".format(rpm), cmd.unit)
    }

    private fun parseFourByteA(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 4) return ParsedValue(r, null, "N/A", cmd.unit)
        val v = ((d[0].toLong() shl 24) or (d[1].toLong() shl 16) or
                (d[2].toLong() shl 8) or d[3].toLong()).toDouble()
        return ParsedValue(r, v, "%.0f".format(v), cmd.unit)
    }

    private fun parseO2Lambda(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 2) return ParsedValue(r, null, "N/A", cmd.unit)
        val lambda = (d[0] * 256 + d[1]) / 32768.0
        return ParsedValue(r, lambda, "%.3f".format(lambda), "λ")
    }

    private fun parseFuelType(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.isEmpty()) return ParsedValue(r, null, "N/A", cmd.unit)
        val types = mapOf(
            1 to "Benzyna", 2 to "Metanol", 3 to "Etanol", 4 to "Diesel",
            5 to "LPG", 6 to "CNG", 8 to "Elektryczny", 9 to "Hybryda"
        )
        return ParsedValue(r, d[0].toDouble(), types[d[0]] ?: "Typ ${d[0]}", cmd.unit)
    }
    private fun parseMode06O2(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 3) return ParsedValue(r, null, "N/A", cmd.unit)
        val valueIndex = if (d.size >= 7) 1 else 0
        val v = (d[valueIndex] * 256 + d[valueIndex + 1]) * 0.005
        val display = "%.3f V".format(v)
        return ParsedValue(r, v, display, cmd.unit)
    }

    private fun parseMode06OheatCurrent(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 3) return ParsedValue(r, null, "N/A", cmd.unit)
        val valueIndex = if (d.size >= 7) 1 else 0
        val a = (d[valueIndex] * 256 + d[valueIndex + 1]) * 0.001
        val display = "%.3f A".format(a)
        return ParsedValue(r, a, display, cmd.unit)
    }

    private fun parseMode06Percent(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 3) return ParsedValue(r, null, "N/A", cmd.unit)
        val valueIndex = if (d.size >= 7) 1 else 0
        val pct = (d[valueIndex] * 256 + d[valueIndex + 1]) * 100.0 / 65535.0
        val display = "%.1f %%".format(pct)
        return ParsedValue(r, pct, display, cmd.unit)
    }

    private fun parseMode06CatalystTemp(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 3) return ParsedValue(r, null, "N/A", cmd.unit)
        val valueIndex = if (d.size >= 7) 1 else 0
        val temp = (d[valueIndex] * 256 + d[valueIndex + 1]) / 10.0 - 40.0
        val display = "%.1f °C".format(temp)
        return ParsedValue(r, temp, display, cmd.unit)
    }

    private fun parseMode06Pressure(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 3) return ParsedValue(r, null, "N/A", cmd.unit)
        val valueIndex = if (d.size >= 7) 1 else 0
        val pa = (d[valueIndex] * 256 + d[valueIndex + 1]).toDouble()
        val display = "%.0f Pa".format(pa)
        return ParsedValue(r, pa, display, cmd.unit)
    }

    private fun parsePerfTracking(r: String, cmd: ObdCommand): ParsedValue {
        val tokens = r
            .replace(Regex("\\d+:"), " ")
            .replace(Regex("\\b\\d{3}\\b"), " ")
            .replace(Regex("[^0-9A-F ]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.length == 2 && it.matches(Regex("[0-9A-F]{2}")) }

        if (tokens.isEmpty()) return ParsedValue(r, null, "Brak danych", cmd.unit)

        val dataBytes: List<Int> = when {
            tokens.size >= 3 && tokens[0] == "49" && tokens[1] == "08" ->
                tokens.drop(3).map { it.toInt(16) }
            tokens.size >= 2 && tokens[0] == "49" ->
                tokens.drop(2).map { it.toInt(16) }
            else ->
                tokens.map { it.toInt(16) }
        }

        if (dataBytes.isEmpty()) return ParsedValue(r, null, "Brak danych", cmd.unit)

        val monitorNames = listOf(
            "Warunki OBD",
            "Licznik zapłonów",
            "Katalizator B1",
            "Katalizator B2",
            "Sonda O2 B1",
            "Sonda O2 B2",
            "EGR/VVT",
            "EVAP",
            "Wtórne powietrze"
        )

        val sb = StringBuilder()
        var index = 0
        var monitorIndex = 0

        while (index + 3 < dataBytes.size) {
            val numerator   = (dataBytes[index]     shl 8) or dataBytes[index + 1]
            val denominator = (dataBytes[index + 2] shl 8) or dataBytes[index + 3]
            index += 4

            if (numerator == 0 && denominator == 0) {
                monitorIndex++
                continue
            }

            val name = if (monitorIndex < monitorNames.size)
                monitorNames[monitorIndex]
            else
                "Monitor ${monitorIndex + 1}"

            val ratio = if (denominator > 0)
                " (${String.format("%.2f", numerator.toDouble() / denominator)})"
            else
                " (n/a)"

            if (sb.isNotEmpty()) sb.append("  |  ")
            sb.append("$name: $numerator/$denominator$ratio")
            monitorIndex++
        }

        val display = if (sb.isEmpty()) "Brak danych monitorów" else sb.toString()
        return ParsedValue(r, null, display, cmd.unit)
    }

    private fun parseBoostPressure(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 5) {
            if (d.size >= 2) {
                val kpa = (d[0] * 256 + d[1]).toDouble()
                return ParsedValue(r, kpa, "%.0f".format(kpa), cmd.unit)
            }
            return ParsedValue(r, null, "N/A", cmd.unit)
        }
        val desired = (d[1] * 256 + d[2]).toDouble()
        val actual  = (d[3] * 256 + d[4]).toDouble()
        val display = "%.0f (cel: %.0f)".format(actual, desired)
        return ParsedValue(r, actual, display, cmd.unit)
    }

    private fun parseTransmissionGear(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        return when {
            d.size >= 4 -> {
                val gear  = d[3]
                val ratio = (d[1] * 256 + d[2]) / 1000.0
                val gearLabel = when (gear) {
                    0    -> "N/P"
                    else -> gear.toString()
                }
                val display = if (ratio > 0.01) "$gearLabel (${String.format("%.3f", ratio)}:1)"
                else gearLabel
                ParsedValue(r, gear.toDouble(), display, cmd.unit)
            }
            d.size >= 1 -> {
                val gear = d[0]
                ParsedValue(r, gear.toDouble(), gear.toString(), cmd.unit)
            }
            else -> ParsedValue(r, null, "N/A", cmd.unit)
        }
    }

    private fun parseMonitorStatus(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 4) return ParsedValue(r, null, if (r.isBlank()) "N/A" else r, cmd.unit)

        val milOn   = (d[0] and 0x80) != 0
        val dtcCnt  = d[0] and 0x7F
        val isDiesel = (d[1] and 0x08) != 0

        val monitors = if (isDiesel) listOf(
            "NMHC"   to (Pair((d[1] shr 3) and 1, (d[2] shr 3) and 1)),
            "NOx"    to (Pair((d[1] shr 2) and 1, (d[2] shr 2) and 1)),
            "Boost"  to (Pair((d[1] shr 1) and 1, (d[2] shr 1) and 1)),
            "EGT"    to (Pair((d[1] shr 0) and 1, (d[2] shr 0) and 1))
        ) else listOf(
            "Kat"    to (Pair((d[1] shr 3) and 1, (d[2] shr 3) and 1)),
            "Ogrzew" to (Pair((d[1] shr 2) and 1, (d[2] shr 2) and 1)),
            "EVAP"   to (Pair((d[1] shr 1) and 1, (d[2] shr 1) and 1)),
            "AIR"    to (Pair((d[1] shr 0) and 1, (d[2] shr 0) and 1))
        )

        val readyList   = monitors.filter { (_, p) -> p.first == 1 && p.second == 0 }.map { it.first }
        val notReadyList= monitors.filter { (_, p) -> p.first == 1 && p.second == 1 }.map { it.first }

        val sb = StringBuilder()
        if (milOn) sb.append("MIL ON • $dtcCnt DTC") else sb.append("OK • $dtcCnt DTC")
        if (readyList.isNotEmpty())    sb.append(" | Gotowe: ${readyList.joinToString(",")}")
        if (notReadyList.isNotEmpty()) sb.append(" | Niegotowe: ${notReadyList.joinToString(",")}")

        return ParsedValue(r, dtcCnt.toDouble(), sb.toString(), cmd.unit)
    }

    private fun parseFuelStatus(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.isEmpty()) return ParsedValue(r, null, "N/A", cmd.unit)
        fun decode(b: Int) = when (b) {
            0x01 -> "Otwarta (rozgrzew)"
            0x02 -> "Zamknieta"
            0x04 -> "Otwarta (za bogata)"
            0x08 -> "Otwarta (za uboga)"
            0x10 -> "Zamknieta (blad sondy)"
            else -> "0x%02X".format(b)
        }
        val b1 = decode(d[0])
        val display = if (d.size >= 2 && d[1] != 0) "B1: $b1 | B2: ${decode(d[1])}" else b1
        return ParsedValue(r, d[0].toDouble(), display, cmd.unit)
    }

    private fun parseCvn(r: String, cmd: ObdCommand): ParsedValue {
        val allTokens = r
            .replace(Regex("\\d+:"), " ")
            .split(Regex("\\s+"))
            .filter { it.length == 2 && it.matches(Regex("[0-9A-Fa-f]{2}")) }
            .map { it.uppercase() }

        if (allTokens.isEmpty()) return ParsedValue(r, null, "N/A", cmd.unit)

        val cvnList = mutableListOf<String>()
        var i = 0
        while (i < allTokens.size) {
            if (i + 5 < allTokens.size &&
                allTokens[i] == "49" && allTokens[i+1] == "06") {
                val cvnHex = allTokens.subList(i + 3, minOf(i + 7, allTokens.size))
                    .joinToString("")
                if (cvnHex.isNotEmpty()) cvnList.add(cvnHex)
                i += 7
            } else {
                i++
            }
        }

        val display = if (cvnList.isEmpty()) r.take(30)
        else cvnList.mapIndexed { idx, v -> "CVN${idx+1}: $v" }.joinToString(" | ")
        return ParsedValue(r, null, display, cmd.unit)
    }
}