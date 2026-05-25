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
import com.obdreader.app.obd.ObdFormulaEngine
import com.obdreader.app.obd.ObdResponseParser
import com.obdreader.app.obd.ReadPriority
import com.obdreader.app.obd.TelemetryUploader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// ─── Data classes ───────────────────────────────────────────────────────────

data class SessionAnalysis(
    val id: Int,
    val sessionId: Int,
    val createdAt: String,
    val summary: String,
    val issues: List<AnalysisIssue>
)

data class AnalysisIssue(
    val component: String,
    val severity: String,
    val description: String
)

sealed class AnalysisState {
    object Idle    : AnalysisState()
    data class Loading(val sessionId: String) : AnalysisState()
    data class Success(val sessionId: String, val analysis: SessionAnalysis) : AnalysisState()
    data class Error(val sessionId: String, val message: String) : AnalysisState()
}

/**
 * Sesja serwerowa — pobierana z GET /api/telemetry/sessions
 */
data class ServerSession(
    val backendId: Int,          // id (integer) — potrzebny do DELETE i analizy
    val sessionId: String,       // sessionId (string) — klucz matchowania z lokalnym plikiem
    val vin: String?,
    val startedAt: String,
    val closedAt: String,
    val recordCount: Int,
    val vehicleId: Int?,
    val vehicleName: String?
)

/**
 * Połączona sesja — widoczna na liście w UI.
 * Może istnieć tylko lokalnie, tylko na serwerze, lub w obu miejscach.
 */
data class MergedSession(
    val sessionId: String,
    val backendId: Int?,                          // null jeśli tylko lokalna
    val localInfo: ObdDataLogger.SessionInfo?,    // null jeśli tylko serwerowa
    val serverInfo: ServerSession?,               // null jeśli tylko lokalna
    val isOnlineOnly: Boolean = backendId != null && localInfo == null,
    val isLocalOnly:  Boolean = backendId == null && localInfo != null
) {
    val displayName: String get() = sessionId
    val recordCount: Int    get() = serverInfo?.recordCount ?: localInfo?.recordCount ?: 0
    val sizeKb: Long        get() = localInfo?.sizeKb ?: 0L
    val vin: String         get() = serverInfo?.vin ?: localInfo?.vin ?: ""
    val vehicleName: String get() = serverInfo?.vehicleName ?: ""
    val vehicleId: Int?     get() = serverInfo?.vehicleId
    val closedAt: String    get() = serverInfo?.closedAt ?: ""
}

// ─── ViewModel ─────────────────────────────────────────────────────────────

class ObdViewModel(application: Application) : AndroidViewModel(application) {

    val bluetoothManager = ObdBluetoothManager()
    val dataLogger       = ObdDataLogger(application)
    val uploader         = TelemetryUploader(application)
    val authManager      = AuthManager(application)
    private val formulaEngine = ObdFormulaEngine()

    private val BASE_INTERVAL_MS = 1000L
    private val MEDIUM_EVERY     = 5
    private val LOW_EVERY        = 30

    // ── Auth ────────────────────────────────────────────────────────────────

    private val _authPassed     = MutableStateFlow(authManager.isLoggedIn)
    val authPassed: StateFlow<Boolean> = _authPassed.asStateFlow()

    private val _isGuest        = MutableStateFlow(false)
    val isGuest: StateFlow<Boolean> = _isGuest.asStateFlow()

    private val _isAuthLoading  = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authError      = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    // ── Vehicles ────────────────────────────────────────────────────────────

    private val _vehicles            = MutableStateFlow<List<AuthManager.Vehicle>>(emptyList())
    val vehicles: StateFlow<List<AuthManager.Vehicle>> = _vehicles.asStateFlow()

    private val _selectedVehicleId   = MutableStateFlow<Int?>(null)
    val selectedVehicleId: StateFlow<Int?> = _selectedVehicleId.asStateFlow()

    private val _isLoadingVehicles   = MutableStateFlow(false)
    val isLoadingVehicles: StateFlow<Boolean> = _isLoadingVehicles.asStateFlow()

    private val _vehicleError        = MutableStateFlow<String?>(null)
    val vehicleError: StateFlow<String?> = _vehicleError.asStateFlow()

    private val _isAddingVehicle     = MutableStateFlow(false)
    val isAddingVehicle: StateFlow<Boolean> = _isAddingVehicle.asStateFlow()

    private val _addVehicleError     = MutableStateFlow<String?>(null)
    val addVehicleError: StateFlow<String?> = _addVehicleError.asStateFlow()

    private val _showAddVehicleDialog = MutableStateFlow(false)
    val showAddVehicleDialog: StateFlow<Boolean> = _showAddVehicleDialog.asStateFlow()

    private val _vehicleToDelete     = MutableStateFlow<AuthManager.Vehicle?>(null)
    val vehicleToDelete: StateFlow<AuthManager.Vehicle?> = _vehicleToDelete.asStateFlow()

    private val _isDeletingVehicle   = MutableStateFlow(false)
    val isDeletingVehicle: StateFlow<Boolean> = _isDeletingVehicle.asStateFlow()

    private val _vehicleToEdit       = MutableStateFlow<AuthManager.Vehicle?>(null)
    val vehicleToEdit: StateFlow<AuthManager.Vehicle?> = _vehicleToEdit.asStateFlow()

    private val _isEditingVehicle    = MutableStateFlow(false)
    val isEditingVehicle: StateFlow<Boolean> = _isEditingVehicle.asStateFlow()

    private val _editVehicleError    = MutableStateFlow<String?>(null)
    val editVehicleError: StateFlow<String?> = _editVehicleError.asStateFlow()

    private val _showEditVehicleDialog = MutableStateFlow(false)
    val showEditVehicleDialog: StateFlow<Boolean> = _showEditVehicleDialog.asStateFlow()

    // ── Sensor / scanning ───────────────────────────────────────────────────

    private val _sensorData      = MutableStateFlow<Map<ObdCommand, ObdResponseParser.ParsedValue>>(emptyMap())
    val sensorData: StateFlow<Map<ObdCommand, ObdResponseParser.ParsedValue>> = _sensorData.asStateFlow()

    private val _isScanning      = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isLogging       = MutableStateFlow(false)
    val isLogging: StateFlow<Boolean> = _isLogging.asStateFlow()

    private val _uploadStatus    = MutableStateFlow<TelemetryUploader.UploadStatus>(TelemetryUploader.UploadStatus.IDLE)
    val uploadStatus: StateFlow<TelemetryUploader.UploadStatus> = _uploadStatus.asStateFlow()

    private val _selectedCategory = MutableStateFlow<ObdCategory?>(null)
    val selectedCategory: StateFlow<ObdCategory?> = _selectedCategory.asStateFlow()

    private val _logMessages     = MutableStateFlow<List<String>>(emptyList())
    val logMessages: StateFlow<List<String>> = _logMessages.asStateFlow()

    // ── Sessions ────────────────────────────────────────────────────────────

    /** Lokalne pliki JSON */
    private val _localSessions   = MutableStateFlow<List<ObdDataLogger.SessionInfo>>(emptyList())

    /** Sesje pobrane z serwera */
    private val _serverSessions  = MutableStateFlow<List<ServerSession>>(emptyList())
    val serverSessions: StateFlow<List<ServerSession>> = _serverSessions.asStateFlow()

    private val _isLoadingSessions = MutableStateFlow(false)
    val isLoadingSessions: StateFlow<Boolean> = _isLoadingSessions.asStateFlow()

    /**
     * Połączona lista sesji widoczna w UI:
     * – filtrowana po aktualnie wybranym pojeździe (vehicleId)
     * – deduplikowana (lokalna + serwerowa = jeden wpis)
     * – serwerowe bez lokalnego odpowiednika oznaczone isOnlineOnly
     */
    val mergedSessions: StateFlow<List<MergedSession>> get() = _mergedSessions
    private val _mergedSessions  = MutableStateFlow<List<MergedSession>>(emptyList())

    /** Sesja oczekująca na potwierdzenie usunięcia */
    private val _sessionToDelete = MutableStateFlow<MergedSession?>(null)
    val sessionToDelete: StateFlow<MergedSession?> = _sessionToDelete.asStateFlow()

    private val _isDeletingSession = MutableStateFlow(false)
    val isDeletingSession: StateFlow<Boolean> = _isDeletingSession.asStateFlow()

    // ── Analysis ────────────────────────────────────────────────────────────

    private val _analysisState   = MutableStateFlow<AnalysisState>(AnalysisState.Idle)
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()

    /** true gdy bottom sheet z analizą ma być widoczny */
    private val _showAnalysisSheet = MutableStateFlow(false)
    val showAnalysisSheet: StateFlow<Boolean> = _showAnalysisSheet.asStateFlow()

    // ── BT ──────────────────────────────────────────────────────────────────

    val connectionState   = bluetoothManager.connectionState
    val supportedCommands = bluetoothManager.supportedCommands
    val vinInfo           = bluetoothManager.vinInfo

    // ── Stale sesje z poprzedniej wersji (sessionFiles) ─────────────────────
    // Zachowane dla kompatybilności z PostConnectContent podczas migracji
    val sessionFiles: StateFlow<List<ObdDataLogger.SessionInfo>> = _localSessions

    private var scanJob:    Job? = null
    private var connectJob: Job? = null

    init {
        if (authManager.isLoggedIn) {
            loadVehicles()
            viewModelScope.launch { refreshSessions() }
        }
        uploader.tokenProvider = { authManager.token }
    }

    // ── Auth actions ────────────────────────────────────────────────────────

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true; _authError.value = null
            when (val r = authManager.login(email, password)) {
                is AuthManager.AuthResult.Success -> {
                    _isGuest.value = false; _authPassed.value = true
                    loadVehicles(); refreshSessions()
                }
                is AuthManager.AuthResult.Error -> _authError.value = r.message
            }
            _isAuthLoading.value = false
        }
    }

    fun register(email: String, firstName: String, lastName: String, password: String) {
        viewModelScope.launch {
            _isAuthLoading.value = true; _authError.value = null
            when (val r = authManager.register(email, firstName, lastName, password)) {
                is AuthManager.AuthResult.Success -> {
                    _isGuest.value = false; _authPassed.value = true
                    loadVehicles(); refreshSessions()
                }
                is AuthManager.AuthResult.Error -> _authError.value = r.message
            }
            _isAuthLoading.value = false
        }
    }

    fun continueAsGuest() { _isGuest.value = true; _authPassed.value = true }

    fun logout() {
        authManager.logout()
        _isGuest.value = false; _authPassed.value = false; _authError.value = null
        _vehicles.value = emptyList(); _selectedVehicleId.value = null
        _serverSessions.value = emptyList(); _localSessions.value = emptyList()
        _mergedSessions.value = emptyList()
        disconnect()
    }

    fun clearAuthError() { _authError.value = null }

    // ── Vehicle actions ─────────────────────────────────────────────────────

    fun loadVehicles() {
        if (!authManager.isLoggedIn) return
        viewModelScope.launch {
            _isLoadingVehicles.value = true; _vehicleError.value = null
            when (val r = authManager.getVehicles()) {
                is AuthManager.VehicleResult.Success -> {
                    _vehicles.value = r.vehicles
                    val current = _selectedVehicleId.value
                    val validId = r.vehicles.firstOrNull { it.id == current }?.id
                        ?: r.vehicles.firstOrNull()?.id
                    _selectedVehicleId.value = validId
                    uploader.vehicleId = validId ?: 0
                    rebuildMerged()
                }
                is AuthManager.VehicleResult.Error -> _vehicleError.value = r.message
                else -> {}
            }
            _isLoadingVehicles.value = false
        }
    }

    fun selectVehicle(vehicleId: Int) {
        _selectedVehicleId.value = vehicleId
        uploader.vehicleId = vehicleId
        addLog("Aktywny pojazd: ID=$vehicleId")
        rebuildMerged()
    }

    fun showAddVehicle()  { _addVehicleError.value = null; _showAddVehicleDialog.value = true }
    fun hideAddVehicle()  { _showAddVehicleDialog.value = false; _addVehicleError.value = null }

    fun addVehicle(
        name: String, make: String, model: String, year: Int,
        fuelType: String, engineDisplacementL: Double,
        cylinderCount: Int, tankCapacityL: Int, vehicleMassKg: Int
    ) {
        viewModelScope.launch {
            _isAddingVehicle.value = true; _addVehicleError.value = null
            when (val r = authManager.addVehicle(
                name = name, make = make, model = model, year = year,
                fuelType = fuelType, engineDisplacementL = engineDisplacementL,
                cylinderCount = cylinderCount, tankCapacityL = tankCapacityL, vehicleMassKg = vehicleMassKg
            )) {
                is AuthManager.VehicleResult.Added -> {
                    _showAddVehicleDialog.value = false
                    loadVehicles()
                    addLog("Pojazd dodany (ID: ${r.id})")
                }
                is AuthManager.VehicleResult.Error -> _addVehicleError.value = r.message
                else -> {}
            }
            _isAddingVehicle.value = false
        }
    }

    fun requestDeleteVehicle(v: AuthManager.Vehicle) { _vehicleToDelete.value = v }
    fun cancelDeleteVehicle()                         { _vehicleToDelete.value = null }

    fun confirmDeleteVehicle() {
        val v = _vehicleToDelete.value ?: return
        viewModelScope.launch {
            _isDeletingVehicle.value = true
            when (val r = authManager.deleteVehicle(v.id)) {
                is AuthManager.VehicleResult.Deleted -> {
                    _vehicleToDelete.value = null
                    if (_selectedVehicleId.value == v.id) { _selectedVehicleId.value = null; uploader.vehicleId = 0 }
                    loadVehicles(); addLog("Pojazd usunięty: ${v.name}")
                }
                is AuthManager.VehicleResult.Error -> { _vehicleError.value = r.message; _vehicleToDelete.value = null }
                else -> _vehicleToDelete.value = null
            }
            _isDeletingVehicle.value = false
        }
    }

    fun requestEditVehicle(v: AuthManager.Vehicle) {
        _editVehicleError.value = null; _vehicleToEdit.value = v; _showEditVehicleDialog.value = true
    }
    fun hideEditVehicle() { _showEditVehicleDialog.value = false; _editVehicleError.value = null }

    fun editVehicle(
        id: Int, name: String, make: String, model: String, year: Int,
        fuelType: String, engineDisplacementL: Double,
        cylinderCount: Int, tankCapacityL: Int, vehicleMassKg: Int
    ) {
        viewModelScope.launch {
            _isEditingVehicle.value = true; _editVehicleError.value = null
            when (val r = authManager.updateVehicle(
                id = id, name = name, make = make, model = model, year = year,
                fuelType = fuelType, engineDisplacementL = engineDisplacementL,
                cylinderCount = cylinderCount, tankCapacityL = tankCapacityL, vehicleMassKg = vehicleMassKg
            )) {
                is AuthManager.VehicleResult.Updated -> {
                    _showEditVehicleDialog.value = false; _vehicleToEdit.value = null
                    loadVehicles(); addLog("Pojazd zaktualizowany (ID: $id)")
                }
                is AuthManager.VehicleResult.Error -> _editVehicleError.value = r.message
                else -> {}
            }
            _isEditingVehicle.value = false
        }
    }

    // ── Session management ──────────────────────────────────────────────────

    /**
     * Odświeża obie listy (lokalne + serwer) i scala je.
     */
    fun refreshSessions() {
        viewModelScope.launch {
            _isLoadingSessions.value = true
            // Lokalne pliki
            val locals = withContext(Dispatchers.IO) { dataLogger.listSessions() }
            _localSessions.value = locals

            // Serwerowe (tylko gdy zalogowany)
            if (authManager.isLoggedIn) {
                val servers = fetchServerSessionsHttp()
                _serverSessions.value = servers
                addLog("Sesje: ${locals.size} lokalnych, ${servers.size} serwerowych")
            }
            rebuildMerged()
            _isLoadingSessions.value = false
        }
    }

    /**
     * Buduje _mergedSessions: deduplikacja + filtr po wybranym pojeździe.
     */
    private fun rebuildMerged() {
        val locals   = _localSessions.value
        val servers  = _serverSessions.value
        val selectedVehicle = _selectedVehicleId.value

        // Mapa: sessionId -> ServerSession
        val serverMap = servers.associateBy { it.sessionId }
        // Mapa: sessionId -> LocalInfo
        val localMap  = locals.associateBy { it.sessionId }

        val allIds = (serverMap.keys + localMap.keys).toSortedSet().toList()

        val merged = allIds.mapNotNull { sid ->
            val srv = serverMap[sid]
            val loc = localMap[sid]
            // Filtr po pojeździe:
            // - sesje TYLKO serwerowe bez pasującego vehicleId → ukryj
            // - sesje lokalne (nawet zsynchronizowane) → zawsze pokaż
            if (selectedVehicle != null && srv != null && loc == null && srv.vehicleId != selectedVehicle) {
                return@mapNotNull null
            }
            MergedSession(
                sessionId  = sid,
                backendId  = srv?.backendId,
                localInfo  = loc,
                serverInfo = srv
            )
        }

        // Sortowanie: najnowsze pierwsze (po sessionId string descending)
        _mergedSessions.value = merged.sortedByDescending { it.sessionId }
    }

    /** Prosi o potwierdzenie usunięcia sesji */
    fun requestDeleteSession(session: MergedSession) { _sessionToDelete.value = session }
    fun cancelDeleteSession()                         { _sessionToDelete.value = null }

    /**
     * Usuwa sesję lokalnie i na serwerze (jeśli ma backendId).
     * DELETE /api/telemetry/sessions/{backendId}
     */
    fun confirmDeleteSession() {
        val session = _sessionToDelete.value ?: return
        viewModelScope.launch {
            _isDeletingSession.value = true
            _sessionToDelete.value = null

            // 1. Usuń lokalnie
            session.localInfo?.file?.let { file ->
                if (file.exists()) {
                    file.delete()
                    addLog("Usunięto lokalnie: ${session.sessionId}")
                }
            }

            // 2. Usuń na serwerze (jeśli istnieje)
            session.backendId?.let { bid ->
                val ok = deleteSessionHttp(bid)
                if (ok) addLog("Usunięto z serwera: ${session.sessionId} (id=$bid)")
                else    addLog("Błąd usuwania z serwera: ${session.sessionId}")
            }

            // 3. Wyczyść analizę jeśli dotyczyła tej sesji
            val cs = _analysisState.value
            if ((cs is AnalysisState.Success && cs.sessionId == session.sessionId) ||
                (cs is AnalysisState.Error   && cs.sessionId == session.sessionId)) {
                _analysisState.value = AnalysisState.Idle
                _showAnalysisSheet.value = false
            }

            refreshSessions()
            _isDeletingSession.value = false
        }
    }

    // ── Analysis ────────────────────────────────────────────────────────────

    /**
     * Logika: GET analizy → jeśli 404 → POST (triggeruj) i wróć wynik.
     * Otwiera bottom sheet po sukcesie lub błędzie.
     */
    fun fetchOrTriggerAnalysis(session: MergedSession) {
        val sessionId   = session.sessionId
        val backendId   = session.backendId

        if (backendId == null) {
            _analysisState.value = AnalysisState.Error(sessionId, "Sesja nie istnieje na serwerze — najpierw prześlij dane")
            _showAnalysisSheet.value = true
            return
        }

        val current = _analysisState.value
        if (current is AnalysisState.Loading && current.sessionId == sessionId) return

        viewModelScope.launch {
            _analysisState.value = AnalysisState.Loading(sessionId)
            _showAnalysisSheet.value = true

            // 1. Spróbuj GET
            val existing = getAnalysisHttp(backendId)
            if (existing != null) {
                _analysisState.value = AnalysisState.Success(sessionId, existing)
                return@launch
            }

            // 2. GET zwrócił 404 (lub inny błąd) → POST
            addLog("Brak analizy dla $sessionId — uruchamiam analizę AI...")
            val triggered = triggerAnalysisHttp(backendId)
            if (!triggered) {
                _analysisState.value = AnalysisState.Error(sessionId, "Nie udało się uruchomić analizy")
                return@launch
            }

            // 3. Poczekaj i pobierz wynik
            delay(4_000)
            val result = getAnalysisHttp(backendId)
            _analysisState.value = if (result != null)
                AnalysisState.Success(sessionId, result)
            else
                AnalysisState.Error(sessionId, "Analiza w toku — spróbuj za chwilę")
        }
    }

    fun dismissAnalysisSheet() {
        _showAnalysisSheet.value = false
        // Nie zerujemy stanu — żeby przy ponownym otwarciu wynik był od razu widoczny
    }

    fun deleteAnalysis(session: MergedSession) {
        val backendId = session.backendId ?: return
        if (_analysisState.value is AnalysisState.Loading) return
        viewModelScope.launch {
            _analysisState.value = AnalysisState.Loading(session.sessionId)
            val ok = deleteAnalysisHttp(backendId)
            _analysisState.value = if (ok) AnalysisState.Idle
            else AnalysisState.Error(session.sessionId, "Nie udało się usunąć analizy")
            if (ok) _showAnalysisSheet.value = false
        }
    }

    fun openAnalysisSheet() { _showAnalysisSheet.value = true }

    // ── BT / scanning ───────────────────────────────────────────────────────

    fun connect(device: BluetoothDevice) {
        connectJob?.cancel()
        connectJob = viewModelScope.launch {
            val success = bluetoothManager.connect(device, maxRetries = 3)
            if (success) {
                addLog("Połączono! Znaleziono ${supportedCommands.value.size} czujników")
                addLog("Protokół: ${bluetoothManager.activeProtocolName} | Timeout: ${bluetoothManager.calibratedTimeoutMs}ms")
                val vin = vinInfo.value
                if (vin.isNotBlank()) addLog("VIN: $vin")
                if (!_isGuest.value) {
                    if (uploader.vehicleId == 0) loadVehiclesSync()
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
        connectJob?.cancel(); stopScanning()
        viewModelScope.launch {
            if (!_isGuest.value) { flushAndUpload(); dataLogger.closeSession() }
        }
        bluetoothManager.disconnect()
        _sensorData.value = emptyMap(); _isLogging.value = false
        addLog("Rozłączono")
    }

    private fun startSession() {
        dataLogger.openSession(vin = vinInfo.value, email = authManager.savedEmail ?: "")
        _isLogging.value = true
        addLog("Sesja JSON: ${dataLogger.currentSessionFile()?.name}")
        addLog("Upload co ${uploader.uploadIntervalRecords} rekordów (~${uploader.uploadIntervalRecords}s)")
        refreshSessions(); startScanning()
    }

    fun stopLogging() {
        viewModelScope.launch {
            flushAndUpload(); dataLogger.closeSession()
            dataLogger.currentSessionFile()?.let { file ->
                if (file.exists()) {
                    val ok = uploader.uploadSessionFile(file)
                    addLog(if (ok) "Sesja wysłana na backend ✓" else "Sesja zapisana lokalnie (brak sieci)")
                }
            }
            _isLogging.value = false; _uploadStatus.value = uploader.lastUploadStatus
            addLog("Logowanie zatrzymane"); refreshSessions()
        }
    }

    fun startScanning() {
        if (_isScanning.value) return
        _isScanning.value = true
        addLog("Skanowanie: HIGH co ${BASE_INTERVAL_MS/1000}s, MEDIUM co ${MEDIUM_EVERY}s, LOW co ${LOW_EVERY}s")
        scanJob = viewModelScope.launch {
            var cycleCount = 0
            val onceCommands = supportedCommands.value.filter { it.priority == ReadPriority.ONCE }
            if (onceCommands.isNotEmpty()) {
                val onceData = bluetoothManager.readCommands(onceCommands)
                mergeData(onceData); addLog("Odczytano ${onceCommands.size} stałych parametrów")
            }
            while (_isScanning.value && bluetoothManager.isConnected) {
                val toRead = supportedCommands.value
                    .filter { it.priority != ReadPriority.ONCE && it.priority != ReadPriority.VIRTUAL }
                    .filter { cmd -> when (cmd.priority) {
                        ReadPriority.HIGH   -> true
                        ReadPriority.MEDIUM -> cycleCount % MEDIUM_EVERY == 0
                        ReadPriority.LOW    -> cycleCount % LOW_EVERY == 0
                        else -> false
                    }}
                if (toRead.isNotEmpty()) {
                    try {
                        val newData = bluetoothManager.readCommands(toRead); mergeData(newData)
                        if (_isLogging.value && !_isGuest.value) {
                            val recordJson = dataLogger.addRecord(_sensorData.value)
                            val metaJson   = buildMetaJson()
                            val uploaded   = if (recordJson != null) uploader.onNewRecord(recordJson, metaJson) else false
                            if (uploaded) {
                                _uploadStatus.value = uploader.lastUploadStatus
                                when (val s = uploader.lastUploadStatus) {
                                    is TelemetryUploader.UploadStatus.SUCCESS -> addLog("↑ Upload: ${s.recordsSent} rekordów wysłano")
                                    is TelemetryUploader.UploadStatus.FAILED  -> addLog("↑ Upload nieudany: ${s.error}")
                                    else -> {}
                                }
                            }
                        }
                    } catch (e: Exception) { addLog("Błąd skanowania: ${e.message}"); delay(2000) }
                }
                cycleCount++; delay(BASE_INTERVAL_MS)
            }
            _isScanning.value = false
        }
    }

    fun stopScanning() { scanJob?.cancel(); scanJob = null; _isScanning.value = false; addLog("Zatrzymano skanowanie") }
    fun selectCategory(category: ObdCategory?) { _selectedCategory.value = category }

    fun setBackendUrl(url: String) { uploader.backendUrl = url; addLog("Backend URL: $url") }

    fun retryPendingUploads() {
        viewModelScope.launch {
            val count = uploader.pendingRetryCount()
            if (count == 0) { addLog("Brak plików do ponownego wysłania"); return@launch }
            addLog("Ponowne wysyłanie $count plików...")
            val sent = uploader.retryPending()
            addLog("Ponownie wysłano: $sent/$count")
            _uploadStatus.value = uploader.lastUploadStatus; refreshSessions()
        }
    }

    // Zachowana dla kompatybilności
    fun refreshSessionList() { refreshSessions() }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private fun mergeData(newData: Map<ObdCommand, ObdResponseParser.ParsedValue>) {
        val merged = _sensorData.value.toMutableMap(); merged.putAll(newData)
        val vehicle = _vehicles.value.find { it.id == _selectedVehicleId.value }
        merged.putAll(formulaEngine.calculate(merged, vehicle))
        _sensorData.value = merged
    }

    private suspend fun flushAndUpload() {
        if (!_isLogging.value) return
        val old = uploader.uploadIntervalRecords; uploader.uploadIntervalRecords = 1
        val lastRecord = dataLogger.addRecord(_sensorData.value)
        if (lastRecord != null) uploader.onNewRecord(lastRecord, buildMetaJson())
        uploader.uploadIntervalRecords = old; _uploadStatus.value = uploader.lastUploadStatus
    }

    private fun buildMetaJson(): JSONObject = JSONObject().apply {
        put("session_id", dataLogger.currentSessionFile()?.nameWithoutExtension ?: "unknown")
        put("vin", vinInfo.value)
        put("started_at", dataLogger.currentSessionFile()?.let {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).format(java.util.Date(it.lastModified()))
        } ?: "")
    }

    private suspend fun loadVehiclesSync() {
        if (!authManager.isLoggedIn) return
        when (val r = authManager.getVehicles()) {
            is AuthManager.VehicleResult.Success -> {
                _vehicles.value = r.vehicles
                val current = _selectedVehicleId.value
                val validId = r.vehicles.firstOrNull { it.id == current }?.id ?: r.vehicles.firstOrNull()?.id ?: 0
                _selectedVehicleId.value = if (validId != 0) validId else null
                uploader.vehicleId = validId; addLog("Pojazd aktywny: ID=${uploader.vehicleId}")
            }
            else -> {}
        }
    }

    fun addLog(message: String) {
        val ts = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        // thread-safe: viewModelScope runs on Main by default, ale addLog może być wołany z IO
        val entry = "[$ts] $message"
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main.immediate) {
            val current = _logMessages.value.toMutableList()
            current.add(0, entry)
            if (current.size > 100) current.removeAt(current.size - 1)
            _logMessages.value = current
        }
    }

    fun getDataForCategory(category: ObdCategory): Map<ObdCommand, ObdResponseParser.ParsedValue> {
        val currentData = _sensorData.value
        return category.pids.associateWith { cmd -> currentData[cmd] ?: ObdResponseParser.ParsedValue("", null, "--", cmd.unit) }
    }

    override fun onCleared() {
        super.onCleared()
        connectJob?.cancel(); scanJob?.cancel()
        viewModelScope.launch { dataLogger.closeSession() }
        bluetoothManager.disconnect()
    }

    // ── HTTP calls ───────────────────────────────────────────────────────────

    /**
     * Wyciąga bazowy URL (https://api.nightingales.pl) z backendUrl,
     * który może wyglądać jak https://api.nightingales.pl/api/Telemetry/upload.
     * Porównanie case-insensitive żeby nie psuło się na różnych konfiguracjach.
     */
    private fun baseUrl(): String {
        val raw = uploader.backendUrl.trimEnd('/')
        val lower = raw.lowercase()
        return when {
            lower.contains("/api/telemetry") -> raw.substring(0, lower.indexOf("/api/telemetry"))
            lower.contains("/api/")          -> raw.substring(0, lower.indexOf("/api/"))
            else                             -> raw
        }
    }

    /** Buduje URL do API telemetrii — dopasowany do PascalCase konwencji serwera */
    private fun telemetryUrl(path: String) = "${baseUrl()}/api/Telemetry/$path"

    private fun authHeader() = authManager.token?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()

    private suspend fun httpGet(url: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            authHeader().forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val code = conn.responseCode
        val body = try { conn.inputStream.bufferedReader().readText() }
        catch (e: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
        conn.disconnect()
        Pair(code, body)
    }

    private suspend fun httpDelete(url: String): Int = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"; connectTimeout = 10_000; readTimeout = 15_000
            authHeader().forEach { (k, v) -> setRequestProperty(k, v) }
        }
        val code = conn.responseCode
        conn.disconnect()
        code
    }

    private suspend fun httpPost(url: String, body: String = "{}"): Pair<Int, String> = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 10_000; readTimeout = 30_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            authHeader().forEach { (k, v) -> setRequestProperty(k, v) }
            outputStream.write(body.toByteArray())
        }
        val code = conn.responseCode
        val resp = try { conn.inputStream.bufferedReader().readText() }
        catch (e: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
        conn.disconnect()
        Pair(code, resp)
    }

    private suspend fun fetchServerSessionsHttp(): List<ServerSession> =
        withContext(Dispatchers.IO) {
            try {
                val url = telemetryUrl("sessions?page=1&pageSize=100")
                addLog("GET sessions → $url")
                val (code, body) = httpGet(url)
                addLog("GET sessions → HTTP $code | body[${body.length}]=${body.take(120)}")
                if (code !in 200..299) return@withContext emptyList()
                val root  = org.json.JSONObject(body)
                val items = root.optJSONArray("items") ?: return@withContext emptyList()
                (0 until items.length()).map { i ->
                    val o   = items.getJSONObject(i)
                    val veh = o.optJSONObject("vehicle")
                    ServerSession(
                        backendId   = o.optInt("id"),
                        sessionId   = o.optString("sessionId"),
                        vin         = o.optString("vin").takeIf { it.isNotBlank() },
                        startedAt   = o.optString("startedAt"),
                        closedAt    = o.optString("closedAt"),
                        recordCount = o.optInt("recordCount"),
                        vehicleId   = veh?.optInt("id"),
                        vehicleName = veh?.let { v -> "${v.optString("make")} ${v.optString("model")}".trim() }
                    )
                }
            } catch (e: Exception) {
                addLog("fetchServerSessions błąd: ${e.message}"); emptyList()
            }
        }

    private suspend fun deleteSessionHttp(backendId: Int): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val code = httpDelete(telemetryUrl("sessions/$backendId"))
                addLog("DELETE sessions/$backendId → $code")
                code in 200..299
            } catch (e: Exception) {
                addLog("deleteSession błąd: ${e.message}"); false
            }
        }

    /** GET sessions/{backendId}/analysis — zwraca null przy 404 */
    private suspend fun getAnalysisHttp(backendId: Int): SessionAnalysis? =
        withContext(Dispatchers.IO) {
            try {
                val (code, body) = httpGet(telemetryUrl("sessions/$backendId/analysis"))
                addLog("GET analysis/$backendId → $code")
                if (code in 200..299) parseAnalysis(body) else null
            } catch (e: Exception) {
                addLog("getAnalysis błąd: ${e.message}"); null
            }
        }

    /** POST sessions/{backendId}/analyze */
    private suspend fun triggerAnalysisHttp(backendId: Int): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val (code, _) = httpPost(telemetryUrl("sessions/$backendId/analyze"))
                addLog("POST analyze/$backendId → $code")
                code in 200..299
            } catch (e: Exception) {
                addLog("triggerAnalysis błąd: ${e.message}"); false
            }
        }

    /** DELETE sessions/{backendId}/analysis */
    private suspend fun deleteAnalysisHttp(backendId: Int): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val code = httpDelete(telemetryUrl("sessions/$backendId/analysis"))
                addLog("DELETE analysis/$backendId → $code")
                code in 200..299
            } catch (e: Exception) {
                addLog("deleteAnalysis błąd: ${e.message}"); false
            }
        }

    private fun parseAnalysis(json: String): SessionAnalysis? = try {
        val obj    = org.json.JSONObject(json)
        val arr    = obj.optJSONArray("issues") ?: org.json.JSONArray()
        val issues = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            AnalysisIssue(
                component   = o.optString("component"),
                severity    = o.optString("severity", "OK"),
                description = o.optString("description")
            )
        }
        SessionAnalysis(
            id        = obj.optInt("id"),
            sessionId = obj.optInt("sessionId"),
            createdAt = obj.optString("createdAt"),
            summary   = obj.optString("summary"),
            issues    = issues
        )
    } catch (e: Exception) { null }

    // Zachowane dla kompatybilności — stare wywołania z string sessionId
    fun fetchOrTriggerAnalysis(sessionId: String) {
        val session = _mergedSessions.value.find { it.sessionId == sessionId }
            ?: MergedSession(sessionId = sessionId, backendId = null, localInfo = null, serverInfo = null)
        fetchOrTriggerAnalysis(session)
    }

    fun deleteAnalysis(sessionId: String) {
        val session = _mergedSessions.value.find { it.sessionId == sessionId } ?: return
        deleteAnalysis(session)
    }
}