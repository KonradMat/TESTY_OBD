package com.obdreader.app.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.obdreader.app.obd.ObdCommand
import com.obdreader.app.obd.ObdResponseParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class ObdBluetoothManager {

    companion object {
        private const val TAG = "ObdBluetooth"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val COMMAND_TIMEOUT_MS   = 3000L
        private const val INIT_DELAY_MS        = 1500L

        private const val TIMEOUT_STANDARD_MS  = 2000L   // VW, większość
        private const val TIMEOUT_SLOW_MS      = 4000L   // BMW, Opel, wolne ECU
        private const val TIMEOUT_INIT_MS      = 5000L   // test protokołu przy init

        private val PROTOCOL_FALLBACK_ORDER = listOf("6","7","8","9","5","4","3")
    }

    sealed class ConnectionState {
        object Disconnected : ConnectionState()
        data class Connecting(val message: String) : ConnectionState()
        data class Connected(val deviceName: String) : ConnectionState()
        data class Error(val message: String) : ConnectionState()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    private val _supportedCommands = MutableStateFlow<List<ObdCommand>>(emptyList())
    val supportedCommands: StateFlow<List<ObdCommand>> = _supportedCommands

    private val _vinInfo = MutableStateFlow<String>("")
    val vinInfo: StateFlow<String> = _vinInfo

    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    private var activeProtocol: String = "0"
    private var commandTimeoutMs: Long = TIMEOUT_STANDARD_MS

    val isConnected: Boolean
        get() = bluetoothSocket?.isConnected == true

    val activeProtocolName: String
        get() = when (activeProtocol) {
            "0"  -> "Auto"
            "1"  -> "J1850 PWM"
            "2"  -> "J1850 VPW"
            "3"  -> "ISO 9141-2"
            "4"  -> "KWP 5baud"
            "5"  -> "KWP fast"
            "6"  -> "CAN 11bit/500k"
            "7"  -> "CAN 29bit/500k"
            "8"  -> "CAN 11bit/250k"
            "9"  -> "CAN 29bit/250k"
            else -> "SP$activeProtocol"
        }

    val calibratedTimeoutMs: Long get() = commandTimeoutMs

    suspend fun connect(device: BluetoothDevice, maxRetries: Int = 3): Boolean =
        withContext(Dispatchers.IO) {
            repeat(maxRetries) { attempt ->
                _connectionState.value =
                    ConnectionState.Connecting("Próba ${attempt + 1}/$maxRetries...")
                Log.d(TAG, "Próba połączenia ${attempt + 1}/$maxRetries z ${device.name}")

                try {
                    closeSocket()
                    @Suppress("DEPRECATION")
                    BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()

                    val socket = tryCreateSocket(device)
                    if (socket != null) {
                        bluetoothSocket = socket
                        inputStream = socket.inputStream
                        outputStream = socket.outputStream

                        delay(INIT_DELAY_MS)

                        // Wyczyść bufor przed inicjalizacją
                        flushInput()

                        if (initializeElm327()) {
                            Log.d(TAG, "ELM327 zainicjalizowany pomyślnie")
                            readVin()
                            detectSupportedCommands()
                            _connectionState.value =
                                ConnectionState.Connected(device.name ?: "OBD Adapter")
                            return@withContext true
                        } else {
                            Log.w(TAG, "Inicjalizacja ELM327 nieudana")
                            closeSocket()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Błąd połączenia (próba ${attempt + 1}): ${e.message}")
                    closeSocket()
                }

                if (attempt < maxRetries - 1) {
                    val waitMs = (3000 + attempt * 2000).toLong()
                    _connectionState.value =
                        ConnectionState.Connecting("Oczekiwanie ${waitMs / 1000}s...")
                    delay(waitMs)
                }
            }

            _connectionState.value =
                ConnectionState.Error("Nie udało się połączyć po $maxRetries próbach")
            false
        }

    private fun tryCreateSocket(device: BluetoothDevice): BluetoothSocket? {
        return try {
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            Log.d(TAG, "Połączono przez SPP UUID")
            socket
        } catch (e: IOException) {
            Log.w(TAG, "SPP UUID nieudany, próba reflection: ${e.message}")
            //reflection
            try {
                val socket = device.javaClass
                    .getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, 1) as BluetoothSocket
                socket.connect()
                Log.d(TAG, "Połączono przez reflection (kanał 1)")
                socket
            } catch (e2: Exception) {
                Log.e(TAG, "Reflection nieudany: ${e2.message}")
                null
            }
        }
    }


    private suspend fun initializeElm327(): Boolean {
        Log.d(TAG, "Rozpoczynam inicjalizację ELM327...")

        sendRawCommand("ATZ", timeoutMs = 4000)
        delay(1500)
        flushInput()

        val baseConfig = listOf(
            "ATE0",
            "ATL0",
            "ATS1",
            "ATH0",
            "ATAT2",
            "ATST FF"
        )

        for (cmd in baseConfig) {
            delay(150)
            val response = sendRawCommand(cmd, timeoutMs = 2000)
            Log.d(TAG, "INIT $cmd -> '$response'")
            if (response.isBlank()) {
                Log.e(TAG, "Brak odpowiedzi na $cmd — przerywam init")
                return false
            }
        }

        val protocolFound = tryProtocols()
        if (!protocolFound) {
            Log.e(TAG, "Żaden protokół nie odpowiada — samochód nie obsługuje OBD2?")
            return false
        }

        calibrateTimeout()

        Log.d(TAG, "Inicjalizacja OK — protokół SP$activeProtocol, timeout ${commandTimeoutMs}ms")
        return true
    }

    private suspend fun tryProtocols(): Boolean {
        delay(200)
        sendRawCommand("ATSP0", timeoutMs = 2000)
        delay(500)

        val autoTest = sendRawCommand("010C", timeoutMs = TIMEOUT_INIT_MS)
        Log.d(TAG, "ATSP0 test -> '$autoTest'")

        if (isValidTestResponse(autoTest)) {
            activeProtocol = "0"
            val dp = sendRawCommand("ATDP", timeoutMs = 1000)
            Log.d(TAG, "Wykryty protokół (ATDP): '$dp'")
            return true
        }

        Log.w(TAG, "ATSP0 nie działa — próbuję kolejnych protokołów...")

        for (sp in PROTOCOL_FALLBACK_ORDER) {
            delay(300)
            sendRawCommand("ATSP$sp", timeoutMs = 2000)
            delay(500)

            val test = sendRawCommand("010C", timeoutMs = TIMEOUT_INIT_MS)
            Log.d(TAG, "ATSP$sp test -> '$test'")

            if (isValidTestResponse(test)) {
                activeProtocol = sp
                Log.d(TAG, "Znaleziono działający protokół: SP$sp")
                return true
            }

            flushInput()
        }

        return false
    }

    private suspend fun calibrateTimeout() {
        val samples = mutableListOf<Long>()

        // Zmierz czas 3 odpowiedzi na RPM
        repeat(3) {
            val start = System.currentTimeMillis()
            val resp = sendRawCommand("010C", timeoutMs = TIMEOUT_SLOW_MS)
            val elapsed = System.currentTimeMillis() - start
            if (resp.isNotBlank() && !resp.contains("NO DATA")) {
                samples.add(elapsed)
            }
            delay(100)
        }

        commandTimeoutMs = if (samples.isEmpty()) {
            TIMEOUT_SLOW_MS
        } else {
            val avgMs = samples.average().toLong()
            val withMargin = (avgMs * 1.5).toLong().coerceIn(500L, TIMEOUT_SLOW_MS)
            Log.d(TAG, "Kalibracja timeoutu: avg=${avgMs}ms → używam ${withMargin}ms")
            withMargin
        }
    }

    private fun isValidTestResponse(response: String): Boolean {
        val r = response.uppercase().trim()
        if (r.isBlank()) return false
        if (r.contains("UNABLE TO CONNECT")) return false
        if (r.contains("BUS INIT ERROR")) return false
        if (r.contains("NO PROTOCOL")) return false
        if (r.contains("CAN ERROR")) return false
        if (r.contains("ERR")) return false
        if (r.contains("NO DATA")) return true
        if (r.contains("41") || r.contains("410C") || r.contains("41 0C")) return true
        val hexOnly = r.replace(" ", "")
        return hexOnly.matches(Regex("[0-9A-F]+")) && hexOnly.length >= 4
    }

    private suspend fun readVin() {
        try {
            val response = sendRawCommand("0902", timeoutMs = 4000)
            Log.d(TAG, "VIN raw: '$response'")
            val parsed = ObdResponseParser.parse(ObdCommand.VIN, response)
            if (parsed.displayValue.isNotBlank() && parsed.displayValue != "N/A" && parsed.displayValue.length > 3) {
                _vinInfo.value = parsed.displayValue
                Log.d(TAG, "VIN odczytany: ${parsed.displayValue}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Nie udało się odczytać VIN: ${e.message}")
        }
    }

    private suspend fun detectSupportedCommands() {
        _connectionState.value = ConnectionState.Connecting("Odczyt bitmapy PIDów...")
        val supported = mutableListOf<ObdCommand>()

        val supportedPidSet = mutableSetOf<String>()
        val pidGroups = listOf("00", "20", "40", "60", "80", "A0", "C0")

        for (groupPid in pidGroups) {
            val rawCmd = "01$groupPid"
            val response = sendRawCommand(rawCmd, timeoutMs = commandTimeoutMs)
            Log.d(TAG, "Bitmapa $rawCmd -> '$response'")
            delay(150)

            val pidBits = parsePidBitmap(response, groupPid)
            if (pidBits.isEmpty()) {
                Log.d(TAG, "  Bitmapa $rawCmd pusta lub błąd – pomijam grupę")
                continue
            }
            supportedPidSet.addAll(pidBits)
            Log.d(TAG, "  Grupa $groupPid: wykryto ${pidBits.size} PIDów: $pidBits")
        }

        Log.d(TAG, "Bitmapa trybu 01: łącznie ${supportedPidSet.size} PIDów")

        _connectionState.value = ConnectionState.Connecting("Mapowanie czujników tryb 01...")

        val bitmapPids = setOf("00", "20", "40", "60", "80", "A0", "C0")
        val mode01Commands = ObdCommand.values().filter { cmd ->
            cmd.mode == "01" && cmd.pid.uppercase() !in bitmapPids
        }

        if (supportedPidSet.isNotEmpty()) {
            mode01Commands.forEach { cmd ->
                if (supportedPidSet.contains(cmd.pid.uppercase())) {
                    supported.add(cmd)
                    Log.d(TAG, "  ✓ M01 (bitmapa) ${cmd.cmdName} [${cmd.pid}]")
                }
            }
        } else {
            Log.w(TAG, "Bitmapa pusta — fallback na bezpośrednie testowanie PIDów trybu 01")
            _connectionState.value = ConnectionState.Connecting("Skanowanie PIDów tryb 01 (fallback)...")

            mode01Commands.forEachIndexed { index, cmd ->
                _connectionState.value = ConnectionState.Connecting(
                    "Skanowanie M01 ${index + 1}/${mode01Commands.size}: ${cmd.cmdName}"
                )
                val response = sendRawCommand("01${cmd.pid}", timeoutMs = commandTimeoutMs)
                if (isValidObdResponse(response, cmd.pid)) {
                    supported.add(cmd)
                    Log.d(TAG, "  ✓ M01 (fallback) ${cmd.cmdName}")
                }
                delay(if (commandTimeoutMs > TIMEOUT_STANDARD_MS) 120L else 80L)
            }
        }

        _connectionState.value = ConnectionState.Connecting("Sprawdzanie Freeze Frame (tryb 02)...")
        val freezeResponse = sendRawCommand("0200", timeoutMs = commandTimeoutMs)
        Log.d(TAG, "Freeze Frame 0200 -> '$freezeResponse'")
        delay(150)

        val freezeSupported = with(freezeResponse.uppercase().trim()) {
            !isBlank() &&
                    !contains("UNABLE TO CONNECT") &&
                    !contains("BUS INIT ERROR") &&
                    !contains("7F 02") &&    // negative response specyficznie dla trybu 02
                    !contains("ERROR")
        }
        if (freezeSupported) {
            supported.add(ObdCommand.FREEZE_DTC)
            Log.d(TAG, "  ✓ Tryb 02 Freeze Frame obsługiwany")
            // Dodaj wszystkie komendy Freeze Frame – będą zwracać NO DATA gdy nie ma DTC,
            // ale są obsługiwane przez ECU. NO DATA jest prawidłową odpowiedzią (brak błędów).
            val freezeCommands = ObdCommand.values().filter { it.mode == "02" }
            supported.addAll(freezeCommands)
            Log.d(TAG, "  Tryb 02: dodano ${freezeCommands.size} komend Freeze Frame")
        }

        _connectionState.value = ConnectionState.Connecting("Sprawdzanie testów monitorów (tryb 06)...")
        val mode06Response = sendRawCommand("0600", timeoutMs = commandTimeoutMs)
        Log.d(TAG, "Tryb 06 test -> '$mode06Response'")
        delay(150)

        val mode06Supported = with(mode06Response.uppercase().trim()) {
            !isBlank() &&
                    !contains("NO DATA") &&
                    !contains("UNABLE") &&
                    !contains("ERROR") &&
                    !contains("7F 06")
        }
        if (mode06Supported) {
            val mode06Commands = ObdCommand.values().filter { it.mode == "06" }
            mode06Commands.forEach { cmd ->
                val response = sendRawCommand("06${cmd.pid}", timeoutMs = commandTimeoutMs)
                val valid = with(response.uppercase().trim()) {
                    !isBlank() && !contains("NO DATA") && !contains("ERROR") &&
                            !contains("UNABLE") && !contains("7F 06")
                }
                if (valid) {
                    supported.add(cmd)
                    Log.d(TAG, "  ✓ M06 (test) ${cmd.cmdName} [TID=${cmd.pid}]")
                }
                delay(80)
            }
            Log.d(TAG, "  Tryb 06: wykryto ${supported.count { it.mode == "06" }} testów")
        }

        _connectionState.value = ConnectionState.Connecting("Info o pojeździe (tryb 09)...")
        val mode09BitmapResponse = sendRawCommand("0900", timeoutMs = commandTimeoutMs)
        Log.d(TAG, "Tryb 09 bitmapa -> '$mode09BitmapResponse'")
        delay(150)

        val mode09PidSet = parsePidBitmap(mode09BitmapResponse, "00")
        Log.d(TAG, "Tryb 09 bitmapa: PIDs = $mode09PidSet")

        val mode09Commands = listOf(
            ObdCommand.VIN, ObdCommand.ECU_NAME, ObdCommand.CALIBRATION_ID,
            ObdCommand.CVN, ObdCommand.PERF_TRACKING, ObdCommand.ESN
        )

        mode09Commands.forEach { cmd ->
            val inBitmap = mode09PidSet.isNotEmpty() && mode09PidSet.contains(cmd.pid.uppercase())
            if (inBitmap) {
                supported.add(cmd)
                Log.d(TAG, "  ✓ M09 (bitmapa) ${cmd.cmdName}")
            } else {
                val response = sendRawCommand("09${cmd.pid}", timeoutMs = commandTimeoutMs + 1000L)
                if (isValidObdResponse(response, cmd.pid)) {
                    supported.add(cmd)
                    Log.d(TAG, "  ✓ M09 (test) ${cmd.cmdName}")
                }
                delay(200)
            }
        }

        _supportedCommands.value = supported
        val byMode = supported.groupBy { it.mode }
        Log.d(TAG, "=== Wykryto ${supported.size} czujników ===")
        byMode.forEach { (mode, cmds) ->
            Log.d(TAG, "  Tryb $mode: ${cmds.size} czujników")
            cmds.forEach { Log.d(TAG, "    - ${it.cmdName} (${it.mode}${it.pid})") }
        }
    }

    private fun parsePidBitmap(response: String, groupPid: String): Set<String> {
        val result = mutableSetOf<String>()
        val r = response.uppercase().trim()
        if (r.isBlank() || r.contains("NO DATA") || r.contains("ERROR") ||
            r.contains("UNABLE") || r.contains("7F")
        ) return result

        val tokens = r.split(Regex("\\s+"))
            .filter { it.length == 2 && it.matches(Regex("[0-9A-F]{2}")) }

        val dataBytes = when {
            tokens.size >= 6 && tokens[0] == "41" -> tokens.drop(2).take(4)
            tokens.size >= 5 && tokens[0] == "41" -> tokens.drop(2).take(4)
            tokens.size >= 4 -> tokens.takeLast(4)
            else -> return result
        }

        if (dataBytes.size < 4) return result

        val baseOffset = groupPid.toInt(16)  // np. 0x00, 0x20, 0x40
        val allBits = dataBytes.joinToString("") {
            it.toInt(16).toString(2).padStart(8, '0')
        }

        allBits.forEachIndexed { index, bit ->
            if (bit == '1') {
                val pidNum = baseOffset + index + 1
                result.add("%02X".format(pidNum))
            }
        }
        return result
    }

    private fun isValidObdResponse(response: String, pid: String): Boolean {
        val r = response.uppercase().trim()
        if (r.isBlank()) return false
        if (r.contains("NO DATA")) return false
        if (r.contains("ERROR")) return false
        if (r.contains("UNABLE")) return false
        if (r.contains("BUS")) return false
        if (r.contains("STOPPED")) return false
        if (r.startsWith("7F")) return false

        val expectedHeader = "41 ${pid.uppercase()}"
        if (r.contains(expectedHeader)) return true

        val expectedHeaderNoSpace = "41${pid.uppercase()}"
        if (r.replace(" ", "").contains(expectedHeaderNoSpace)) return true

        val hexOnly = r.replace(" ", "").replace("\r", "").replace("\n", "")
        if (hexOnly.matches(Regex("[0-9A-F]+"))) return true

        return false
    }

    suspend fun readCommands(commands: List<ObdCommand>): Map<ObdCommand, ObdResponseParser.ParsedValue> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<ObdCommand, ObdResponseParser.ParsedValue>()
            commands.forEach { cmd ->
                try {
                    val rawCmd = when (cmd.mode) {
                        "02" -> "02${cmd.pid}00"
                        else -> "${cmd.mode}${cmd.pid}"
                    }

                    val raw = sendRawCommand(rawCmd, timeoutMs = commandTimeoutMs)
                    Log.v(TAG, "SCAN ${cmd.cmdName} | $rawCmd | '$raw'")
                    results[cmd] = ObdResponseParser.parse(cmd, raw)
                } catch (e: Exception) {
                    Log.e(TAG, "SCAN ERROR ${cmd.cmdName}: ${e.message}")
                    results[cmd] = ObdResponseParser.ParsedValue("", null, "Błąd", cmd.unit)
                }
                delay(if (commandTimeoutMs > TIMEOUT_STANDARD_MS) 120L else 60L)
            }
            results
        }

    suspend fun readCommand(command: ObdCommand): ObdResponseParser.ParsedValue =
        withContext(Dispatchers.IO) {
            val rawCmd = "${command.mode}${command.pid}"
            val raw = sendRawCommand(rawCmd, timeoutMs = 2000)
            ObdResponseParser.parse(command, raw)
        }

    private suspend fun sendRawCommand(
        command: String,
        timeoutMs: Long = COMMAND_TIMEOUT_MS
    ): String = withContext(Dispatchers.IO) {
        val out = outputStream ?: return@withContext ""
        val inp = inputStream ?: return@withContext ""

        try {
            out.write("$command\r".toByteArray(Charsets.ISO_8859_1))
                        out.flush()
            Log.v(TAG, "CMD -> '$command'")

            val response = StringBuilder()
            val deadline  = System.currentTimeMillis() + timeoutMs
            val buffer    = ByteArray(1)

            while (System.currentTimeMillis() < deadline) {

                val avail = try { inp.available() } catch (e: IOException) { break; 0 }

                if (avail <= 0) {
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0) break
                    Thread.sleep(minOf(10L, remaining))
                    continue
                }

                val toRead = minOf(avail, 256)
                val chunk  = ByteArray(toRead)
                val nRead  = try { inp.read(chunk, 0, toRead) } catch (e: IOException) { break; -1 }
                if (nRead == -1) break

                for (i in 0 until nRead) {
                    val char = (chunk[i].toInt() and 0xFF).toChar()
                    if (char == '>') {
                        val result = formatResponse(response.toString())
                        Log.v(TAG, "RSP <- '$result'")
                        return@withContext result
                    }
                    response.append(char)
                }
            }

            val result = formatResponse(response.toString())
            if (result.isNotBlank()) Log.v(TAG, "RSP (timeout) <- '$result'")
            else Log.w(TAG, "RSP empty — timeout ${timeoutMs}ms dla '$command'")
            result

        } catch (e: IOException) {
            Log.e(TAG, "Błąd I/O przy komendzie '$command': ${e.message}")
            ""
        }
    }

    private fun formatResponse(raw: String): String {
        return raw.trim()
            .replace("\r\n", " ")
            .replace("\r", " ")
            .replace("\n", " ")
            .replace(">", "")
            .trim()
            .uppercase()
    }

    private suspend fun flushInput() {
        val inp = inputStream ?: return
        withContext(Dispatchers.IO) {
            try {
                val buffer = ByteArray(512)
                var flushed = 0
                repeat(10) {
                    val avail = inp.available()
                    if (avail > 0) {
                        inp.read(buffer, 0, minOf(avail, buffer.size))
                        flushed++
                        Thread.sleep(20)
                    }
                }
                if (flushed > 0) Log.d(TAG, "Wyczyszczono bufor ($flushed iteracji)")
            } catch (e: IOException) { /* ignoruj */
            }
        }
        delay(100)
    }

    fun disconnect() {
        closeSocket()
        _connectionState.value = ConnectionState.Disconnected
        _supportedCommands.value = emptyList()
        _vinInfo.value = ""
        activeProtocol = "0"
        commandTimeoutMs = TIMEOUT_STANDARD_MS
    }

    private fun closeSocket() {
        try { inputStream?.close()     } catch (_: IOException) {}
        try { outputStream?.close()    } catch (_: IOException) {}
        try { bluetoothSocket?.close() } catch (_: IOException) {}
        inputStream     = null
        outputStream    = null
        bluetoothSocket = null
    }

    data class DtcResult(val codes: List<String>, val rawResponse: String)

    private fun parseDtcResponse(response: String): List<String> {
        val codes = mutableListOf<String>()
        val r = response.uppercase().trim()
        if (r.isBlank() || r.contains("NO DATA") || r.contains("ERROR")) return codes
        val tokens = r.split(Regex("\\s+"))
            .filter { it.length == 2 && it.matches(Regex("[0-9A-F]{2}")) }
        val dataStart = if (tokens.firstOrNull() == "43") 1 else 0
        tokens.drop(dataStart).chunked(2).forEach { pair ->
            if (pair.size < 2) return@forEach
            val b1 = pair[0].toInt(16)
            val b2 = pair[1].toInt(16)
            if (b1 == 0 && b2 == 0) return@forEach
            val typeChar = when ((b1 shr 6) and 0x03) {
                0 -> "P"; 1 -> "C"; 2 -> "B"; else -> "U"
            }
            val digit2 = (b1 shr 4) and 0x03
            val digit3 = b1 and 0x0F
            codes.add("$typeChar$digit2$digit3%02X".format(b2))
        }
        return codes
    }

    suspend fun readDtcCodes(): DtcResult = withContext(Dispatchers.IO) {
        val response = sendRawCommand("03", timeoutMs = 5000)
        Log.d(TAG, "DTC raw: '$response'")
        val codes = parseDtcResponse(response)
        Log.d(TAG, "DTC znalezione: $codes")
        DtcResult(codes, response)
    }

    suspend fun clearDtcCodes(): Boolean = withContext(Dispatchers.IO) {
        val response = sendRawCommand("04", timeoutMs = 5000)
        Log.d(TAG, "DTC clear: '$response'")
        response.uppercase().contains("44") || response.uppercase().contains("OK")
    }
}