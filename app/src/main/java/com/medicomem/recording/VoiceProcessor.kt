package com.medicomem.recording

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Tratamiento de la voz antes de codificarla, pensado para una consulta con el móvil en la mesa:
 *  1. Filtro paso alto (~90 Hz): quita golpes en la mesa, aire acondicionado y zumbidos.
 *  2. Control automático de volumen suave: sube la voz lejana del médico sin disparar el ruido
 *     de fondo en los silencios (solo sube cuando detecta voz por encima del ruido medido).
 *  3. Limitador suave: la voz cercana nunca satura.
 * Sin dependencias de Android, para poder probarlo en el ordenador.
 */
class VoiceProcessor(sampleRate: Int) {
    // --- Paso alto Butterworth de 2º orden ---
    private val b0: Double
    private val b1: Double
    private val b2: Double
    private val a1: Double
    private val a2: Double
    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    // --- Control de volumen ---
    private var envelope = 0.0
    private var noiseFloor = NOISE_FLOOR_START
    private var gain = 1.0

    /** Nivel de salida del último bloque, de 0 (silencio) a 1 (máximo), para el medidor. */
    @Volatile var level = 0f
        private set

    init {
        val w0 = 2 * PI * HIGH_PASS_HZ / sampleRate
        val alpha = sin(w0) / (2 * 0.7071)
        val cosW = cos(w0)
        val a0 = 1 + alpha
        b0 = (1 + cosW) / 2 / a0
        b1 = -(1 + cosW) / a0
        b2 = (1 + cosW) / 2 / a0
        a1 = -2 * cosW / a0
        a2 = (1 - alpha) / a0
    }

    /** Procesa un bloque (idealmente de 10–30 ms) de PCM de 16 bits en el mismo array. */
    fun process(samples: ShortArray, count: Int = samples.size) {
        if (count <= 0) return
        val buf = DoubleArray(count)
        var sum = 0.0
        for (i in 0 until count) {
            val x = samples[i] / 32768.0
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            buf[i] = y
            sum += y * y
        }
        val rms = sqrt(sum / count)

        // Envolvente: sube deprisa con la voz y baja despacio.
        envelope += (rms - envelope) * if (rms > envelope) ENV_ATTACK else ENV_RELEASE
        // Ruido de fondo: sigue los mínimos y sube muy despacio.
        noiseFloor = if (envelope < noiseFloor) envelope else noiseFloor * NOISE_FLOOR_RISE
        noiseFloor = noiseFloor.coerceAtLeast(ABS_SILENCE)

        val voice = envelope > noiseFloor * VOICE_OVER_NOISE && envelope > ABS_SILENCE * 2
        val target = if (voice) (TARGET_RMS / envelope).coerceIn(MIN_GAIN, MAX_GAIN) else gain
        val previous = gain
        // Baja rápido (evita saturar si alguien habla cerca) y sube despacio (evita «bombeo»).
        gain += (target - gain) * if (target < gain) GAIN_DOWN else GAIN_UP

        var outSum = 0.0
        for (i in 0 until count) {
            val g = previous + (gain - previous) * (i + 1) / count // sin saltos entre bloques
            val v = softLimit(buf[i] * g)
            outSum += v * v
            samples[i] = (v * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        val outRms = sqrt(outSum / count)
        level = if (outRms <= 1e-5) 0f else ((20 * log10(outRms) + METER_RANGE_DB) / METER_RANGE_DB).toFloat().coerceIn(0f, 1f)
    }

    val currentGain: Double get() = gain

    private fun softLimit(v: Double): Double {
        val a = abs(v)
        if (a <= LIMIT_KNEE) return v
        return sign(v) * (LIMIT_KNEE + (1 - LIMIT_KNEE) * tanh((a - LIMIT_KNEE) / (1 - LIMIT_KNEE)))
    }

    companion object {
        const val HIGH_PASS_HZ = 90.0
        /** Volumen objetivo de la voz: −20 dBFS de media. */
        const val TARGET_RMS = 0.1
        /** Máximo +18 dB: suficiente para un médico a 2–3 m sin convertir el ruido en protagonista. */
        const val MAX_GAIN = 8.0
        const val MIN_GAIN = 0.5
        private const val ENV_ATTACK = 0.5
        private const val ENV_RELEASE = 0.08
        private const val GAIN_DOWN = 0.4
        private const val GAIN_UP = 0.015
        private const val NOISE_FLOOR_START = 0.003
        private const val NOISE_FLOOR_RISE = 1.002
        private const val VOICE_OVER_NOISE = 2.5
        private const val ABS_SILENCE = 0.0003
        private const val LIMIT_KNEE = 0.8
        private const val METER_RANGE_DB = 50.0
    }
}
