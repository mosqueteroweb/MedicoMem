package com.medicomem.storage

import java.io.File
import java.util.Properties

/** Datos de una grabación ya cerrada (un archivo .m4a). */
data class RecordingMeta(
    val id: String,
    val startedAt: Long,
    val durationSec: Long,
    val battery: Int,
    val phoneName: String,
    /** Texto del motivo si la grabación se cortó sola; vacío si la paró la persona. */
    val autoCut: String = "",
    /** Número de parte dentro de una misma consulta (1, 2…). */
    val part: Int = 1,
    /** true si la consulta tiene (o va a tener) más de una parte. */
    val multipart: Boolean = false,
    val sentAt: Long = 0,
)

/**
 * Archivos de audio en el almacenamiento privado:
 *   recording/  el que se está grabando ahora
 *   pending/    cerrados, esperando a enviarse (audio + .properties)
 *   sent/       ya enviados; se borran pasados [SENT_RETENTION_DAYS] días
 */
class RecordingStore(baseDir: File) {
    val recordingDir = File(baseDir, "recording").apply { mkdirs() }
    val pendingDir = File(baseDir, "pending").apply { mkdirs() }
    val sentDir = File(baseDir, "sent").apply { mkdirs() }

    /** Archivo nuevo en recording/; [extension] «ogg» (Opus, Android 10+) o «m4a» (AAC). */
    fun newRecordingFile(id: String, extension: String = "m4a"): File = File(recordingDir, "$id.$extension")

    /** Pasa el audio de recording/ a pending/ junto a sus datos. Devuelve el audio ya movido. */
    fun finalize(meta: RecordingMeta, audio: File): File {
        val target = File(pendingDir, audio.name)
        writeMeta(pendingDir, meta)
        if (!audio.renameTo(target)) {
            audio.copyTo(target, overwrite = true)
            audio.delete()
        }
        return target
    }

    fun pendingAudio(id: String): File = audioFile(pendingDir, id)
    fun sentAudio(id: String): File = audioFile(sentDir, id)

    fun pending(id: String): RecordingMeta? = readMeta(metaFile(pendingDir, id))

    fun listPending(): List<RecordingMeta> = list(pendingDir)
    fun listSent(): List<RecordingMeta> = list(sentDir)

    fun markSent(id: String, now: Long = System.currentTimeMillis()) {
        val meta = pending(id) ?: return
        val audio = audioFile(pendingDir, id)
        if (audio.exists() && !audio.renameTo(File(sentDir, audio.name))) {
            audio.copyTo(File(sentDir, audio.name), overwrite = true)
            audio.delete()
        }
        writeMeta(sentDir, meta.copy(sentAt = now))
        metaFile(pendingDir, id).delete()
    }

    /** Vuelve a poner en cola una grabación ya enviada (reenvío manual). */
    fun requeueSent(id: String): Boolean {
        val meta = readMeta(metaFile(sentDir, id)) ?: return false
        val audio = audioFile(sentDir, id)
        if (!audio.exists()) return false
        writeMeta(pendingDir, meta.copy(sentAt = 0))
        if (!audio.renameTo(File(pendingDir, audio.name))) {
            audio.copyTo(File(pendingDir, audio.name), overwrite = true)
            audio.delete()
        }
        metaFile(sentDir, id).delete()
        return true
    }

    /** Borra lo enviado hace más de [days] días. Devuelve cuántas grabaciones se han borrado. */
    fun purgeSent(now: Long = System.currentTimeMillis(), days: Int = SENT_RETENTION_DAYS): Int {
        val limit = now - days * DAY_MS
        var removed = 0
        for (meta in listSent()) {
            if (meta.sentAt in 1 until limit) {
                audioFile(sentDir, meta.id).delete()
                metaFile(sentDir, meta.id).delete()
                removed++
            }
        }
        return removed
    }

    /** Audios que quedaron a medias en recording/ (la app murió grabando). */
    fun orphanRecordings(): List<File> =
        recordingDir.listFiles { f -> f.isFile && f.extension in AUDIO_EXTENSIONS }?.toList().orEmpty()

    private fun list(dir: File): List<RecordingMeta> =
        dir.listFiles { f -> f.name.endsWith(".properties") }
            ?.mapNotNull { readMeta(it) }
            ?.filter { audioFile(dir, it.id).exists() }
            ?.sortedByDescending { it.startedAt * 100 + it.part }
            .orEmpty()

    private fun audioFile(dir: File, id: String): File =
        AUDIO_EXTENSIONS.map { File(dir, "$id.$it") }.firstOrNull { it.exists() } ?: File(dir, "$id.m4a")
    private fun metaFile(dir: File, id: String) = File(dir, "$id.properties")

    private fun writeMeta(dir: File, meta: RecordingMeta) {
        val props = Properties().apply {
            setProperty("id", meta.id)
            setProperty("startedAt", meta.startedAt.toString())
            setProperty("durationSec", meta.durationSec.toString())
            setProperty("battery", meta.battery.toString())
            setProperty("phoneName", meta.phoneName)
            setProperty("autoCut", meta.autoCut)
            setProperty("part", meta.part.toString())
            setProperty("multipart", meta.multipart.toString())
            setProperty("sentAt", meta.sentAt.toString())
        }
        val tmp = File(dir, "${meta.id}.properties.tmp")
        tmp.outputStream().use { props.store(it, null) }
        val target = metaFile(dir, meta.id)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun readMeta(file: File): RecordingMeta? {
        if (!file.isFile) return null
        return runCatching {
            val p = Properties().apply { file.inputStream().use { load(it) } }
            RecordingMeta(
                id = p.getProperty("id")!!,
                startedAt = p.getProperty("startedAt")!!.toLong(),
                durationSec = p.getProperty("durationSec", "0").toLong(),
                battery = p.getProperty("battery", "-1").toInt(),
                phoneName = p.getProperty("phoneName", ""),
                autoCut = p.getProperty("autoCut", ""),
                part = p.getProperty("part", "1").toInt(),
                multipart = p.getProperty("multipart", "false").toBoolean(),
                sentAt = p.getProperty("sentAt", "0").toLong(),
            )
        }.getOrNull()
    }

    companion object {
        const val SENT_RETENTION_DAYS = 15
        private val AUDIO_EXTENSIONS = listOf("ogg", "m4a")
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
