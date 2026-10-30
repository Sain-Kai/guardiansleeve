package com.guardiansleeve.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.IBinder
import android.os.PowerManager
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import com.guardiansleeve.app.GuardianApplication
import com.guardiansleeve.app.R
import com.guardiansleeve.app.ble.GuardianBleManager
import com.guardiansleeve.app.data.AppSettings
import com.guardiansleeve.app.data.BackendSyncManager
import com.guardiansleeve.app.data.ContactsRepository
import com.guardiansleeve.app.data.TelegramApiService
import com.guardiansleeve.app.model.AlertEscalationState
import com.guardiansleeve.app.model.EmergencyAlert
import com.guardiansleeve.app.model.TriggerSource
import com.guardiansleeve.app.ui.MainActivity
import com.guardiansleeve.app.ui.screens.StealthTakeoverActivity
import com.guardiansleeve.app.utils.LocationHelper
import com.guardiansleeve.app.utils.OwnerAlarmBuzzer
import com.guardiansleeve.app.utils.VoiceTriggerDetector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class GuardianForegroundService : Service(), SensorEventListener {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var bleManager: GuardianBleManager
    private lateinit var voiceDetector: VoiceTriggerDetector
    private lateinit var locationHelper: LocationHelper
    private val contactsRepo = ContactsRepository()
    private val telegramApi = TelegramApiService()
    private val backendSync = BackendSyncManager()

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var lastFreefallTimestamp = 0L

    private var countdownJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GuardianSleeve::ProtectionLock").apply {
            acquire(24 * 60 * 60 * 1000L) // 24 hours
        }

        bleManager = GuardianBleManager(this)
        voiceDetector = VoiceTriggerDetector(this)
        locationHelper = LocationHelper(this)

        // Load persisted settings (Hotspot/Server URL & optional NVIDIA Nemotron ASR key)
        val settings = AppSettings(this)
        backendSync.setServerUrl(settings.serverUrl)
        if (settings.telegramBotToken.isNotBlank()) {
            telegramApi.setBotToken(settings.telegramBotToken)
        }
        val notification = buildForegroundNotification("Active & Guarding")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            var serviceType = android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                serviceType = serviceType or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            startForeground(NOTIFICATION_ID, notification, serviceType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        bleManager.startScanAndConnect()
        // NOTE: Mobile microphone is NOT started here!
        // It stays completely OFF to preserve battery & privacy until INMP441 on sleeve detects a shout.

        setupPhoneAccelerometerDetection()
        observeSubsystems()
        startPeriodicBackendSync()

        // Register receiver for USB Serial Hardware Bridge and Hotspot IP dynamic updates
        val filter = android.content.IntentFilter().apply {
            addAction("com.guardiansleeve.app.HARDWARE_ALERT")
            addAction("com.guardiansleeve.app.UPDATE_SETTINGS")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(hardwareAlertReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(hardwareAlertReceiver, filter)
        }
    }

    private val hardwareAlertReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == "com.guardiansleeve.app.UPDATE_SETTINGS") {
                val newUrl = intent.getStringExtra("serverUrl")
                val newKey = intent.getStringExtra("apiKey") ?: ""
                val newTg = intent.getStringExtra("telegramToken") ?: ""
                val settings = AppSettings(context ?: this@GuardianForegroundService)
                if (!newUrl.isNullOrBlank()) {
                    Log.i(TAG, "📡 Dynamic Hotspot IP Update received: $newUrl")
                    settings.serverUrl = newUrl
                    backendSync.setServerUrl(newUrl)
                }
                if (newKey.isNotBlank()) {
                    settings.nemotronApiKey = newKey
                    voiceDetector.nemotronClient.setApiKey(newKey)
                }
                if (newTg.isNotBlank()) {
                    settings.telegramBotToken = newTg
                    telegramApi.setBotToken(newTg)
                    Log.i(TAG, "📡 Telegram Bot Token updated!")
                }
                return
            }

            val alert = intent?.getStringExtra("alert") ?: return
            Log.i(TAG, "📡 Received hardware alert via USB Bridge: $alert")
            when {
                alert.contains("HELP_VOICE") || alert.contains("VOICE_HELP") -> {
                    handleTrigger(TriggerSource.VOICE_HELP_X3, "Confirmed 'HELP' Voice Trigger")
                }
                alert.contains("SHOUT") || alert.contains("WAKE_MIC") -> {
                    voiceDetector.triggerOnDemandVerification("Hardware Sleeve Shout (USB Bridge)")
                }
                alert.contains("ACCIDENT") || alert.contains("FALL") -> {
                    handleTrigger(TriggerSource.ACCIDENT_FALL_BANG, "Sleeve Sensor: Hard Bang / Fall from Height Detected!")
                }
                alert.contains("BUTTON") -> {
                    handleTrigger(TriggerSource.SLEEVE_BUTTON, "Physical Panic Button")
                }
                alert.contains("PALPITATION") -> {
                    handleTrigger(TriggerSource.PALPITATION_SPIKE, "Abnormal Pulse Spike / Palpitation")
                }
                alert.contains("IMPACT") -> {
                    handleTrigger(TriggerSource.IMPACT_FALL, "Severe Impact / Struggle")
                }
            }
        }
    }

    private fun setupPhoneAccelerometerDetection() {
        try {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            accelerometer?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
                Log.d(TAG, "Phone built-in accelerometer listener active for fall/accident detection.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not register phone accelerometer: ${e.message}")
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val magnitude = sqrt((x * x + y * y + z * z).toDouble()) // In m/s^2 (1g ~ 9.8 m/s^2)
        val now = System.currentTimeMillis()

        // 1. Free-fall detection (< 3.5 m/s^2, i.e. < 0.35g)
        if (magnitude < 3.5) {
            lastFreefallTimestamp = now
        }

        // 2. Severe impact bang (> 45 m/s^2 ~ 4.6g) or fall from height (freefall + impact > 35 m/s^2)
        val isFreefallPreceded = (now - lastFreefallTimestamp) < 1500
        val isHardBangCrash = magnitude > 45.0
        val isFallFromHeight = isFreefallPreceded && (magnitude > 35.0)

        if ((isHardBangCrash || isFallFromHeight) && _alertStateFlow.value.escalationState == AlertEscalationState.IDLE) {
            Log.e(TAG, "🚨 ACCIDENT / FALL FROM HEIGHT DETECTED ON PHONE! (Magnitude: $magnitude m/s²)")
            handleTrigger(
                TriggerSource.ACCIDENT_FALL_BANG,
                if (isFallFromHeight) "Fall from Height Detected!" else "Hard Bang / Crash Accident Detected!"
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun startPeriodicBackendSync() {
        serviceScope.launch {
            while (isActive) {
                val loc = locationHelper.getCurrentLocation()
                val currentAlert = _alertStateFlow.value
                val vitals = bleManager.vitals.value

                backendSync.syncTelemetry(
                    userId = "sohan",
                    latitude = loc?.latitude,
                    longitude = loc?.longitude,
                    vitals = vitals,
                    alertState = currentAlert.escalationState,
                    triggerSource = currentAlert.triggerSource,
                    isAccident = currentAlert.isAccidentEmergency,
                    isOwnerAlarmBeeping = currentAlert.isOwnerAlarmBeeping
                )
                delay(8000L) // Sync every 8 seconds
            }
        }
    }

    private fun observeSubsystems() {
        // 1. Observe Voice "HELP" Trigger (Fires only after on-demand verification confirms HELP)
        serviceScope.launch {
            voiceDetector.onHelpDetected.collect {
                handleTrigger(TriggerSource.VOICE_HELP_X3, "Loud Shout + 'HELP' Keyword Confirmed!")
            }
        }

        // 2. Observe Microphone Active State for UI
        serviceScope.launch {
            voiceDetector.isMicActiveFlow.collect { active ->
                _isMicActiveFlow.value = active
            }
        }

        // 3. Observe BLE Alerts from ESP32 Sleeve (Panic Button, Palpitation, Impact, Accident/Fall, Shout)
        serviceScope.launch {
            bleManager.incomingAlert.collect { alertRaw ->
                if (alertRaw != null) {
                    when {
                        // When INMP441 on sleeve hears a loud shout, wake up mobile mic on-demand to check for HELP
                        alertRaw.contains("SHOUT") || alertRaw.contains("SCREAM") || alertRaw.contains("WAKE_MIC") -> {
                            Log.i(TAG, "Sleeve INMP441 heard a shout! Activating mobile mic on-demand...")
                            voiceDetector.triggerOnDemandVerification("INMP441 Wearable Sleeve Shout")
                        }
                        alertRaw.contains("ACCIDENT") || alertRaw.contains("FALL") ->
                            handleTrigger(TriggerSource.ACCIDENT_FALL_BANG, "Sleeve Sensor: Hard Bang / Fall from Height Detected!")
                        alertRaw.contains("BUTTON") -> handleTrigger(TriggerSource.SLEEVE_BUTTON, "Physical Panic Button")
                        alertRaw.contains("PALPITATION") -> handleTrigger(TriggerSource.PALPITATION_SPIKE, "Abnormal Pulse Spike / Palpitation")
                        alertRaw.contains("IMPACT") -> handleTrigger(TriggerSource.IMPACT_FALL, "Severe Impact / Struggle")
                    }
                    bleManager.clearAlert()
                }
            }
        }

        // 3. Observe Vitals for sudden heart rate spike
        serviceScope.launch {
            bleManager.vitals.collect { vitals ->
                _currentVitalsFlow.value = vitals
                if (vitals.isPalpitationDetected && _alertStateFlow.value.escalationState == AlertEscalationState.IDLE) {
                    handleTrigger(TriggerSource.PALPITATION_SPIKE, "Sudden Heart Rate Surge (${vitals.heartRate} BPM)")
                }
            }
        }
    }

    /**
     * Two-Stage Escalation Protocol:
     * Stage 1: Asks "Are you okay?" with 5-second countdown & vibration.
     * Stage 2: If unanswered, triggers Telegram dispatch, emergency call, and screen takeover.
     * In case of Hard Bang / Fall, activates continuous owner alarm beep until manually closed!
     */
    fun handleTrigger(source: TriggerSource, description: String) {
        if (_alertStateFlow.value.escalationState == AlertEscalationState.STAGE_2_ESCALATED) {
            Log.d(TAG, "Already escalated. Ignoring duplicate trigger.")
            return
        }

        Log.w(TAG, "Initiating Stage 1 Escalation for: $description")

        // Trigger discreet haptic feedback
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(longArrayOf(0, 300, 200, 300, 200, 500), -1)

        val alert = EmergencyAlert(
            triggerSource = source,
            escalationState = AlertEscalationState.STAGE_1_COUNTDOWN,
            statusMessage = "Alert: $description. 5s Countdown started.",
            isAccidentEmergency = (source == TriggerSource.ACCIDENT_FALL_BANG)
        )
        _alertStateFlow.value = alert
        _countdownSecondsFlow.value = 5

        countdownJob?.cancel()
        countdownJob = serviceScope.launch {
            for (sec in 5 downTo 1) {
                _countdownSecondsFlow.value = sec
                delay(1000L)
            }
            _countdownSecondsFlow.value = 0

            // If not cancelled within 5 seconds, escalate to Stage 2!
            executeFullStage2Escalation(source, description)
        }
    }

    /**
     * User / Sleeve disarm within 5 seconds or via secret gesture / remote owner
     */
    fun cancelAlert(reason: String = "User Dismissed", notifyBackend: Boolean = true) {
        Log.i(TAG, "Alert Cancelled / Disarmed: $reason")
        countdownJob?.cancel()
        _countdownSecondsFlow.value = 0
        OwnerAlarmBuzzer.stopBeep()

        _alertStateFlow.value = EmergencyAlert(
            triggerSource = TriggerSource.MANUAL_APP,
            escalationState = AlertEscalationState.IDLE,
            statusMessage = "Shield Active & Armed",
            isAccidentEmergency = false,
            isOwnerAlarmBeeping = false
        )
        bleManager.sendDisarmCommand()

        if (notifyBackend) {
            serviceScope.launch(Dispatchers.IO) {
                backendSync.silenceAlarmOnBackend("sohan")
            }
        }
    }

    /**
     * Silences only the continuous alarm beep from the Owner side while maintaining SOS tracking
     */
    fun silenceOwnerAlarm(reason: String = "Owner Silenced Beep", notifyBackend: Boolean = true) {
        Log.i(TAG, "Owner silenced continuous emergency beep: $reason")
        OwnerAlarmBuzzer.stopBeep()
        _alertStateFlow.value = _alertStateFlow.value.copy(isOwnerAlarmBeeping = false)

        if (notifyBackend) {
            serviceScope.launch(Dispatchers.IO) {
                backendSync.silenceAlarmOnBackend("sohan")
            }
        }
    }

    private suspend fun executeFullStage2Escalation(source: TriggerSource, description: String) {
        val isAccident = (source == TriggerSource.ACCIDENT_FALL_BANG)
        Log.e(TAG, "🚨 STAGE 2 ESCALATION TRIGGERED! (isAccident: $isAccident). Broadcasting SOS!")

        val location = locationHelper.getCurrentLocation()
        val lat = location?.latitude
        val lng = location?.longitude
        val vitals = bleManager.vitals.value

        // In accident/fall cases, start continuous strong alarm beep for Owner
        if (isAccident) {
            OwnerAlarmBuzzer.startContinuousBeep()
        }

        _alertStateFlow.value = EmergencyAlert(
            triggerSource = source,
            escalationState = AlertEscalationState.STAGE_2_ESCALATED,
            latitude = lat,
            longitude = lng,
            heartRateAtTrigger = vitals.heartRate,
            batteryPercent = vitals.batteryPercent,
            statusMessage = if (isAccident)
                "🚨 CRITICAL: Hard Bang / Fall from Height! 5s Unanswered. Continuous Alarm Ringing!"
            else "SOS Dispatched to 5 Contacts! Stealth Takeover active.",
            isAccidentEmergency = isAccident,
            isOwnerAlarmBeeping = isAccident
        )

        // 1. Resolve 2 dynamically closest contacts + 3 fixed contacts = 5 recipients
        val recipients = contactsRepo.resolveTop5EmergencyRecipients(lat, lng)

        // 2. Dispatch Telegram alerts to all 5 recipients with customized urgency
        val customTriggerMsg = if (isAccident) {
            "🚨 CRITICAL ACCIDENT / HARD BANG / FALL FROM HEIGHT DETECTED! Victim did not respond to 5s check! Immediate emergency medical help required!"
        } else {
            description
        }

        for (contact in recipients) {
            serviceScope.launch(Dispatchers.IO) {
                telegramApi.sendEmergencyMessage(
                    chatId = contact.telegramChatId,
                    victimName = "Sohan",
                    triggerCause = customTriggerMsg,
                    latitude = lat,
                    longitude = lng,
                    heartRate = vitals.heartRate,
                    batteryPercent = vitals.batteryPercent
                )
            }
        }

        // Immediately notify Cloud Backend so Owner portal triggers the strong alarm beep
        serviceScope.launch(Dispatchers.IO) {
            backendSync.syncTelemetry(
                userId = "sohan",
                latitude = lat,
                longitude = lng,
                vitals = vitals,
                alertState = AlertEscalationState.STAGE_2_ESCALATED,
                triggerSource = source,
                isAccident = isAccident,
                isOwnerAlarmBeeping = isAccident
            )
        }

        // 3. Launch the Stealth Takeover Activity ("Fake Shutdown" Screen)
        val stealthIntent = Intent(this@GuardianForegroundService, StealthTakeoverActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(stealthIntent)

        // 4. Emergency call ability (Direct call to fixed emergency responder e.g. 112)
        tryDirectEmergencyCall("112")
    }

    @SuppressLint("MissingPermission")
    private fun tryDirectEmergencyCall(phoneNumber: String) {
        try {
            val callIntent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(callIntent)
            Log.d(TAG, "Emergency call dispatched to $phoneNumber")
        } catch (e: Exception) {
            Log.w(TAG, "Emergency call failed or permission not granted: ${e.message}")
        }
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, GuardianApplication.CHANNEL_MONITORING_ID)
            .setContentTitle("Guardian Sleeve Protection")
            .setContentText(statusText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(hardwareAlertReceiver) } catch (_: Exception) {}
        sensorManager?.unregisterListener(this)
        voiceDetector.stopListening()
        OwnerAlarmBuzzer.stopBeep()
        serviceScope.cancel()
        wakeLock?.release()
        instance = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "GuardianService"
        private const val NOTIFICATION_ID = 9991

        var instance: GuardianForegroundService? = null
            private set

        private val _alertStateFlow = MutableStateFlow(
            EmergencyAlert(
                triggerSource = TriggerSource.MANUAL_APP,
                escalationState = AlertEscalationState.IDLE,
                statusMessage = "Shield Active & Armed"
            )
        )
        val alertStateFlow: StateFlow<EmergencyAlert> = _alertStateFlow.asStateFlow()

        private val _countdownSecondsFlow = MutableStateFlow(0)
        val countdownSecondsFlow: StateFlow<Int> = _countdownSecondsFlow.asStateFlow()

        private val _currentVitalsFlow = MutableStateFlow(com.guardiansleeve.app.model.VitalsData())
        val currentVitalsFlow: StateFlow<com.guardiansleeve.app.model.VitalsData> = _currentVitalsFlow.asStateFlow()

        private val _isMicActiveFlow = MutableStateFlow(false)
        val isMicActiveFlow: StateFlow<Boolean> = _isMicActiveFlow.asStateFlow()

        fun triggerOnDemandVoiceCheck(reason: String = "Manual Dashboard Test") {
            instance?.voiceDetector?.triggerOnDemandVerification(reason)
        }

        fun updateNetworkAndAsrSettings(serverUrl: String, apiKey: String) {
            instance?.let { service ->
                Log.i(TAG, "Applying updated network & ASR settings: $serverUrl")
                service.backendSync.setServerUrl(serverUrl)
                service.voiceDetector.nemotronClient.setApiKey(apiKey)
            }
        }

        fun start(context: Context) {
            val intent = Intent(context, GuardianForegroundService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
