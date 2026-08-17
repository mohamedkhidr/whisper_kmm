package com.khidrew.notelydesktop.module.transcription

import kotlinx.coroutines.flow.StateFlow

interface TranscriptionBackend {
    suspend fun init(onSuccess: () -> Unit)
    fun reset()
    fun isReady(): Boolean
    suspend fun feed(audio: FloatArray)
    fun listenForTranscriptionResults(): StateFlow<String>
    suspend fun finishTranscription()
    suspend fun release()
    suspend fun startProcessingLoop() {}
}

sealed class TranscriptionError : Exception() {
    class ProcessingError           : TranscriptionError()
    class ModelInitializationError  : TranscriptionError()
    class EmptyTokenError           : TranscriptionError()
    class TranscriptionNotReadyError: TranscriptionError()
}

fun TranscriptionError.toMessage(): String = when (this) {
    is TranscriptionError.ModelInitializationError   -> "Failed to load the Whisper model"
    is TranscriptionError.ProcessingError            -> "Transcription error"
    is TranscriptionError.EmptyTokenError            -> "Empty transcription result"
    is TranscriptionError.TranscriptionNotReadyError -> "Transcription engine not ready"
}
