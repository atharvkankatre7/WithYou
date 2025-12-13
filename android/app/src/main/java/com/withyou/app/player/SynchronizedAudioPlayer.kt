package com.withyou.app.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import timber.log.Timber

/**
 * Plays extracted audio synchronized with video player
 * 
 * This allows playing video (original) + audio (extracted/converted) separately
 * while keeping them perfectly synchronized
 */
class SynchronizedAudioPlayer(private val context: Context) {
    
    companion object {
        private const val TAG = "SynchronizedAudioPlayer"
    }
    
    private var audioPlayer: ExoPlayer? = null
    private var videoPlayer: Player? = null
    private var isSynchronized = false
    private val handler = Handler(Looper.getMainLooper())
    private val syncRunnable = object : Runnable {
        override fun run() {
            if (isSynchronized) {
                syncPosition()
                handler.postDelayed(this, 500)  // Sync every 500ms
            }
        }
    }
    
    /**
     * Initialize audio player for synchronized playback
     */
    fun initialize() {
        if (audioPlayer == null) {
            audioPlayer = ExoPlayer.Builder(context).build().apply {
                volume = 1.0f
                Timber.i("$TAG: Audio player initialized")
            }
        }
    }
    
    /**
     * Load audio file and prepare for synchronized playback
     */
    fun loadAudio(audioUri: Uri) {
        audioPlayer?.let { player ->
            player.setMediaItem(MediaItem.fromUri(audioUri))
            player.prepare()
            Timber.i("$TAG: Audio loaded: $audioUri")
        }
    }
    
    /**
     * Synchronize audio player with video player
     * 
     * @param videoPlayer The main video player to sync with
     */
    fun synchronizeWith(videoPlayer: Player) {
        this.videoPlayer = videoPlayer
        
        audioPlayer?.let { audio ->
            // Sync playback state
            videoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            if (!isSynchronized) {
                                syncPlayback()
                            }
                        }
                        Player.STATE_ENDED -> {
                            audio.stop()
                        }
                    }
                }
                
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isSynchronized) {
                        if (isPlaying) {
                            if (!audio.isPlaying) {
                                audio.play()
                            }
                        } else {
                            if (audio.isPlaying) {
                                audio.pause()
                            }
                        }
                    }
                }
                
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    if (isSynchronized && reason == Player.DISCONTINUITY_REASON_SEEK) {
                        syncSeek()
                    }
                }
            })
        }
    }
    
    /**
     * Start synchronized playback
     */
    fun start() {
        videoPlayer?.let { video ->
            audioPlayer?.let { audio ->
                // Get video position
                val videoPosition = video.currentPosition
                
                // Seek audio to match video
                audio.seekTo(videoPosition)
                
                // Start both
                if (video.isPlaying) {
                    audio.play()
                }
                
                isSynchronized = true
                
                // Start continuous sync
                handler.removeCallbacks(syncRunnable)
                handler.post(syncRunnable)
                
                Timber.i("$TAG: Synchronized playback started")
            }
        }
    }
    
    /**
     * Sync playback state
     */
    private fun syncPlayback() {
        videoPlayer?.let { video ->
            audioPlayer?.let { audio ->
                // Match position
                val videoPosition = video.currentPosition
                audio.seekTo(videoPosition)
                
                // Match playing state
                if (video.isPlaying && !audio.isPlaying) {
                    audio.play()
                } else if (!video.isPlaying && audio.isPlaying) {
                    audio.pause()
                }
                
                isSynchronized = true
                
                // Start continuous sync
                handler.removeCallbacks(syncRunnable)
                handler.post(syncRunnable)
            }
        }
    }
    
    /**
     * Sync seek operation
     */
    private fun syncSeek() {
        videoPlayer?.let { video ->
            audioPlayer?.let { audio ->
                val videoPosition = video.currentPosition
                audio.seekTo(videoPosition)
                Timber.d("$TAG: Synced seek to ${videoPosition}ms")
            }
        }
    }
    
    /**
     * Continuously sync position to prevent drift
     */
    private fun syncPosition() {
        videoPlayer?.let { video ->
            audioPlayer?.let { audio ->
                if (video.isPlaying && audio.isPlaying) {
                    val videoPos = video.currentPosition
                    val audioPos = audio.currentPosition
                    val diff = kotlin.math.abs(audioPos - videoPos)
                    
                    // If drift is more than 200ms, re-sync
                    if (diff > 200) {
                        Timber.d("$TAG: Drift detected (${diff}ms), re-syncing...")
                        audio.seekTo(videoPos)
                    }
                }
            }
        }
    }
    
    /**
     * Handle seek from video player
     */
    fun onVideoSeek(positionMs: Long) {
        audioPlayer?.seekTo(positionMs)
    }
    
    /**
     * Handle play/pause from video player
     */
    fun onVideoPlayPause(isPlaying: Boolean) {
        audioPlayer?.let { audio ->
            if (isPlaying && !audio.isPlaying) {
                audio.play()
            } else if (!isPlaying && audio.isPlaying) {
                audio.pause()
            }
        }
    }
    
    /**
     * Set playback speed for synchronized audio
     */
    fun setPlaybackSpeed(speed: Float) {
        audioPlayer?.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
        Timber.d("$TAG: Audio playback speed set to: ${speed}x")
    }
    
    /**
     * Switch to different audio track
     * 
     * @param audioUri URI of new audio track
     */
    fun switchAudioTrack(audioUri: Uri) {
        val video = videoPlayer
        val audio = audioPlayer
        
        if (video == null || audio == null) {
            Timber.e("$TAG: Cannot switch audio track - video or audio player is null")
            return
        }
        
        // Capture current state BEFORE stopping
        val currentPosition = video.currentPosition
        val isPlaying = video.isPlaying
        
        Timber.i("$TAG: Switching audio track to: $audioUri")
        Timber.i("$TAG: Current position: ${currentPosition}ms, isPlaying: $isPlaying")
        
        try {
            // Stop current audio
            if (audio.isPlaying) {
                audio.pause()
            }
            audio.stop()
            audio.clearMediaItems()
            
            // Load new audio
            audio.setMediaItem(MediaItem.fromUri(audioUri))
            audio.prepare()
            
            // Wait for preparation and sync
            val listener = object : Player.Listener {
                private var hasSynced = false
                
                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            if (!hasSynced) {
                                hasSynced = true
                                Timber.i("$TAG: New audio track ready, syncing position...")
                                
                                // Get the latest video position (might have changed during loading)
                                val videoPos = video.currentPosition
                                
                                // Seek audio to match video position
                                audio.seekTo(videoPos)
                                
                                Timber.i("$TAG: Audio seeked to ${videoPos}ms (video at ${videoPos}ms)")
                                
                                // Wait a bit for seek to complete, then verify and sync playing state
                                handler.postDelayed({
                                    // Double-check position after seek
                                    val audioPos = audio.currentPosition
                                    val videoPosAfter = video.currentPosition
                                    
                                    Timber.d("$TAG: After seek - Audio: ${audioPos}ms, Video: ${videoPosAfter}ms")
                                    
                                    // If there's still a mismatch, seek again
                                    val diff = kotlin.math.abs(audioPos - videoPosAfter)
                                    if (diff > 100) {  // More than 100ms difference
                                        Timber.w("$TAG: Position mismatch detected (${diff}ms), re-syncing...")
                                        audio.seekTo(videoPosAfter)
                                        
                                        // One more check after re-seek
                                        handler.postDelayed({
                                            val finalAudioPos = audio.currentPosition
                                            val finalVideoPos = video.currentPosition
                                            Timber.d("$TAG: Final sync - Audio: ${finalAudioPos}ms, Video: ${finalVideoPos}ms")
                                            
                                            // Sync playing state
                                            if (isPlaying) {
                                                if (!audio.isPlaying) {
                                                    audio.play()
                                                }
                                                Timber.i("$TAG: Audio track switched and playing")
                                            } else {
                                                if (audio.isPlaying) {
                                                    audio.pause()
                                                }
                                                Timber.i("$TAG: Audio track switched and paused")
                                            }
                                            
                                            // Re-enable synchronization
                                            isSynchronized = true
                                            
                                            // Start continuous sync
                                            handler.removeCallbacks(syncRunnable)
                                            handler.post(syncRunnable)
                                        }, 50)
                                    } else {
                                        // Sync playing state
                                        if (isPlaying) {
                                            if (!audio.isPlaying) {
                                                audio.play()
                                            }
                                            Timber.i("$TAG: Audio track switched and playing")
                                        } else {
                                            if (audio.isPlaying) {
                                                audio.pause()
                                            }
                                            Timber.i("$TAG: Audio track switched and paused")
                                        }
                                        
                                        // Re-enable synchronization
                                        isSynchronized = true
                                        
                                        // Start continuous sync
                                        handler.removeCallbacks(syncRunnable)
                                        handler.post(syncRunnable)
                                    }
                                }, 100)  // Wait 100ms for seek to complete
                                
                                // Remove listener to avoid memory leaks
                                audio.removeListener(this)
                            }
                        }
                        Player.STATE_IDLE, Player.STATE_ENDED -> {
                            // Remove listener if player goes to idle/ended
                            audio.removeListener(this)
                        }
                    }
                }
                
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    Timber.e("$TAG: Error loading new audio track: ${error.message}")
                    audio.removeListener(this)
                }
            }
            
            audio.addListener(listener)
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error switching audio track")
        }
    }
    
    /**
     * Release resources
     */
    fun release() {
        handler.removeCallbacks(syncRunnable)
        audioPlayer?.release()
        audioPlayer = null
        videoPlayer = null
        isSynchronized = false
        Timber.i("$TAG: Released")
    }
    
    /**
     * Check if audio is loaded and ready
     */
    fun isReady(): Boolean {
        return audioPlayer?.playbackState == Player.STATE_READY
    }
    
    /**
     * Get current audio position
     */
    fun getCurrentPosition(): Long {
        return audioPlayer?.currentPosition ?: 0L
    }
}

