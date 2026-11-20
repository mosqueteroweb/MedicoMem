package com.medicomem.recording

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class VoiceProcessorTest {
    private val rate = 32_000
    private val block = 640 // 20 ms

    /** Genera [seconds] de señal, la procesa por bloques y devuelve el resultado. */
    private fun run(p: VoiceProcessor, seconds: Double, gen: (Int) -> Double): DoubleArray {
        val n = (seconds * rate).toInt()
        val out = DoubleArray(n)
        var i = 0
        while (i < n) {
            val len = minOf(block, n - i)
            val s = ShortArray(len) { (gen(i + it).coerceIn(-1.0, 1.0) * 32767).toInt().toShort() }
            p.process(s, len)
            for (k in 0 until len) out[i + k] = s[k] / 32768.0
            i += len
        }
        return out
    }

    private fun rms(x: DoubleArray, from: Int = 0, to: Int = x.size): Double {
        var s = 0.0
        for (i in from until to) s += x[i] * x[i]
        return sqrt(s / (to - from))
    }

    private fun tone(hz: Double, amp: Double) = { i: Int -> amp * sin(2 * PI * hz * i / rate) }

    /** Voz simulada: tono modulado en sílabas (~4 por segundo). */
    private fun speech(amp: Double) = { i: Int ->
        val t = i.toDouble() / rate
        val syllable = 0.5 + 0.5 * sin(2 * PI * 4 * t)
        amp * syllable * (0.6 * sin(2 * PI * 220 * t) + 0.4 * sin(2 * PI * 1100 * t))
    }

    @Test fun highPassRemovesRumbleKeepsVoice() {
        val low = run(VoiceProcessor(rate).also { }, 2.0, tone(30.0, 0.05))
        val p2 = VoiceProcessor(rate)
        val mid = run(p2, 2.0, tone(1000.0, 0.05))
        val lowRms = rms(low, rate, low.size)
        val midRms = rms(mid, rate, mid.size)
        assertTrue("30 Hz debe quedar muy por debajo de 1 kHz ($lowRms vs $midRms)", lowRms < midRms / 5)
    }

    @Test fun distantVoiceIsRaised() {
        val inRms = 0.012 // voz lejana, unos −38 dBFS
        val out = run(VoiceProcessor(rate), 8.0, speech(inRms * 2.0))
        val outRms = rms(out, 4 * rate, out.size)
        assertTrue("La voz lejana debe subir al menos 10 dB (sale $outRms)", outRms > inRms * 3.16)
    }

    @Test fun loudVoiceNeverClips() {
        val out = run(VoiceProcessor(rate), 4.0, speech(0.95))
        assertTrue(out.all { abs(it) < 1.0 })
        assertTrue(rms(out, 2 * rate, out.size) < 0.3)
    }

    @Test fun backgroundNoiseIsNotPumpedUp() {
        val rnd = Random(7)
        val noiseAmp = 0.004 // ruido de sala, unos −50 dBFS
        val out = run(VoiceProcessor(rate), 10.0) { noiseAmp * (rnd.nextDouble() * 2 - 1) }
        val outRms = rms(out, 5 * rate, out.size)
        val inRms = noiseAmp / sqrt(3.0)
        assertTrue("El ruido solo no debe subir más de 6 dB ($outRms vs $inRms)", outRms < inRms * 2)
    }

    @Test fun voiceStaysUpAfterPause() {
        // Voz lejana, 3 s de silencio, voz otra vez: la ganancia no debe desplomarse en la pausa.
        val p = VoiceProcessor(rate)
        run(p, 6.0, speech(0.02))
        val gainBefore = p.currentGain
        run(p, 3.0) { 0.0 }
        assertTrue(p.currentGain >= gainBefore * 0.9)
    }
}
