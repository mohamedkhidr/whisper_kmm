package com.khidrew.notelydesktop.module.transcription

import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WhisperCppBackend private constructor(
    val whisperContext: WhisperContext?
) : ASRBackend {

    override val separator = " "

    fun canTranscribe(): Boolean = whisperContext != null

    override suspend fun transcribe(
        audio: FloatArray,
        language: String,
        initPrompt: String
    ): List<TimestampedWord> = withContext(Dispatchers.Default) {
        val words = mutableListOf<TimestampedWord>()
        try {
            whisperContext?.let { ctx ->
                val text = ctx.streamTranscribeData(
                    language      = language,
                    data          = audio,
                    initialPrompt = initPrompt
                )
                val duration = audio.size / 16000f
                words.addAll(parseTextToWords(text, duration))
            }
        } catch (e: Exception) {
            throw RuntimeException("Transcription failed: ${e.message}", e)
        }
        words
    }

    private fun parseTextToWords(text: String, duration: Float): List<TimestampedWord> {
        val words = text.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val totalChars = words.sumOf { it.length }
        var currentTime = 0f
        return words.map { word ->
            val proportion = word.length.toFloat() / totalChars
            val wordEnd = (currentTime + duration * proportion).coerceAtMost(duration)
            TimestampedWord(currentTime, wordEnd, word).also { currentTime = wordEnd }
        }
    }

    override fun getSegmentEndTimestamps(result: Any): List<Float> = emptyList()

    fun stopTranscription() { whisperContext?.stopTranscription() }

    suspend fun release() { whisperContext?.release() }

    companion object {
        fun createFromFile(filePath: String): WhisperCppBackend {
            val ctx = try {
                WhisperContext.createFromFile(filePath)
            } catch (e: OutOfMemoryError) { null }
              catch (e: Exception)        { null }
            return WhisperCppBackend(ctx)
        }

        fun getSystemInfo(): String = WhisperContext.getSystemInfo()
    }
}
