package com.guardiansleeve.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TelegramApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Default Bot Token (configure in App Settings or via TelegramApiService.setBotToken)
    private var botToken: String = "YOUR_TELEGRAM_BOT_TOKEN"

    fun setBotToken(token: String) {
        this.botToken = token
    }

    suspend fun sendEmergencyMessage(
        chatId: String,
        victimName: String,
        triggerCause: String,
        latitude: Double?,
        longitude: Double?,
        heartRate: Int,
        batteryPercent: Int
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val mapsLink = if (latitude != null && longitude != null) {
                "https://www.google.com/maps?q=$latitude,$longitude"
            } else {
                "Location unavailable"
            }

            val text = """
                🚨 <b>GUARDIAN SLEEVE EMERGENCY SOS</b> 🚨
                
                ⚠️ <b>Victim:</b> $victimName
                ⚠️ <b>Trigger:</b> $triggerCause
                💓 <b>Heart Rate:</b> $heartRate BPM
                🔋 <b>Battery:</b> $batteryPercent%
                
                📍 <b>Current Location:</b>
                $mapsLink
                
                <i>Immediate assistance is required! If this is an emergency, contact local authorities immediately.</i>
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                put("chat_id", chatId)
                put("text", text)
                put("parse_mode", "HTML")
                put("disable_web_page_preview", false)
            }

            val url = "https://api.telegram.org/bot$botToken/sendMessage"
            val body = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()

            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            Log.d(TAG, "Telegram sendMessage to $chatId status: ${response.code}, success: $success")

            // If coordinates are available, also send pin location
            if (success && latitude != null && longitude != null) {
                sendLocationPin(chatId, latitude, longitude)
            }

            return@withContext success
        } catch (e: Exception) {
            Log.e(TAG, "Error sending Telegram alert to $chatId: ${e.message}", e)
            return@withContext false
        }
    }

    private suspend fun sendLocationPin(chatId: String, latitude: Double, longitude: Double) = withContext(Dispatchers.IO) {
        try {
            val jsonBody = JSONObject().apply {
                put("chat_id", chatId)
                put("latitude", latitude)
                put("longitude", longitude)
            }
            val url = "https://api.telegram.org/bot$botToken/sendLocation"
            val body = jsonBody.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute().close()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send precise pin location: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "TelegramApiService"
    }
}
