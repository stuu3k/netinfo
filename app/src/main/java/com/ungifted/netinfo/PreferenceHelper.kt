package com.ungifted.netinfo

import android.content.Context
import android.media.ToneGenerator

class PreferenceHelper(context: Context) {
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    
    companion object {
        const val SUCCESS_SOUND_KEY = "success_sound"
        const val FAIL_SOUND_KEY = "fail_sound"
        const val PING_ALERT_MODE_KEY = "ping_alert_mode"
        const val SUCCESS_SOUND_URI_KEY = "success_sound_uri"
        const val FAIL_SOUND_URI_KEY = "fail_sound_uri"
        const val ON_SUCCESS_CHECK_KEY = "on_success_check"
        const val ON_FAIL_CHECK_KEY = "on_fail_check"

        // Sound options
        val SOUND_OPTIONS = mapOf(
            "Beep" to ToneGenerator.TONE_PROP_BEEP,
            "Confirmation" to ToneGenerator.TONE_PROP_ACK,
            "Error" to ToneGenerator.TONE_PROP_NACK,
            "Alert" to ToneGenerator.TONE_SUP_ERROR,
            "Click" to ToneGenerator.TONE_PROP_PROMPT
        )

        // Ping alert modes
        const val PING_ALERT_EVERY = 0
        const val PING_ALERT_TEN = 1
        const val PING_ALERT_STATUS = 2  // Default
    }

    var showWelcomePopup: Boolean
        get() = prefs.getBoolean("show_welcome_popup", true)
        set(value) = prefs.edit().putBoolean("show_welcome_popup", value).apply()

    var successSoundUri: String?
        get() = prefs.getString(SUCCESS_SOUND_URI_KEY, null)
        set(value) = prefs.edit().putString(SUCCESS_SOUND_URI_KEY, value).apply()

    var failSoundUri: String?
        get() = prefs.getString(FAIL_SOUND_URI_KEY, null)
        set(value) = prefs.edit().putString(FAIL_SOUND_URI_KEY, value).apply()

    var successSound: Int
        get() = prefs.getInt(SUCCESS_SOUND_KEY, ToneGenerator.TONE_PROP_BEEP)
        set(value) = prefs.edit().putInt(SUCCESS_SOUND_KEY, value).apply()

    var failSound: Int
        get() = prefs.getInt(FAIL_SOUND_KEY, ToneGenerator.TONE_PROP_NACK)
        set(value) = prefs.edit().putInt(FAIL_SOUND_KEY, value).apply()

    var pingAlertMode: Int
        get() = prefs.getInt(PING_ALERT_MODE_KEY, PING_ALERT_STATUS)
        set(value) = prefs.edit().putInt(PING_ALERT_MODE_KEY, value).apply()

    var alertMode: String
        get() = prefs.getString("alert_mode", "change") ?: "change"
        set(value) = prefs.edit().putString("alert_mode", value).apply()

    var onSuccessCheck: Boolean
        get() = prefs.getBoolean(ON_SUCCESS_CHECK_KEY, true)  // Default to true
        set(value) = prefs.edit().putBoolean(ON_SUCCESS_CHECK_KEY, value).apply()

    var onFailCheck: Boolean
        get() = prefs.getBoolean(ON_FAIL_CHECK_KEY, true)  // Default to true
        set(value) = prefs.edit().putBoolean(ON_FAIL_CHECK_KEY, value).apply()

    var defaultPage: String
        get() = prefs.getString("default_page", "HOME") ?: "HOME"
        set(value) = prefs.edit().putString("default_page", value).apply()

    var pinchZoomMode: String
        get() = prefs.getString("pinch_zoom_mode", "Enabled") ?: "Enabled"
        set(value) = prefs.edit().putString("pinch_zoom_mode", value).apply()

    var defaultResultsTextSize: Float
        get() = prefs.getFloat("default_results_text_size", 12f)  // Default to 12sp
        set(value) = prefs.edit().putFloat("default_results_text_size", value).apply()

    var showLogo: Boolean
        get() = prefs.getBoolean("show_logo", true)  // Default to true
        set(value) = prefs.edit().putBoolean("show_logo", value).apply()

    fun isPinchZoomAllowed(isResultsWindow: Boolean): Boolean {
        return when (pinchZoomMode) {
            "Enabled" -> true
            "Disabled" -> false
            "Pages" -> !isResultsWindow
            "Results" -> isResultsWindow
            else -> true  // Default to enabled if unknown value
        }
    }
} 