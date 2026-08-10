package com.khidrew.notelydesktop.module.recorder

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine

class JvmAudioRecorder : AudioSource {

    private val format = AudioFormat(
        16000f, // sample rate
        16,     // sample size in bits (PCM_SIGNED)
        1,      // channels (mono)
        true,   // signed
        false   // little-endian
    )

    // ~100 ms of audio: 1600 samples × 2 bytes = 3200 bytes
    private val bufferBytes = 3200

    override fun audioStream(): Flow<ShortArray> = flow {
        val info = DataLine.Info(TargetDataLine::class.java, format)
        if (!AudioSystem.isLineSupported(info)) {
            throw AudioSourceException.AudioRecordingError()
        }

        val line = AudioSystem.getLine(info) as TargetDataLine
        try {
            line.open(format)
            line.start()

            val bytes  = ByteArray(bufferBytes)
            val shorts = ShortArray(bufferBytes / 2)

            while (true) {
                val read = line.read(bytes, 0, bytes.size)
                if (read <= 0) throw AudioSourceException.AudioReadingError()
                val count = read / 2
                for (i in 0 until count) {
                    val lo = bytes[i * 2].toInt() and 0xFF
                    val hi = bytes[i * 2 + 1].toInt() and 0xFF
                    shorts[i] = ((hi shl 8) or lo).toShort()
                }
                emit(shorts.copyOf(count))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AudioSourceException) {
            throw e
        } catch (e: Exception) {
            throw AudioSourceException.AudioRecordingError()
        } finally {
            line.stop()
            line.close()
        }
    }.flowOn(Dispatchers.IO)
}

fun Flow<ShortArray>.chunked(chunkSize: Int): Flow<ShortArray> = flow {
    val accumulator = ShortArray(chunkSize)
    var filled = 0

    collect { buffer ->
        var offset = 0
        while (offset < buffer.size) {
            val toCopy = minOf(buffer.size - offset, chunkSize - filled)
            buffer.copyInto(accumulator, filled, offset, offset + toCopy)
            filled += toCopy
            offset += toCopy
            if (filled == chunkSize) {
                emit(accumulator.copyOf())
                filled = 0
            }
        }
    }
    if (filled > 0) emit(accumulator.copyOf(filled))
}
