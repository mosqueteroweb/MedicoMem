package com.medicomem.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
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
import androidx.appcompat.app.AlertDialog
import com.medicomem.upload.BotChat
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
    private lateinit var micRaw: RadioButton
    private lateinit var micStd: RadioButton
    private lateinit var micVideo: RadioButton
    private lateinit var enhance: Switch
    private lateinit var batteryStatus: TextView
    private lateinit var list: LinearLayout
    private var player: MediaPlayer? = null
    private var playingButton: Button? = null

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
        micRaw = findViewById(R.id.micRaw)
        micStd = findViewById(R.id.micStd)
        micVideo = findViewById(R.id.micVideo)
        enhance = findViewById(R.id.enhanceSwitch)
        batteryStatus = findViewById(R.id.batteryStatus)
        list = findViewById(R.id.recordingsList)

        name.setText(config.phoneName)
        token.setText(config.botToken)
        chat.setText(config.chatId)
        mobileData.isChecked = config.useMobileData
        enhance.isChecked = config.enhanceVoice
        when (config.micMode) {
            AppConfig.MicMode.RAW -> micRaw.isChecked = true
            AppConfig.MicMode.STANDARD -> micStd.isChecked = true
            AppConfig.MicMode.VIDEO -> micVideo.isChecked = true
        }
        findViewById<TextView>(R.id.versionText).text =
            getString(R.string.version, BuildConfig.VERSION_NAME) + "\n" + deviceInfo()

        findViewById<Button>(R.id.tokenToggle).setOnClickListener { toggleTokenVisibility() }
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
        stopPlayback()
        super.onPause()
    }

    private fun toggleTokenVisibility() {
        val visible = token.inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD == 0
        val selection = token.selectionEnd
        token.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            if (visible) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        token.typeface = android.graphics.Typeface.MONOSPACE
        token.setSelection(selection.coerceIn(0, token.length()))
    }

    private fun save() {
        val networkChanged = config.useMobileData != mobileData.isChecked
        val credentialsChanged = config.botToken != token.text.toString().trim() || config.chatId != chat.text.toString().trim()
        config.phoneName = name.text.toString()
        config.botToken = token.text.toString()
        config.chatId = chat.text.toString()
        config.useMobileData = mobileData.isChecked
        config.enhanceVoice = enhance.isChecked
        config.micMode = when {
            micStd.isChecked -> AppConfig.MicMode.STANDARD
            micVideo.isChecked -> AppConfig.MicMode.VIDEO
            else -> AppConfig.MicMode.RAW
        }
        // Aplica la nueva red a lo que ya está en cola, o reintenta ya con el token corregido.
        if (networkChanged || credentialsChanged) UploadScheduler.enqueueAllPending(this, ExistingWorkPolicy.REPLACE)
    }

    private fun testSend() {
        save()
        testResult.text = getString(R.string.testing)
        val text = "✅ MedicoMem conectado — ${config.phoneName}\n📱 ${deviceInfo()} · app ${BuildConfig.VERSION_NAME}\n" +
            if (Build.VERSION.SDK_INT >= 29) "🎤 Notas de voz con transcripción →A" else "🎤 Audio M4A (sin →A: necesita Android 10)"
        thread {
            val uploader = TelegramUploader()
            var result = uploader.sendMessage(config.botToken, config.chatId, text)
            if (result is SendResult.Migrated) {
                config.chatId = result.newChatId
                result = uploader.sendMessage(config.botToken, config.chatId, text)
            }
            runOnUiThread {
                chat.setText(config.chatId)
                testResult.text = when (result) {
                    SendResult.Ok -> getString(R.string.test_ok)
                    is SendResult.Retry -> getString(R.string.test_network, result.reason)
                    is SendResult.Rejected -> getString(R.string.test_rejected, result.reason)
                    is SendResult.Migrated -> getString(R.string.test_rejected, "el grupo ha cambiado de número")
                }
            }
        }
    }

    private fun findChatId() {
        save()
        testResult.text = getString(R.string.testing)
        thread {
            val chats = TelegramUploader().findChats(config.botToken)
            runOnUiThread {
                when {
                    chats.isEmpty() -> testResult.text = getString(R.string.chat_not_found)
                    chats.size == 1 -> useChat(chats.first())
                    else -> AlertDialog.Builder(this)
                        .setTitle(R.string.choose_chat)
                        .setItems(chats.map { (if (it.isGroup) "👥 " else "👤 ") + it.name }.toTypedArray()) { _, i ->
                            useChat(chats[i])
                        }
                        .setNegativeButton(android.R.string.cancel) { _, _ -> testResult.text = "" }
                        .show()
                }
            }
        }
    }

    private fun useChat(c: BotChat) {
        chat.setText(c.id)
        save()
        testResult.text = getString(R.string.chat_found, c.name)
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
            setOnClickListener { togglePlay(this, if (sent) store.sentAudio(meta.id) else store.pendingAudio(meta.id)) }
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

    /** ▶ Oír / ⏹ Parar: un toque empieza y otro para; al acabar el audio vuelve a ▶. */
    private fun togglePlay(button: Button, file: java.io.File) {
        val wasThis = playingButton === button
        stopPlayback()
        if (wasThis) return
        player = runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { stopPlayback() }
                prepare()
                start()
            }
        }.getOrNull()
        if (player != null) {
            playingButton = button
            button.text = getString(R.string.playing_stop)
        }
    }

    private fun stopPlayback() {
        player?.release()
        player = null
        playingButton?.text = getString(R.string.play)
        playingButton = null
    }

    private fun label(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setTextColor(ContextCompat.getColor(this@SettingsActivity, R.color.dark_text))
    }

    /** «LG G7 ThinQ (LM-G710EM) · Android 10». */
    private fun deviceInfo(): String {
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = if (Build.MODEL.startsWith(maker, ignoreCase = true)) Build.MODEL else "$maker ${Build.MODEL}"
        return "$model · Android ${Build.VERSION.RELEASE}"
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
