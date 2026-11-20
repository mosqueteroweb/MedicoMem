package com.medicomem.recording

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.medicomem.R
import com.medicomem.config.AppConfig
import com.medicomem.storage.RecordingMeta
import com.medicomem.storage.RecordingStore
import com.medicomem.ui.MainActivity
import com.medicomem.upload.UploadScheduler
import com.medicomem.widget.RecordWidgetProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Graba en primer plano (sigue con la pantalla apagada o el móvil en el bolso).
 * Cada consulta puede tener varias partes: al acercarse al límite de tamaño de Telegram,
 * o tras una llamada, se cierra el archivo, se pone en cola de envío y se abre otro.
 */
class RecordingService : Service() {

    private lateinit var config: AppConfig
    private lateinit var store: RecordingStore
    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())

    private var recorder: MediaRecorder? = null
    private var segmentFile: File? = null
    private var segmentStartElapsed = 0L
    private var segmentStartWall = 0L
    private var sessionId = ""
    private var part = 0
    private var paused = false
    private var sessionStartElapsed = 0L
    private var battery = -1
    private var charging = false
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        config = AppConfig(this)
        store = RecordingStore(filesDir)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> if (!RecordingState.isRecording) startSession() else ensureForeground()
            ACTION_STOP -> if (RecordingState.isRecording) stopSession("") else stopSelf()
            else -> if (!RecordingState.isRecording) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (RecordingState.isRecording) stopSession("la app se cerró")
        super.onDestroy()
    }

    // ---------- Sesión ----------

    private fun startSession() {
        ensureForeground()
        recoverOrphans(this)

        val now = System.currentTimeMillis()
        sessionId = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(now))
        sessionStartElapsed = SystemClock.elapsedRealtime()
        part = 0
        paused = false

        acquireWakeLock()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::readBattery)
        requestFocus()

        if (!startSegment()) {
            Log.e(TAG, "No se pudo abrir el micrófono")
            releaseAll()
            RecordingState.stopped("", failed = true)
            RecordWidgetProvider.updateAll(this)
            return
        }
        RecordingState.started(now, sessionStartElapsed)
        RecordWidgetProvider.updateAll(this)
        handler.postDelayed(ticker, TICK_MS)
    }

    private fun stopSession(autoCut: String) {
        handler.removeCallbacks(ticker)
        finishSegment(autoCut, continues = false)
        releaseAll()
        RecordingState.stopped(autoCut)
        RecordWidgetProvider.updateAll(this)
        if (autoCut.isNotEmpty()) Feedback.stopped(this)
    }

    private fun releaseAll() {
        abandonFocus()
        runCatching { unregisterReceiver(batteryReceiver) }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private val ticker = object : Runnable {
        override fun run() {
            val elapsedMin = (SystemClock.elapsedRealtime() - sessionStartElapsed) / 60_000
            when {
                elapsedMin >= config.maxDurationMinutes -> { stopSession("límite de ${config.maxDurationMinutes / 60} h"); return }
                battery in 0..LOW_BATTERY_STOP && !charging -> { stopSession("batería al $battery %"); return }
                paused && !inCall() && requestFocus() -> resumeAfterInterruption()
            }
            handler.postDelayed(this, TICK_MS)
        }
    }

    // ---------- Partes (archivos) ----------

    private fun startSegment(): Boolean {
        part++
        val file = store.newRecordingFile("$sessionId-p$part")
        // Primero la calidad elegida; si el móvil la rechaza, opciones más comunes.
        val attempts = listOf(
            Triple(config.audioSource, 24_000, 48_000),
            Triple(config.audioSource, 44_100, 64_000),
            Triple(MediaRecorder.AudioSource.MIC, 44_100, 64_000),
            Triple(MediaRecorder.AudioSource.MIC, 16_000, 32_000),
        ).distinct()
        for ((source, rate, bitrate) in attempts) {
            val rec = newRecorder()
            try {
                rec.setAudioSource(source)
                rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                rec.setAudioChannels(1)
                rec.setAudioSamplingRate(rate)
                rec.setAudioEncodingBitRate(bitrate)
                rec.setMaxFileSize(MAX_SEGMENT_BYTES)
                rec.setOutputFile(file.absolutePath)
                rec.setOnInfoListener { _, what, _ ->
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) handler.post { rotateSegment() }
                }
                rec.setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Error de MediaRecorder $what/$extra")
                    handler.post { rotateSegment() }
                }
                rec.prepare()
                rec.start()
                recorder = rec
                segmentFile = file
                segmentStartElapsed = SystemClock.elapsedRealtime()
                segmentStartWall = System.currentTimeMillis()
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Configuración $source/$rate rechazada: $e")
                runCatching { rec.reset() }
                rec.release()
                file.delete()
            }
        }
        part--
        return false
    }

    /** Cierra el archivo actual y lo pone en cola. [continues]: la consulta sigue en otra parte. */
    private fun finishSegment(autoCut: String, continues: Boolean) {
        val rec = recorder ?: return
        val file = segmentFile ?: return
        recorder = null
        segmentFile = null
        // stop() falla si apenas hay datos, o si el archivo ya se cerró solo al llegar al tamaño máximo:
        // por eso se conserva todo archivo con contenido real.
        runCatching { rec.stop() }
        rec.release()
        if (!file.exists() || file.length() < MIN_USEFUL_BYTES) {
            file.delete()
            return
        }
        val meta = RecordingMeta(
            id = file.nameWithoutExtension,
            startedAt = segmentStartWall,
            durationSec = (SystemClock.elapsedRealtime() - segmentStartElapsed) / 1000,
            battery = battery,
            phoneName = config.phoneName,
            autoCut = autoCut,
            part = part,
            multipart = continues || part > 1,
        )
        store.finalize(meta, file)
        UploadScheduler.enqueue(this, meta.id)
    }

    private fun rotateSegment() {
        if (!RecordingState.isRecording || recorder == null) return
        finishSegment("", continues = true)
        if (!startSegment()) stopSession("fallo del micrófono")
    }

    private fun pauseForInterruption() {
        if (paused || recorder == null) return
        Log.i(TAG, "Interrupción (llamada u otro audio): se guarda la parte $part")
        finishSegment("", continues = true)
        paused = true
    }

    private fun resumeAfterInterruption() {
        paused = false
        if (!startSegment()) stopSession("fallo del micrófono tras una llamada")
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()

    // ---------- Foco de audio (llamadas) ----------

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pauseForInterruption()
            AudioManager.AUDIOFOCUS_GAIN -> if (paused && !inCall()) resumeAfterInterruption()
        }
    }

    private fun inCall(): Boolean = audioManager.mode in setOf(
        AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION, AudioManager.MODE_RINGTONE,
    )

    private fun requestFocus(): Boolean {
        val result = if (Build.VERSION.SDK_INT >= 26) {
            val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener, handler)
                .build().also { focusRequest = it }
            audioManager.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }

    // ---------- Batería, pantalla, notificación ----------

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = readBattery(intent)
    }

    private fun readBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level >= 0 && scale > 0) battery = level * 100 / scale
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "medicomem:grabacion").apply {
            setReferenceCounted(false)
            acquire((config.maxDurationMinutes + 15) * 60_000L)
        }
    }

    private fun ensureForeground() {
        createChannel(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 30) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification, type)
    }

    companion object {
        private const val TAG = "RecordingService"
        const val ACTION_START = "com.medicomem.START"
        const val ACTION_STOP = "com.medicomem.STOP"
        private const val CHANNEL = "grabacion"
        private const val NOTIF_ID = 1
        private const val TICK_MS = 5_000L
        private const val LOW_BATTERY_STOP = 5
        private const val MIN_USEFUL_BYTES = 2_048L
        /** 42 MB: deja margen bajo el límite de 50 MB de los bots de Telegram (~1 h 50 min a 48 kbps). */
        const val MAX_SEGMENT_BYTES = 42L * 1024 * 1024

        fun start(context: Context) = ContextCompat.startForegroundService(
            context, Intent(context, RecordingService::class.java).setAction(ACTION_START),
        )

        fun stop(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP))
        }

        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, context.getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
                )
            }
        }

        /**
         * Si el móvil se apagó o la app murió grabando, el audio quedó en recording/.
         * Se envía igualmente avisando de que puede estar incompleto.
         */
        fun recoverOrphans(context: Context) {
            if (RecordingState.isRecording) return
            val store = RecordingStore(context.filesDir)
            val name = AppConfig(context).phoneName
            for (file in store.orphanRecordings()) {
                if (file.length() == 0L) { file.delete(); continue }
                val id = file.nameWithoutExtension
                val startedAt = runCatching {
                    SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).parse(id.substringBeforeLast("-p"))!!.time
                }.getOrDefault(file.lastModified())
                val meta = RecordingMeta(
                    id = id,
                    startedAt = startedAt,
                    durationSec = file.length() * 8 / 48_000,
                    battery = -1,
                    phoneName = name,
                    autoCut = "el móvil se apagó grabando; el audio puede estar dañado",
                    part = id.substringAfterLast("-p", "1").toIntOrNull() ?: 1,
                )
                store.finalize(meta, file)
                UploadScheduler.enqueue(context, id)
            }
        }
    }
}
