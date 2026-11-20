package com.medicomem.upload

import com.medicomem.storage.RecordingMeta
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class SendResult {
    object Ok : SendResult()
    /** Error de red o del servidor: se vuelve a intentar más tarde. */
    data class Retry(val reason: String) : SendResult()
    /** Telegram rechaza los datos (token o chat erróneos): no sirve repetir sin cambiar la configuración. */
    data class Rejected(val reason: String) : SendResult()
}

/** Envía audios al bot de Telegram en streaming desde disco (nunca carga el archivo en memoria). */
class TelegramUploader(
    private val baseUrl: HttpUrl = "https://api.telegram.org/".toHttpUrl(),
    private val client: OkHttpClient = defaultClient,
) {
    /**
     * Envía la grabación como nota de voz (con Telegram Premium se transcribe con el botón «→A»
     * y se puede oír a 1,5× o 2×). Si Telegram la rechaza (p. ej. el destinatario no admite notas
     * de voz), la reenvía como archivo de audio para que nunca se quede sin llegar.
     */
    fun sendRecording(token: String, chatId: String, meta: RecordingMeta, audio: File): SendResult {
        val voice = sendVoice(token, chatId, meta, audio)
        return if (voice is SendResult.Rejected && audio.isFile) sendAudio(token, chatId, meta, audio) else voice
    }

    fun sendVoice(token: String, chatId: String, meta: RecordingMeta, audio: File): SendResult {
        if (!audio.isFile) return SendResult.Rejected("El archivo no existe")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("caption", Captions.caption(meta))
            .addFormDataPart("duration", meta.durationSec.toString())
            .addFormDataPart("voice", audio.name, audio.asRequestBody(AUDIO_TYPE))
            .build()
        return post(token, "sendVoice", body)
    }

    fun sendAudio(token: String, chatId: String, meta: RecordingMeta, audio: File): SendResult {
        if (!audio.isFile) return SendResult.Rejected("El archivo no existe")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("title", Captions.title(meta))
            .addFormDataPart("performer", meta.phoneName)
            .addFormDataPart("caption", Captions.caption(meta))
            .addFormDataPart("duration", meta.durationSec.toString())
            .addFormDataPart("audio", audio.name, audio.asRequestBody(AUDIO_TYPE))
            .build()
        return post(token, "sendAudio", body)
    }

    fun sendMessage(token: String, chatId: String, text: String): SendResult {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("chat_id", chatId)
            .addFormDataPart("text", text)
            .build()
        return post(token, "sendMessage", body)
    }

    /** Chats que han escrito al bot hace poco (para rellenar el Chat ID sin buscarlo a mano). */
    fun findChatIds(token: String): List<String> {
        if (token.isBlank()) return emptyList()
        val url = baseUrl.newBuilder().addPathSegment("bot$token").addPathSegment("getUpdates").build()
        return try {
            client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                val text = response.body?.string().orEmpty()
                CHAT_ID.findAll(text).map { it.groupValues[1] }.distinct().toList().reversed()
            }
        } catch (e: IOException) {
            emptyList()
        }
    }

    private fun post(token: String, method: String, body: RequestBody): SendResult {
        if (token.isBlank()) return SendResult.Rejected("Falta el token del bot")
        val url = baseUrl.newBuilder().addPathSegment("bot$token").addPathSegment(method).build()
        val request = Request.Builder().url(url).post(body).build()
        return try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    response.isSuccessful && OK_TRUE.containsMatchIn(text) -> SendResult.Ok
                    response.code == 429 || response.code >= 500 -> SendResult.Retry("HTTP ${response.code}")
                    response.code in 400..499 -> SendResult.Rejected(description(text) ?: "HTTP ${response.code}")
                    else -> SendResult.Retry(description(text) ?: "Respuesta inesperada")
                }
            }
        } catch (e: IOException) {
            SendResult.Retry(e.javaClass.simpleName)
        }
    }

    private fun description(json: String): String? =
        DESCRIPTION.find(json)?.groupValues?.get(1)?.replace("\\\"", "\"")

    companion object {
        private val AUDIO_TYPE = "audio/mp4".toMediaType()
        private val OK_TRUE = Regex("\"ok\"\\s*:\\s*true")
        private val CHAT_ID = Regex("\"chat\"\\s*:\\s*\\{\\s*\"id\"\\s*:\\s*(-?\\d+)")
        private val DESCRIPTION = Regex("\"description\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")

        /** Tiempos generosos: las redes móviles de los hospitales suelen ser lentas. */
        val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
