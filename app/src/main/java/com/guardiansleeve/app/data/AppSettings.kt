package com.guardiansleeve.app.data

import android.content.Context
import android.content.SharedPreferences

class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) {
            val sanitized = if (!value.startsWith("http://") && !value.startsWith("https://")) {
                "http://$value"
            } else {
                value
            }.trimEnd('/')
            prefs.edit().putString(KEY_SERVER_URL, sanitized).apply()
        }

    var nemotronApiKey: String
        get() = prefs.getString(KEY_NEMOTRON_API_KEY, DEFAULT_NEMOTRON_API_KEY) ?: DEFAULT_NEMOTRON_API_KEY
        set(value) = prefs.edit().putString(KEY_NEMOTRON_API_KEY, value.trim()).apply()

    var victimName: String
        get() = prefs.getString(KEY_VICTIM_NAME, DEFAULT_VICTIM_NAME) ?: DEFAULT_VICTIM_NAME
        set(value) = prefs.edit().putString(KEY_VICTIM_NAME, value.trim()).apply()

    var telegramBotToken: String
        get() = prefs.getString(KEY_TELEGRAM_BOT_TOKEN, DEFAULT_TELEGRAM_BOT_TOKEN) ?: DEFAULT_TELEGRAM_BOT_TOKEN
        set(value) = prefs.edit().putString(KEY_TELEGRAM_BOT_TOKEN, value.trim()).apply()

    companion object {
        private const val PREFS_NAME = "guardian_sleeve_prefs"
        private const val KEY_SERVER_URL = "key_server_url"
        private const val KEY_NEMOTRON_API_KEY = "key_nemotron_api_key"
        private const val KEY_VICTIM_NAME = "key_victim_name"
        private const val KEY_TELEGRAM_BOT_TOKEN = "key_telegram_bot_token"

        const val DEFAULT_SERVER_URL = "http://10.0.2.2:5000"
        const val DEFAULT_VICTIM_NAME = "Guardian User"
        const val DEFAULT_NEMOTRON_API_KEY = "" // Enter your NVIDIA API key (nvapi-...)
        const val DEFAULT_TELEGRAM_BOT_TOKEN = "YOUR_TELEGRAM_BOT_TOKEN"
    }
}
