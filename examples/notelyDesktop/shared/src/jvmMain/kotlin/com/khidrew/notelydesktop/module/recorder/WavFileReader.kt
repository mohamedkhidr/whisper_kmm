package com.khidrew.notelydesktop.module.recorder

import java.io.File
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem

object WavFileReader {
    private const val TARGET_RATE = 16000

    fun readToFloatArray(file: File): FloatArray {
        val stream = AudioSystem.getAudioInputStream(file)
        val srcFmt = stream.format

        // Normalize to 16-bit signed PCM little-endian at the original rate/channels
        val pcmFmt = AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            srcFmt.sampleRate,
            16,
            srcFmt.channels,
            srcFmt.channels * 2,
            srcFmt.sampleRate,
            false
        )
        val pcmStream = if (srcFmt.encoding == AudioFormat.Encoding.PCM_SIGNED
                && srcFmt.sampleSizeInBits == 16 && !srcFmt.isBigEndian) {
            stream
        } else {
            AudioSystem.getAudioInputStream(pcmFmt, stream)
        }

        val bytes = pcmStream.readAllBytes()
        pcmStream.close()

        val channels   = srcFmt.channels
        val srcRate    = srcFmt.sampleRate.toInt()
        val frameCount = bytes.size / (2 * channels)

        // Interleaved bytes → mono float, averaging channels
        val mono = FloatArray(frameCount) { i ->
            var sum = 0f
            for (ch in 0 until channels) {
                val off = (i * channels + ch) * 2
                val s = ((bytes[off + 1].toInt() shl 8) or (bytes[off].toInt() and 0xFF)).toShort()
                sum += s / 32768f
            }
            sum / channels
        }

        return if (srcRate == TARGET_RATE) mono else resample(mono, srcRate, TARGET_RATE)
    }

    private fun resample(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        val ratio = srcRate.toDouble() / dstRate
        val len   = (input.size / ratio).toInt()
        return FloatArray(len) { i ->
            val pos  = i * ratio
            val idx  = pos.toInt()
            val frac = (pos - idx).toFloat()
            val a    = input.getOrElse(idx) { 0f }
            val b    = input.getOrElse(idx + 1) { 0f }
            a + (b - a) * frac
        }
    }
}
