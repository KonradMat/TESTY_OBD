package com.obdreader.app.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.obdreader.app.auth.AuthManager
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
    val authManager      = AuthManager(application)

    // ─── Priorytety / interwały ───────────────────────────────────────────────
    private val BASE_INTERVAL_MS = 1000L
    private val MEDIUM_EVERY     = 5
    private val LOW_EVERY        = 30

    // ─── Stan Auth ────────────────────────────────────────────────────────────
    /** true = zalogowany lub gość; false = pokaż ekran logowania */
    private val _authPassed = MutableStateFlow(authManager.isLoggedIn)
    val authPassed: StateFlow<Boolean> = _authPassed.asStateFlow()

    /** true = użytkownik wszedł jako gość (nie ma tokenu) */
    private val _isGuest = MutableStateFlow(false)
    val isGuest: StateFlow<Boolean> = _isGuest.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // ─── Stan pojazdów ────────────────────────────────────────────────────────
    private val _vehicles = MutableStateFlow<List<AuthManager.Vehicle>>(emptyList())
    val vehicles: StateFlow<List<AuthManager.Vehicle>> = _vehicles.asStateFlow()

    private val _isLoadingVehicles = MutableStateFlow(false)
    val isLoadingVehicles: StateFlow<Boolean> = _isLoadingVehicles.asStateFlow()

    private val _vehicleError = MutableStateFlow<String?>(null)
    val vehicleError: StateFlow<String?> = _vehicleError.asStateFlow()

    private val _isAddingVehicle = MutableStateFlow(false)
    val isAddingVehicle: StateFlow<Boolean> = _isAddingVehicle.asStateFlow()

    private val _addVehicleError = MutableStateFlow<String?>(null)
    val addVehicleError: StateFlow<String?> = _addVehicleError.asStateFlow()

    private val _showAddVehicleDialog = MutableStateFlow(false)
    val showAddVehicleDialog: StateFlow<Boolean> = _showAddVehicleDialog.asStateFlow()

    // Pojazd do potwierdzenia usunięcia (null = dialog zamknięty)
    private val _vehicleToDelete = MutableStateFlow<AuthManager.Vehicle?>(null)
    val vehicleToDelete: StateFlow<AuthManager.Vehicle?> = _vehicleToDelete.asStateFlow()

    private val _isDeletingVehicle = MutableStateFlow(false)
    val isDeletingVehicle: StateFlow<Boolean> = _isDeletingVehicle.asStateFlow()

    // ─── Stan UI (OBD) ────────────────────────────────────────────────────────
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

    // ─── Auth ─────────────────────────────────────────────────────────────────

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            when (val result = authManager.login(email, password)) {
                is AuthManager.AuthResult.Success -> {
                    _isGuest.value = false
                    _authPassed.value = true
                    loadVehicles()
                }
                is AuthManager.AuthResult.Error -> {
                    _authError.value = result.message
                }
            }
            _isAuthLoading.value = false
        }
    }

    fun register(email: String, firstName: String, lastName: String, password: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authError.value = null
            when (val result = authManager.register(email, firstName, lastName, password)) {
                is AuthManager.AuthResult.Success -> {
                    _isGuest.value = false
                    _authPassed.value = true
                    loadVehicles()
                }
                is AuthManager.AuthResult.Error -> {
                    _authError.value = result.message
                }
            }
            _isAuthLoading.value = false
        }
    }

    fun continueAsGuest() {
        _isGuest.value = true
        _authPassed.value = true
    }

    fun logout() {
        authManager.logout()
        _isGuest.value = false
        _authPassed.value = false
        _authError.value = null
        _vehicles.value = emptyList()
        disconnect()
    }

    fun clearAuthError() { _authError.value = null }

    // ─── Pojazdy ──────────────────────────────────────────────────────────────

    fun loadVehicles() {
        if (!authManager.isLoggedIn) return
        viewModelScope.launch {
            _isLoadingVehicles.value = true
            _vehicleError.value = null
            when (val result = authManager.getVehicles()) {
                is AuthManager.VehicleResult.Success -> _vehicles.value = result.vehicles
                is AuthManager.VehicleResult.Error   -> _vehicleError.value = result.message
                else -> {}
            }
            _isLoadingVehicles.value = false
        }
    }

    fun showAddVehicle() {
        _addVehicleError.value = null
        _showAddVehicleDialog.value = true
    }

    fun hideAddVehicle() {
        _showAddVehicleDialog.value = false
        _addVehicleError.value = null
    }

    fun addVehicle(name: String, make: String, model: String, year: String) {
        viewModelScope.launch {
            _isAddingVehicle.value = true
            _addVehicleError.value = null
            when (val result = authManager.addVehicle(name, make, model, year)) {
                is AuthManager.VehicleResult.Added -> {
                    _showAddVehicleDialog.value = false
                    loadVehicles()
                    addLog("Pojazd dodany (ID: ${result.id})")
                }
                is AuthManager.VehicleResult.Error -> {
                    _addVehicleError.value = result.message
                }
                else -> {}
            }
            _isAddingVehicle.value = false
        }
    }

    fun requestDeleteVehicle(vehicle: AuthManager.Vehicle) {
        _vehicleToDelete.value = vehicle
    }

    fun cancelDeleteVehicle() {
        _vehicleToDelete.value = null
    }

    fun confirmDeleteVehicle() {
        val vehicle = _vehicleToDelete.value ?: return
        viewModelScope.launch {
            _isDeletingVehicle.value = true
            when (val result = authManager.deleteVehicle(vehicle.id)) {
                is AuthManager.VehicleResult.Deleted -> {
                    _vehicleToDelete.value = null
                    loadVehicles()
                    addLog("Pojazd usunięty: ${vehicle.name}")
                }
                is AuthManager.VehicleResult.Error -> {
                    // Zostaw dialog otwarty, pokaż błąd przez vehicleError
                    _vehicleError.value = result.message
                    _vehicleToDelete.value = null
                }
                else -> {
                    _vehicleToDelete.value = null
                }
            }
            _isDeletingVehicle.value = false
        }
    }

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
                if (!_isGuest.value) {
                    val retryCount = uploader.pendingRetryCount()
                    if (retryCount > 0) {
                        addLog("Próba wysłania $retryCount oczekujących plików...")
                        val sent = uploader.retryPending()
                        if (sent > 0) addLog("Ponownie wysłano: $sent plików")
                    }
                    startSession()
                }
            } else {
                addLog("Błąd: nie udało się połączyć")
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        stopScanning()
        viewModelScope.launch {
            if (!_isGuest.value) {
                flushAndUpload()
                dataLogger.closeSession()
            }
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

    // ─── Ustawienia uploadera ─────────────────────────────────────────────────

    fun setBackendUrl(url: String) {
        uploader.backendUrl = url
        addLog("Backend URL: $url")
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

                        // Logowanie tylko dla zalogowanych
                        if (_isLogging.value && !_isGuest.value) {
                            val recordJson = dataLogger.addRecord(_sensorData.value)
                            val metaJson   = buildMetaJson()
                            val uploaded = if (recordJson != null)
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

    private suspend fun flushAndUpload() {
        if (!_isLogging.value) return
        val old = uploader.uploadIntervalRecords
        uploader.uploadIntervalRecords = 1
        val lastRecord = dataLogger.addRecord(_sensorData.value)
        if (lastRecord != null) uploader.onNewRecord(lastRecord, buildMetaJson())
        uploader.uploadIntervalRecords = old
        _uploadStatus.value = uploader.lastUploadStatus
    }

    private fun buildMetaJson(): JSONObject = JSONObject().apply {
        put("session_id", dataLogger.currentSessionFile()?.nameWithoutExtension ?: "unknown")
        put("vin",        vinInfo.value)
        put("started_at", dataLogger.currentSessionFile()?.let {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .format(java.util.Date(it.lastModified()))
        } ?: "")
    }

    fun addLog(message: String) {
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