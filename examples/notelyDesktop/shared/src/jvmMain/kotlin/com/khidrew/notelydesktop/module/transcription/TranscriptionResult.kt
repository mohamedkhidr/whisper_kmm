package com.khidrew.notelydesktop.module.transcription

data class TranscriptionResult(
    val startTime: Float?,
    val endTime:   Float?,
    val text:      String
) {
    val isEmpty: Boolean get() = text.isEmpty()
}
