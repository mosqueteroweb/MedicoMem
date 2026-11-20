package com.medicomem.upload

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.medicomem.config.AppConfig
import com.medicomem.storage.RecordingStore
import java.util.concurrent.TimeUnit

object UploadPolicy {
    /** Datos móviles o Wi-Fi por defecto; solo Wi-Fi si se desactiva en el menú familiar. */
    fun networkType(useMobileData: Boolean): NetworkType =
        if (useMobileData) NetworkType.CONNECTED else NetworkType.UNMETERED

    const val BACKOFF_SECONDS = 30L
}

object UploadScheduler {
    private const val MAINTENANCE = "maintenance"
    private fun uniqueName(id: String) = "upload-$id"

    fun store(context: Context) = RecordingStore(context.filesDir)

    /** Pone en cola el envío de una grabación. Sobrevive a reinicios del móvil. */
    fun enqueue(context: Context, id: String, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        val config = AppConfig(context)
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setInputData(workDataOf(UploadWorker.KEY_ID to id))
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(UploadPolicy.networkType(config.useMobileData)).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, UploadPolicy.BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(UploadWorker.TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(id), policy, request)
    }

    /** Vuelve a poner en cola todo lo pendiente; con REPLACE aplica un cambio de red recién hecho. */
    fun enqueueAllPending(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
        store(context).listPending().forEach { enqueue(context, it.id, policy) }
    }

    /** Tarea diaria: borra lo enviado hace más de 15 días y recupera envíos que se hubieran perdido. */
    fun scheduleMaintenance(context: Context) {
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(MAINTENANCE, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
