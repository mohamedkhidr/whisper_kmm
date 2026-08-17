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

        // Tried in order — first format the OS actually supports wins.
        // macOS hardware runs at 44100/48000 Hz; requesting 16kHz may silently give wrong data.
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

    // Emit FloatArray at 16kHz — same as iOS AVAudioConverter → floatChannelData output.
    override fun audioStream(): Flow<FloatArray> = flow {
        val (line, fmt) = openBestLine()
        val captureRate     = fmt.sampleRate.toInt()
        val captureChannels = fmt.channels
        val isBigEndian     = fmt.isBigEndian

        // Size the read buffer to capture ~100ms at the native rate, frame-aligned
        val frameBytes      = captureChannels * 2
        val targetFrames    = TARGET_RATE / 10           // 100ms = 1600 frames at 16kHz
        val nativeFrames    = (targetFrames.toDouble() * captureRate / TARGET_RATE).toInt()
        val readBytes       = nativeFrames * frameBytes

        val bytes = ByteArray(readBytes)
        try {
            line.start()
            while (true) {
                val read = line.read(bytes, 0, bytes.size)
                if (read <= 0) throw AudioSourceException.AudioReadingError()

                // Decode interleaved 16-bit PCM → mono float [-1, 1], handling endianness
                val frameCount = read / frameBytes
                val mono = FloatArray(frameCount) { i ->
                    var sum = 0L
                    for (ch in 0 until captureChannels) {
                        val off = (i * captureChannels + ch) * 2
                        sum += if (isBigEndian)
                            ((bytes[off].toInt() shl 8) or (bytes[off + 1].toInt() and 0xFF)).toShort()
                        else
                            ((bytes[off + 1].toInt() shl 8) or (bytes[off].toInt() and 0xFF)).toShort()
                    }
                    (sum / captureChannels).toFloat() / 32768f
                }

                // Resample to TARGET_RATE if the hardware runs at a different rate
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

    // Linear interpolation resampler — same role as AVAudioConverter on iOS
    private fun resample(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        val ratio = srcRate.toDouble() / dstRate
        val len   = (input.size / ratio).toInt()
        return FloatArray(len) { i ->
            val pos  = i * ratio
            val idx  = pos.toInt()
            val frac = (pos - idx).toFloat()
            val a    = input.getOrElse(idx)     { 0f }
            val b    = input.getOrElse(idx + 1) { 0f }
            a + (b - a) * frac
        }
    }
}

fun Flow<FloatArray>.chunked(chunkSize: Int): Flow<FloatArray> = flow {
    val accumulator = FloatArray(chunkSize)
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
