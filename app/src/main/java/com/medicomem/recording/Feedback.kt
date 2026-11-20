package com.medicomem.recording

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** Vibración y pitido, compatibles desde Android 7. */
object Feedback {
    fun started(context: Context) {
        vibrate(context, longArrayOf(0, 400))
        beep(ToneGenerator.TONE_PROP_BEEP, 200)
    }

    fun stopped(context: Context) {
        vibrate(context, longArrayOf(0, 120, 120, 120))
        beep(ToneGenerator.TONE_PROP_ACK, 300)
    }

    private fun vibrate(context: Context, pattern: LongArray) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(pattern, -1)
        }
    }

    private fun beep(tone: Int, ms: Int) {
        runCatching {
            val gen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            gen.startTone(tone, ms)
            Handler(Looper.getMainLooper()).postDelayed({ gen.release() }, ms + 200L)
        }
    }
}
