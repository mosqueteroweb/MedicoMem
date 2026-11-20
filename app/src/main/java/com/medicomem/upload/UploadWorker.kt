package com.medicomem.upload

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.medicomem.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString(KEY_ID) ?: return@withContext Result.failure()
        val store = UploadScheduler.store(applicationContext)
        val meta = store.pending(id) ?: return@withContext Result.success() // ya enviado o borrado
        val audio = store.pendingAudio(id)
        if (!audio.isFile) return@withContext Result.success()

        val config = AppConfig(applicationContext)
        // Sin configurar todavía: se queda guardado y se reintenta más tarde.
        if (!config.isTelegramConfigured) return@withContext Result.retry()

        val uploader = TelegramUploader()
        var result = uploader.sendRecording(config.botToken, config.chatId, meta, audio)
        if (result is SendResult.Migrated) {
            // El grupo pasó a supergrupo: se guarda su número nuevo y se reenvía allí.
            Log.i(TAG, "El grupo cambió de número; se actualiza el Chat ID")
            config.chatId = result.newChatId
            result = uploader.sendRecording(config.botToken, config.chatId, meta, audio)
        }
        when (result) {
            SendResult.Ok -> {
                store.markSent(id)
                Result.success()
            }
            is SendResult.Retry -> {
                Log.w(TAG, "Reintento de $id: ${result.reason}")
                Result.retry()
            }
            is SendResult.Rejected -> {
                // Token o chat mal puestos: se conserva el audio y se reintenta con espera creciente.
                Log.w(TAG, "Telegram rechaza $id: ${result.reason}")
                Result.retry()
            }
            is SendResult.Migrated -> Result.retry()
        }
    }

    companion object {
        const val KEY_ID = "id"
        const val TAG = "upload"
    }
}

class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        UploadScheduler.store(applicationContext).purgeSent()
        UploadScheduler.enqueueAllPending(applicationContext)
        Result.success()
    }
}
