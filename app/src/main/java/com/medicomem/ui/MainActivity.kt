package com.medicomem.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.medicomem.R
import com.medicomem.config.AppConfig
import com.medicomem.recording.Feedback
import com.medicomem.recording.RecordingService
import com.medicomem.recording.RecordingState

/** Pantalla única: un botón gigante que empieza y para. */
class MainActivity : AppCompatActivity() {

    private enum class Screen { IDLE, STARTING, RECORDING, SAVED, FAILED, TOO_SHORT }

    private lateinit var root: View
    private lateinit var familyHint: TextView
    private lateinit var status: TextView
    private lateinit var timer: TextView
    private lateinit var button: TextView
    private lateinit var footer: TextView
    private lateinit var doctorHint: TextView
    private lateinit var levelTrack: View
    private lateinit var levelBar: View
    private var shownLevel = 0f

    private val handler = Handler(Looper.getMainLooper())
    private var screen = Screen.IDLE
    private var pendingStartAfterPermission = false

    private val stateListener: (RecordingState.Event) -> Unit = { event ->
        when (event) {
            RecordingState.Event.Started -> show(Screen.RECORDING)
            is RecordingState.Event.Stopped -> show(
                when {
                    event.failed -> Screen.FAILED
                    event.tooShort -> Screen.TOO_SHORT
                    else -> Screen.SAVED
                }
            )
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (screen == Screen.RECORDING) {
                timer.text = formatElapsed(SystemClock.elapsedRealtime() - RecordingState.startedElapsed)
                // Medidor: sube al instante y baja suave, para que se lea como «está oyendo».
                val level = RecordingService.currentLevel()
                shownLevel = if (level > shownLevel) level else shownLevel * 0.85f + level * 0.15f
                levelBar.scaleX = shownLevel.coerceIn(0.03f, 1f)
                handler.postDelayed(this, 100)
            }
        }
    }

    private val backToIdle = Runnable { if (!RecordingState.isRecording) show(Screen.IDLE) }
    private val openSettings = Runnable {
        familyHint.setTextColor(ContextCompat.getColor(this, R.color.hint))
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.root)
        familyHint = findViewById(R.id.familyHint)
        status = findViewById(R.id.statusText)
        timer = findViewById(R.id.timerText)
        button = findViewById(R.id.bigButton)
        footer = findViewById(R.id.footerText)
        doctorHint = findViewById(R.id.doctorHint)
        levelTrack = findViewById(R.id.levelTrack)
        levelBar = findViewById(R.id.levelBar)
        levelBar.pivotX = 0f

        button.setOnClickListener { onBigButton() }
        setupHiddenSettings()

        if (savedInstanceState == null) handleWidgetIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        RecordingState.addListener(stateListener)
        when {
            RecordingState.isRecording -> show(Screen.RECORDING)
            screen == Screen.RECORDING || screen == Screen.IDLE -> show(Screen.IDLE)
        }
        footer.visibility = if (AppConfig(this).isTelegramConfigured) View.GONE else View.VISIBLE
    }

    override fun onStop() {
        RecordingState.removeListener(stateListener)
        handler.removeCallbacks(tick)
        super.onStop()
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent?.action == ACTION_TOGGLE) {
            intent.action = null
            onBigButton()
        }
    }

    private fun onBigButton() {
        when {
            screen == Screen.SAVED || screen == Screen.STARTING || screen == Screen.TOO_SHORT -> Unit // evita un toque de más
            RecordingState.isRecording -> {
                // Anti-doble-toque: ignora un segundo toque justo después de empezar.
                if (SystemClock.elapsedRealtime() - RecordingState.startedElapsed < DOUBLE_TAP_GUARD_MS) return
                Feedback.stopped(this)
                RecordingService.stop(this)
            }
            else -> startRecording()
        }
    }

    private fun startRecording() {
        val missing = buildList {
            if (!granted(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (Manifest.permission.RECORD_AUDIO in missing) {
            pendingStartAfterPermission = true
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQ_PERMS)
            return
        }
        Feedback.started(this)
        show(Screen.STARTING)
        RecordingService.start(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMS && pendingStartAfterPermission) {
            pendingStartAfterPermission = false
            if (granted(Manifest.permission.RECORD_AUDIO)) startRecording()
        }
    }

    private fun granted(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun show(target: Screen) {
        screen = target
        handler.removeCallbacks(tick)
        handler.removeCallbacks(backToIdle)
        val c = { id: Int -> ContextCompat.getColor(this, id) }
        when (target) {
            Screen.IDLE, Screen.STARTING -> {
                root.setBackgroundColor(c(R.color.idle_bg))
                familyHint.visibility = View.VISIBLE
                status.visibility = View.GONE
                timer.visibility = View.GONE
                doctorHint.visibility = View.GONE
                levelTrack.visibility = View.GONE
                turn(upsideDown = false)
                button.text = getString(if (target == Screen.STARTING) R.string.starting else R.string.start)
                styleButton(c(R.color.green), Color.WHITE, 0)
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            Screen.RECORDING -> {
                root.setBackgroundColor(c(R.color.red))
                familyHint.visibility = View.INVISIBLE
                status.visibility = View.VISIBLE
                status.text = getString(R.string.recording)
                timer.visibility = View.VISIBLE
                doctorHint.visibility = View.VISIBLE
                levelTrack.visibility = View.VISIBLE
                familyHint.visibility = View.GONE
                turn(upsideDown = true)
                button.text = getString(R.string.stop)
                styleButton(Color.WHITE, c(R.color.red), 0)
                handler.post(tick)
            }
            Screen.SAVED, Screen.FAILED, Screen.TOO_SHORT -> {
                root.setBackgroundColor(c(if (target == Screen.SAVED) R.color.green else R.color.idle_bg))
                familyHint.visibility = View.INVISIBLE
                status.visibility = View.GONE
                timer.visibility = View.GONE
                doctorHint.visibility = View.GONE
                levelTrack.visibility = View.GONE
                if (target == Screen.SAVED) {
                    button.text = getString(R.string.saved)
                    styleButton(c(R.color.green), Color.WHITE, Color.WHITE)
                } else if (target == Screen.TOO_SHORT) {
                    turn(upsideDown = false) // seguramente aún lo tiene en la mano
                    button.text = getString(R.string.too_short)
                    styleButton(c(R.color.grey), Color.WHITE, 0)
                } else {
                    button.text = getString(R.string.failed)
                    styleButton(c(R.color.dark_text), Color.WHITE, 0)
                }
                handler.postDelayed(backToIdle, SAVED_SCREEN_MS)
            }
        }
        button.contentDescription = button.text
        val bg = (root.background as? android.graphics.drawable.ColorDrawable)?.color ?: c(R.color.idle_bg)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        val light = target == Screen.IDLE || target == Screen.STARTING || target == Screen.FAILED || target == Screen.TOO_SHORT
        androidx.core.view.WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
        footer.setTextColor(if (light) c(R.color.hint) else Color.WHITE)
    }

    /**
     * Al grabar, la pantalla se pone del revés: quien la lee tiene que girar el móvil y así el borde
     * de abajo (donde está el micrófono) queda apuntando al médico.
     */
    private fun turn(upsideDown: Boolean) {
        val target = if (upsideDown) 180f else 0f
        if (root.rotation == target) return
        root.animate().cancel()
        root.animate().rotation(target).setDuration(TURN_MS).start()
    }

    private fun styleButton(fill: Int, text: Int, stroke: Int) {
        val d = resources.displayMetrics.density
        button.background = GradientDrawable().apply {
            cornerRadius = 28 * d
            setColor(fill)
            if (stroke != 0) setStroke((4 * d).toInt(), stroke)
        }
        button.setTextColor(text)
    }

    /** Menú familiar: mantener pulsado 4 segundos el texto de arriba. */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupHiddenSettings() {
        familyHint.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    familyHint.setTextColor(ContextCompat.getColor(this, R.color.dark_text))
                    handler.postDelayed(openSettings, HIDDEN_PRESS_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(openSettings)
                    familyHint.setTextColor(ContextCompat.getColor(this, R.color.hint))
                    if (e.actionMasked == MotionEvent.ACTION_UP) v.performClick()
                }
            }
            true
        }
    }

    companion object {
        const val ACTION_TOGGLE = "com.medicomem.TOGGLE"
        private const val REQ_PERMS = 1
        private const val DOUBLE_TAP_GUARD_MS = 1_500L
        private const val SAVED_SCREEN_MS = 3_000L
        private const val HIDDEN_PRESS_MS = 4_000L
        private const val TURN_MS = 600L

        fun formatElapsed(ms: Long): String {
            val s = (ms / 1000).coerceAtLeast(0)
            return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60)
            else "%02d:%02d".format(s / 60, s % 60)
        }
    }
}
