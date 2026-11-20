package com.medicomem.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RecordingStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun meta(id: String, startedAt: Long = 1_000L) =
        RecordingMeta(id = id, startedAt = startedAt, durationSec = 90, battery = 55, phoneName = "Mamá")

    private fun recorded(store: RecordingStore, id: String) =
        store.newRecordingFile(id).apply { writeBytes(ByteArray(4096) { 1 }) }

    @Test fun finalizeMovesAudioAndMetaToPending() {
        val store = RecordingStore(tmp.root)
        val audio = recorded(store, "a-p1")
        store.finalize(meta("a-p1"), audio)

        assertFalse(audio.exists())
        assertTrue(store.pendingAudio("a-p1").exists())
        assertEquals(listOf("a-p1"), store.listPending().map { it.id })
        assertEquals("Mamá", store.pending("a-p1")!!.phoneName)
        assertTrue(store.orphanRecordings().isEmpty())
    }

    @Test fun markSentMovesToSentWithTimestamp() {
        val store = RecordingStore(tmp.root)
        store.finalize(meta("b-p1"), recorded(store, "b-p1"))
        store.markSent("b-p1", now = 5_000L)

        assertTrue(store.listPending().isEmpty())
        assertNull(store.pending("b-p1"))
        val sent = store.listSent().single()
        assertEquals(5_000L, sent.sentAt)
        assertTrue(store.sentAudio("b-p1").exists())
    }

    @Test fun purgeDeletesOnlySentOlderThan15Days() {
        val store = RecordingStore(tmp.root)
        val day = 24L * 60 * 60 * 1000
        val now = 100 * day
        store.finalize(meta("old-p1"), recorded(store, "old-p1"))
        store.markSent("old-p1", now = now - 16 * day)
        store.finalize(meta("new-p1"), recorded(store, "new-p1"))
        store.markSent("new-p1", now = now - 2 * day)
        store.finalize(meta("pend-p1"), recorded(store, "pend-p1"))

        assertEquals(1, store.purgeSent(now))
        assertEquals(listOf("new-p1"), store.listSent().map { it.id })
        assertFalse(store.sentAudio("old-p1").exists())
        assertNotNull(store.pending("pend-p1"))
    }

    @Test fun requeueSentPutsItBackInPending() {
        val store = RecordingStore(tmp.root)
        store.finalize(meta("c-p1"), recorded(store, "c-p1"))
        store.markSent("c-p1", now = 9L)

        assertTrue(store.requeueSent("c-p1"))
        assertEquals(0L, store.pending("c-p1")!!.sentAt)
        assertTrue(store.listSent().isEmpty())
    }

    @Test fun oggRecordingsMoveLikeM4a() {
        val store = RecordingStore(tmp.root)
        val audio = store.newRecordingFile("o-p1", "ogg").apply { writeBytes(ByteArray(4096) { 2 }) }
        store.finalize(meta("o-p1"), audio)
        assertEquals("o-p1.ogg", store.pendingAudio("o-p1").name)
        assertTrue(store.pendingAudio("o-p1").exists())
        store.markSent("o-p1", now = 3L)
        assertEquals("o-p1.ogg", store.sentAudio("o-p1").name)
        assertTrue(store.sentAudio("o-p1").exists())
        assertTrue(store.requeueSent("o-p1"))
        assertTrue(store.pendingAudio("o-p1").exists())
    }

    @Test fun orphansAreListed() {
        val store = RecordingStore(tmp.root)
        recorded(store, "crash-p1")
        assertEquals(listOf("crash-p1.m4a"), store.orphanRecordings().map { it.name })
    }

    @Test fun pendingWithoutAudioIsIgnored() {
        val store = RecordingStore(tmp.root)
        store.finalize(meta("d-p1"), recorded(store, "d-p1"))
        store.pendingAudio("d-p1").delete()
        assertTrue(store.listPending().isEmpty())
    }
}
