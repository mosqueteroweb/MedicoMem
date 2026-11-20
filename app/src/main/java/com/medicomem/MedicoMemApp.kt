package com.medicomem

import android.app.Application
import com.medicomem.recording.RecordingService
import com.medicomem.upload.UploadScheduler

class MedicoMemApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RecordingService.createChannel(this)
        RecordingService.recoverOrphans(this)
        UploadScheduler.enqueueAllPending(this)
        UploadScheduler.scheduleMaintenance(this)
    }
}
