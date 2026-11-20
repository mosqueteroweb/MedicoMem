package com.medicomem.recording

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

/** Estado compartido entre el servicio, la pantalla y el widget (mismo proceso). */
object RecordingState {
    sealed class Event {
        object Started : Event()
        /** [autoCut] vacío si la persona pulsó Parar. [failed] si no se pudo abrir el micrófono. */
        data class Stopped(val autoCut: String, val failed: Boolean = false) : Event()
    }

    @Volatile var isRecording = false
        private set
    /** Momento (reloj de pared) en que empezó la consulta. */
    @Volatile var sessionStartedAt = 0L
        private set
    /** Momento (elapsedRealtime) en que se pulsó Empezar: base del anti-doble-toque y del contador. */
    @Volatile var startedElapsed = 0L
        private set

    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArraySet<(Event) -> Unit>()

    fun addListener(l: (Event) -> Unit) { listeners += l }
    fun removeListener(l: (Event) -> Unit) { listeners -= l }

    internal fun started(wall: Long, elapsed: Long) {
        isRecording = true
        sessionStartedAt = wall
        startedElapsed = elapsed
        emit(Event.Started)
    }

    internal fun stopped(autoCut: String, failed: Boolean = false) {
        isRecording = false
        emit(Event.Stopped(autoCut, failed))
    }

    private fun emit(e: Event) = main.post { listeners.forEach { it(e) } }
}
