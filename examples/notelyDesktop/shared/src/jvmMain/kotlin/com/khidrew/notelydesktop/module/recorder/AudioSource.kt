package com.khidrew.notelydesktop.module.recorder

import kotlinx.coroutines.flow.Flow

interface AudioSource {
    fun audioStream(): Flow<FloatArray>
}

sealed class AudioSourceException : Exception() {
    class AudioRecordingError : AudioSourceException()
    class AudioReadingError   : AudioSourceException()
}

fun AudioSourceException.toMessage(): String = when (this) {
    is AudioSourceException.AudioRecordingError -> "Failed to open microphone"
    is AudioSourceException.AudioReadingError   -> "Error reading audio data"
}
