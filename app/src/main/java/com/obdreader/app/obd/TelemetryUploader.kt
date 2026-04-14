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
        // Działa z: adb reverse tcp:5032 tcp:5032 (telefon USB)
        // Na fizycznym urządzeniu zmień na adres IP serwera, np. "http://192.168.1.100:5032/..."

    var uploadIntervalRecords: Int = 30
        // Wyślij batch co N rekordów (przy ~1s/rekord = co ~30 sekund)

    var uploadOnSessionClose: Boolean = true
        // Wyślij cały plik po zamknięciu sesji

    var retryOnWifi: Boolean = true
        // Ponów nieudane wysyłki gdy pojawi się WiFi

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
        // Aktualizacja bufora zawsze na IO (bezpieczna operacja na liście)
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
            val json = file.readText()
            val ok = postJson(json)
            if (ok) {
                Log.d(TAG, "Sesja wysłana: ${file.name}")
                lastUploadStatus = UploadStatus.SUCCESS(recordsSent = 0, timestamp = ISO.format(Date()))
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
                val ok = postJson(file.readText())
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

        // Zbuduj payload identyczny jak plik sesji
        val payload = JSONObject().apply {
            put("session_id",   meta.optString("session_id"))
            put("vin",          meta.optString("vin"))
            put("started_at",   meta.optString("started_at"))
            put("uploaded_at",  ISO.format(Date()))
            put("record_count", records.size)
            put("batch",        true)
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
                timestamp   = ISO.format(Date())
            )
        } else {
            Log.w(TAG, "Batch nieudany – zapisuję do retry")
            lastUploadStatus = UploadStatus.FAILED(
                error      = "HTTP error lub brak sieci",
                willRetry  = true
            )
            // Zapisz do retry jako plik
            saveJsonToRetry(payload.toString())
        }
        ok
    }

    /**
     * Wykonuje HTTP POST z JSON body.
     * Używa tylko standardowej biblioteki Java – bez zewnętrznych zależności.
     */
    private fun postJson(jsonBody: String): Boolean {
        val url = URL(backendUrl)
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.apply {
                requestMethod       = "POST"
                doOutput            = true
                doInput             = true
                connectTimeout      = CONNECT_TIMEOUT_MS
                readTimeout         = READ_TIMEOUT_MS
                setRequestProperty("Content-Type",  "application/json; charset=utf-8")
                setRequestProperty("Accept",        "application/json")
                setRequestProperty("User-Agent",    "OBD2Reader-Android/1.0")
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonBody)
                writer.flush()
            }

            val code = conn.responseCode
            Log.d(TAG, "POST $backendUrl → HTTP $code")

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
            // Usuń stare pliki retry jeśli za dużo
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

    /** Sprawdza czy jest połączenie z internetem */
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
