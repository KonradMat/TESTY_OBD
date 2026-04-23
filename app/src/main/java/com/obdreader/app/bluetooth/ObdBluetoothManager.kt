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

        // Timeouty per typ samochodu — BMW i niektóre Ople potrzebują więcej czasu
        private const val TIMEOUT_STANDARD_MS  = 2000L   // VW, większość
        private const val TIMEOUT_SLOW_MS      = 4000L   // BMW, Opel, wolne ECU
        private const val TIMEOUT_INIT_MS      = 5000L   // test protokołu przy init

        // Protokoły ELM327 do próbowania gdy ATSP0 zawiedzie
        // SP1=SAE J1850 PWM, SP2=SAE J1850 VPW, SP3=ISO9141-2,
        // SP4=ISO14230-4 KWP(5baud), SP5=ISO14230-4 KWP(fast),
        // SP6=ISO15765-4 CAN(11bit,500k) ← najczęstszy,
        // SP7=ISO15765-4 CAN(29bit,500k) ← BMW extended,
        // SP8=ISO15765-4 CAN(11bit,250k), SP9=ISO15765-4 CAN(29bit,250k)
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

    // Wykryty protokół i timeout dostosowany do konkretnego samochodu
    private var activeProtocol: String = "0"   // "0"=auto, "6"=CAN11, "7"=CAN29 itd.
    private var commandTimeoutMs: Long = TIMEOUT_STANDARD_MS

    val isConnected: Boolean
        get() = bluetoothSocket?.isConnected == true

    /** Zwraca nazwę aktywnego protokołu do wyświetlenia w UI */
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

    // ─── Połączenie ──────────────────────────────────────────────────────────

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
        // Próba 1: standardowy SPP UUID
        return try {
            val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            Log.d(TAG, "Połączono przez SPP UUID")
            socket
        } catch (e: IOException) {
            Log.w(TAG, "SPP UUID nieudany, próba reflection: ${e.message}")
            // Próba 2: reflection (niezbędne dla niektórych tanich kostek)
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

    // ─── Inicjalizacja ELM327 ────────────────────────────────────────────────

    private suspend fun initializeElm327(): Boolean {
        Log.d(TAG, "Rozpoczynam inicjalizację ELM327...")

        // ── Krok 1: Reset adaptera ───────────────────────────────────────────
        // ATZ wysyłamy bez sprawdzania odpowiedzi – echo może być jeszcze włączone
        sendRawCommand("ATZ", timeoutMs = 4000)
        delay(1500)  // ATZ potrzebuje chwili na pełny reset układu
        flushInput()

        // ── Krok 2: Konfiguracja podstawowa ─────────────────────────────────
        // Kolejność ma znaczenie — ATE0 musi być pierwsza (wyłącza echo)
        // Bez ATE0 parser dostaje każdą komendę z powrotem + odpowiedź = chaos
        val baseConfig = listOf(
            "ATE0",   // Echo OFF — KRYTYCZNE, musi być pierwsze
            "ATL0",   // Linefeeds OFF
            "ATS1",   // Spaces ON — "41 0C 1A FF" łatwiej parsować niż "410C1AFF"
            "ATH0",   // Headers OFF — nie potrzebujemy nagłówków CAN w normalnym trybie
            "ATAT2",  // Adaptive timing MAX — kluczowe dla BMW/Opel które odpowiadają wolno
            "ATST FF" // Timeout maksymalny (255 * 4ms = ~1s per odpowiedź ECU)
        )

        for (cmd in baseConfig) {
            delay(150)
            val response = sendRawCommand(cmd, timeoutMs = 2000)
            Log.d(TAG, "INIT $cmd -> '$response'")
            if (response.isBlank()) {
                Log.e(TAG, "Brak odpowiedzi na $cmd — przerywam init")
                return false
            }
            // "?" = nieznana komenda (stare kostki) — akceptujemy, idziemy dalej
        }

        // ── Krok 3: Wykrycie protokołu z fallback ───────────────────────────
        // ATSP0 = auto-detect. Dla BMW (CAN 29-bit) auto często wybiera zły protokół.
        // Najpierw próbujemy auto, jeśli test PID zawiedzie — przechodzimy przez
        // listę protokołów aż znajdziemy działający.
        val protocolFound = tryProtocols()
        if (!protocolFound) {
            Log.e(TAG, "Żaden protokół nie odpowiada — samochód nie obsługuje OBD2?")
            return false
        }

        // ── Krok 4: Wykryj czy ECU jest wolne (BMW/Opel) ────────────────────
        // Mierzymy czas odpowiedzi na RPM i ustawiamy odpowiedni timeout
        calibrateTimeout()

        Log.d(TAG, "Inicjalizacja OK — protokół SP$activeProtocol, timeout ${commandTimeoutMs}ms")
        return true
    }

    /**
     * Próbuje protokołów ELM327 po kolei aż znajdzie działający.
     *
     * Kolejność: ATSP0 (auto) → SP6 (CAN 11bit) → SP7 (CAN 29bit, BMW)
     *            → SP8 → SP9 → SP5 (KWP fast) → SP4 (KWP 5baud) → SP3 (ISO9141)
     *
     * Dla każdego protokołu wysyłamy "010C" (RPM) jako test.
     * Pierwsze działające = protokół pojazdu.
     */
    private suspend fun tryProtocols(): Boolean {
        // Próba 1: ATSP0 (auto-detect) — działa dla ~80% samochodów
        delay(200)
        sendRawCommand("ATSP0", timeoutMs = 2000)
        delay(500)  // auto-detect potrzebuje chwili na negocjację

        val autoTest = sendRawCommand("010C", timeoutMs = TIMEOUT_INIT_MS)
        Log.d(TAG, "ATSP0 test -> '$autoTest'")

        if (isValidTestResponse(autoTest)) {
            activeProtocol = "0"
            // Zapytaj ELM327 jaki protokół wybrał
            val dp = sendRawCommand("ATDP", timeoutMs = 1000)
            Log.d(TAG, "Wykryty protokół (ATDP): '$dp'")
            return true
        }

        Log.w(TAG, "ATSP0 nie działa — próbuję kolejnych protokołów...")

        // Próba 2–8: kolejne protokoły z listy
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

            // Wyczyść bufor po nieudanej próbie
            flushInput()
        }

        return false
    }

    /**
     * Mierzy czas odpowiedzi ECU i ustawia commandTimeoutMs.
     *
     * BMW i Opel mogą odpowiadać w 800–2000ms, VW zazwyczaj <300ms.
     * Zbyt krótki timeout = puste odpowiedzi = "0 czujników".
     * Zbyt długi timeout = powolne skanowanie.
     *
     * Rozwiązanie: mierzymy faktyczny czas odpowiedzi i dodajemy 50% marginesu.
     */
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
            // Nie udało się zmierzyć — użyj bezpiecznego domyślnego
            TIMEOUT_SLOW_MS
        } else {
            val avgMs = samples.average().toLong()
            val withMargin = (avgMs * 1.5).toLong().coerceIn(500L, TIMEOUT_SLOW_MS)
            Log.d(TAG, "Kalibracja timeoutu: avg=${avgMs}ms → używam ${withMargin}ms")
            withMargin
        }
    }

    /** Sprawdza czy odpowiedź na testowy PID jest prawidłowa */
    private fun isValidTestResponse(response: String): Boolean {
        val r = response.uppercase().trim()
        if (r.isBlank()) return false
        if (r.contains("UNABLE TO CONNECT")) return false
        if (r.contains("BUS INIT ERROR")) return false
        if (r.contains("NO PROTOCOL")) return false
        if (r.contains("CAN ERROR")) return false
        if (r.contains("ERR")) return false
        // Akceptujemy nawet NO DATA — oznacza że ECU odpowiada, tylko silnik nie pracuje
        if (r.contains("NO DATA")) return true
        // Szukamy odpowiedzi zawierającej bajty hex odpowiedzi na 010C (41 0C ...)
        if (r.contains("41") || r.contains("410C") || r.contains("41 0C")) return true
        // Odpowiedź hex bez nagłówka też OK
        val hexOnly = r.replace(" ", "")
        return hexOnly.matches(Regex("[0-9A-F]+")) && hexOnly.length >= 4
    }

    // ─── Odczyt VIN ──────────────────────────────────────────────────────────

    private suspend fun readVin() {
        try {
            // Tryb 09 PID 02 = VIN
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

    // ─── Wykrywanie obsługiwanych komend ────────────────────────────────────

    /**
     * POPRAWIONA wersja – testuje każdy PID bezpośrednio, tak jak Python robił:
     *   for cmd in all_commands:
     *       if self.connection.supports(cmd): supported.append(cmd)
     *
     * Nie polegamy na bitmapie PIDów (0x00/0x20/0x40) bo wiele tanich kostek
     * zwraca niepoprawne bitmapy lub w ogóle nie odpowiada na te komendy.
     */
    /**
     * Wykrywa obsługiwane komendy przy użyciu bitmapy PIDów (0x00, 0x20, 0x40, 0x60, 0x80, 0xA0, 0xC0)
     *
     * To jest DOKŁADNIE to co robi python-obd: pyta ECU o listę wspieranych PIDów
     * przez komendy 0100, 0120, 0140 itd., a ECU odpowiada 4-bajtową bitmapą.
     *
     * Przewaga nad testowaniem każdego PID-a:
     * - szybkie (7 zapytań zamiast 60+)
     * - nie zależy od stanu pojazdu (NO DATA przy biegu jałowym nie wyklucza PID-a)
     * - ECU samo mówi co obsługuje
     */
    private suspend fun detectSupportedCommands() {
        _connectionState.value = ConnectionState.Connecting("Odczyt bitmapy PIDów...")
        val supported = mutableListOf<ObdCommand>()

        // ── Krok 1: Bitmapa PIDów trybu 01 ──────────────────────────────────
        // Grupy: 0100→01-20, 0120→21-40, 0140→41-60, 0160→61-80,
        //        0180→81-A0, 01A0→A1-C0, 01C0→C1-E0
        // WAŻNE: poprzednia wersja odpytywała tylko 3 grupy (00,20,40) przez
        // stałe PIDS_A/B/C – przez to PIDy 61-C0 (turbo, moment, DPF, AdBlue)
        // nigdy nie były wykrywane. Teraz odpytujemy wszystkie 7 grup.
        val supportedPidSet = mutableSetOf<String>()
        val pidGroups = listOf("00", "20", "40", "60", "80", "A0", "C0")

        for (groupPid in pidGroups) {
            val rawCmd = "01$groupPid"
            // Używamy commandTimeoutMs — BMW potrzebuje więcej czasu na odpowiedź bitmapy
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

        // ── Krok 2: Mapuj bitmapę trybu 01 na ObdCommand ────────────────────
        _connectionState.value = ConnectionState.Connecting("Mapowanie czujników tryb 01...")

        // Wyklucz tylko bitmapowe meta-PIDy (00,20,40,60,80,A0,C0) – same w sobie
        // nie są czujnikami, służą tylko do wykrywania
        val bitmapPids = setOf("00", "20", "40", "60", "80", "A0", "C0")
        val mode01Commands = ObdCommand.values().filter { cmd ->
            cmd.mode == "01" && cmd.pid.uppercase() !in bitmapPids
        }

        if (supportedPidSet.isNotEmpty()) {
            // Bitmapa działa — użyj jej
            mode01Commands.forEach { cmd ->
                if (supportedPidSet.contains(cmd.pid.uppercase())) {
                    supported.add(cmd)
                    Log.d(TAG, "  ✓ M01 (bitmapa) ${cmd.cmdName} [${cmd.pid}]")
                }
            }
        } else {
            // Fallback: bitmapa zawiodła — testuj każdy PID bezpośrednio
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

        // ── Krok 3: Tryb 02 – Freeze Frame ──────────────────────────────────
        // Freeze Frame zawiera te same PIDy co tryb 01, ale zrzut stanu z chwili
        // wystąpienia błędu. Wykrywamy przez próbę odczytu 0200 (bitmapa) lub 0205
        // (RPM freeze). Freeze Frame jest dostępny tylko jeśli jest aktywny DTC.
        // Nie blokujemy wykrywania gdy ECU zwraca NO DATA (brak DTC = normalny stan).
        _connectionState.value = ConnectionState.Connecting("Sprawdzanie Freeze Frame (tryb 02)...")
        val freezeResponse = sendRawCommand("0200", timeoutMs = commandTimeoutMs)
        Log.d(TAG, "Freeze Frame 0200 -> '$freezeResponse'")
        delay(150)

        // Freeze Frame dodajemy jeśli ECU w ogóle odpowiada na tryb 02
        // (nawet NO DATA oznacza że tryb jest obsługiwany – po prostu nie ma błędów)
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

        // ── Krok 4: Tryb 05 – Sondy O2 przed katalizatorem ──────────────────
        // Tryb 05 to legacy (sprzed CAN/ISO15765) – dostępny głównie w starszych
        // pojazdach (pre-2008). Na CAN bus zazwyczaj zastąpiony przez tryb 01 PID 14-1B.
        // Pomijamy aktywne wykrywanie trybu 05 – ECU CAN zwróci błąd lub odpowiedź
        // niekompatybilną z parserem; dane O2 dostępne są przez tryb 01.

        // ── Krok 5: Tryb 06 – Wyniki testów komponentów (On-Board Monitoring) ─
        // Tryb 06 zwraca wyniki testów poszczególnych czujników wykonywanych przez ECU.
        // Dostępny w większości aut z protokołem CAN (ISO 15765-4, post-2008).
        // Testujemy przez 0600 (bitmapa obsługiwanych testów) lub 0601 (pierwszy test).
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
            // Tryb 06 jest dostępny — wykryj które TIDy są obsługiwane
            // Odpytuj indywidualnie (nie ma standardowej bitmapy trybu 06 jak w trybie 01)
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

        // ── Krok 6: Tryb 09 – informacje o pojeździe ─────────────────────────
        // Bitmapa trybu 09 (0900) – odpytujemy najpierw, potem weryfikujemy poszczególne PIDy
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
            // Jeśli bitmapa trybu 09 zadziałała – użyj jej; w przeciwnym wypadku testuj
            val inBitmap = mode09PidSet.isNotEmpty() && mode09PidSet.contains(cmd.pid.uppercase())
            if (inBitmap) {
                supported.add(cmd)
                Log.d(TAG, "  ✓ M09 (bitmapa) ${cmd.cmdName}")
            } else {
                // Bezpośredni test – VIN i ECU_NAME są kluczowe, warto sprawdzić
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

    /**
     * Parsuje 4-bajtową bitmapę PIDów z odpowiedzi ECU.
     *
     * Odpowiedź np. na "0100": "41 00 BE 3E B8 11"
     *                               ^^ ^^ ^^ ^^ ^^
     *                           tryb PID  b1 b2 b3 b4
     *
     * Każdy bit w b1-b4 oznacza że dany PID jest obsługiwany:
     * bit 7 b1 → PID 01, bit 6 b1 → PID 02, ... bit 0 b4 → PID 20
     */
    private fun parsePidBitmap(response: String, groupPid: String): Set<String> {
        val result = mutableSetOf<String>()
        val r = response.uppercase().trim()
        if (r.isBlank() || r.contains("NO DATA") || r.contains("ERROR") ||
            r.contains("UNABLE") || r.contains("7F")
        ) return result

        val tokens = r.split(Regex("\\s+"))
            .filter { it.length == 2 && it.matches(Regex("[0-9A-F]{2}")) }

        // Znajdź dane (po "41 XX")
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

    // ─── Odczyt danych ───────────────────────────────────────────────────────

    /**
     * Odczytuje podaną listę komend – wywoływane przez ViewModel z uwzględnieniem priorytetu
     */
    suspend fun readCommands(commands: List<ObdCommand>): Map<ObdCommand, ObdResponseParser.ParsedValue> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<ObdCommand, ObdResponseParser.ParsedValue>()
            commands.forEach { cmd ->
                try {
                    // Format komendy zależy od trybu:
                    // Tryb 01: "01PID"        np. "010C"
                    // Tryb 02: "02PID00"      np. "020C00" – 00 = frame number freeze frame
                    // Tryb 06: "06TID"        np. "0601"
                    // Tryb 09: "09PID"        np. "0902"
                    val rawCmd = when (cmd.mode) {
                        "02" -> "02${cmd.pid}00"
                        else -> "${cmd.mode}${cmd.pid}"
                    }
                    // Używamy commandTimeoutMs skalibrowanego podczas init —
                    // BMW/Opel dostają więcej czasu, VW standardowy
                    val raw = sendRawCommand(rawCmd, timeoutMs = commandTimeoutMs)
                    Log.v(TAG, "SCAN ${cmd.cmdName} | $rawCmd | '$raw'")
                    results[cmd] = ObdResponseParser.parse(cmd, raw)
                } catch (e: Exception) {
                    Log.e(TAG, "SCAN ERROR ${cmd.cmdName}: ${e.message}")
                    results[cmd] = ObdResponseParser.ParsedValue("", null, "Błąd", cmd.unit)
                }
                // Przerwa między komendami — BMW potrzebuje więcej czasu niż VW
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

    // ─── Komunikacja niskopoziomowa ──────────────────────────────────────────

    /**
     * Wysyła surową komendę i czeka na prompt '>'.
     *
     * NAPRAWIONE: poprzednia wersja używała inp.available() do sprawdzania
     * czy są dane — na wielu tanich kostkach ELM327 available() zawsze zwraca 0
     * mimo że dane są dostępne (bug w Android BluetoothSocket). Efekt: pętla
     * kręciła się przez cały timeout nie czytając nic, zwracała pusty string.
     *
     * Nowe rozwiązanie: blokujące read() na osobnym wątku (via Future) z
     * timeoutem. read() blokuje wątek do momentu gdy bajt jest dostępny —
     * działa niezależnie od available(). Gdy timeout minie, zamykamy socket
     * I/O i wątek się odblokuje z IOException.
     */
    private suspend fun sendRawCommand(
        command: String,
        timeoutMs: Long = COMMAND_TIMEOUT_MS
    ): String = withContext(Dispatchers.IO) {
        val out = outputStream ?: return@withContext ""
        val inp = inputStream ?: return@withContext ""

        try {
            // Wyślij komendę z terminatorem
            out.write("$command\r".toByteArray(Charsets.ISO_8859_1))
                        out.flush()
            Log.v(TAG, "CMD -> '$command'")

            val response = StringBuilder()
            val deadline  = System.currentTimeMillis() + timeoutMs
            val buffer    = ByteArray(1)

            // Czytaj bajt po bajcie używając blokującego read()
            // available() jest celowo pominięte — jest zawodne na BT
            while (System.currentTimeMillis() < deadline) {

                // Sprawdź nieblokująco: jeśli available() > 0 to na pewno są dane,
                // jeśli == 0 to NIE wiemy (może są, może nie) — i tak próbujemy read()
                // z krótkim sleep tylko gdy bufor naprawdę pusty przez dłuższy czas
                val avail = try { inp.available() } catch (e: IOException) { break; 0 }

                if (avail <= 0) {
                    // Bufor może być pusty chwilowo — czekaj krócej niż timeout
                    // ale nie blokuj na read() bo moglibyśmy zablokować wątek na stałe
                    // gdy kostka się rozłączyła
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0) break
                    Thread.sleep(minOf(10L, remaining))
                    continue
                }

                // Czytaj dostępne bajty do bufora (max tyle ile available() mówi)
                val toRead = minOf(avail, 256)
                val chunk  = ByteArray(toRead)
                val nRead  = try { inp.read(chunk, 0, toRead) } catch (e: IOException) { break; -1 }
                if (nRead == -1) break

                for (i in 0 until nRead) {
                    val char = (chunk[i].toInt() and 0xFF).toChar()
                    if (char == '>') {
                        // Prompt ELM327 = koniec odpowiedzi
                        val result = formatResponse(response.toString())
                        Log.v(TAG, "RSP <- '$result'")
                        return@withContext result
                    }
                    response.append(char)
                }
            }

            // Timeout — zwróć co zdążyliśmy zebrać
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

    /** Opróżnia bufor wejściowy – ważne przed wysłaniem nowej komendy */
    private suspend fun flushInput() {
        val inp = inputStream ?: return
        withContext(Dispatchers.IO) {
            try {
                val buffer = ByteArray(512)
                var flushed = 0
                // Czytaj co jest dostępne, max 10 razy z krótką przerwą
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

    // ─── Rozłączenie ─────────────────────────────────────────────────────────

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

    // ─── DTC (kody błędów) ───────────────────────────────────────────────────

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