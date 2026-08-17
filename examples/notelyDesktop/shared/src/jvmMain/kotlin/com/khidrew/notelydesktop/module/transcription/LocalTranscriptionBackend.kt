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

    override suspend fun feed(audio: FloatArray) {
        processor.insertAudioChunk(audio)
    }

    override fun listenForTranscriptionResults(): StateFlow<String> = processor.transcriptionResult

    override suspend fun finishTranscription() { processor.finish() }

    override suspend fun startProcessingLoop() { processor.start() }

    override suspend fun release() { whisper?.release() }

    // Splits audio into 25-second segments — whisper's safe context window.
    // Each segment uses the previous result as initialPrompt for cross-boundary continuity.
    suspend fun transcribeFileChunked(audio: FloatArray, onSegment: (String) -> Unit) {
        val segmentSamples = 25 * LocalASRProcessor.SAMPLING_RATE
        var prompt = ""
        var offset = 0
        while (offset < audio.size) {
            val end = minOf(offset + segmentSamples, audio.size)
            val words = whisper?.transcribe(
                audio.copyOfRange(offset, end),
                transcriptionLanguage,
                prompt
            ) ?: emptyList()
            val text = words.joinToString(" ") { it.text }.trim()
            if (text.isNotBlank()) {
                onSegment(text)
                prompt = text.takeLast(200)
            }
            offset = end
        }
    }
}

