package com.khidrew.notelydesktop.module.transcription

import kotlinx.coroutines.flow.StateFlow

class LocalTranscriptionBackend(
    private val modelFilePath: String,
    private val transcriptionLanguage: String = "en",
    private val onError: (Throwable) -> Unit,
    private val onFinish: () -> Unit,
) : TranscriptionBackend {

    private var whisper: WhisperCppBackend? = null
    private lateinit var processor: LocalASRProcessor

    override suspend fun init(onSuccess: () -> Unit) {
        whisper = WhisperCppBackend.createFromFile(filePath = modelFilePath)
        if (whisper?.canTranscribe() == true) {
            processor = LocalASRProcessor(
                asr                    = whisper!!,
                language               = transcriptionLanguage,
                bufferTrimmingStrategy = LocalASRProcessor.BufferTrimmingStrategy.SEGMENT,
                bufferTrimmingSec      = 15f,
                onError                = onError,
                onFinish               = onFinish
            )
            onSuccess()
        } else {
            onError(TranscriptionError.ModelInitializationError())
        }
    }

    override fun reset() { processor.init() }

    override fun isReady(): Boolean = whisper?.canTranscribe() == true

    override suspend fun feed(audio: ShortArray) {
        processor.insertAudioChunk(audio.toAudioFloatArray())
    }

    override fun listenForTranscriptionResults(): StateFlow<String> = processor.transcriptionResult

    override suspend fun finishTranscription() { processor.finish() }

    override suspend fun startProcessingLoop() { processor.start() }

    override suspend fun release() { whisper?.release() }

    suspend fun transcribeOnce(audio: FloatArray): String {
        val words = whisper?.transcribe(audio, transcriptionLanguage, "") ?: return ""
        return words.joinToString(" ") { it.text }
    }
}

fun ShortArray.toAudioFloatArray(): FloatArray = FloatArray(size) { i -> this[i] / 32768.0f }
