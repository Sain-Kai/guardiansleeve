package com.guardiansleeve.app.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import kotlinx.coroutines.*

/**
 * Continuous emergency buzzer for the Owner device.
 * Rings high-decibel urgent dual tones in an infinite loop
 * until the Owner manually silences/closes it.
 */
object OwnerAlarmBuzzer {

    private const val TAG = "OwnerAlarmBuzzer"
    private var toneGenerator: ToneGenerator? = null
    private var beepingJob: Job? = null
    private var isPlaying = false

    fun startContinuousBeep() {
        if (isPlaying) return
        isPlaying = true

        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ToneGenerator: ${e.message}")
        }

        beepingJob?.cancel()
        beepingJob = CoroutineScope(Dispatchers.Default).launch {
            Log.w(TAG, "🚨 Owner emergency alarm beeping started!")
            while (isActive && isPlaying) {
                try {
                    // High urgency repeating siren: Tone CD_HIGH_PBX / SUP_RINGTONE / SUP_ERROR
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 400)
                    delay(500)
                    toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, 350)
                    delay(450)
                } catch (e: Exception) {
                    delay(500)
                }
            }
        }
    }

    fun stopBeep() {
        Log.i(TAG, "Owner manually silenced the emergency beep.")
        isPlaying = false
        beepingJob?.cancel()
        beepingJob = null
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (e: Exception) {
            // Ignore
        }
        toneGenerator = null
    }

    fun isBeeping(): Boolean = isPlaying
}
