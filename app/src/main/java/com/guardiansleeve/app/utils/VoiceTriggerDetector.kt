package com.guardiansleeve.app.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * On-Demand Voice Verifier
 * 
 * DESIGN REQUIREMENT:
 * The mobile microphone is NEVER running continuously.
 * It is activated ONLY when the wearable sleeve's INMP441 microphone detects
 * a loud shout/scream. Once activated, it listens for 6 seconds for the word "HELP".
 * Once detected (or after timeout), the microphone immediately powers down.
 */
class VoiceTriggerDetector(private val context: Context) {

    private val detectorScope = CoroutineScope(Dispatchers.IO + Job())
    private var speechRecognizer: SpeechRecognizer? = null
    private var isVerifying = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    val nemotronClient = NemotronAsrClient()

    private val _onHelpDetected = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onHelpDetected: SharedFlow<Unit> = _onHelpDetected.asSharedFlow()

    private val _isMicActiveFlow = MutableSharedFlow<Boolean>(extraBufferCapacity = 1)
    val isMicActiveFlow: SharedFlow<Boolean> = _isMicActiveFlow.asSharedFlow()

    /**
     * Called when the wearable sleeve (INMP441) detects a loud acoustic spike / shout,
     * or when triggered manually via the dashboard test button.
     * Wakes up the mobile microphone for a verification window.
     */
    fun triggerOnDemandVerification(reason: String = "Sleeve INMP441 Loud Shout") {
        if (isVerifying) {
            Log.d(TAG, "On-demand verification already in progress. Ignoring duplicate trigger.")
            return
        }

        Log.w(TAG, "🎙️ Waking up mobile microphone on-demand! Reason: $reason")
        isVerifying = true
        _isMicActiveFlow.tryEmit(true)

        // Give local SpeechRecognizer exclusive access to the microphone (no AudioRecord contention)
        mainHandler.post {
            startLocalRecognizer()
        }

        // Set safety timeout: 10 seconds listening window (extended automatically if speech starts)
        resetSafetyTimeout(10000L, "Initial 10-second window")
    }

    private fun resetSafetyTimeout(millis: Long, reason: String) {
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = Runnable {
            if (isVerifying) {
                Log.i(TAG, "⏳ Verification timeout elapsed ($reason). No 'HELP' detected. Turning mic OFF.")
                stopListening()
            }
        }
        mainHandler.postDelayed(timeoutRunnable!!, millis)
    }

    private var preferOnDevice = false

    private fun startLocalRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition not available on this device.")
            stopListening()
            return
        }

        cleanupRecognizer()

        try {
            speechRecognizer = (if (preferOnDevice && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                Log.i(TAG, "⚡ Using On-Device SpeechRecognizer")
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                Log.i(TAG, "🎤 Using System SpeechRecognizer")
                SpeechRecognizer.createSpeechRecognizer(context)
            }).apply {
                setRecognitionListener(listener)
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "en-IN"))
                // Pass INTEGER values (not Long) as required by Google Speech Services
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500)
            }

            speechRecognizer?.startListening(intent)
            Log.d(TAG, "🎤 Microphone active & listening for 'HELP' / 'BACHAO' / 'SAVE ME'...")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting voice recognition: ${e.message}")
            if (isVerifying) {
                mainHandler.postDelayed({ if (isVerifying) restartListening() }, 500)
            } else {
                stopListening()
            }
        }
    }

    private var speechDetectedInSession = false

    private fun restartListening() {
        if (!isVerifying) return
        startLocalRecognizer()
    }

    fun stopListening() {
        isVerifying = false
        speechDetectedInSession = false
        _isMicActiveFlow.tryEmit(false)
        timeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        timeoutRunnable = null

        mainHandler.post {
            cleanupRecognizer()
        }
        Log.d(TAG, "Microphone completely powered OFF. Battery & privacy preserved.")
    }

    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            // Ignore
        }
        speechRecognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech. Say 'HELP' / 'BACHAO' now!")
        }

        override fun onBeginningOfSpeech() {
            Log.i(TAG, "🗣️ Speech detected by microphone! Extending listening window by 6 seconds...")
            speechDetectedInSession = true
            resetSafetyTimeout(6000L, "Speech in progress extension")
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (rmsdB > 5.0f) {
                speechDetectedInSession = true
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {
            Log.d(TAG, "Speech ended. Processing audio...")
        }

        override fun onError(error: Int) {
            Log.d(TAG, "SpeechRecognizer status code: $error (speechDetected=$speechDetectedInSession)")
            if (error == 11 || error == 13 || error == 9) {
                preferOnDevice = false
            }

            // If speech was actively detected during a verified acoustic alarm window, treat as confirmed vocal distress
            if (isVerifying && speechDetectedInSession && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                Log.w(TAG, "🚨 Loud speech / vocalization confirmed during emergency verification window! Triggering Escalation!")
                stopListening()
                _onHelpDetected.tryEmit(Unit)
                return
            }

            // Automatically restart recognizer on ANY error during active verification window
            if (isVerifying) {
                mainHandler.postDelayed({
                    if (isVerifying) {
                        restartListening()
                    }
                }, 350)
            }
        }

        override fun onResults(results: Bundle?) {
            val confirmed = processMatches(results, isPartial = false)
            if (!confirmed && isVerifying) {
                if (speechDetectedInSession) {
                    Log.w(TAG, "🚨 Speech was captured during acoustic verification! Triggering Escalation!")
                    stopListening()
                    _onHelpDetected.tryEmit(Unit)
                    return
                }
                mainHandler.postDelayed({
                    if (isVerifying) {
                        restartListening()
                    }
                }, 200)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            processMatches(partialResults, isPartial = true)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun processMatches(bundle: Bundle?, isPartial: Boolean): Boolean {
        val matches = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return false

        for (text in matches) {
            val lower = text.lowercase().trim()
            Log.i(TAG, "🎙️ [${if (isPartial) "Partial" else "Final"}] Heard: '$lower'")

            // Checks for 'help' any number of times + broad distress keywords
            if (lower.contains("help") || lower.contains("halp") || lower.contains("held") || lower.contains("hel") ||
                lower.contains("bachao") || lower.contains("save me") || lower.contains("save") ||
                lower.contains("emergency") || lower.contains("sos") || lower.contains("please") ||
                lower.contains("madad") || lower.contains("danger") || lower.contains("chodo")) {
                Log.w(TAG, "🚨 Distress keyword confirmed ($lower)! Triggering Escalation Strategy!")
                stopListening()
                _onHelpDetected.tryEmit(Unit)
                return true
            }
        }
        return false
    }

    fun isMicrophoneActive(): Boolean = isVerifying

    companion object {
        private const val TAG = "VoiceTriggerDetector"
    }
}
