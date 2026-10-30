package com.guardiansleeve.app.model

data class VitalsData(
    val heartRate: Int = 72,
    val spO2: Int = 98,
    val accelerationG: Float = 1.0f,
    val batteryPercent: Int = 92,
    val isPalpitationDetected: Boolean = false,
    val isImpactDetected: Boolean = false,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)
