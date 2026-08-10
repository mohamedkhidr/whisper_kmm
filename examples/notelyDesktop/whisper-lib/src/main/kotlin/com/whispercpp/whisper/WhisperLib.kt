package com.whispercpp.whisper

import java.nio.file.Files

class WhisperLib {
    companion object {

        init {
            NativeLoader.load()
        }

        external fun initContext(modelPath: String): Long
        external fun freeContext(contextPtr: Long)
        external fun fullTranscribe(
            contextPtr: Long,
            numThreads: Int,
            audioData: FloatArray,
            language: String,
            initialPrompt: String
        )
        external fun getTextSegmentCount(contextPtr: Long): Int
        external fun getTextSegment(contextPtr: Long, index: Int): String
        external fun getTextSegmentT0(contextPtr: Long, index: Int): Long
        external fun getTextSegmentT1(contextPtr: Long, index: Int): Long
        external fun getSystemInfo(): String
        external fun benchMemcpy(nThreads: Int): String
        external fun benchGgmlMulMat(nThreads: Int): String
    }
}

private object NativeLoader {
    @Volatile private var loaded = false

    @Synchronized
    fun load() {
        if (loaded) return

        val libName = System.mapLibraryName("whisper_jni")
        val stream = NativeLoader::class.java.getResourceAsStream("/natives/$libName")
            ?: throw UnsatisfiedLinkError(
                "whisper_jni native library not found.\n" +
                "Run: ./gradlew :whisper-lib:buildNative\n" +
                "Expected resource: /natives/$libName"
            )

        val ext = "." + libName.substringAfterLast('.')
        val tmp = Files.createTempFile("whisper_jni_", ext).toFile().also { it.deleteOnExit() }
        stream.use { input -> tmp.outputStream().use { output -> input.copyTo(output) } }
        System.load(tmp.absolutePath)
        loaded = true
    }
}
