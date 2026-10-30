package com.guardiansleeve.app.model

enum class TriggerSource {
    VOICE_HELP_X3,        // "help help help" repeated 3 times
    SLEEVE_BUTTON,        // Dedicated hardware push button on ESP32
    PALPITATION_SPIKE,    // Heart rate sudden surge / tachycardia
    IMPACT_FALL,          // Severe impact / struggle detected by ADXL345
    ACCIDENT_FALL_BANG,   // Hard bang, collision accident, or fall from height
    MANUAL_APP,           // Triggered directly from app UI
    REMOTE_OWNER          // Triggered remotely by Owner
}

enum class AlertEscalationState {
    IDLE,
    STAGE_1_COUNTDOWN, // "Are you okay?" 5 seconds countdown window
    STAGE_2_ESCALATED, // Full Telegram alert dispatch + fake shutdown takeover + call
    DISARMED           // Returned to safe state
}

enum class UserRole {
    USER,  // Carries device & phone (active protection)
    OWNER  // Remote viewer tracking location & vitals globally
}

data class EmergencyAlert(
    val triggerSource: TriggerSource,
    val escalationState: AlertEscalationState = AlertEscalationState.STAGE_1_COUNTDOWN,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val heartRateAtTrigger: Int = 0,
    val batteryPercent: Int = 100,
    val timestamp: Long = System.currentTimeMillis(),
    val notifiedContactsCount: Int = 0,
    val statusMessage: String = "",
    val isAccidentEmergency: Boolean = false,
    val isOwnerAlarmBeeping: Boolean = false
)
