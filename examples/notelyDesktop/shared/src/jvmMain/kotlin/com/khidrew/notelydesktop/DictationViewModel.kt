package com.khidrew.notelydesktop

import com.khidrew.notelydesktop.module.recorder.WavFileReader
import com.khidrew.notelydesktop.module.transcription.TranscriptionEngine
import com.khidrew.notelydesktop.module.transcription.TranscriptionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class DictationViewModel {

    companion object {
        const val MODEL_NAME    = "ggml-base.en.bin"
        const val MODEL_URL     = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin"
        const val MODEL_SIZE_MB = 142
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val modelDir  = File(System.getProperty("user.home"), ".notelydesktop${File.separator}models")
        .also { it.mkdirs() }
    private val modelFile get() = File(modelDir, MODEL_NAME)

    private var engine: TranscriptionEngine? = null
    private var resultsJob: Job? = null

    private val _transcriptionText  = MutableStateFlow("")
    val transcriptionText = _transcriptionText.asStateFlow()

    private val _isRecording        = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _engineState        = MutableStateFlow(TranscriptionState.INITIALIZING)
    val engineState = _engineState.asStateFlow()

    private val _error              = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _showDownloadDialog = MutableStateFlow(false)
    val showDownloadDialog = _showDownloadDialog.asStateFlow()

    // null = not downloading, 0f–1f = progress
    private val _downloadProgress    = MutableStateFlow<Float?>(null)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _isTranscribingFile  = MutableStateFlow(false)
    val isTranscribingFile = _isTranscribingFile.asStateFlow()

    init {
        if (modelFile.exists()) {
            createAndInitEngine()
        } else {
            _engineState.value = TranscriptionState.IDLE
        }
    }

    private fun createAndInitEngine() {
        _engineState.value = TranscriptionState.INITIALIZING
        engine = TranscriptionEngine(
            modelPath = modelFile.absolutePath,
            language  = "en",
            scope     = scope,
            onError   = { e ->
                _error.value      = e.message ?: "Transcription error"
                _isRecording.value = false
                _engineState.value = TranscriptionState.IDLE
            }
        )
        scope.launch {
            engine?.init(onSuccess = {
                _engineState.value = TranscriptionState.IDLE
                startCollectingResults()
            })
        }
    }

    private fun startCollectingResults() {
        resultsJob?.cancel()
        resultsJob = scope.launch {
            engine?.listen()?.collect { chunk ->
                if (chunk.isNotEmpty()) {
                    val current = _transcriptionText.value
                    _transcriptionText.value = if (current.isEmpty()) chunk else "$current $chunk"
                }
            }
        }
    }

    fun startRecording() {
        if (!modelFile.exists()) { _showDownloadDialog.value = true; return }
        val eng = engine ?: return
        if (!eng.isReady()) {
            _error.value = "Engine not ready. Wait for the model to finish loading."
            return
        }
        _error.value = null
        eng.start(onError = { e ->
            _error.value       = e.message ?: "Recording error"
            _isRecording.value = false
        })
        _isRecording.value = true
    }

    fun stopRecording() {
        engine?.stop()
        _isRecording.value = false
    }

    fun confirmDownload() {
        _showDownloadDialog.value = false
        scope.launch(Dispatchers.IO) {
            val tempFile = File(modelDir, "$MODEL_NAME.tmp")
            try {
                println("================ start ============ ")
                withContext(Dispatchers.Main) { _downloadProgress.value = 0f }

                val connection = URL(MODEL_URL).openConnection() as HttpURLConnection
                connection.connect()
                val total = connection.contentLengthLong
                println("================ $MODEL_URL ============ ")

                connection.inputStream.use { input ->
                    println("================ inputStream ============ ")
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var downloaded = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            if (total > 0) {
                                val progress = downloaded.toFloat() / total
                                println("================ $progress ============ ")
                                _downloadProgress.value = progress
                            }
                        }
                    }
                }
                connection.disconnect()
                tempFile.renameTo(modelFile)

                _downloadProgress.value = null
                createAndInitEngine()
            } catch (e: Exception) {
                println("======== ${e.message} =============")
                tempFile.delete()
                _downloadProgress.value = null
                _error.value = "Download failed: ${e.message}"
            }
        }
    }

    fun transcribeWavFile(filePath: String) {
        val eng = engine
        if (eng == null || !eng.isReady()) {
            _error.value = "Engine not ready. Wait for the model to finish loading."
            return
        }
        if (_isRecording.value) {
            _error.value = "Stop recording before transcribing a file."
            return
        }
        scope.launch {
            try {
                _isTranscribingFile.value = true
                val audio = withContext(Dispatchers.IO) {
                    WavFileReader.readToFloatArray(File(filePath))
                }
                val text = eng.transcribeFile(audio)
                if (text.isNotBlank()) {
                    val current = _transcriptionText.value
                    _transcriptionText.value =
                        if (current.isEmpty()) text else "$current\n\n$text"
                }
            } catch (e: Exception) {
                _error.value = "WAV transcription failed: ${e.message}"
            } finally {
                _isTranscribingFile.value = false
            }
        }
    }

    fun dismissDownloadDialog() { _showDownloadDialog.value = false }
    fun clearText()             { _transcriptionText.value  = "" }
    fun onTextChange(text: String) { _transcriptionText.value = text }
    fun dismissError()          { _error.value = null }

    fun release() {
        resultsJob?.cancel()
        engine?.release()
    }
}
