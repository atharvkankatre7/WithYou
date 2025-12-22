package com.withyou.app.player

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import timber.log.Timber

/**
 * PlayerEngine - Direct wrapper around LibVLC MediaPlayer
 * 
 * This is equivalent to VLC's PlayerController class.
 * It provides a clean interface to LibVLC operations without exposing
 * the complexity of MediaPlayer directly.
 * 
 * Architecture mapping:
 * - VLC: PlayerController wraps MediaPlayer
 * - This: PlayerEngine wraps LibVLCVideoPlayer (which wraps MediaPlayer)
 * 
 * Since LibVLCVideoPlayer doesn't expose StateFlows, we create them here
 * by polling the player properties and listening to callbacks.
 */
class PlayerEngine(
    private val context: Context,
    videoLayout: VLCVideoLayout
) {
    private val player: LibVLCVideoPlayer = LibVLCVideoPlayer(context, videoLayout)
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // State flows for UI observation - created by polling player properties
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    
    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()
    
    private val _playbackRate = MutableStateFlow(1.0f)
    val playbackRate: StateFlow<Float> = _playbackRate.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    // Callbacks for UI updates
    var onStateChanged: ((State) -> Unit)? = null
    var onPositionChanged: ((Long, Long) -> Unit)? = null // timeMs, lengthMs
    var onError: ((String) -> Unit)? = null // Error callback
    var onTracksChanged: (() -> Unit)? = null // Callback when tracks become available
    
    // Polling job - can be suspended when player is released or screen not visible
    private var pollingJob: kotlinx.coroutines.Job? = null
    private var isReleased = false
    
    init {
        // Wire up callbacks from LibVLCVideoPlayer to update StateFlows
        player.onStateChanged = { state ->
            val engineState = when (state) {
                LibVLCVideoPlayer.State.PLAYING -> State.PLAYING
                LibVLCVideoPlayer.State.PAUSED -> State.PAUSED
                LibVLCVideoPlayer.State.BUFFERING -> State.BUFFERING
                LibVLCVideoPlayer.State.IDLE -> State.IDLE
                LibVLCVideoPlayer.State.ENDED -> State.STOPPED
                LibVLCVideoPlayer.State.ERROR -> State.ERROR
            }
            
            _isPlaying.value = (state == LibVLCVideoPlayer.State.PLAYING)
            _isBuffering.value = (state == LibVLCVideoPlayer.State.BUFFERING)
            
            // Clear error when state changes away from ERROR
            if (state != LibVLCVideoPlayer.State.ERROR) {
                _errorMessage.value = null
            }
            
            onStateChanged?.invoke(engineState)
        }
        
        player.onPositionChanged = { timeMs, lengthMs ->
            _position.value = timeMs
            _duration.value = lengthMs
            onPositionChanged?.invoke(timeMs, lengthMs)
        }
        
        // Wire up error callback
        player.onError = { errorMsg ->
            Timber.e("PlayerEngine: Error from LibVLCVideoPlayer: $errorMsg")
            _errorMessage.value = errorMsg
            onError?.invoke(errorMsg)
        }
        
        // Wire up tracks changed callback
        player.onTracksChanged = {
            Timber.d("PlayerEngine: Tracks changed, notifying listener")
            onTracksChanged?.invoke()
        }
        
        // Start polling - will be suspended when released
        startPolling()
    }
    
    /**
     * Start polling player state periodically to keep StateFlows in sync.
     * Polling is suspended when player is released or screen is not visible.
     * This ensures UI stays updated even if callbacks are missed.
     */
    private fun startPolling() {
        if (isReleased) return
        
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive && !isReleased) {
                try {
                    _isPlaying.value = player.isPlaying
                    _position.value = player.position
                    _duration.value = player.duration
                    // Rate is tracked via setRate() calls, no need to poll
                } catch (e: Exception) {
                    Timber.w(e, "PlayerEngine: Error polling player state")
                }
                delay(250) // Poll every 250ms
            }
        }
    }
    
    /**
     * Suspend polling (e.g., when screen is not visible or player is paused)
     * Call resumePolling() to restart.
     */
    fun suspendPolling() {
        Timber.d("PlayerEngine: Suspending polling")
        pollingJob?.cancel()
        pollingJob = null
    }
    
    /**
     * Resume polling if player is not released
     */
    fun resumePolling() {
        if (!isReleased && pollingJob?.isActive != true) {
            Timber.d("PlayerEngine: Resuming polling")
            startPolling()
        }
    }
    
    enum class State {
        IDLE,
        OPENING,
        BUFFERING,
        PLAYING,
        PAUSED,
        STOPPED,
        ERROR
    }
    
    /**
     * Play the current media
     * Maps to: MediaPlayer.play()
     */
    fun play() {
        player.play()
    }
    
    /**
     * Pause the current media
     * Maps to: MediaPlayer.pause()
     */
    fun pause() {
        player.pause()
    }
    
    /**
     * Toggle play/pause state
     */
    fun togglePlayPause() {
        if (player.isPlaying) {
            pause()
        } else {
            play()
        }
    }
    
    /**
     * Seek to a specific time position
     * @param timeMs Time in milliseconds
     * @param fast Whether to use fast seeking (keyframe-based)
     * 
     * Maps to: MediaPlayer.setTime(time: Long, fast: Boolean)
     */
    fun seekTo(timeMs: Long, fast: Boolean = false) {
        player.seekTo(timeMs)
    }
    
    /**
     * Seek to a position as a fraction (0.0 to 1.0)
     * @param position Fraction of total duration (0.0 = start, 1.0 = end)
     * 
     * Maps to: MediaPlayer.position = position
     */
    fun setPosition(position: Float) {
        val durationMs = duration.value
        if (durationMs > 0) {
            val timeMs = (position * durationMs).toLong()
            seekTo(timeMs)
        }
    }
    
    /**
     * Set playback rate (speed)
     * @param rate Playback rate (1.0 = normal, 2.0 = 2x speed, 0.5 = half speed)
     * 
     * Maps to: MediaPlayer.rate = rate
     */
    fun setRate(rate: Float) {
        player.setRate(rate)
        _playbackRate.value = rate // Update StateFlow immediately
    }
    
    /**
     * Set video aspect ratio
     * @param aspectRatio Aspect ratio string (e.g., "16:9", "4:3") or null for auto
     * 
     * Maps to: MediaPlayer.aspectRatio = aspectRatio
     */
    fun setAspectRatio(aspectRatio: String?) {
        player.setCustomAspectRatio(aspectRatio ?: "")
    }
    
    /**
     * Set video scale mode
     * @param mode Scale mode (FIT, FILL, ORIGINAL)
     */
    fun setScaleMode(mode: LibVLCVideoPlayer.VideoScaleMode, surfaceWidth: Int, surfaceHeight: Int) {
        player.setScaleMode(mode, surfaceWidth, surfaceHeight)
    }
    
    /**
     * Get available audio tracks
     * Maps to: MediaPlayer.getAudioTracks()
     */
    fun getAudioTracks(): List<MediaPlayer.TrackDescription> {
        return player.getAudioTracks()
    }
    
    /**
     * Get available subtitle tracks
     * Maps to: MediaPlayer.getSpuTracks()
     */
    fun getSubtitleTracks(): List<MediaPlayer.TrackDescription> {
        return player.getSubtitleTracks()
    }
    
    /**
     * Get currently selected audio track ID
     * Returns -1 if no track selected
     */
    fun getCurrentAudioTrackId(): Int {
        return player.getCurrentAudioTrack()
    }
    
    /**
     * Get currently selected subtitle track ID
     * Returns -1 if no track selected
     */
    fun getCurrentSubtitleTrackId(): Int {
        return player.getCurrentSubtitleTrack()
    }
    
    /**
     * Get currently selected audio track (full TrackDescription)
     * Returns null if no track selected
     */
    fun getCurrentAudioTrack(): MediaPlayer.TrackDescription? {
        val trackId = getCurrentAudioTrackId()
        if (trackId < 0) return null
        return getAudioTracks().find { it.id == trackId }
    }
    
    /**
     * Get currently selected subtitle track (full TrackDescription)
     * Returns null if no track selected
     */
    fun getCurrentSubtitleTrack(): MediaPlayer.TrackDescription? {
        val trackId = getCurrentSubtitleTrackId()
        if (trackId < 0) return null
        return getSubtitleTracks().find { it.id == trackId }
    }
    
    /**
     * Set audio track
     * @param trackId Track ID from MediaPlayer.TrackDescription
     * 
     * Maps to: MediaPlayer.setAudioTrack(trackId)
     */
    fun setAudioTrack(trackId: Int) {
        player.setAudioTrack(trackId)
    }
    
    /**
     * Set subtitle track
     * @param trackId Track ID from MediaPlayer.TrackDescription
     * 
     * Maps to: MediaPlayer.setSpuTrack(trackId)
     */
    fun setSubtitleTrack(trackId: Int) {
        player.setSubtitleTrack(trackId)
    }
    
    /**
     * Load a media file
     * @param uri Media URI (as String or android.net.Uri)
     */
    fun loadMedia(uri: String) {
        player.setMediaUri(android.net.Uri.parse(uri))
    }
    
    /**
     * Load a media file from Uri
     * @param uri Media URI
     */
    fun loadMedia(uri: android.net.Uri) {
        player.setMediaUri(uri)
    }
    
    /**
     * Get playback rate
     * Note: LibVLCVideoPlayer doesn't expose rate directly, so we track it via setRate calls
     */
    fun getRate(): Float {
        return _playbackRate.value
    }
    
    /**
     * Access underlying LibVLCVideoPlayer (for advanced operations)
     * Use sparingly - prefer PlayerEngine methods
     */
    fun getPlayer(): LibVLCVideoPlayer {
        return player
    }
    
    /**
     * Release the player and free resources
     */
    fun release() {
        isReleased = true  // Mark as released before canceling scope
        pollingJob?.cancel()  // Explicitly cancel polling job
        scope.cancel()  // Cancel the entire scope
        player.release()  // Release the underlying player
        Timber.d("PlayerEngine: Released")
    }
    
    /**
     * Reattach to a new VLCVideoLayout (e.g., after orientation change)
     */
    fun attachToLayout(videoLayout: VLCVideoLayout) {
        player.attachToLayout(videoLayout)
    }
    
    /**
     * Check if media is seekable
     */
    fun isSeekable(): Boolean {
        return duration.value > 0
    }
    
    /**
     * Check if media is pausable
     */
    fun isPausable(): Boolean {
        return isPlaying.value || duration.value > 0
    }
}

