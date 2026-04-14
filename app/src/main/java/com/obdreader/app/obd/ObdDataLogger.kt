package com.obdreader.app.obd

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Zapisuje odczyty OBD2 do pliku JSON w katalogu aplikacji.
 *
 * Struktura pliku:
 * {
 *   "session_id": "2026-03-17T12:34:56",
 *   "vin": "WF0XXGCBXKJA12345",
 *   "started_at": "2026-03-17T12:34:56.000Z",
 *   "records": [
 *     {
 *       "ts": "2026-03-17T12:34:57.123Z",
 *       "data": {
 *         "ENGINE_RPM":   { "v": 1250.0, "display": "1250", "unit": "rpm" },
 *         "VEHICLE_SPEED":{ "v": 0.0,    "display": "0",    "unit": "km/h" },
 *         ...
 *       }
 *     }
 *   ]
 * }
 *
 * Każdy plik = jedna sesja jazdy.
 * Pliki trafiają do: /data/data/com.obdreader.app/files/obd_sessions/
 * Można je pobrać przez Android Studio → Device File Explorer,
 * lub udostępnić przez FileProvider do wysyłania na backend.
 */
class ObdDataLogger(private val context: Context) {

    companion object {
        private const val TAG = "ObdLogger"
        private const val DIR_NAME = "obd_sessions"
        private const val MAX_RECORDS_IN_MEMORY = 500   // flush co tyle rekordów
        private const val MAX_FILES = 50                // stare pliki są usuwane
        private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        private val FILE_DATE = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    }

    private var currentFile: File? = null
    private var sessionArray: JSONArray = JSONArray()
    private var sessionMeta: JSONObject = JSONObject()
    private var recordCount = 0
    private var isOpen = false

    // ─── Sesja ───────────────────────────────────────────────────────────────

    /**
     * Otwiera nową sesję logowania.
     * @param vin VIN pojazdu (lub pusty string)
     */
    fun openSession(vin: String = "") {
        val now = Date()
        val sessionId = FILE_DATE.format(now)
        val dir = File(context.filesDir, DIR_NAME).also { it.mkdirs() }
        currentFile = File(dir, "session_$sessionId.json")

        sessionMeta = JSONObject().apply {
            put("session_id", sessionId)
            put("vin", vin)
            put("started_at", ISO.format(now))
            put("app_version", "1.0")
        }
        sessionArray = JSONArray()
        recordCount = 0
        isOpen = true

        Log.d(TAG, "Sesja otwarta: ${currentFile?.name}")
        pruneOldFiles(dir)
    }

    /**
     * Zamyka sesję i zapisuje finalny plik JSON na dysk.
     */
    suspend fun closeSession() = withContext(Dispatchers.IO) {
        if (!isOpen) return@withContext
        isOpen = false
        flush()
        Log.d(TAG, "Sesja zamknięta. Rekordów: $recordCount")
    }

    // ─── Zapis rekordu ────────────────────────────────────────────────────────

    /**
     * Dodaje jeden rekord z bieżącymi danymi wszystkich czujników.
     * Wywołuj po każdym cyklu skanowania.
     */
    suspend fun addRecord(data: Map<ObdCommand, ObdResponseParser.ParsedValue>): JSONObject? =
        withContext(Dispatchers.IO) {
            if (!isOpen) return@withContext null   // ← dodaj null

            val dataObj = JSONObject()
            data.forEach { (cmd, parsed) ->
                if (parsed.displayValue == "Brak danych" ||
                    parsed.displayValue == "N/A" ||
                    parsed.displayValue == "--"
                ) return@forEach

                dataObj.put(cmd.cmdName, JSONObject().apply {
                    parsed.value?.let { put("v", it) }
                    put("display", parsed.displayValue)
                    put("unit", parsed.unit)
                    put("pid", "${cmd.mode}${cmd.pid}")
                })
            }

            if (dataObj.length() == 0) return@withContext null   // ← dodaj null

            val record = JSONObject().apply {
                put("ts", ISO.format(Date()))
                put("data", dataObj)
            }
            sessionArray.put(record)
            recordCount++

            if (recordCount % MAX_RECORDS_IN_MEMORY == 0) {
                flush()
            }

            record   // ← dodaj return na końcu
        }

    // ─── Zapis pliku ─────────────────────────────────────────────────────────

    private fun flush() {
        val file = currentFile ?: return
        try {
            val root = JSONObject(sessionMeta.toString())  // kopia meta
            root.put("record_count", recordCount)
            root.put("closed_at", ISO.format(Date()))
            root.put("records", sessionArray)

            file.writeText(root.toString(2))   // pretty-print z wcięciem 2 spacji
            Log.d(TAG, "Flush: ${file.name} ($recordCount rekordów, ${file.length() / 1024}KB)")
        } catch (e: Exception) {
            Log.e(TAG, "Błąd zapisu: ${e.message}")
        }
    }

    // ─── Zarządzanie plikami ─────────────────────────────────────────────────

    /** Usuwa najstarsze pliki jeśli przekroczono limit */
    private fun pruneOldFiles(dir: File) {
        val files = dir.listFiles()
            ?.filter { it.name.endsWith(".json") }
            ?.sortedBy { it.lastModified() }
            ?: return

        if (files.size > MAX_FILES) {
            files.take(files.size - MAX_FILES).forEach {
                it.delete()
                Log.d(TAG, "Usunięto stary plik: ${it.name}")
            }
        }
    }

    /** Zwraca listę wszystkich zapisanych plików sesji */
    fun listSessions(): List<SessionInfo> {
        val dir = File(context.filesDir, DIR_NAME)
        return dir.listFiles()
            ?.filter { it.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                try {
                    val json = JSONObject(file.readText())
                    SessionInfo(
                        file = file,
                        sessionId = json.optString("session_id"),
                        vin = json.optString("vin"),
                        startedAt = json.optString("started_at"),
                        recordCount = json.optInt("record_count"),
                        sizeKb = file.length() / 1024
                    )
                } catch (e: Exception) {
                    SessionInfo(file = file, sessionId = file.name)
                }
            } ?: emptyList()
    }

    /** Zwraca aktualnie otwarty plik (do wysyłki na backend) */
    fun currentSessionFile(): File? = currentFile

    val isSessionOpen: Boolean get() = isOpen

    data class SessionInfo(
        val file: File,
        val sessionId: String,
        val vin: String = "",
        val startedAt: String = "",
        val recordCount: Int = 0,
        val sizeKb: Long = 0
    )
}
