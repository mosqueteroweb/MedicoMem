package com.medicomem.upload

import com.medicomem.storage.RecordingMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class CaptionsTest {
    private val madrid = TimeZone.getTimeZone("Europe/Madrid")
    // 20/11/2026 10:35 en Madrid
    private val start = 1_795_167_300_000L
    private fun meta(battery: Int = 68, autoCut: String = "", part: Int = 1, multipart: Boolean = false) =
        RecordingMeta("x", start, 22 * 60, battery, "Mamá", autoCut, part, multipart)

    @Test fun normalCaption() {
        assertEquals(
            "🎙️ Cita médica — Mamá\n📅 20/11/2026 10:35 · ⏱️ 22 min · 🔋 68%",
            Captions.caption(meta(), madrid),
        )
        assertEquals("Cita médica - Mamá (20/11 10:35)", Captions.title(meta(), madrid))
    }

    @Test fun lowBatteryWarningBelow20() {
        assertTrue(Captions.caption(meta(battery = 19), madrid).contains("Batería baja"))
        assertFalse(Captions.caption(meta(battery = 20), madrid).contains("Batería baja"))
    }

    @Test fun autoCutAndParts() {
        val c = Captions.caption(meta(autoCut = "límite de 3 h", part = 2, multipart = true), madrid)
        assertTrue(c.startsWith("🎙️ Cita médica — Mamá · Parte 2"))
        assertTrue(c.contains("⚠️ Corte automático (límite de 3 h)"))
        assertTrue(Captions.title(meta(part = 2, multipart = true), madrid).endsWith("· Parte 2"))
    }

    @Test fun durations() {
        assertEquals("45 s", Captions.duration(45))
        assertEquals("22 min", Captions.duration(22 * 60 + 10))
        assertEquals("1 h 50 min", Captions.duration(110 * 60))
    }
}
