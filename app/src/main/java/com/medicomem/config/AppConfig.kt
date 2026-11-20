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

    /**
     * Micrófono que se pide a Android. Por defecto VOICE_RECOGNITION: llega «en bruto» (sin el control
     * de volumen del fabricante), que es lo ideal cuando la app trata la voz por su cuenta.
     */
    var micMode: MicMode
        get() = MicMode.entries.firstOrNull { it.name == prefs.getString(KEY_MIC, null) }
            ?: if (prefs.getBoolean(KEY_FAR_MIC, true)) MicMode.RAW else MicMode.STANDARD
        set(value) = prefs.edit().putString(KEY_MIC, value.name).apply()

    val audioSource: Int get() = micMode.source

    /** Tratamiento de voz propio (filtro de graves + volumen automático). Desactivable por si un móvil da problemas. */
    var enhanceVoice: Boolean
        get() = prefs.getBoolean(KEY_ENHANCE, true)
        set(value) = prefs.edit().putBoolean(KEY_ENHANCE, value).apply()

    enum class MicMode(val source: Int) {
        RAW(MediaRecorder.AudioSource.VOICE_RECOGNITION),
        STANDARD(MediaRecorder.AudioSource.MIC),
        VIDEO(MediaRecorder.AudioSource.CAMCORDER),
    }

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
        private const val KEY_FAR_MIC = "far_voice_mic" // versión 1.0.x
        private const val KEY_MIC = "mic_mode"
        private const val KEY_ENHANCE = "enhance_voice"
        private const val KEY_MAX_MIN = "max_duration_minutes"
    }
}
