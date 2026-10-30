package com.guardiansleeve.app.data

import android.util.Log
import com.guardiansleeve.app.model.AlertEscalationState
import com.guardiansleeve.app.model.TriggerSource
import com.guardiansleeve.app.model.VitalsData
import com.guardiansleeve.app.service.GuardianForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class BackendSyncManager(
    private var backendBaseUrl: String = AppSettings.DEFAULT_SERVER_URL
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun setServerUrl(url: String) {
        this.backendBaseUrl = url.trimEnd('/')
    }

    suspend fun syncTelemetry(
        userId: String = "sohan",
        latitude: Double?,
        longitude: Double?,
        vitals: VitalsData,
        alertState: AlertEscalationState,
        triggerSource: TriggerSource,
        isAccident: Boolean = false,
        isOwnerAlarmBeeping: Boolean = false
    ) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("userId", userId)
                put("latitude", latitude)
                put("longitude", longitude)
                put("heartRate", vitals.heartRate)
                put("spO2", vitals.spO2)
                put("battery", vitals.batteryPercent)
                put("alertState", alertState.name)
                put("triggerSource", triggerSource.name)
                put("isAccident", isAccident)
                put("isOwnerAlarmBeeping", isOwnerAlarmBeeping)
            }

            val url = "$backendBaseUrl/api/telemetry"
            val body = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(url).post(body).build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val respStr = response.body?.string() ?: ""
                val respJson = JSONObject(respStr)

                // Check if Owner issued a remote command
                if (!respJson.isNull("pendingCommand")) {
                    val cmdObj = respJson.getJSONObject("pendingCommand")
                    val cmd = cmdObj.optString("command", "")
                    handleRemoteCommand(cmd)
                }
            }
            response.close()
        } catch (e: Exception) {
            Log.w(TAG, "Backend sync skipped (offline or server unreachable): ${e.message}")
        }
    }

    suspend fun silenceAlarmOnBackend(userId: String = "sohan"): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "$backendBaseUrl/api/device/$userId/silence-beep"
            val request = Request.Builder().url(url).post("{}".toRequestBody("application/json".toMediaType())).build()
            val response = client.newCall(request).execute()
            val success = response.isSuccessful
            response.close()
            success
        } catch (e: Exception) {
            false
        }
    }

    private fun handleRemoteCommand(command: String) {
        Log.i(TAG, "Received remote command from Owner: $command")
        when (command) {
            "DISARM" -> {
                GuardianForegroundService.instance?.cancelAlert("Disarmed Remotely by Owner via Cloud", notifyBackend = false)
            }
            "FORCE_SOS" -> {
                GuardianForegroundService.instance?.handleTrigger(
                    TriggerSource.REMOTE_OWNER,
                    "Owner Remote Emergency Alarm"
                )
            }
            "SILENCE_BEEP" -> {
                GuardianForegroundService.instance?.silenceOwnerAlarm("Owner Silenced Alarm Remotely", notifyBackend = false)
            }
        }
    }

    companion object {
        private const val TAG = "BackendSyncManager"
    }
}
