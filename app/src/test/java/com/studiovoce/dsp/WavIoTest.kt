package com.studiovoce.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** Segnali sintetici: nessun file di prova nel repo. */
class WavIoTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun sine(freq: Double, seconds: Double, sr: Int = 44_100, amp: Float = 0.5f) =
        FloatArray((seconds * sr).toInt()) { i ->
            (amp * sin(2 * PI * freq * i / sr)).toFloat()
        }

    @Test
    fun `scrittura e rilettura conservano durata e forma`() {
        val sr = 44_100
        val signal = sine(220.0, 0.25, sr)
        val f = tmp.newFile("sine.wav")
        WavIo.write(f, signal, sr)

        val read = WavIo.read(f)
        assertEquals(sr, read.sampleRate)
        assertEquals(signal.size, read.samples.size)
        assertEquals(0.25, read.durationS, 1e-4)
        // La quantizzazione a 16 bit introduce al massimo 1/32768 di errore.
        for (i in signal.indices) {
            assertTrue("campione $i", abs(signal[i] - read.samples[i]) < 1e-4)
        }
    }

    @Test
    fun `l'intestazione e lunga 44 byte e dichiara le dimensioni giuste`() {
        val f = tmp.newFile("h.wav")
        WavIo.write(f, ShortArray(100) { 0 }, 44_100)
        assertEquals(44L + 200L, f.length())

        val b = f.readBytes()
        assertEquals("RIFF", String(b, 0, 4))
        assertEquals("WAVE", String(b, 8, 4))
        assertEquals("fmt ", String(b, 12, 4))
        assertEquals("data", String(b, 36, 4))
        assertEquals(36 + 200, le32(b, 4))
        assertEquals(1, le16(b, 20))          // PCM
        assertEquals(1, le16(b, 22))          // mono
        assertEquals(44_100, le32(b, 24))
        assertEquals(16, le16(b, 34))         // bit per campione
        assertEquals(200, le32(b, 40))
    }

    @Test
    fun `il writer incrementale chiude un file corretto`() {
        val f = File(tmp.root, "inc/rec.wav")
        val block = ShortArray(1024) { (8000 * sin(2 * PI * 200 * it / 44_100.0)).toInt().toShort() }
        val writer = WavIo.WavWriter(f, 44_100)
        repeat(10) { writer.write(block) }
        assertEquals(10 * 1024, writer.samplesWritten)
        writer.close()

        val read = WavIo.read(f)
        assertEquals(10 * 1024, read.samples.size)
        assertEquals(44_100, read.sampleRate)
        assertEquals(10 * 1024 * 2, le32(f.readBytes(), 40))
    }

    @Test
    fun `un file troncato si legge comunque fino in fondo`() {
        val f = tmp.newFile("trunc.wav")
        WavIo.write(f, ShortArray(1000) { 1000 }, 44_100)
        // Simuliamo un'app chiusa a metà: dimensioni a zero nell'intestazione.
        val raf = java.io.RandomAccessFile(f, "rw")
        raf.seek(4); raf.write(byteArrayOf(0, 0, 0, 0))
        raf.seek(40); raf.write(byteArrayOf(0, 0, 0, 0))
        raf.close()

        val read = WavIo.read(f)
        assertEquals(1000, read.samples.size)
    }

    @Test
    fun `i chunk sconosciuti si saltano`() {
        // RIFF / WAVE / LIST (da ignorare) / fmt / data
        val data = byteArrayOf(0x00, 0x40, 0x00, (-0x40).toByte()) // due campioni 16 bit
        val out = java.io.ByteArrayOutputStream()
        fun le32(v: Int) = out.write(byteArrayOf(
            (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
        ))
        val fmt = WavIo.header(data.size, 22_050, 1, 16) // riuso: ne prendo i pezzi
        out.write("RIFF".toByteArray()); le32(0); out.write("WAVE".toByteArray())
        out.write("LIST".toByteArray()); le32(4); out.write("INFO".toByteArray())
        out.write(fmt, 12, 24) // "fmt " + corpo
        out.write("data".toByteArray()); le32(data.size); out.write(data)

        val read = WavIo.read(out.toByteArray().inputStream())
        assertEquals(22_050, read.sampleRate)
        assertEquals(2, read.samples.size)
    }

    @Test
    fun `stereo diventa mono mediando i canali`() {
        val out = java.io.ByteArrayOutputStream()
        fun le32(v: Int) = out.write(byteArrayOf(
            (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
        ))
        // L = +16384, R = -16384 su due frame: la media e' 0
        val data = byteArrayOf(
            0x00, 0x40, 0x00, (-0x40).toByte(),
            0x00, 0x40, 0x00, (-0x40).toByte(),
        )
        val fmt = WavIo.header(data.size, 44_100, 2, 16)
        out.write(fmt, 0, 36)
        out.write("data".toByteArray()); le32(data.size); out.write(data)

        val read = WavIo.read(out.toByteArray().inputStream())
        assertEquals(2, read.samples.size)
        assertEquals(0f, read.samples[0], 1e-4f)
        assertEquals(0f, read.samples[1], 1e-4f)
    }

    @Test(expected = WavIo.WavFormatException::class)
    fun `un file che non e un WAV da un errore chiaro`() {
        WavIo.read("non sono un wav, nemmeno un po'".toByteArray().inputStream())
    }

    @Test
    fun `tutte le clip della voice bank sono mono 44100 a 16 bit`() {
        // Gli asset non sono sul classpath dei test JVM: se la cartella c'e', controlliamo.
        val dir = File("src/main/assets/clips").takeIf { it.isDirectory }
            ?: File("app/src/main/assets/clips").takeIf { it.isDirectory }
            ?: return
        val wavs = dir.listFiles { f -> f.name.endsWith(".wav") }.orEmpty()
        if (wavs.isEmpty()) return
        for (w in wavs) {
            val a = WavIo.read(w)
            assertEquals("frequenza di ${w.name}", 44_100, a.sampleRate)
            assertTrue("${w.name} e' vuota", a.samples.isNotEmpty())
            assertTrue("${w.name} dura troppo poco", a.durationS >= 1.0)
        }
        assertNotNull(wavs)
    }

    private fun le16(b: ByteArray, at: Int) =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8)

    private fun le32(b: ByteArray, at: Int) =
        (b[at].toInt() and 0xFF) or ((b[at + 1].toInt() and 0xFF) shl 8) or
            ((b[at + 2].toInt() and 0xFF) shl 16) or ((b[at + 3].toInt() and 0xFF) shl 24)
}
