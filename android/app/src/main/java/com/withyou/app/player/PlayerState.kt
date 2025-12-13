package com.withyou.app.player

import org.videolan.libvlc.MediaPlayer

/**
 * PlayerState - Data classes for player UI state
 * 
 * Similar to VLC's Progress and other state classes,
 * but designed for Compose UI observation.
 */
data class PlayerUiState(
    val isPlaying: Boolean = false,
    val position: Long = 0L, // milliseconds
    val duration: Long = 0L, // milliseconds
    val buffered: Long = 0L, // milliseconds (if available)
    val isBuffering: Boolean = false,
    val playbackRate: Float = 1.0f,
    val isLocked: Boolean = false,
    val aspectMode: AspectMode = AspectMode.FIT,
    val audioTracks: List<MediaPlayer.TrackDescription> = emptyList(),
    val subtitleTracks: List<MediaPlayer.TrackDescription> = emptyList(),
    val currentAudioTrack: MediaPlayer.TrackDescription? = null,
    val currentSubtitleTrack: MediaPlayer.TrackDescription? = null,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val showControls: Boolean = false // Control overlay visibility (VLC-style: start hidden, tap to show)
)

enum class AspectMode {
    FIT,        // Fit to screen (letterbox/pillarbox)
    FILL,       // Fill screen (crop if needed)
    ORIGINAL,   // Original aspect ratio
    CUSTOM      // Custom aspect ratio (16:9, 4:3, etc.)
}

data class SeekInfo(
    val position: Long,
    val duration: Long,
    val fromUser: Boolean = false,
    val fast: Boolean = false
)

