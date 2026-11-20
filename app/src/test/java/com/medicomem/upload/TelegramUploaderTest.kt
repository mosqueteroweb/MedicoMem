package com.medicomem.upload

import com.medicomem.storage.RecordingMeta
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TelegramUploaderTest {
    @get:Rule val tmp = TemporaryFolder()
    private val server = MockWebServer()
    private lateinit var uploader: TelegramUploader
    private val meta = RecordingMeta("20261120-103500-p1", 1_795_167_300_000L, 60, 15, "Papá")

    @Before fun setUp() {
        server.start()
        uploader = TelegramUploader(baseUrl = server.url("/"))
    }

    @After fun tearDown() = server.shutdown()

    private fun audio() = tmp.newFile("a.m4a").apply { writeBytes(ByteArray(200_000) { (it % 251).toByte() }) }

    @Test fun uploadsMultipartToSendAudio() {
        server.enqueue(MockResponse().setBody("""{"ok":true,"result":{}}"""))
        val result = uploader.sendAudio("123:ABC", "-42", meta, audio())
        assertEquals(SendResult.Ok, result)

        val req = server.takeRequest()
        assertEquals("/bot123:ABC/sendAudio", req.path)
        assertTrue(req.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("name=\"chat_id\"\r\nContent-Length: 3\r\n\r\n-42"))
        assertTrue(body.contains("Batería baja"))
        assertTrue(body.contains("filename=\"a.m4a\""))
        assertTrue(req.bodySize > 200_000)
    }

    @Test fun recordingGoesAsVoiceNote() {
        server.enqueue(MockResponse().setBody("""{"ok":true,"result":{}}"""))
        assertEquals(SendResult.Ok, uploader.sendRecording("t", "1", meta, audio()))
        val req = server.takeRequest()
        assertEquals("/bott/sendVoice", req.path)
        assertTrue(req.body.readUtf8().contains("name=\"voice\"; filename=\"a.m4a\""))
        assertEquals(1, server.requestCount)
    }

    @Test fun rejectedVoiceFallsBackToAudioFile() {
        server.enqueue(MockResponse().setResponseCode(400)
            .setBody("""{"ok":false,"description":"Bad Request: VOICE_MESSAGES_FORBIDDEN"}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true,"result":{}}"""))
        assertEquals(SendResult.Ok, uploader.sendRecording("t", "1", meta, audio()))
        assertEquals("/bott/sendVoice", server.takeRequest().path)
        assertEquals("/bott/sendAudio", server.takeRequest().path)
    }

    @Test fun networkErrorOnVoiceDoesNotFallBack() {
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(uploader.sendRecording("t", "1", meta, audio()) is SendResult.Retry)
        assertEquals(1, server.requestCount)
    }

    @Test fun serverErrorIsRetried() {
        server.enqueue(MockResponse().setResponseCode(502))
        assertTrue(uploader.sendAudio("t", "1", meta, audio()) is SendResult.Retry)
    }

    @Test fun tooManyRequestsIsRetried() {
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"ok":false}"""))
        assertTrue(uploader.sendAudio("t", "1", meta, audio()) is SendResult.Retry)
    }

    @Test fun networkFailureIsRetried() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertTrue(uploader.sendAudio("t", "1", meta, audio()) is SendResult.Retry)
    }

    @Test fun badChatIsRejectedWithDescription() {
        server.enqueue(MockResponse().setResponseCode(400)
            .setBody("""{"ok":false,"error_code":400,"description":"Bad Request: chat not found"}"""))
        val result = uploader.sendMessage("t", "1", "hola")
        assertEquals(SendResult.Rejected("Bad Request: chat not found"), result)
    }

    @Test fun okWithoutTrueIsNotSuccess() {
        server.enqueue(MockResponse().setBody("""{"ok":false}"""))
        assertTrue(uploader.sendMessage("t", "1", "hola") !is SendResult.Ok)
    }

    @Test fun findsOnlyChatsWhereTheBotStillIs() {
        server.enqueue(MockResponse().setBody(
            """{"ok":true,"result":[{"update_id":1,"message":{"chat":{"id":111,"first_name":"Pedro"}}},""" +
                """{"update_id":2,"my_chat_member":{"chat":{"id":-555,"title":"Viejo"}}},""" +
                """{"update_id":3,"message":{"chat":{"id":-100222,"title":"Familia"}}}]}"""))
        // getChat de cada uno, del más reciente al más antiguo: -100222, -555, 111
        server.enqueue(MockResponse().setBody("""{"ok":true,"result":{"id":-100222,"title":"M\u00e9dicos Familia","type":"supergroup"}}"""))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"ok":false,"description":"Forbidden: bot was kicked from the group chat"}"""))
        server.enqueue(MockResponse().setBody("""{"ok":true,"result":{"id":111,"first_name":"Pedro","type":"private"}}"""))

        val chats = uploader.findChats("t")
        assertEquals(listOf(BotChat("-100222", "Médicos Familia", true), BotChat("111", "Pedro", false)), chats)
    }

    @Test fun upgradedGroupReportsNewId() {
        server.enqueue(MockResponse().setResponseCode(400).setBody(
            """{"ok":false,"error_code":400,"description":"Bad Request: group chat was upgraded to a supergroup chat","parameters":{"migrate_to_chat_id":-1001234567890}}"""))
        assertEquals(SendResult.Migrated("-1001234567890"), uploader.sendRecording("t", "-555", meta, audio()))
        assertEquals(1, server.requestCount) // no se cae a sendAudio
    }
}
