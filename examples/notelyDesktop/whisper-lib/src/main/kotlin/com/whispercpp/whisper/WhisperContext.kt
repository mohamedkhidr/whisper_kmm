package com.whispercpp.whisper

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.Executors

class WhisperContext private constructor(private var ptr: Long) : Closeable {

    // Serializes all whisper.cpp calls — the library is not thread-safe.
    private val singleThread = Executors.newSingleThreadExecutor()
    private val dispatcher   = singleThread.asCoroutineDispatcher()

    val isReleased: Boolean get() = ptr == 0L

    suspend fun streamTranscribeData(
        language: String = "en",
        data: FloatArray,
        initialPrompt: String = ""
    ): String = withContext(dispatcher) {
        check(!isReleased) { "WhisperContext already released" }
        WhisperLib.fullTranscribe(ptr, defaultNumThreads(), data, language, initialPrompt)
        val count = WhisperLib.getTextSegmentCount(ptr)
        buildString { for (i in 0 until count) append(WhisperLib.getTextSegment(ptr, i)) }
    }

    fun stopTranscription() {
        // no-op: cancellation is handled at the coroutine level
    }

    fun getTranscriptionSegments(audioData: FloatArray, numThreads: Int = defaultNumThreads()): List<TranscriptionSegment> {
        check(!isReleased) { "WhisperContext already released" }
        WhisperLib.fullTranscribe(ptr, numThreads, audioData, "en", "")
        val count = WhisperLib.getTextSegmentCount(ptr)
        return List(count) { i ->
            TranscriptionSegment(
                text    = WhisperLib.getTextSegment(ptr, i),
                startMs = WhisperLib.getTextSegmentT0(ptr, i) * 10,
                endMs   = WhisperLib.getTextSegmentT1(ptr, i) * 10
            )
        }
    }

    fun benchMemcpy(numThreads: Int = defaultNumThreads()): String =
        WhisperLib.benchMemcpy(numThreads)

    fun benchGgmlMulMat(numThreads: Int = defaultNumThreads()): String =
        WhisperLib.benchGgmlMulMat(numThreads)

    suspend fun release() = withContext(dispatcher) {
        if (!isReleased) {
            WhisperLib.freeContext(ptr)
            ptr = 0L
        }
        singleThread.shutdown()
    }

    override fun close() = runBlocking { release() }

    companion object {
        fun createFromFile(modelPath: String): WhisperContext {
            val ptr = WhisperLib.initContext(modelPath)
            check(ptr != 0L) { "Failed to load whisper model from: $modelPath" }
            return WhisperContext(ptr)
        }

        fun getSystemInfo(): String = WhisperLib.getSystemInfo()

        private fun defaultNumThreads(): Int =
            (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    }
}

data class TranscriptionSegment(
    val text: String,
    val startMs: Long,
    val endMs: Long
)
