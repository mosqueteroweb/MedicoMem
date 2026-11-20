package com.medicomem.config

import android.content.Context
import android.media.MediaRecorder

/** Ajustes del menú familiar. Todo se guarda en el almacenamiento privado de la app. */
class AppConfig(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("config", Context.MODE_PRIVATE)

    var phoneName: String
        get() = prefs.getString(KEY_NAME, "")!!.ifBlank { DEFAULT_NAME }
        set(value) = prefs.edit().putString(KEY_NAME, value.trim()).apply()

    var botToken: String
        get() = prefs.getString(KEY_TOKEN, "")!!
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    var chatId: String
        get() = prefs.getString(KEY_CHAT, "")!!
        set(value) = prefs.edit().putString(KEY_CHAT, value.trim()).apply()

    /** true: datos móviles o Wi-Fi (por defecto). false: solo Wi-Fi. */
    var useMobileData: Boolean
        get() = prefs.getBoolean(KEY_MOBILE, true)
        set(value) = prefs.edit().putBoolean(KEY_MOBILE, value).apply()

    /** true: VOICE_RECOGNITION (voz a distancia, por defecto). false: MIC estándar. */
    var farVoiceMic: Boolean
        get() = prefs.getBoolean(KEY_FAR_MIC, true)
        set(value) = prefs.edit().putBoolean(KEY_FAR_MIC, value).apply()

    val audioSource: Int
        get() = if (farVoiceMic) MediaRecorder.AudioSource.VOICE_RECOGNITION else MediaRecorder.AudioSource.MIC

    val maxDurationMinutes: Int
        get() = prefs.getInt(KEY_MAX_MIN, DEFAULT_MAX_MINUTES)

    val isTelegramConfigured: Boolean
        get() = botToken.isNotBlank() && chatId.isNotBlank()

    companion object {
        const val DEFAULT_NAME = "Mamá"
        const val DEFAULT_MAX_MINUTES = 180
        private const val KEY_NAME = "phone_name"
        private const val KEY_TOKEN = "bot_token"
        private const val KEY_CHAT = "chat_id"
        private const val KEY_MOBILE = "use_mobile_data"
        private const val KEY_FAR_MIC = "far_voice_mic"
        private const val KEY_MAX_MIN = "max_duration_minutes"
    }
}
