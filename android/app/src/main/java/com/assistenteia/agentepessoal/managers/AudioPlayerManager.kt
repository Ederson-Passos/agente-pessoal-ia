package com.assistenteia.agentepessoal.managers

import android.media.MediaPlayer

object AudioPlayerManager {
    private var mediaPlayer: MediaPlayer? = null

    fun play(url: String, onCompletion: () -> Unit) {
        stop()

        mediaPlayer = MediaPlayer().apply {
            try {
                setDataSource(url)
                setOnPreparedListener { it.start() }
                setOnCompletionListener {
                    onCompletion()
                    stop()
                }
                prepareAsync()
            } catch (e: Exception) {
                e.printStackTrace()
                onCompletion()
                stop()
            }
        }
    }

    fun stop() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}