package com.obdreader.app.obd

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wysyła dane telemetryczne OBD2 na backend.
 *
 * Endpoint: POST /api/Telemetry/upload
 * Body: JSON identyczny jak plik sesji z ObdDataLogger
 *
 * Strategie wysyłania:
 * - BATCH:   bufor N rekordów → wyślij jeden request
 * - SESSION: wyślij cały plik sesji po jej zamknięciu
 * - RETRY:   nieudane requesty trafiają do kolejki retry
 */
class TelemetryUploader(private val context: Context) {

    companion object {
        private const val TAG = "TelemetryUploader"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS    = 15_000
        private const val MAX_RETRY_FILES    = 20
        private const val RETRY_DIR          = "upload_retry"
        private val ISO = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    }

    // ─── Konfiguracja (można zmienić w runtime) ───────────────────────────────

    var backendUrl: String = "https://api.nightingales.pl/api/Telemetry/upload"

    var tokenProvider: (() -> String?)? = null

    var vehicleId: Int = 0

    var uploadIntervalRecords: Int = 30

    var uploadOnSessionClose: Boolean = true

    var retryOnWifi: Boolean = true

    // ─── Stan ─────────────────────────────────────────────────────────────────

    private var pendingBatch = mutableListOf<JSONObject>()
    private var sessionMeta  = JSONObject()
    var lastUploadStatus: UploadStatus = UploadStatus.IDLE
        private set

    sealed class UploadStatus {
        object IDLE       : UploadStatus()
        object UPLOADING  : UploadStatus()
        data class SUCCESS(val recordsSent: Int, val timestamp: String) : UploadStatus()
        data class FAILED (val error: String,    val willRetry: Boolean) : UploadStatus()
    }

    // ─── API publiczne ────────────────────────────────────────────────────────

    /** Wywoływane przez ObdViewModel przy każdym nowym rekordzie */
    suspend fun onNewRecord(record: JSONObject, meta: JSONObject): Boolean {
        return withContext(Dispatchers.IO) {
            sessionMeta = meta
            pendingBatch.add(record)

            if (pendingBatch.size >= uploadIntervalRecords) {
                val batch = pendingBatch.toList()
                pendingBatch.clear()
                uploadBatch(batch, meta)
            } else false
        }
    }

    /** Wywoływane po zamknięciu sesji – wysyła cały plik */
    suspend fun uploadSessionFile(file: File): Boolean = withContext(Dispatchers.IO) {
        if (!uploadOnSessionClose) return@withContext false
        try {
            val json = JSONObject(file.readText())
            json.put("vehicle_id", vehicleId)
            json.remove("vin")
            json.remove("record_count")
            if (!json.has("closed_at") || json.optString("closed_at").isBlank()) {
                json.put("closed_at", ISO.format(Date()))
            }
            json.remove("app_version")
            json.remove("batch")
            json.remove("uploaded_at")

            val ok = postJson(json.toString())
            if (ok) {
                Log.d(TAG, "Sesja wysłana: ${file.name}")
                lastUploadStatus = UploadStatus.SUCCESS(
                    recordsSent = json.optInt("record_count"),
                    timestamp   = ISO.format(Date())
                )
                true
            } else {
                saveToRetry(file)
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd wysyłania sesji: ${e.message}")
            saveToRetry(file)
            false
        }
    }

    /** Ponów wysyłanie plików z kolejki retry (np. po odzyskaniu sieci) */
    suspend fun retryPending(): Int = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, RETRY_DIR)
        if (!dir.exists()) return@withContext 0

        val files = dir.listFiles()?.filter { it.name.endsWith(".json") } ?: return@withContext 0
        var successCount = 0

        files.forEach { file ->
            try {
                // Zaktualizuj vehicle_id przed wysłaniem
                val json = JSONObject(file.readText())
                if (vehicleId > 0) json.put("vehicle_id", vehicleId)
                json.remove("vin")
                json.remove("record_count")
                json.remove("app_version")
                val ok = postJson(json.toString())
                if (ok) {
                    file.delete()
                    successCount++
                    Log.d(TAG, "Retry udany: ${file.name}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Retry nieudany: ${file.name} – ${e.message}")
            }
        }

        Log.d(TAG, "Retry: $successCount/${files.size} udanych")
        successCount
    }

    fun pendingRetryCount(): Int {
        val dir = File(context.filesDir, RETRY_DIR)
        return dir.listFiles()?.count { it.name.endsWith(".json") } ?: 0
    }

    // ─── Wewnętrzne ───────────────────────────────────────────────────────────

    private suspend fun uploadBatch(
        records: List<JSONObject>,
        meta: JSONObject
    ): Boolean = withContext(Dispatchers.IO) {

        lastUploadStatus = UploadStatus.UPLOADING

        val now = ISO.format(Date())

        Log.d(TAG, "Upload batch: vehicleId=$vehicleId, session=${meta.optString("session_id")}")

        val payload = JSONObject().apply {
            put("vehicle_id",   vehicleId)
            put("session_id",   meta.optString("session_id"))
            put("started_at",   meta.optString("started_at"))
            put("closed_at",    now)
            val arr = org.json.JSONArray()
            records.forEach { arr.put(it) }
            put("records", arr)
        }

        val ok = try {
            postJson(payload.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Błąd batcha: ${e.message}")
            false
        }

        if (ok) {
            Log.d(TAG, "Batch wysłany: ${records.size} rekordów")
            lastUploadStatus = UploadStatus.SUCCESS(
                recordsSent = records.size,
                timestamp   = now
            )
        } else {
            Log.w(TAG, "Batch nieudany – zapisuję do retry")
            lastUploadStatus = UploadStatus.FAILED(
                error     = "HTTP error lub brak sieci",
                willRetry = true
            )
            saveJsonToRetry(payload.toString())
        }
        ok
    }

    private fun postJson(jsonBody: String): Boolean {
        val url = URL(backendUrl)
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "OBD2Reader-Android/1.0")
                tokenProvider?.invoke()?.let {
                    setRequestProperty("Authorization", "Bearer $it")
                }
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = try {
                    conn.errorStream?.bufferedReader()?.readText() ?: "(brak body)"
                } catch (e: Exception) { "(błąd odczytu body)" }
                Log.e(TAG, "POST $backendUrl → HTTP $code | $errBody")
                lastUploadStatus = UploadStatus.FAILED(
                    error = "HTTP $code: $errBody",
                    willRetry = true
                )
            } else {
                Log.d(TAG, "POST $backendUrl → HTTP $code OK")
            }

            code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "POST nieudany [${e.javaClass.simpleName}]: ${e.message}", e)
            lastUploadStatus = UploadStatus.FAILED(
                error = "${e.javaClass.simpleName}: ${e.message ?: "unknown"}",
                willRetry = true
            )
            false
        } finally {
            conn.disconnect()
        }
    }

    private fun saveToRetry(file: File) {
        try {
            val retryDir = File(context.filesDir, RETRY_DIR).also { it.mkdirs() }
            val dest = File(retryDir, file.name)
            file.copyTo(dest, overwrite = true)
            Log.d(TAG, "Zapisano do retry: ${file.name}")
            pruneRetry(retryDir)
        } catch (e: Exception) {
            Log.e(TAG, "Błąd zapisu retry: ${e.message}")
        }
    }

    private fun saveJsonToRetry(json: String) {
        try {
            val retryDir = File(context.filesDir, RETRY_DIR).also { it.mkdirs() }
            val name = "batch_${System.currentTimeMillis()}.json"
            File(retryDir, name).writeText(json)
            pruneRetry(retryDir)
        } catch (e: Exception) {
            Log.e(TAG, "Błąd zapisu retry: ${e.message}")
        }
    }

    private fun pruneRetry(dir: File) {
        val files = dir.listFiles()
            ?.filter { it.name.endsWith(".json") }
            ?.sortedBy { it.lastModified() } ?: return
        if (files.size > MAX_RETRY_FILES) {
            files.take(files.size - MAX_RETRY_FILES).forEach { it.delete() }
        }
    }

    fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun isOnWifi(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}