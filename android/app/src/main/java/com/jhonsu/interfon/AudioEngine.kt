package com.jhonsu.interfon

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder

/** Grabacion PCM16 16k mono y reproduccion de WAV en memoria. */
object AudioEngine {

    const val RATE = 16000

    @Volatile var recording = false
        private set

    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null

    /** Inicia la captura y entrega chunks de ~100ms. False si no hay permiso. */
    @SuppressLint("MissingPermission")
    fun startRecording(onChunk: (ByteArray) -> Unit): Boolean {
        if (recording) return true
        val minBuf = AudioRecord.getMinBufferSize(
            RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(minBuf, RATE * 2) // >= 1s de margen
        val rec = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(RATE)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build())
                .setBufferSizeInBytes(bufSize)
                .build()
        } catch (e: Exception) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        audioRecord = rec
        rec.startRecording()
        recording = true
        recordThread = Thread {
            val buf = ShortArray(1600) // 100 ms
            while (recording) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) onChunk(shortsToPcm(buf, n))
            }
        }.apply { name = "interfon-mic"; start() }
        return true
    }

    fun stopRecording() {
        recording = false
        runCatching { recordThread?.join(800) }
        recordThread = null
        runCatching {
            audioRecord?.let {
                if (it.state == AudioRecord.STATE_INITIALIZED) it.stop()
                it.release()
            }
        }
        audioRecord = null
    }

    /**
     * Reproduce un WAV PCM en memoria y bloquea hasta terminar.
     * voice=true enruta por el canal de comunicacion (auricular/altavoz segun
     * CallAudio); voice=false usa el canal de medios (audios push).
     */
    fun playWav(wav: ByteArray, voice: Boolean = false) {
        runCatching {
            val parsed = parseWav(wav)
            val pcm = parsed.pcm
            if (pcm.isEmpty()) return
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(if (voice) AudioAttributes.USAGE_VOICE_COMMUNICATION
                              else AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(parsed.rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size)
                .build()
            val written = track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            track.play()
            val totalFrames = written / 2
            while (track.playbackHeadPosition < totalFrames &&
                track.state == AudioTrack.STATE_INITIALIZED) {
                Thread.sleep(60)
            }
            track.release()
        }
    }

    /** Envuelve PCM crudo en un WAV (para el walkie). */
    fun pcmToWav(pcm: ByteArray): ByteArray {
        val header = ByteArray(44)
        val byteRate = RATE * 2
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        putIntLE(header, 4, 36 + pcm.size)
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        putIntLE(header, 16, 16)
        putShortLE(header, 20, 1)
        putShortLE(header, 22, 1)
        putIntLE(header, 24, RATE)
        putIntLE(header, 28, byteRate)
        putShortLE(header, 32, 2)
        putShortLE(header, 34, 16)
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        putIntLE(header, 40, pcm.size)
        return header + pcm
    }

    // ---------- helpers ----------

    data class WavInfo(val pcm: ByteArray, val rate: Int)

    private fun parseWav(wav: ByteArray): WavInfo {
        if (wav.size < 44 || String(wav, 0, 4) != "RIFF") return WavInfo(ByteArray(0), RATE)
        var pos = 12
        var rate = RATE
        var pcm: ByteArray = ByteArray(0)
        while (pos + 8 <= wav.size) {
            val id = String(wav, pos, 4)
            val size = readIntLE(wav, pos + 4)
            if (id == "fmt " && pos + 8 + 4 <= wav.size) {
                rate = readIntLE(wav, pos + 12)
            } else if (id == "data") {
                val end = minOf(pos + 8 + size, wav.size)
                pcm = wav.copyOfRange(pos + 8, end)
            }
            pos += 8 + size + (size and 1)
        }
        return WavInfo(pcm, rate)
    }

    private fun shortsToPcm(buf: ShortArray, n: Int): ByteArray {
        val out = ByteArray(n * 2)
        for (i in 0 until n) {
            val v = buf[i].toInt()
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun putIntLE(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
        b[off + 2] = ((v shr 16) and 0xFF).toByte()
        b[off + 3] = ((v shr 24) and 0xFF).toByte()
    }

    private fun putShortLE(b: ByteArray, off: Int, v: Int) {
        b[off] = (v and 0xFF).toByte()
        b[off + 1] = ((v shr 8) and 0xFF).toByte()
    }

    private fun readIntLE(b: ByteArray, off: Int): Int =
        (b[off].toInt() and 0xFF) or
            ((b[off + 1].toInt() and 0xFF) shl 8) or
            ((b[off + 2].toInt() and 0xFF) shl 16) or
            ((b[off + 3].toInt() and 0xFF) shl 24)
}
