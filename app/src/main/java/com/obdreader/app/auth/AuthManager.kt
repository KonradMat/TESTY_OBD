package com.obdreader.app.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Zarządza autentykacją użytkownika (JWT).
 * Przechowuje token w SharedPreferences.
 */
class AuthManager(private val context: Context) {

    companion object {
        private const val TAG = "AuthManager"
        private const val PREFS_NAME = "obd_auth"
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_EMAIL = "user_email"
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 15_000
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val BASE_URL = "https://api.nightingales.pl"

    // ─── Token ────────────────────────────────────────────────────────────────

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        private set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    var savedEmail: String?
        get() = prefs.getString(KEY_EMAIL, null)
        private set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    val isLoggedIn: Boolean get() = !token.isNullOrBlank()

    fun logout() {
        prefs.edit().remove(KEY_TOKEN).apply()
        Log.d(TAG, "Wylogowano")
    }

    // ─── Auth endpoints ───────────────────────────────────────────────────────

    sealed class AuthResult {
        object Success : AuthResult()
        data class Error(val message: String) : AuthResult()
    }

    /**
     * Rejestracja nowego konta.
     * POST /api/Auth/register
     */
    suspend fun register(
        email: String,
        firstName: String,
        lastName: String,
        password: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("email", email)
                put("firstName", firstName)
                put("lastName", lastName)
                put("password", password)
            }
            val response = postJson("$BASE_URL/api/Auth/register", body.toString())
            if (response.first in 200..299) {
                login(email, password)
            } else {
                val msg = parseErrorMessage(response.second) ?: "Błąd rejestracji (${response.first})"
                AuthResult.Error(msg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd rejestracji: ${e.message}")
            AuthResult.Error("Brak połączenia z serwerem")
        }
    }

    /**
     * Logowanie.
     * POST /api/Auth/login
     */
    suspend fun login(email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("email", email)
                    put("password", password)
                }
                val response = postJson("$BASE_URL/api/Auth/login", body.toString())
                if (response.first in 200..299) {
                    val jwt = extractToken(response.second)
                    if (jwt != null) {
                        token = jwt
                        savedEmail = email
                        AuthResult.Success
                    } else {
                        AuthResult.Error("Serwer nie zwrócił tokenu")
                    }
                } else {
                    val msg = parseErrorMessage(response.second) ?: "Nieprawidłowy email lub hasło"
                    AuthResult.Error(msg)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Błąd logowania: ${e.message}")
                AuthResult.Error("Brak połączenia z serwerem")
            }
        }

    // ─── Vehicles endpoint ─────────────────────────────────────────────────────

    sealed class VehicleResult {
        data class Success(val vehicles: List<Vehicle>) : VehicleResult()
        data class Added(val id: Int?) : VehicleResult()
        object Deleted : VehicleResult()
        data class Error(val message: String) : VehicleResult()
    }

    data class Vehicle(
        val id: Int,
        val name: String,
        val make: String,
        val model: String,
        val year: String
    )

    /**
     * Pobiera listę pojazdów użytkownika.
     * GET /api/Vehicles
     */
    suspend fun getVehicles(): VehicleResult = withContext(Dispatchers.IO) {
        try {
            val response = getJson("$BASE_URL/api/Vehicles")
            if (response.first in 200..299) {
                val list = mutableListOf<Vehicle>()
                val arr = org.json.JSONArray(response.second)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        Vehicle(
                            id    = obj.optInt("id", -1),
                            name  = obj.optString("name"),
                            make  = obj.optString("make"),
                            model = obj.optString("model"),
                            year  = obj.optString("year")
                        )
                    )
                }
                VehicleResult.Success(list)
            } else {
                VehicleResult.Error("Błąd pobierania pojazdów (${response.first})")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd getVehicles: ${e.message}")
            VehicleResult.Error("Brak połączenia z serwerem")
        }
    }

    /**
     * Dodaje nowy pojazd.
     * POST /api/Vehicles
     */
    suspend fun addVehicle(
        name: String,
        make: String,
        model: String,
        year: String
    ): VehicleResult = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                put("name", name)
                put("make", make)
                put("model", model)
                put("year", year)
            }
            val response = postJson("$BASE_URL/api/Vehicles", body.toString())
            if (response.first in 200..299) {
                val id = try {
                    JSONObject(response.second).optInt("id")
                } catch (e: Exception) { null }
                VehicleResult.Added(id)
            } else {
                val msg = parseErrorMessage(response.second) ?: "Błąd dodawania pojazdu (${response.first})"
                VehicleResult.Error(msg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd addVehicle: ${e.message}")
            VehicleResult.Error("Brak połączenia z serwerem")
        }
    }

    /**
     * Usuwa pojazd.
     * DELETE /api/Vehicles/{id}
     */
    suspend fun deleteVehicle(id: Int): VehicleResult = withContext(Dispatchers.IO) {
        try {
            val response = deleteJson("$BASE_URL/api/Vehicles/$id")
            if (response.first in 200..299) {
                VehicleResult.Deleted
            } else {
                val msg = parseErrorMessage(response.second) ?: "Błąd usuwania pojazdu (${response.first})"
                VehicleResult.Error(msg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd deleteVehicle: ${e.message}")
            VehicleResult.Error("Brak połączenia z serwerem")
        }
    }

    // ─── HTTP helpers ─────────────────────────────────────────────────────────

    private fun postJson(urlStr: String, jsonBody: String): Pair<Int, String> {
        val url = URL(urlStr)
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
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use {
                it.write(jsonBody); it.flush()
            }
            val code = conn.responseCode
            val body = try { conn.inputStream.bufferedReader().readText() }
            catch (e: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
            Pair(code, body)
        } finally {
            conn.disconnect()
        }
    }

    private fun getJson(urlStr: String): Pair<Int, String> {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.apply {
                requestMethod = "GET"
                doInput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            val code = conn.responseCode
            val body = try { conn.inputStream.bufferedReader().readText() }
            catch (e: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
            Pair(code, body)
        } finally {
            conn.disconnect()
        }
    }

    private fun deleteJson(urlStr: String): Pair<Int, String> {
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.apply {
                requestMethod = "DELETE"
                doInput = true
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                token?.let { setRequestProperty("Authorization", "Bearer $it") }
            }
            val code = conn.responseCode
            val body = try { conn.inputStream.bufferedReader().readText() }
            catch (e: Exception) { conn.errorStream?.bufferedReader()?.readText() ?: "" }
            Pair(code, body)
        } finally {
            conn.disconnect()
        }
    }

    // ─── Parsery ──────────────────────────────────────────────────────────────

    private fun extractToken(body: String): String? {
        return try {
            val obj = JSONObject(body)
            obj.optString("token").takeIf { it.isNotBlank() }
                ?: obj.optString("accessToken").takeIf { it.isNotBlank() }
                ?: obj.optString("access_token").takeIf { it.isNotBlank() }
                ?: obj.optString("jwt").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            body.trim().takeIf { it.length > 20 && !it.startsWith("{") }
        }
    }

    private fun parseErrorMessage(body: String): String? {
        return try {
            val obj = JSONObject(body)
            obj.optString("message").takeIf { it.isNotBlank() }
                ?: obj.optString("error").takeIf { it.isNotBlank() }
                ?: obj.optString("title").takeIf { it.isNotBlank() }
        } catch (e: Exception) {
            body.takeIf { it.isNotBlank() && it.length < 200 }
        }
    }
}