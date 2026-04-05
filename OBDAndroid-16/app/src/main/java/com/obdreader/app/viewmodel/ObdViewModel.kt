package com.obdreader.app.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obdreader.app.bluetooth.ObdBluetoothManager
import com.obdreader.app.obd.ObdCategory
import com.obdreader.app.obd.ObdCommand
import com.obdreader.app.obd.ObdDataLogger
import com.obdreader.app.obd.ObdResponseParser
import com.obdreader.app.obd.ReadPriority
import com.obdreader.app.obd.TelemetryUploader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

class ObdViewModel(application: Application) : AndroidViewModel(application) {

    val bluetoothManager = ObdBluetoothManager()
    val dataLogger       = ObdDataLogger(application)
    val uploader         = TelemetryUploader(application)

    // ─── Priorytety / interwały ───────────────────────────────────────────────
    private val BASE_INTERVAL_MS = 1000L
    private val MEDIUM_EVERY     = 5
    private val LOW_EVERY        = 30

    // ─── Stan UI ──────────────────────────────────────────────────────────────
    private val _sensorData = MutableStateFlow<Map<ObdCommand, ObdResponseParser.ParsedValue>>(emptyMap())
    val sensorData: StateFlow<Map<ObdCommand, ObdResponseParser.ParsedValue>> = _sensorData.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isLogging = MutableStateFlow(false)
    val isLogging: StateFlow<Boolean> = _isLogging.asStateFlow()

    private val _uploadStatus = MutableStateFlow<TelemetryUploader.UploadStatus>(TelemetryUploader.UploadStatus.IDLE)
    val uploadStatus: StateFlow<TelemetryUploader.UploadStatus> = _uploadStatus.asStateFlow()

    private val _selectedCategory = MutableStateFlow<ObdCategory?>(null)
    val selectedCategory: StateFlow<ObdCategory?> = _selectedCategory.asStateFlow()

    private val _logMessages = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

    private val _sessionFiles = MutableStateFlow<List<ObdDataLogger.SessionInfo>>(emptyList())
    val sessionFiles: StateFlow<List<ObdDataLogger.SessionInfo>> = _sessionFiles.asStateFlow()

    val connectionState   = bluetoothManager.connectionState
    val supportedCommands = bluetoothManager.supportedCommands
    val vinInfo           = bluetoothManager.vinInfo

    private var scanJob:    Job? = null
    private var connectJob: Job? = null

    // ─── Połączenie ───────────────────────────────────────────────────────────
    fun connect(device: BluetoothDevice) {
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            addLog("Łączenie z ${device.name}...")
            val success = bluetoothManager.connect(device, maxRetries = 3)
            if (success) {
                addLog("Połączono! Znaleziono ${supportedCommands.value.size} czujników")
                addLog("Protokół: ${bluetoothManager.activeProtocolName} | Timeout: ${bluetoothManager.calibratedTimeoutMs}ms")
                val vin = vinInfo.value
                if (vin.isNotBlank()) addLog("VIN: $vin")
                // Sprawdź czy są pliki do ponownego wysłania
                val retryCount = uploader.pendingRetryCount()
                if (retryCount > 0) {
                    addLog("Próba wysłania $retryCount oczekujących plików...")
                    val sent = uploader.retryPending()
                    if (sent > 0) addLog("Ponownie wysłano: $sent plików")
                }
                startSession()
            } else {
                addLog("Błąd: nie udało się połączyć")
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        stopScanning()
        viewModelScope.launch {
            // Wyślij ostatni batch przed rozłączeniem
            flushAndUpload()
            dataLogger.closeSession()
        }
        bluetoothManager.disconnect()
        _sensorData.value = emptyMap()
        _isLogging.value  = false
        addLog("Rozłączono")
    }

    // ─── Sesja ────────────────────────────────────────────────────────────────
    private fun startSession() {
        dataLogger.openSession(vin = vinInfo.value)
        _isLogging.value = true
        addLog("Sesja JSON: ${dataLogger.currentSessionFile()?.name}")
        addLog("Upload co ${uploader.uploadIntervalRecords} rekordów (~${uploader.uploadIntervalRecords}s)")
        refreshSessionList()
        startScanning()
    }

    fun stopLogging() {
        viewModelScope.launch {
            flushAndUpload()
            dataLogger.closeSession()
            // Wyślij cały plik sesji
            dataLogger.currentSessionFile()?.let { file ->
                if (file.exists()) {
                    val ok = uploader.uploadSessionFile(file)
                    addLog(if (ok) "Sesja wysłana na backend ✓" else "Sesja zapisana lokalnie (brak sieci)")
                }
            }
            _isLogging.value = false
            _uploadStatus.value = uploader.lastUploadStatus
            addLog("Logowanie zatrzymane")
            refreshSessionList()
        }
    }

    fun refreshSessionList() { _sessionFiles.value = dataLogger.listSessions() }

    // ─── Ustawienia uploadera (można zmieniać z UI) ───────────────────────────
    fun setBackendUrl(url: String) {
        uploader.backendUrl = url
        addLog("Backend URL: $url")
    }

    fun setUploadInterval(records: Int) {
        uploader.uploadIntervalRecords = records
        addLog("Interwał uploadu: co $records rekordów")
    }

    fun retryPendingUploads() {
        viewModelScope.launch {
            val count = uploader.pendingRetryCount()
            if (count == 0) { addLog("Brak plików do ponownego wysłania"); return@launch }
            addLog("Ponowne wysyłanie $count plików...")
            val sent = uploader.retryPending()
            addLog("Ponownie wysłano: $sent/$count")
            _uploadStatus.value = uploader.lastUploadStatus
            refreshSessionList()
        }
    }

    // ─── Skanowanie z priorytetami ─────────────────────────────────────────────
    fun startScanning() {
        if (_isScanning.value) return
        _isScanning.value = true
        addLog("Skanowanie: HIGH co ${BASE_INTERVAL_MS/1000}s, MEDIUM co ${MEDIUM_EVERY}s, LOW co ${LOW_EVERY}s")

        scanJob = viewModelScope.launch {
            var cycleCount = 0

            // Jednorazowy odczyt ONCE
            val onceCommands = supportedCommands.value.filter { it.priority == ReadPriority.ONCE }
            if (onceCommands.isNotEmpty()) {
                val onceData = bluetoothManager.readCommands(onceCommands)
                mergeData(onceData)
                addLog("Odczytano ${onceCommands.size} stałych parametrów")
            }

            while (_isScanning.value && bluetoothManager.isConnected) {
                val toRead = supportedCommands.value
                    .filter { it.priority != ReadPriority.ONCE }
                    .filter { cmd ->
                        when (cmd.priority) {
                            ReadPriority.HIGH   -> true
                            ReadPriority.MEDIUM -> cycleCount % MEDIUM_EVERY == 0
                            ReadPriority.LOW    -> cycleCount % LOW_EVERY == 0
                            ReadPriority.ONCE   -> false
                        }
                    }

                if (toRead.isNotEmpty()) {
                    try {
                        val newData = bluetoothManager.readCommands(toRead)
                        mergeData(newData)

                        // ── Logowanie + Upload ─────────────────────────────
                        if (_isLogging.value) {
                            // addRecord zwraca ten sam JSONObject który trafia do pliku —
                            // przekazujemy go bezpośrednio do uploadera, żeby uploader
                            // wysyłał DOKŁADNIE tyle czujników co jest zapisanych na dysku
                            val recordJson = dataLogger.addRecord(_sensorData.value)
                            val metaJson   = buildMetaJson()
                            val uploaded   = if (recordJson != null)
                                uploader.onNewRecord(recordJson, metaJson)
                            else false

                            if (uploaded) {
                                _uploadStatus.value = uploader.lastUploadStatus
                                when (val s = uploader.lastUploadStatus) {
                                    is TelemetryUploader.UploadStatus.SUCCESS ->
                                        addLog("↑ Upload: ${s.recordsSent} rekordów wysłano")
                                    is TelemetryUploader.UploadStatus.FAILED ->
                                        addLog("↑ Upload nieudany: ${s.error}")
                                    else -> {}
                                }
                            }
                        }
                    } catch (e: Exception) {
                        addLog("Błąd skanowania: ${e.message}")
                        delay(2000)
                    }
                }

                cycleCount++
                delay(BASE_INTERVAL_MS)
            }
            _isScanning.value = false
        }
    }

    fun stopScanning() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
        addLog("Zatrzymano skanowanie")
    }

    fun selectCategory(category: ObdCategory?) { _selectedCategory.value = category }

    // ─── Helpery ──────────────────────────────────────────────────────────────
    private fun mergeData(newData: Map<ObdCommand, ObdResponseParser.ParsedValue>) {
        val merged = _sensorData.value.toMutableMap()
        merged.putAll(newData)
        _sensorData.value = merged
    }

    /** Wysyła pozostały batch przed rozłączeniem */
    private suspend fun flushAndUpload() {
        if (!_isLogging.value) return
        val old = uploader.uploadIntervalRecords
        uploader.uploadIntervalRecords = 1
        val lastRecord = dataLogger.addRecord(_sensorData.value)
        if (lastRecord != null) uploader.onNewRecord(lastRecord, buildMetaJson())
        uploader.uploadIntervalRecords = old
        _uploadStatus.value = uploader.lastUploadStatus
    }

    // buildRecordJson usunięty — ViewModel używa teraz JSONObject
    // zwróconego przez dataLogger.addRecord() bezpośrednio

    private fun buildMetaJson(): JSONObject = JSONObject().apply {
        put("session_id", dataLogger.currentSessionFile()?.nameWithoutExtension ?: "unknown")
        put("vin",        vinInfo.value)
        put("started_at", dataLogger.currentSessionFile()?.let {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .format(java.util.Date(it.lastModified()))
        } ?: "")
    }

    private fun addLog(message: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val current = _logMessages.value.toMutableList()
        current.add(0, "[$ts] $message")
        if (current.size > 100) current.removeAt(current.size - 1)
        _logMessages.value = current
    }

    fun getDataForCategory(category: ObdCategory): Map<ObdCommand, ObdResponseParser.ParsedValue> {
        val currentData = _sensorData.value
        return category.pids.associateWith { cmd ->
            currentData[cmd] ?: ObdResponseParser.ParsedValue("", null, "--", cmd.unit)
        }
    }

    override fun onCleared() {
        super.onCleared()
        connectJob?.cancel()
        scanJob?.cancel()
        viewModelScope.launch { dataLogger.closeSession() }
        bluetoothManager.disconnect()
    }
}
