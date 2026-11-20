package com.medicomem.recording

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import kotlin.math.log10

/** Graba un archivo .m4a (una parte de la consulta). */
interface SegmentRecorder {
    /** Empieza a grabar. false si el móvil no admite esta configuración. */
    fun start(): Boolean
    /** Para y cierra el archivo. */
    fun stop()
    /** Nivel de sonido de 0 a 1 para el medidor de la pantalla. */
    val level: Float
}

/**
 * Motor con tratamiento de voz: AudioRecord (PCM) → [VoiceProcessor] → AAC (MediaCodec) → MP4 (MediaMuxer).
 * Todo ocurre en un hilo propio con bloques de 20 ms; ligero incluso en móviles modestos.
 */
class ProcessedSegmentRecorder(
    private val source: Int,
    private val sampleRate: Int,
    private val bitrate: Int,
    private val file: File,
    private val maxBytes: Long,
    /** Se llama (desde el hilo de grabación) al llegar al tamaño máximo o si el micrófono falla. */
    private val onLimitOrError: (error: Boolean) -> Unit,
) : SegmentRecorder {

    private var record: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val processor = VoiceProcessor(sampleRate)

    override val level: Float get() = processor.level

    @SuppressLint("MissingPermission") // el servicio solo arranca con el permiso concedido
    override fun start(): Boolean {
        try {
            val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) return false
            val rec = AudioRecord(source, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, sampleRate / 5 * 2)) // ≥ 200 ms de margen
            if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
            record = rec

            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024)
            }
            val enc = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            codec = enc
            enc.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            enc.start()
            muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            rec.startRecording()
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) { releaseAll(); return false }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo iniciar ($source/$sampleRate): $e")
            releaseAll()
            file.delete()
            return false
        }
        running = true
        thread = Thread(::loop, "grabacion").apply { priority = Thread.MAX_PRIORITY; start() }
        return true
    }

    override fun stop() {
        running = false
        val t = thread
        if (t != null && t !== Thread.currentThread()) t.join(5_000)
        thread = null
    }

    private fun loop() {
        val rec = record ?: return
        val enc = codec ?: return
        val mux = muxer ?: return
        val block = ShortArray(sampleRate / 50) // 20 ms
        val bytes = java.nio.ByteBuffer.allocateDirect(block.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        val info = MediaCodec.BufferInfo()
        var track = -1
        var samplesIn = 0L
        var written = 0L
        var limitSignalled = false
        var failed = false

        fun drain(endOfStream: Boolean) {
            while (true) {
                val idx = enc.dequeueOutputBuffer(info, if (endOfStream) 10_000 else 0)
                when {
                    idx == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        track = mux.addTrack(enc.outputFormat)
                        mux.start()
                    }
                    idx >= 0 -> {
                        val out = enc.getOutputBuffer(idx)
                        val config = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (out != null && info.size > 0 && !config && track >= 0) {
                            out.position(info.offset); out.limit(info.offset + info.size)
                            mux.writeSampleData(track, out, info)
                            written += info.size
                        }
                        enc.releaseOutputBuffer(idx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        try {
            while (running) {
                val n = rec.read(block, 0, block.size)
                if (n < 0) { failed = true; break } // micrófono perdido
                if (n == 0) continue
                processor.process(block, n)
                val inIdx = enc.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val input = enc.getInputBuffer(inIdx)!!
                    input.clear()
                    bytes.clear()
                    for (i in 0 until n) bytes.putShort(block[i])
                    bytes.flip()
                    input.put(bytes)
                    enc.queueInputBuffer(inIdx, 0, n * 2, samplesIn * 1_000_000 / sampleRate, 0)
                    samplesIn += n
                }
                drain(false)
                if (!limitSignalled && written + MP4_OVERHEAD >= maxBytes) {
                    limitSignalled = true
                    onLimitOrError(false)
                }
            }
            // Cierre ordenado: fin de flujo, vaciar el codificador y escribir el índice del MP4.
            val inIdx = enc.dequeueInputBuffer(10_000)
            if (inIdx >= 0) {
                enc.queueInputBuffer(inIdx, 0, 0, samplesIn * 1_000_000 / sampleRate, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                drain(true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallo grabando", e)
            failed = true
        } finally {
            if (track >= 0) runCatching { mux.stop() }
            releaseAll()
        }
        if (failed && running) { running = false; onLimitOrError(true) }
    }

    private fun releaseAll() {
        runCatching { record?.stop() }
        runCatching { record?.release() }
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { muxer?.release() }
        record = null; codec = null; muxer = null
    }

    companion object {
        private const val TAG = "ProcessedRecorder"
        /** Margen para el índice que MediaMuxer escribe al cerrar. */
        private const val MP4_OVERHEAD = 512L * 1024
    }
}

/** Motor sencillo de Android (MediaRecorder), sin tratamiento: se usa si el otro falla o si se desactiva. */
class PlainSegmentRecorder(
    private val context: Context,
    private val source: Int,
    private val sampleRate: Int,
    private val bitrate: Int,
    private val file: File,
    private val maxBytes: Long,
    private val onLimitOrError: (error: Boolean) -> Unit,
) : SegmentRecorder {
    private var rec: MediaRecorder? = null

    override val level: Float
        get() {
            val amp = runCatching { rec?.maxAmplitude ?: 0 }.getOrDefault(0)
            return if (amp <= 0) 0f else ((20 * log10(amp / 32767.0) + 50) / 50).toFloat().coerceIn(0f, 1f)
        }

    override fun start(): Boolean {
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        return try {
            r.setAudioSource(source)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(sampleRate)
            r.setAudioEncodingBitRate(bitrate)
            r.setMaxFileSize(maxBytes)
            r.setOutputFile(file.absolutePath)
            r.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) onLimitOrError(false)
            }
            r.setOnErrorListener { _, _, _ -> onLimitOrError(true) }
            r.prepare()
            r.start()
            rec = r
            true
        } catch (e: Exception) {
            runCatching { r.reset() }
            r.release()
            file.delete()
            false
        }
    }

    override fun stop() {
        val r = rec ?: return
        rec = null
        runCatching { r.stop() } // falla si apenas hay datos o si ya se cerró al llegar al tamaño máximo
        r.release()
    }
}
