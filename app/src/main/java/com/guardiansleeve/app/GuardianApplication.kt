package com.guardiansleeve.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class GuardianApplication : Application() {

    companion object {
        const val CHANNEL_MONITORING_ID = "guardian_monitoring_channel"
        const val CHANNEL_ALERT_ID = "guardian_emergency_channel"
        lateinit var instance: GuardianApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val monitoringChannel = NotificationChannel(
                CHANNEL_MONITORING_ID,
                "Guardian Background Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps BLE and safety sensors active in low power mode."
            }

            val emergencyChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "Guardian Emergency Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High priority SOS alert notifications."
                enableVibration(true)
                enableLights(true)
            }

            notificationManager.createNotificationChannel(monitoringChannel)
            notificationManager.createNotificationChannel(emergencyChannel)
        }
    }
}
