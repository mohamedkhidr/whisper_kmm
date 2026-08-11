package com.khidrew.notelydesktop.module.transcription

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LocalASRProcessor(
    val asr: WhisperCppBackend,
    val language: String,
    private val minChunkSize: Float = 1.0f,
    private val bufferTrimmingStrategy: BufferTrimmingStrategy = BufferTrimmingStrategy.SEGMENT,
    private val bufferTrimmingSec: Float = 15f,
    private val onError: (Throwable) -> Unit,
    private val onFinish: () -> Unit
) {
    private val _transcriptionResult = MutableStateFlow("")
    val transcriptionResult = _transcriptionResult.asStateFlow()

    companion object {
        const val SAMPLING_RATE = 16000
    }

    enum class BufferTrimmingStrategy { SEGMENT, SENTENCE }

    var audioBuffer = mutableListOf<Float>()
    private val transcriptBuffer = HypothesisBuffer()
    private var bufferTimeOffset = 0f
    private val committed = mutableListOf<TimestampedWord>()
    private val mutex = Mutex()
    private var _isTranscribing = false

    init { init() }

    fun init() {
        _isTranscribing = false
        audioBuffer.clear()
        transcriptBuffer.reset()
        bufferTimeOffset = 0f
        transcriptBuffer.lastCommittedTime = bufferTimeOffset
        committed.clear()
    }

    suspend fun start() {
        _isTranscribing = true
        var lastProcessedDuration = 0f
        while (true) {
            if (!_isTranscribing) return
            val currentDuration = getAudioBufferDuration()
            val newAudio = currentDuration - lastProcessedDuration
            if (newAudio >= minChunkSize) {
                val result = processIter()
                lastProcessedDuration = currentDuration
                if (!result.isEmpty) {
                    _transcriptionResult.value = result.text
                }
            } else {
                delay(500)
            }
        }
    }

    suspend fun insertAudioChunk(audio: FloatArray) {
        mutex.withLock { audioBuffer.addAll(audio.toList()) }
    }

    private fun prompt(): Pair<String, String> {
        var k = maxOf(0, committed.size - 1)
        while (k > 0 && committed[k - 1].end > bufferTimeOffset) k--
        val p = committed.subList(0, k).map { it.text }.toMutableList()
        val prompt = mutableListOf<String>()
        var length = 0
        while (p.isNotEmpty() && length < 200) {
            val x = p.removeAt(p.lastIndex); length += x.length + 1; prompt.add(x)
        }
        val nonPrompt = committed.subList(k, committed.size).joinToString(asr.separator) { it.text }
        return Pair(prompt.reversed().joinToString(asr.separator), nonPrompt)
    }

    suspend fun processIter(): TranscriptionResult {
        val (promptText, _) = prompt()
        val audioArray: FloatArray
        val audioDuration: Float
        mutex.withLock {
            audioArray    = audioBuffer.toFloatArray()
            audioDuration = audioArray.size.toFloat() / SAMPLING_RATE
        }
        println("============= iteration ============ ${audioArray.size}")
        val words = asr.transcribe(audioArray, language, promptText)
        transcriptBuffer.insert(words, bufferTimeOffset)
        val flushed = transcriptBuffer.flush()
        committed.addAll(flushed)
        val result = toFlush(flushed)

        if (audioDuration > bufferTrimmingSec) {
            when (bufferTrimmingStrategy) {
                BufferTrimmingStrategy.SEGMENT -> {
                    if (committed.isNotEmpty()) {
                        val trimPoint = findSegmentTrimPoint(committed.last().end)
                        if (trimPoint != null) chunkAt(trimPoint)
                    }
                }
                BufferTrimmingStrategy.SENTENCE -> {
                    if (committed.size >= 2) chunkAt(committed[committed.size - 2].end)
                }
            }
        }
        if (audioDuration > 30f && committed.isNotEmpty()) {
            val trimPoint = committed.last().end - 10f
            if (trimPoint > bufferTimeOffset) chunkAt(trimPoint)
        }

        return result
    }

    private fun findSegmentTrimPoint(maxTime: Float): Float? {
        for (word in committed.reversed()) {
            if (word.end <= maxTime && word.end > bufferTimeOffset) return word.end
        }
        return null
    }

    private suspend fun chunkAt(time: Float) {
        mutex.withLock {
            transcriptBuffer.popCommitted(time)
            val cutSamples = ((time - bufferTimeOffset) * SAMPLING_RATE).toInt()
            if (cutSamples > 0 && cutSamples < audioBuffer.size) {
                audioBuffer = audioBuffer.drop(cutSamples).toMutableList()
                bufferTimeOffset = time
            }
        }
    }

    suspend fun finish() {
        _isTranscribing = false
        val result = processIter()
        val incompleteWords = transcriptBuffer.complete()
        val inCompleteResult = toFlush(incompleteWords)
        _transcriptionResult.value = result.text
        if (!inCompleteResult.isEmpty) _transcriptionResult.value = inCompleteResult.text
        onFinish()
    }

    private fun toFlush(words: List<TimestampedWord>): TranscriptionResult {
        if (words.isEmpty()) return TranscriptionResult(null, null, "")
        return TranscriptionResult(
            startTime = words.first().start,
            endTime   = words.last().end,
            text      = words.joinToString(asr.separator) { it.text }
        )
    }

    fun getAudioBufferDuration(): Float = audioBuffer.size.toFloat() / SAMPLING_RATE
}
