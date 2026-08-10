package com.khidrew.notelydesktop.module.transcription

import kotlin.math.abs
import kotlin.math.min

class HypothesisBuffer {
    private val committedInBuffer = mutableListOf<TimestampedWord>()
    private var buffer = mutableListOf<TimestampedWord>()
    private var new    = mutableListOf<TimestampedWord>()

    var lastCommittedTime = 0f
    private var lastCommittedWord: String? = null

    fun insert(newWords: List<TimestampedWord>, offset: Float) {
        val offsetWords = newWords.map { it.copy(start = it.start + offset, end = it.end + offset) }
        new = offsetWords.filter { it.start > lastCommittedTime - 0.1f }.toMutableList()

        if (new.isNotEmpty()) {
            val (a, _, _) = new[0]
            if (abs(a - lastCommittedTime) < 1f && committedInBuffer.isNotEmpty()) {
                val cn = committedInBuffer.size
                val nn = new.size
                for (i in 1..min(min(cn, nn), 5)) {
                    val committed = (1..i).map { j -> committedInBuffer[cn - j].text }.reversed().joinToString(" ")
                    val tail      = (0 until i).joinToString(" ") { j -> new[j].text }
                    if (committed == tail) { repeat(i) { new.removeAt(0) }; break }
                }
            }
        }
    }

    fun flush(): List<TimestampedWord> {
        val commit = mutableListOf<TimestampedWord>()
        while (new.isNotEmpty()) {
            val (na, nb, nt) = new[0]
            if (buffer.isEmpty()) break
            if (nt == buffer[0].text) {
                commit.add(TimestampedWord(na, nb, nt))
                lastCommittedWord = nt
                lastCommittedTime = nb
                buffer.removeAt(0)
                new.removeAt(0)
            } else break
        }
        buffer = new.toMutableList()
        new.clear()
        committedInBuffer.addAll(commit)
        return commit
    }

    fun popCommitted(time: Float) { committedInBuffer.removeAll { it.end <= time } }
    fun complete(): List<TimestampedWord> = buffer.toList()

    fun reset() {
        committedInBuffer.clear(); buffer.clear(); new.clear()
        lastCommittedTime = 0f; lastCommittedWord = null
    }
}
