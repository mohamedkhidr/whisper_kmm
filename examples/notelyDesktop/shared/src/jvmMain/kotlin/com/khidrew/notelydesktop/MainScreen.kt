package com.khidrew.notelydesktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.khidrew.notelydesktop.module.transcription.TranscriptionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

@Composable
fun DictationScreen() {
    val viewModel = remember { DictationViewModel() }
    DisposableEffect(Unit) { onDispose { viewModel.release() } }

    val scope = rememberCoroutineScope()

    var transcriptionText    by remember { mutableStateOf("") }
    var isRecording          by remember { mutableStateOf(false) }
    var engineState          by remember { mutableStateOf(TranscriptionState.INITIALIZING) }
    var error                by remember { mutableStateOf<String?>(null) }
    var showDownloadDialog   by remember { mutableStateOf(false) }
    var downloadProgress     by remember { mutableStateOf<Float?>(null) }
    var isTranscribingFile   by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.transcriptionText.collect   { transcriptionText   = it } }
    LaunchedEffect(Unit) { viewModel.isRecording.collect         { isRecording         = it } }
    LaunchedEffect(Unit) { viewModel.engineState.collect         { engineState         = it } }
    LaunchedEffect(Unit) { viewModel.error.collect               { error               = it } }
    LaunchedEffect(Unit) { viewModel.showDownloadDialog.collect  { showDownloadDialog  = it } }
    LaunchedEffect(Unit) { viewModel.downloadProgress.collect    { downloadProgress    = it } }
    LaunchedEffect(Unit) { viewModel.isTranscribingFile.collect  { isTranscribingFile  = it } }

    val scrollState = rememberScrollState()
    LaunchedEffect(transcriptionText) { scrollState.animateScrollTo(scrollState.maxValue) }

    val isDownloading        = downloadProgress != null
    val isEngineInitializing = engineState == TranscriptionState.INITIALIZING
    val fabEnabled           = !isDownloading && !isEngineInitializing && !isTranscribingFile

    val fabColor by animateColorAsState(
        targetValue = when {
            isRecording        -> MaterialTheme.colorScheme.error
            !fabEnabled        -> MaterialTheme.colorScheme.surfaceVariant
            else               -> MaterialTheme.colorScheme.primary
        },
        animationSpec = tween(300),
        label = "fabColor"
    )

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDownloadDialog() },
            title   = { Text("Download Speech Model") },
            text    = {
                Text(
                    "To enable dictation, a Whisper speech recognition model needs to be downloaded.\n\n" +
                    "Model: ${DictationViewModel.MODEL_NAME}\n" +
                    "Size:  ~${DictationViewModel.MODEL_SIZE_MB} MB\n\n" +
                    "The model will be stored in your home directory."
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.confirmDownload() })      { Text("Download") } },
            dismissButton = { TextButton(onClick = { viewModel.dismissDownloadDialog() }) { Text("Cancel")   } }
        )
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!fabEnabled) return@FloatingActionButton
                    if (isRecording) viewModel.stopRecording() else viewModel.startRecording()
                },
                containerColor = fabColor,
                shape          = CircleShape,
                modifier       = Modifier.size(72.dp)
            ) {
                Icon(
                    imageVector     = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = if (isRecording) "Stop recording" else "Start recording",
                    modifier        = Modifier.size(32.dp),
                    tint = if (fabEnabled) MaterialTheme.colorScheme.onPrimary
                           else           MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        isDownloading          -> "Downloading model… ${((downloadProgress ?: 0f) * 100).toInt()}%"
                        isEngineInitializing   -> "Loading model…"
                        isTranscribingFile     -> "Transcribing file…"
                        isRecording            -> "Listening…"
                        engineState == TranscriptionState.PROCESSING -> "Processing…"
                        else                   -> "Click the mic to start dictating"
                    },
                    style  = MaterialTheme.typography.bodySmall,
                    color  = when {
                        isRecording        -> MaterialTheme.colorScheme.error
                        isTranscribingFile -> MaterialTheme.colorScheme.tertiary
                        isDownloading      -> MaterialTheme.colorScheme.primary
                        else               -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        scope.launch {
                            val path = withContext(Dispatchers.IO) {
                                val chooser = JFileChooser().apply {
                                    dialogTitle  = "Select WAV file"
                                    fileFilter   = FileNameExtensionFilter("WAV audio (*.wav)", "wav")
                                    isMultiSelectionEnabled = false
                                }
                                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                                    chooser.selectedFile.absolutePath
                                else null
                            }
                            path?.let { viewModel.transcribeWavFile(it) }
                        }
                    },
                    enabled = fabEnabled && !isRecording,
                ) {
                    Icon(
                        imageVector        = Icons.Filled.AudioFile,
                        contentDescription = "Transcribe WAV file",
                        tint = if (fabEnabled && !isRecording)
                                   MaterialTheme.colorScheme.primary
                               else
                                   MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    )
                }
                if (transcriptionText.isNotEmpty() && !isRecording) {
                    TextButton(onClick = { viewModel.clearText() }) { Text("Clear") }
                }
            }

            if (isDownloading) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { downloadProgress ?: 0f },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(
                        width = 1.dp,
                        color = when {
                            isRecording   -> MaterialTheme.colorScheme.error
                            isDownloading -> MaterialTheme.colorScheme.primary
                            else          -> MaterialTheme.colorScheme.outline
                        },
                        shape = MaterialTheme.shapes.medium
                    )
            ) {
                TextField(
                    value       = transcriptionText,
                    onValueChange = { viewModel.onTextChange(it) },
                    modifier    = Modifier.fillMaxSize().verticalScroll(scrollState),
                    placeholder = {
                        Text(
                            text = when {
                                isDownloading        -> "Downloading Whisper model, please wait…"
                                isEngineInitializing -> "Loading Whisper model, please wait…"
                                else                 -> "Your transcription will appear here…"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor   = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor   = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }

            error?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Card(
                    colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier           = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment  = Alignment.CenterVertically
                    ) {
                        Text(
                            text     = msg,
                            color    = MaterialTheme.colorScheme.onErrorContainer,
                            style    = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.dismissError() }) { Text("Dismiss") }
                    }
                }
            }

            Spacer(Modifier.height(88.dp))
        }
    }
}
