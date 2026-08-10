package com.khidrew.notelydesktop.module.transcription

interface ASRBackend {
    val separator: String

    suspend fun transcribe(
        audio: FloatArray,
        language: String = "en",
        initPrompt: String = ""
    ): List<TimestampedWord>

    fun getSegmentEndTimestamps(result: Any): List<Float>
}
