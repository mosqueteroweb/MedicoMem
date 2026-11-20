package com.medicomem.upload

import com.medicomem.storage.RecordingMeta
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Textos que acompañan al audio en Telegram. Sin dependencias de Android para poder probarlos. */
object Captions {
    const val LOW_BATTERY = 20

    fun title(meta: RecordingMeta, tz: TimeZone = TimeZone.getDefault()): String {
        val base = "Cita médica - ${meta.phoneName} (${format("dd/MM HH:mm", meta.startedAt, tz)})"
        return if (meta.multipart) "$base · Parte ${meta.part}" else base
    }

    fun caption(meta: RecordingMeta, tz: TimeZone = TimeZone.getDefault()): String = buildString {
        append("🎙️ Cita médica — ").append(meta.phoneName)
        if (meta.multipart) append(" · Parte ").append(meta.part)
        append('\n')
        append("📅 ").append(format("dd/MM/yyyy HH:mm", meta.startedAt, tz))
        append(" · ⏱️ ").append(duration(meta.durationSec))
        if (meta.battery >= 0) append(" · 🔋 ").append(meta.battery).append('%')
        if (meta.battery in 0 until LOW_BATTERY) append("\n⚠️ Batería baja — recordar cargar")
        if (meta.autoCut.isNotBlank()) append("\n⚠️ Corte automático (").append(meta.autoCut).append(')')
    }

    fun duration(seconds: Long): String = when {
        seconds < 60 -> "$seconds s"
        seconds < 3600 -> "${seconds / 60} min"
        else -> "${seconds / 3600} h ${(seconds % 3600) / 60} min"
    }

    private fun format(pattern: String, millis: Long, tz: TimeZone): String =
        SimpleDateFormat(pattern, Locale("es", "ES")).apply { timeZone = tz }.format(Date(millis))
}
