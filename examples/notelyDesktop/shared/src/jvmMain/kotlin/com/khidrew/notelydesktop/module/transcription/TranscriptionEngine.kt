package com.khidrew.notelydesktop.module.transcription

import com.khidrew.notelydesktop.module.recorder.JvmAudioRecorder
import com.khidrew.notelydesktop.module.recorder.chunked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

enum class TranscriptionState {
    INITIALIZING, IDLE, LISTENING, PROCESSING, RESULT
}

class TranscriptionEngine(
    private val modelPath: String,
    private val language: String,
    private val scope: CoroutineScope,
    private val onError: (Throwable) -> Unit
) {
    private val _state = MutableStateFlow(TranscriptionState.INITIALIZING)
    val state = _state.asStateFlow()

    private var transcriber: TranscriptionBackend? = null
    private var audioSource: JvmAudioRecorder?     = null
    private var audioStreamJob: Job?               = null
    private var processingJob: Job?                = null

    suspend fun init(onSuccess: () -> Unit) {
        transcriber = LocalTranscriptionBackend(
            modelFilePath         = modelPath,
            transcriptionLanguage = language,
            onError = { e ->
                _state.value = TranscriptionState.IDLE
                onError(e)
            },
            onFinish = { _state.value = TranscriptionState.RESULT }
        )
        audioSource = JvmAudioRecorder()
        transcriber?.init(onSuccess = {
            onSuccess()
            _state.value = TranscriptionState.IDLE
        })
    }

    fun isReady(): Boolean = transcriber?.isReady() == true

    fun start(onError: (Throwable) -> Unit) {
        _state.value = TranscriptionState.LISTENING
        processingJob = scope.launch { transcriber?.startProcessingLoop() }
        audioStreamJob = scope.launch {
            audioSource?.audioStream()
                ?.chunked(100)
                ?.catch { e ->
                    e.printStackTrace()
                    _state.value = TranscriptionState.IDLE
                    onError(e)
                }
                ?.collect { transcriber?.feed(it) }
        }
    }

    fun listen() = transcriber?.listenForTranscriptionResults()

    fun stop() {
        scope.launch {
            _state.value = TranscriptionState.PROCESSING
            audioStreamJob?.cancel(); audioStreamJob = null
            transcriber?.finishTranscription()
            processingJob?.cancel(); processingJob = null
        }
    }

    fun release() {
        scope.launch {
            transcriber?.release(); transcriber = null
            audioSource = null
            audioStreamJob?.cancel(); audioStreamJob = null
            processingJob?.cancel(); processingJob = null
        }
    }

    fun reset() {
        scope.launch { transcriber?.reset(); _state.value = TranscriptionState.IDLE }
    }
}
