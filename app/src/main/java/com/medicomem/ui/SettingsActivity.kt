package com.medicomem.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import com.medicomem.BuildConfig
import com.medicomem.R
import com.medicomem.config.AppConfig
import com.medicomem.storage.RecordingMeta
import com.medicomem.storage.RecordingStore
import com.medicomem.upload.Captions
import com.medicomem.upload.SendResult
import com.medicomem.upload.TelegramUploader
import com.medicomem.upload.UploadScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/** Menú familiar: solo para quien instala la app. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var config: AppConfig
    private lateinit var store: RecordingStore
    private lateinit var name: EditText
    private lateinit var token: EditText
    private lateinit var chat: EditText
    private lateinit var testResult: TextView
    private lateinit var mobileData: Switch
    private lateinit var micFar: RadioButton
    private lateinit var micStd: RadioButton
    private lateinit var batteryStatus: TextView
    private lateinit var list: LinearLayout
    private var player: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        config = AppConfig(this)
        store = RecordingStore(filesDir)

        name = findViewById(R.id.nameInput)
        token = findViewById(R.id.tokenInput)
        chat = findViewById(R.id.chatInput)
        testResult = findViewById(R.id.testResult)
        mobileData = findViewById(R.id.mobileDataSwitch)
        micFar = findViewById(R.id.micFar)
        micStd = findViewById(R.id.micStd)
        batteryStatus = findViewById(R.id.batteryStatus)
        list = findViewById(R.id.recordingsList)

        name.setText(config.phoneName)
        token.setText(config.botToken)
        chat.setText(config.chatId)
        mobileData.isChecked = config.useMobileData
        if (config.farVoiceMic) micFar.isChecked = true else micStd.isChecked = true
        findViewById<TextView>(R.id.versionText).text = getString(R.string.version, BuildConfig.VERSION_NAME)

        findViewById<Button>(R.id.findChatButton).setOnClickListener { findChatId() }
        findViewById<Button>(R.id.testButton).setOnClickListener { testSend() }
        findViewById<Button>(R.id.batteryButton).setOnClickListener { openBatterySettings() }
        findViewById<Button>(R.id.appInfoButton).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        findViewById<Button>(R.id.closeButton).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        refreshBattery()
        refreshList()
    }

    override fun onPause() {
        save()
        player?.release()
        player = null
        super.onPause()
    }

    private fun save() {
        val networkChanged = config.useMobileData != mobileData.isChecked
        val credentialsChanged = config.botToken != token.text.toString().trim() || config.chatId != chat.text.toString().trim()
        config.phoneName = name.text.toString()
        config.botToken = token.text.toString()
        config.chatId = chat.text.toString()
        config.useMobileData = mobileData.isChecked
        config.farVoiceMic = micFar.isChecked
        // Aplica la nueva red a lo que ya está en cola, o reintenta ya con el token corregido.
        if (networkChanged || credentialsChanged) UploadScheduler.enqueueAllPending(this, ExistingWorkPolicy.REPLACE)
    }

    private fun testSend() {
        save()
        testResult.text = getString(R.string.testing)
        val text = "✅ MedicoMem conectado — ${config.phoneName}"
        thread {
            val result = TelegramUploader().sendMessage(config.botToken, config.chatId, text)
            runOnUiThread {
                testResult.text = when (result) {
                    SendResult.Ok -> getString(R.string.test_ok)
                    is SendResult.Retry -> getString(R.string.test_network, result.reason)
                    is SendResult.Rejected -> getString(R.string.test_rejected, result.reason)
                }
            }
        }
    }

    private fun findChatId() {
        save()
        testResult.text = getString(R.string.testing)
        thread {
            val ids = TelegramUploader().findChatIds(config.botToken)
            runOnUiThread {
                if (ids.isEmpty()) {
                    testResult.text = getString(R.string.chat_not_found)
                } else {
                    chat.setText(ids.first())
                    save()
                    testResult.text = getString(R.string.chat_found, ids.joinToString())
                }
            }
        }
    }

    @SuppressLint("BatteryLife")
    private fun openBatterySettings() {
        val intent = if (!ignoringBattery()) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        } else {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
        runCatching { startActivity(intent) }.onFailure {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun ignoringBattery(): Boolean =
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)

    private fun refreshBattery() {
        batteryStatus.text = getString(if (ignoringBattery()) R.string.battery_ok else R.string.battery_ko)
        batteryStatus.setTextColor(ContextCompat.getColor(this, if (ignoringBattery()) R.color.green else R.color.red))
    }

    private fun refreshList() {
        list.removeAllViews()
        val pending = store.listPending()
        val sent = store.listSent()
        if (pending.isEmpty() && sent.isEmpty()) {
            list.addView(label(getString(R.string.no_recordings)))
            return
        }
        pending.forEach { addRow(it, sent = false) }
        sent.forEach { addRow(it, sent = true) }
    }

    private fun addRow(meta: RecordingMeta, sent: Boolean) {
        val date = SimpleDateFormat("dd/MM HH:mm", Locale("es", "ES")).format(Date(meta.startedAt))
        val part = if (meta.multipart) " · parte ${meta.part}" else ""
        val state = if (sent) "✓ enviado" else "⏳ pendiente"
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(label("$date$part · ${Captions.duration(meta.durationSec)} · $state"),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(Button(this).apply {
            text = getString(R.string.play)
            setOnClickListener { play(if (sent) store.sentAudio(meta.id) else store.pendingAudio(meta.id)) }
        })
        row.addView(Button(this).apply {
            text = getString(R.string.resend)
            setOnClickListener {
                if (sent) store.requeueSent(meta.id)
                UploadScheduler.enqueue(this@SettingsActivity, meta.id, ExistingWorkPolicy.REPLACE)
                refreshList()
            }
        })
        list.addView(row)
    }

    private fun play(file: java.io.File) {
        player?.release()
        player = runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
            }
        }.getOrNull()
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.dark_text))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
