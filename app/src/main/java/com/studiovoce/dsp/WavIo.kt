package com.studiovoce.dsp

import java.io.Closeable
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Lettura e scrittura di WAV PCM, in Kotlin puro (nessuna API Android), cosi' si puo'
 * testare sulla JVM con segnali sintetici. SPEC §3: PCM 16 bit mono 44.100 Hz.
 *
 * In lettura siamo tolleranti: si accettano 8/16/24/32 bit interi e float 32 bit,
 * uno o piu' canali (mischiati in mono), e si saltano i chunk che non servono.
 */
object WavIo {

    const val DEFAULT_SAMPLE_RATE = 44_100

    private const val FORMAT_PCM = 1
    private const val FORMAT_FLOAT = 3
    private const val FORMAT_EXTENSIBLE = 0xFFFE

    class WavFormatException(message: String) : IOException(message)

    /** Audio mono con campioni in [-1, 1]. */
    class Audio(val samples: FloatArray, val sampleRate: Int) {
        val durationS: Double
            get() = if (sampleRate > 0) samples.size.toDouble() / sampleRate else 0.0
    }

    fun read(file: File): Audio = file.inputStream().buffered().use { read(it) }

    fun read(input: InputStream): Audio {
        val all = input.readBytes()
        if (all.size < 12) throw WavFormatException("File troppo corto per essere un WAV")
        if (all.ascii(0, 4) != "RIFF" || all.ascii(8, 4) != "WAVE") {
            throw WavFormatException("Non e' un file WAV (manca RIFF/WAVE)")
        }

        var pos = 12
        var channels = 1
        var sampleRate = DEFAULT_SAMPLE_RATE
        var bits = 16
        var format = FORMAT_PCM
        var fmtSeen = false
        var dataOffset = -1
        var dataLength = 0

        while (pos + 8 <= all.size) {
            val id = all.ascii(pos, 4)
            val size = all.le32(pos + 4)
            val body = pos + 8
            if (size < 0) throw WavFormatException("Chunk con dimensione non valida: " + id)
            if (id == "fmt ") {
                if (body + 16 > all.size) throw WavFormatException("Chunk fmt incompleto")
                format = all.le16(body)
                channels = all.le16(body + 2).coerceAtLeast(1)
                sampleRate = all.le32(body + 4)
                bits = all.le16(body + 14)
                if (format == FORMAT_EXTENSIBLE && body + 26 <= all.size) {
                    format = all.le16(body + 24)
                }
                fmtSeen = true
            } else if (id == "data") {
                dataOffset = body
                // Alcuni encoder scrivono size = 0 oppure troncano il file.
                dataLength = if (size == 0 || body + size > all.size) all.size - body else size
            }
            // I chunk sono allineati a 2 byte.
            pos = body + size + (size and 1)
        }

        if (!fmtSeen) throw WavFormatException("Chunk fmt mancante")
        if (dataOffset < 0) throw WavFormatException("Chunk data mancante")
        if (sampleRate <= 0) throw WavFormatException("Frequenza di campionamento non valida")

        return Audio(decodeToMono(all, dataOffset, dataLength, format, bits, channels), sampleRate)
    }

    private fun decodeToMono(
        b: ByteArray,
        offset: Int,
        length: Int,
        format: Int,
        bits: Int,
        channels: Int,
    ): FloatArray {
        val bytesPerSample = when {
            format == FORMAT_FLOAT && bits == 32 -> 4
            format == FORMAT_PCM && bits == 8 -> 1
            format == FORMAT_PCM && bits == 16 -> 2
            format == FORMAT_PCM && bits == 24 -> 3
            format == FORMAT_PCM && bits == 32 -> 4
            else -> throw WavFormatException("Formato non supportato: codice $format, $bits bit")
        }
        val frameBytes = bytesPerSample * channels
        val frames = if (frameBytes > 0) length / frameBytes else 0
        val out = FloatArray(frames)
        for (f in 0 until frames) {
            var sum = 0f
            val base = offset + f * frameBytes
            for (c in 0 until channels) {
                sum += sampleAt(b, base + c * bytesPerSample, format, bits)
            }
            out[f] = sum / channels
        }
        return out
    }

    private fun sampleAt(b: ByteArray, i: Int, format: Int, bits: Int): Float = when {
        format == FORMAT_FLOAT -> Float.fromBits(b.le32(i))
        bits == 8 -> ((b[i].toInt() and 0xFF) - 128) / 128f
        bits == 16 -> b.le16Signed(i) / 32768f
        bits == 24 -> {
            val v = (b[i].toInt() and 0xFF) or
                ((b[i + 1].toInt() and 0xFF) shl 8) or
                (b[i + 2].toInt() shl 16) // il byte alto porta il segno
            v / 8_388_608f
        }
        else -> b.le32(i) / 2_147_483_648f
    }

    /** Scrive un WAV PCM 16 bit mono completo. */
    fun write(file: File, samples: ShortArray, sampleRate: Int = DEFAULT_SAMPLE_RATE) {
        file.parentFile?.mkdirs()
        DataOutputStream(file.outputStream().buffered()).use { out ->
            out.write(header(samples.size * 2, sampleRate, channels = 1, bits = 16))
            out.write(toLeBytes(samples, samples.size))
        }
    }

    /** Comodo per i test: float in [-1, 1] verso 16 bit. */
    fun write(file: File, samples: FloatArray, sampleRate: Int = DEFAULT_SAMPLE_RATE) =
        write(file, ShortArray(samples.size) { toPcm16(samples[it]) }, sampleRate)

    fun toPcm16(v: Float): Short =
        (v.coerceIn(-1f, 1f) * 32767f).toInt().coerceIn(-32768, 32767).toShort()

    private fun toLeBytes(samples: ShortArray, count: Int): ByteArray {
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            val v = samples[i].toInt()
            bytes[i * 2] = (v and 0xFF).toByte()
            bytes[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    /**
     * Intestazione WAV canonica di 44 byte. Con [dataBytes] a 0 serve da segnaposto:
     * [WavWriter] la riscrive alla chiusura con le dimensioni vere.
     */
    fun header(dataBytes: Int, sampleRate: Int, channels: Int, bits: Int): ByteArray {
        val byteRate = sampleRate * channels * bits / 8
        val out = ByteArrayOutputStream(44)
        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun le32(v: Int) {
            out.write(v and 0xFF)
            out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF)
            out.write((v shr 24) and 0xFF)
        }
        fun le16(v: Int) {
            out.write(v and 0xFF)
            out.write((v shr 8) and 0xFF)
        }

        ascii("RIFF"); le32(36 + dataBytes); ascii("WAVE")
        ascii("fmt "); le32(16); le16(FORMAT_PCM); le16(channels)
        le32(sampleRate); le32(byteRate); le16(channels * bits / 8); le16(bits)
        ascii("data"); le32(dataBytes)
        return out.toByteArray()
    }

    /**
     * Scrittore incrementale: il registratore aggiunge blocchi mentre registra e alla
     * chiusura si correggono le due dimensioni nell'intestazione. Se l'app si chiude a
     * meta', il file resta leggibile (in lettura ignoriamo le dimensioni sbagliate).
     */
    class WavWriter(
        file: File,
        private val sampleRate: Int = DEFAULT_SAMPLE_RATE,
        private val channels: Int = 1,
    ) : Closeable {
        private val raf: RandomAccessFile
        private var dataBytes = 0

        init {
            file.parentFile?.mkdirs()
            raf = RandomAccessFile(file, "rw")
            raf.setLength(0)
            raf.write(header(0, sampleRate, channels, bits = 16))
        }

        /** Aggiunge [count] campioni dall'inizio di [buffer]. */
        fun write(buffer: ShortArray, count: Int = buffer.size) {
            val bytes = toLeBytes(buffer, count)
            raf.write(bytes)
            dataBytes += bytes.size
        }

        val samplesWritten: Int get() = dataBytes / 2 / channels
        val durationS: Double get() = samplesWritten.toDouble() / sampleRate

        override fun close() {
            try {
                raf.seek(4)
                raf.write(le32Bytes(36 + dataBytes))
                raf.seek(40)
                raf.write(le32Bytes(dataBytes))
            } finally {
                raf.close()
            }
        }

        private fun le32Bytes(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(),
            ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(),
            ((v shr 24) and 0xFF).toByte(),
        )
    }

    // --- piccoli aiuti di lettura binaria ---

    private fun ByteArray.ascii(at: Int, len: Int): String =
        if (at + len > size) "" else String(this, at, len, Charsets.US_ASCII)

    private fun ByteArray.le16(at: Int): Int =
        (this[at].toInt() and 0xFF) or ((this[at + 1].toInt() and 0xFF) shl 8)

    private fun ByteArray.le16Signed(at: Int): Int = le16(at).toShort().toInt()

    private fun ByteArray.le32(at: Int): Int =
        (this[at].toInt() and 0xFF) or
            ((this[at + 1].toInt() and 0xFF) shl 8) or
            ((this[at + 2].toInt() and 0xFF) shl 16) or
            ((this[at + 3].toInt() and 0xFF) shl 24)
}
