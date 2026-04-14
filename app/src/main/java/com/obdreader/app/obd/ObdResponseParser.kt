package com.obdreader.app.obd

/**
 * Parser odpowiedzi ELM327 / OBD2
 * Konwertuje surowe dane hex na wartości z jednostkami
 */
object ObdResponseParser {

    data class ParsedValue(
        val raw: String,
        val value: Double?,
        val displayValue: String,
        val unit: String
    )

    /**
     * Parsuje odpowiedź OBD2 dla danej komendy
     */
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
                ObdCommand.ECU_NAME -> parseAscii(cleaned, command)
                ObdCommand.CALIBRATION_ID -> parseAscii(cleaned, command)
                ObdCommand.CVN -> ParsedValue(cleaned, null, cleaned, command.unit)
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
                // Bitmapki PIDów
                ObdCommand.PIDS_A, ObdCommand.PIDS_B, ObdCommand.PIDS_C,
                ObdCommand.PIDS_D, ObdCommand.PIDS_E, ObdCommand.PIDS_F, ObdCommand.PIDS_G ->
                    ParsedValue(cleaned, null, "Bitmask: $cleaned", command.unit)

                // Tryb 09
                ObdCommand.PERF_TRACKING -> parsePerfTracking(cleaned, command)
                ObdCommand.CVN -> parseCvn(cleaned, command)
                ObdCommand.ESN ->
                    ParsedValue(cleaned, null, cleaned, command.unit)
                ObdCommand.ECU_NAME, ObdCommand.CALIBRATION_ID -> parseAscii(cleaned, command)

                // Procentowe / pojedynczy bajt
                //ObdCommand.ABSOLUTE_THROTTLE_POS_B -> parsePercentA(cleaned, command)
                ObdCommand.ACCELERATOR_POS_REL -> parsePercentA(cleaned, command)
                ObdCommand.HYBRID_BATTERY_LIFE -> parsePercentA(cleaned, command)
                ObdCommand.DIESEL_EXHAUST_FLUID -> parsePercentA(cleaned, command)
                ObdCommand.ETHANOL_PERCENT -> parsePercentA(cleaned, command)
                ObdCommand.COMMANDED_DPF -> parsePercentA(cleaned, command)
                ObdCommand.SHORT_FUEL_TRIM_B1 -> parseFuelTrim(cleaned, command)
                ObdCommand.LONG_FUEL_TRIM_B1 -> parseFuelTrim(cleaned, command)
                ObdCommand.SHORT_FUEL_TRIM_B2 -> parseFuelTrim(cleaned, command)
                ObdCommand.LONG_FUEL_TRIM_B2 -> parseFuelTrim(cleaned, command)

                // Temperatury (1 bajt, offset -40)
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

                // Ciśnienia (2 bajty)
                ObdCommand.EXHAUST_PRESSURE -> parseTwoByteA(cleaned, command, 0)
                ObdCommand.DPF_DIFFERENTIAL_PRESSURE -> parseTwoByteA(cleaned, command, 0)
                ObdCommand.INTAKE_MANIFOLD_PRESSURE -> parseTwoByteA(cleaned, command, 0)
                ObdCommand.FUEL_RAIL_PRESSURE_ABS -> parseFuelRailDirect(cleaned, command)
                ObdCommand.EVAP_VAPOR_PRESSURE3 -> parseTwoByteA(cleaned, command, 0)
                ObdCommand.EVAP_SYSTEM_VAPOR -> parseTwoByteA(cleaned, command, 0)

                // Moment obrotowy (% od ref)
                ObdCommand.DRIVER_DEMAND_TORQUE -> parseSingleByteA(cleaned, command, -125)
                ObdCommand.ACTUAL_TORQUE -> parseSingleByteA(cleaned, command, -125)
                ObdCommand.ENGINE_PERCENT_TORQUE -> ParsedValue(cleaned, null, cleaned, command.unit)
                ObdCommand.REFERENCE_TORQUE -> parseTwoByteA(cleaned, command, 0)

                // RPM turbo (2 bajty * 4)
                ObdCommand.TURBO_RPM -> parseTurboRpm(cleaned, command)

                // MAF alternatywny
                ObdCommand.MASS_AIR_FLOW_SENSOR -> parseMaf(cleaned, command)

                // Lambda O2 (4 bajty: 2 lambda + 2 voltage/current)
                ObdCommand.O2_S1_WR_LAMBDA, ObdCommand.O2_S2_WR_LAMBDA,
                ObdCommand.O2_S3_WR_LAMBDA, ObdCommand.O2_S4_WR_LAMBDA,
                ObdCommand.O2_S5_WR_LAMBDA, ObdCommand.O2_S6_WR_LAMBDA,
                ObdCommand.O2_S7_WR_LAMBDA, ObdCommand.O2_S8_WR_LAMBDA -> parseO2Lambda(cleaned, command)

                // Sensory O2 (alternatywne banki)
                ObdCommand.O2_SENSOR_1_3, ObdCommand.O2_SENSOR_1_4,
                ObdCommand.O2_SENSOR_2_3, ObdCommand.O2_SENSOR_2_4 -> parseO2Voltage(cleaned, command)
                ObdCommand.O2_S5_WR_CURRENT, ObdCommand.O2_S6_WR_CURRENT,
                ObdCommand.O2_S7_WR_CURRENT, ObdCommand.O2_S8_WR_CURRENT -> parseWrCurrent(cleaned, command)
                ObdCommand.O2_SENSOR_WIDE -> parseWrCurrent(cleaned, command)

                // NOx, PM
                ObdCommand.NOX_SENSOR -> parseTwoByteA(cleaned, command, 0)
                ObdCommand.NOX_SENSOR_CORRECTED -> parseTwoByteA(cleaned, command, 0)
                ObdCommand.PM_SENSOR -> parseTwoByteA(cleaned, command, 0)

                // Prze bieg / czas
                ObdCommand.ODOMETER -> parseFourByteA(cleaned, command)
                ObdCommand.ENGINE_RUN_TIME_EXT -> parseFourByteA(cleaned, command)
                ObdCommand.ENGINE_RUN_TIME_AECD -> parseFourByteA(cleaned, command)
                ObdCommand.ENGINE_RUN_TIME_AECD2 -> parseFourByteA(cleaned, command)

                // AdBlue / bieg skrzyni
                ObdCommand.TRANSMISSION_ACTUAL_GEAR -> parseTransmissionGear(cleaned, command)
                ObdCommand.CYLINDER_FUEL_RATE -> parseTwoByteA(cleaned, command, 0)

                // Statusy tekstowe
                ObdCommand.FUEL_STATUS -> parseFuelStatus(cleaned, command)
                ObdCommand.SECONDARY_AIR_STATUS, ObdCommand.O2_SENSORS_ALT,
                ObdCommand.AUX_INPUT_STATUS, ObdCommand.MAX_VALUES, ObdCommand.MAX_MAF,
                ObdCommand.EMISSION_REQUIREMENTS, ObdCommand.AUX_IO_SUPPORTED,
                ObdCommand.COMMANDED_EGR2, ObdCommand.NOX_REAGENT_SYSTEM,
                ObdCommand.SCR_INDUCE_SYSTEM, ObdCommand.AECD_11_15, ObdCommand.AECD_16_20,
                ObdCommand.DIESEL_AFTERTREAT, ObdCommand.VGT_STATUS, ObdCommand.WASTEGATE_STATUS,
                ObdCommand.DPF_STATUS, ObdCommand.NOX_NTE_STATUS, ObdCommand.PM_NTE_STATUS ->
                    ParsedValue(cleaned, null, cleaned, command.unit)
                ObdCommand.MONITOR_STATUS_DRIVE -> parseMonitorStatus(cleaned, command)

                // ── Tryb 02 Freeze Frame ─────────────────────────────────
                // Odpowiedź trybu 02 ma nagłówek "42 XX" zamiast "41 XX".
                // Parsowanie identyczne jak tryb 01 – tylko nagłówek inny.
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

                // ── Tryb 06 On-Board Monitor Tests ───────────────────────
                // Odpowiedź trybu 06 ma strukturę:
                //   46 TID 02 CC HH HL LH LL MH ML  (CAN ISO 15765)
                // gdzie: CC = unit/limit type, HH:HL = wartość zmierzona,
                //        LH:LL = min limit, MH:ML = max limit
                // Skalowanie zależy od Unit/Limit Type (ULT byte).
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

    // ─── Helpery ────────────────────────────────────────────────────────────

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
            s == "?" ||                          // tylko sam "?" bez kontekstu
            s.startsWith("7F 00") ||             // negative response z service 00
            (s.startsWith("7F") && s.length <= 8) // short negative response

    private fun errorMessage(s: String) = when {
        s.contains("NO DATA") -> "Brak danych"
        s.contains("ERROR") -> "Błąd"
        s.contains("UNABLE") -> "Niedostępny"
        s.contains("7F") -> "Odmowa ECU"
        else -> "N/A"
    }

    /** Wydobywa bajty danych z odpowiedzi (pomija naglowek tryb+PID)
     *
     * Obsluguje trzy formaty ELM327:
     * 1) Jednoliniowy:  "41 0C 1A 2B"  -> dane: [0x1A, 0x2B]
     * 2) Wieloliniowy z ramkami: "009 0: 41 8B 51 00 03 00  1: 00 00"
     *    Parser usuwa "009","0:","1:" i zbiera bajty hex z pierwszej odpowiedzi.
     * 3) Zduplikowany (dwie odpowiedzi sklejone) - bierzemy tylko pierwsza.
     *
     * Bajty odpowiedzi: 41=tryb01, 42=tryb02(FF), 46=tryb06, 49=tryb09
     */
    private fun extractDataBytes(response: String): List<Int> {
        // Usun naglowki wieloliniowe: "009","01B" (bajt dlugosci) i "0:","1:" (numery ramek)
        val stripped = response
            .replace(Regex("\b[0-9A-Fa-f]{3}\b"), " ")
            .replace(Regex("\\d+:"), " ")

        val tokens = stripped.split(Regex("\\s+"))
            .filter { it.length == 2 && it.matches(Regex("[0-9A-Fa-f]{2}")) }
            .map { it.uppercase() }

        if (tokens.isEmpty()) return emptyList()

        // Znajdz PIERWSZY naglowek odpowiedzi i pobierz dane po nim
        val responseHeaders = setOf("41", "42", "46", "49")
        val firstHdrIdx = tokens.indexOfFirst { it in responseHeaders }

        return if (firstHdrIdx >= 0 && firstHdrIdx + 2 <= tokens.size) {
            val dataStart = firstHdrIdx + 2  // pominz naglowek + PID
            // Zatrzymaj sie przed kolejnym naglowkiem (zduplikowana odpowiedz)
            val nextHdrIdx = tokens.drop(dataStart).indexOfFirst { it in responseHeaders }
            val dataEnd = if (nextHdrIdx >= 0) dataStart + nextHdrIdx else tokens.size
            tokens.subList(dataStart, dataEnd).map { it.toInt(16) }
        } else {
            tokens.map { it.toInt(16) }
        }
    }


    // ─── Parsery dla konkretnych PIDów ──────────────────────────────────────

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
        // VIN odpowiedź ELM327 wygląda tak (wieloliniowa ISO 15765):
        // "014 \r 0: 49 02 01 31 57 30 \r 1: 4C 34 38 36 34 52 \r 2: 30 39 35 31 30 39 36"
        // Musimy: 1) usunąć nagłówki ramek ("0:", "1:", "2:" itd.)
        //         2) zebrać wszystkie bajty hex
        //         3) pominąć nagłówek OBD (49 02 01) lub (41 xx)
        //         4) zamienić hex na ASCII

        // Usuń nagłówki ramek wieloliniowych: "0:", "1:", "014", itp.
        val cleaned = r
            .replace(Regex("\\d+:"), " ")   // usuń "0:", "1:", "2:"
            .replace(Regex("\\b\\d{3}\\b"), " ") // usuń bajt długości "014"
            .replace(Regex("[^0-9A-F ]"), " ") // zostaw tylko hex i spacje
            .trim()

        val tokens = cleaned.split(Regex("\\s+"))
            .filter { it.length == 2 && it.matches(Regex("[0-9A-F]{2}")) }

        if (tokens.isEmpty()) return ParsedValue(r, null, r.take(50), cmd.unit)

        // Znajdź nagłówek odpowiedzi i pomiń go
        // Tryb 09 PID 02 = VIN: nagłówek to "49 02 01" (lub "49 02" + count)
        // Tryb 09 inne:          nagłówek to "49 XX"
        val dataBytes = when {
            tokens.size >= 3 && tokens[0] == "49" && tokens[1] == "02" -> tokens.drop(3)
            tokens.size >= 2 && tokens[0] == "49" -> tokens.drop(2)
            tokens.size >= 2 && tokens[0] == "41" -> tokens.drop(2)
            else -> tokens
        }

        // Hex → ASCII, pomijamy zera i znaki spoza zakresu
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

    // ─── Parsery trybu 02 (Freeze Frame) ────────────────────────────────────
    // Tryb 02 odpowiedź ma nagłówek "42 XX" – cleanResponse() usuwa go,
    // więc extractDataBytes() działa tak samo jak dla trybu 01.
    // Brak potrzeby osobnych funkcji – korzystamy z tych samych co tryb 01.

    // ─── Parsery trybu 06 (On-Board Monitor Tests) ───────────────────────────
    // Struktura odpowiedzi ISO 15765 (CAN): 46 TID 02 ULT VH VL LH LL HH HL
    // ULT = Unit/Limit Type, V = wartość zmierzona, L = min, H = max
    // extractDataBytes() zwróci dane po nagłówku "46 TID" (lub resztę bajtów)

    /**
     * Parsuje wynik testu O2 z trybu 06.
     * ULT 0x09 = napięcie O2, skala: 0.005 V/bit
     */
    private fun parseMode06O2(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        // Minimum: ULT(1) + Value(2) + Min(2) + Max(2) = 7 bajtów
        if (d.size < 3) return ParsedValue(r, null, "N/A", cmd.unit)
        // Pomiń ULT (d[0]) – możemy dostać dane bez nagłówka trybu
        val valueIndex = if (d.size >= 7) 1 else 0
        val v = (d[valueIndex] * 256 + d[valueIndex + 1]) * 0.005
        val display = "%.3f V".format(v)
        return ParsedValue(r, v, display, cmd.unit)
    }

    /**
     * Parsuje prąd grzejnika sondy O2 z trybu 06.
     * ULT 0x0A = prąd, skala: 0.001 A/bit
     */
    private fun parseMode06OheatCurrent(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 3) return ParsedValue(r, null, "N/A", cmd.unit)
        val valueIndex = if (d.size >= 7) 1 else 0
        val a = (d[valueIndex] * 256 + d[valueIndex + 1]) * 0.001
        val display = "%.3f A".format(a)
        return ParsedValue(r, a, display, cmd.unit)
    }

    /**
     * Parsuje wynik testu procentowego z trybu 06 (np. EGR, EVAP purge).
     * ULT 0x01 = procent, skala: 100/255 %/bit
     */
    private fun parseMode06Percent(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 3) return ParsedValue(r, null, "N/A", cmd.unit)
        val valueIndex = if (d.size >= 7) 1 else 0
        val pct = (d[valueIndex] * 256 + d[valueIndex + 1]) * 100.0 / 65535.0
        val display = "%.1f %%".format(pct)
        return ParsedValue(r, pct, display, cmd.unit)
    }

    /**
     * Parsuje temperaturę katalizatora z trybu 06.
     * ULT 0x0C = temperatura, skala: 0.1°C/bit, offset -40
     */
    private fun parseMode06CatalystTemp(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 3) return ParsedValue(r, null, "N/A", cmd.unit)
        val valueIndex = if (d.size >= 7) 1 else 0
        val temp = (d[valueIndex] * 256 + d[valueIndex + 1]) / 10.0 - 40.0
        val display = "%.1f °C".format(temp)
        return ParsedValue(r, temp, display, cmd.unit)
    }

    /**
     * Parsuje ciśnienie testu EVAP z trybu 06.
     * ULT 0x07 = ciśnienie bezwzgl. Pa, skala: 1 Pa/bit
     */
    private fun parseMode06Pressure(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 3) return ParsedValue(r, null, "N/A", cmd.unit)
        val valueIndex = if (d.size >= 7) 1 else 0
        val pa = (d[valueIndex] * 256 + d[valueIndex + 1]).toDouble()
        val display = "%.0f Pa".format(pa)
        return ParsedValue(r, pa, display, cmd.unit)
    }

    // =========================================================================
    // ██████╗  █████╗ ██████╗ ███████╗███████╗██████╗
    // ██╔══██╗██╔══██╗██╔══██╗██╔════╝██╔════╝██╔══██╗
    // ██████╔╝███████║██████╔╝███████╗█████╗  ██████╔╝
    // ██╔═══╝ ██╔══██║██╔══██╗╚════██║██╔══╝  ██╔══██╗
    // ██║     ██║  ██║██║  ██║███████║███████╗██║  ██║
    // TRYB 09 PID 08 – PERFORMANCE TRACKING (IUMPR)
    // Plik: ObdResponseParser.kt  |  Szukaj: parsePerfTracking
    // =========================================================================
    /**
     * Parser dla trybu 09 PID 08 — IUMPR (In-Use Monitor Performance Ratio).
     *
     * ECU zwraca serię par 16-bitowych liczników dla każdego monitora:
     *   [numerator_16bit] [denominator_16bit]  — powtórzone dla każdego monitora
     *
     * Odpowiedź wieloliniowa ISO 15765 (CAN), np.:
     *   01B 0: 49 08 14 19 56 51
     *         1: CA 21 9F 19 56 00 00
     *         2: 00 00 23 82 19 56 00
     *         3: 00 00 00 0A 8F 19 56
     *         4: 00 00 00 00 00 00 00
     *
     * Nagłówek: 49 08 [count_byte] — count_byte = liczba wartości (par liczb)
     * Następnie pary: NUMERATOR_HI NUMERATOR_LO DENOM_HI DENOM_LO
     *
     * Monitory (kolejność standardowa SAE J1979):
     *   0: OBD Monitor Conditions Encountered
     *   1: Ignition Counter
     *   2: Catalyst B1
     *   3: Catalyst B2
     *   4: O2 Sensor B1
     *   5: O2 Sensor B2
     *   6: EGR/VVT
     *   7: EVAP
     *   8: Secondary Air
     *   9–: dodatkowe (zależne od pojazdu)
     *
     * Wyświetlamy jako "Monitor: num/denom" — jeśli denom=0 to "n/a"
     */
    private fun parsePerfTracking(r: String, cmd: ObdCommand): ParsedValue {
        // Zbierz wszystkie tokeny hex
        val tokens = r
            .replace(Regex("\\d+:"), " ")        // usuń "0:", "1:", "2:"
            .replace(Regex("\\b\\d{3}\\b"), " ") // usuń bajt długości "01B"
            .replace(Regex("[^0-9A-F ]"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.length == 2 && it.matches(Regex("[0-9A-F]{2}")) }

        if (tokens.isEmpty()) return ParsedValue(r, null, "Brak danych", cmd.unit)

        // Pomiń nagłówek odpowiedzi "49 08 [count]"
        val dataBytes: List<Int> = when {
            tokens.size >= 3 && tokens[0] == "49" && tokens[1] == "08" ->
                tokens.drop(3).map { it.toInt(16) }
            tokens.size >= 2 && tokens[0] == "49" ->
                tokens.drop(2).map { it.toInt(16) }
            else ->
                tokens.map { it.toInt(16) }
        }

        if (dataBytes.isEmpty()) return ParsedValue(r, null, "Brak danych", cmd.unit)

        // Nazwy monitorów wg SAE J1979 Tabela D.5
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

            // Pomiń pary 0/0 — ECU wypełnia nieużywane sloty zerami
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
    // =========================================================================
    // KONIEC parsePerfTracking
    // =========================================================================
    // =========================================================================
    // NOWE PARSERY — dodane po analizie Audi A4 2024
    // Szukaj: parseBoostPressure | parseTransmissionGear |
    //         parseMonitorStatus | parseFuelStatus | parseCvn
    // =========================================================================

    /**
     * PID 0x70 — Boost Pressure Control
     *
     * Format (SAE J1979-2):
     *   41 70 [ctrl] [des_H] [des_L] [act_H] [act_L]
     *   ctrl bit0=1 -> sensor A present, bit1=1 -> sensor B
     *   des  = desired boost kPa (unsigned, skala 1 kPa/bit)
     *   act  = actual  boost kPa (unsigned, skala 1 kPa/bit)
     *
     * Stara wersja brala bajty 0-1 (ctrl+des_H) -> dawalo 512 kPa (stale).
     * Teraz bierzemy bajty 3-4 (act_H + act_L) = faktyczne cisnienie.
     * Wyswietlamy: "act kPa (desired: des kPa)"
     */
    private fun parseBoostPressure(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        // Minimum: ctrl(1) + desired(2) + actual(2) = 5 bajtow
        if (d.size < 5) {
            // Fallback do prostego 2-bajtowego gdy format inny
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

    /**
     * PID 0xA4 — Transmission Actual Gear
     *
     * Format (SAE J1979):
     *   41 A4 [ctrl] [ratio_H] [ratio_L] [gear]
     *   ctrl   = bitmapa dostepnych danych
     *   ratio  = przelozenie * 1000 (unsigned 16-bit)
     *   gear   = aktualny bieg (0=neutral/park, 1-8=bieg)
     *
     * Stara wersja: parseSingleByteA bral bajt 0 (ctrl) -> zawsze 1 (stale).
     * Teraz: bajt 3 = faktyczny bieg, bajty 1-2 = przelozenie.
     */
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
                // Krotka odpowiedz — tylko bieg
                val gear = d[0]
                ParsedValue(r, gear.toDouble(), gear.toString(), cmd.unit)
            }
            else -> ParsedValue(r, null, "N/A", cmd.unit)
        }
    }

    /**
     * PID 0x41 — Monitor Status This Drive Cycle
     *
     * Format: 4 bajty bitmapowe (identyczny jak PID 0x01 STATUS).
     * Bajt A: bit7=MIL, bity 6-0=liczba DTC
     * Bajt B: bity dostepnosci i stanu monitorow (iskrowy/diesel)
     * Bajty C-D: szczegoly monitorow (katalizator, O2, EVAP itd.)
     *
     * Zamiast surowego hex wyswietlamy czytelne podsumowanie.
     */
    private fun parseMonitorStatus(r: String, cmd: ObdCommand): ParsedValue {
        val d = extractDataBytes(r)
        if (d.size < 4) return ParsedValue(r, null, if (r.isBlank()) "N/A" else r, cmd.unit)

        val milOn   = (d[0] and 0x80) != 0
        val dtcCnt  = d[0] and 0x7F
        val isDiesel = (d[1] and 0x08) != 0

        // Bajt B bity 7-4 = dostepnosc monitorow (1=dostepny)
        // Bajt C bity 7-4 = status monitorow     (0=OK/gotowy, 1=niegotowy)
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

    /**
     * PID 0x03 — Fuel System Status
     *
     * Bajt A = status ukladu paliwowego bank 1
     * Bajt B = status ukladu paliwowego bank 2 (0 jesli 1 bank)
     *
     * Kody:
     *  0x01 = petla otwarta (rozgrzewanie)
     *  0x02 = petla zamknieta (normalnie)
     *  0x04 = petla otwarta — za bogate
     *  0x08 = petla otwarta — za ubogie
     *  0x10 = petla zamknieta z usterka (sonda zla)
     */
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

    /**
     * PID 0x06 trybu 09 — CVN (Calibration Verification Number)
     *
     * Odpowiedz wieloliniowa: "49 06 01 1C 70 B2 CB  49 06 01 79 C8 43 CF"
     * Kazdy blok to: 49 06 [count] [4 bajty CVN hex]
     * Wyswietlamy jako "CVN1: 1C70B2CB | CVN2: 79C843CF"
     */
    private fun parseCvn(r: String, cmd: ObdCommand): ParsedValue {
        // Zbierz wszystkie tokeny hex z calej odpowiedzi (wieloliniowej)
        val allTokens = r
            .replace(Regex("\\d+:"), " ")
            .split(Regex("\\s+"))
            .filter { it.length == 2 && it.matches(Regex("[0-9A-Fa-f]{2}")) }
            .map { it.uppercase() }

        if (allTokens.isEmpty()) return ParsedValue(r, null, "N/A", cmd.unit)

        // Znajdz wszystkie bloki "49 06 [cnt] [4 bajty]"
        val cvnList = mutableListOf<String>()
        var i = 0
        while (i < allTokens.size) {
            if (i + 5 < allTokens.size &&
                allTokens[i] == "49" && allTokens[i+1] == "06") {
                // allTokens[i+2] = count byte (ile bajtow CVN, zwykle 4)
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
    // =========================================================================
    // KONIEC NOWYCH PARSERÓW
    // =========================================================================

}