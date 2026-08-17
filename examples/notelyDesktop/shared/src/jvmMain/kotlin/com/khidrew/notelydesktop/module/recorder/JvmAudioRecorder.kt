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

    companion object {
        const val TARGET_RATE = 16000

        // Tried in order — first supported format wins.
        // macOS typically doesn't support 16kHz natively; falls back to 44100/48000.
        private val CANDIDATE_FORMATS = listOf(
            AudioFormat(16000f, 16, 1, true, false),
            AudioFormat(44100f, 16, 1, true, false),
            AudioFormat(44100f, 16, 2, true, false),
            AudioFormat(44100f, 16, 1, true, true),  // big-endian (macOS CoreAudio default)
            AudioFormat(44100f, 16, 2, true, true),
            AudioFormat(48000f, 16, 1, true, false),
            AudioFormat(48000f, 16, 2, true, false),
            AudioFormat(48000f, 16, 1, true, true),
            AudioFormat(48000f, 16, 2, true, true),
        )
    }

    override fun audioStream(): Flow<ShortArray> = flow {
        val (line, fmt) = openBestLine()
        val captureRate     = fmt.sampleRate.toInt()
        val captureChannels = fmt.channels
        val isBigEndian     = fmt.isBigEndian

        // Read enough bytes to capture ~100ms after resampling, aligned to frame boundary
        val targetBytes   = 3200 // 1600 samples × 2 bytes at 16kHz = 100ms
        val frameBytes    = captureChannels * 2
        val rawBytesApprox = (targetBytes.toDouble() * captureChannels * captureRate / TARGET_RATE).toInt()
        val readBytes     = ((rawBytesApprox + frameBytes - 1) / frameBytes) * frameBytes

        val bytes = ByteArray(readBytes)
        try {
            line.start()
            while (true) {
                val read = line.read(bytes, 0, bytes.size)
                if (read <= 0) throw AudioSourceException.AudioReadingError()

                // Decode interleaved bytes → mono ShortArray (average channels, handle endianness)
                val frameCount = read / frameBytes
                val mono = ShortArray(frameCount) { i ->
                    var sum = 0L
                    for (ch in 0 until captureChannels) {
                        val off = (i * captureChannels + ch) * 2
                        sum += if (isBigEndian)
                            ((bytes[off].toInt() shl 8) or (bytes[off + 1].toInt() and 0xFF)).toShort()
                        else
                            ((bytes[off + 1].toInt() shl 8) or (bytes[off].toInt() and 0xFF)).toShort()
                    }
                    (sum / captureChannels).toShort()
                }

                emit(if (captureRate == TARGET_RATE) mono else resample(mono, captureRate, TARGET_RATE))
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

    private fun openBestLine(): Pair<TargetDataLine, AudioFormat> {
        for (fmt in CANDIDATE_FORMATS) {
            val info = DataLine.Info(TargetDataLine::class.java, fmt)
            if (!AudioSystem.isLineSupported(info)) continue
            try {
                val line = AudioSystem.getLine(info) as TargetDataLine
                line.open(fmt)
                println("[JvmAudioRecorder] opened: rate=${fmt.sampleRate} ch=${fmt.channels} bigEndian=${fmt.isBigEndian}")
                return Pair(line, fmt)
            } catch (_: Exception) {}
        }
        throw AudioSourceException.AudioRecordingError()
    }

    private fun resample(input: ShortArray, srcRate: Int, dstRate: Int): ShortArray {
        val ratio = srcRate.toDouble() / dstRate
        val len   = (input.size / ratio).toInt()
        return ShortArray(len) { i ->
            val pos  = i * ratio
            val idx  = pos.toInt()
            val frac = (pos - idx).toFloat()
            val a    = input.getOrElse(idx)     { 0 }.toFloat()
            val b    = input.getOrElse(idx + 1) { 0 }.toFloat()
            (a + (b - a) * frac).toInt().toShort()
        }
    }
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
