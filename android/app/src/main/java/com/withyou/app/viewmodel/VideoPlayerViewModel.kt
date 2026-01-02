package com.withyou.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.withyou.app.player.AspectMode
import com.withyou.app.player.LibVLCVideoPlayer
import com.withyou.app.player.PlayerEngine
import com.withyou.app.player.PlayerUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import timber.log.Timber

/**
 * VideoPlayerViewModel - Bridge between UI and PlayerEngine
 * 
 * This is equivalent to VLC's PlaybackService in terms of responsibility,
 * but implemented as a ViewModel for Compose UI.
 * 
 * Architecture mapping:
 * - VLC: VideoPlayerActivity → PlaybackService → PlayerController → MediaPlayer
 * - This: VideoPlayerScreen → VideoPlayerViewModel → PlayerEngine → LibVLCVideoPlayer → MediaPlayer
 * 
 * Handles:
 * - Playback control (play, pause, seek, rate, aspect ratio)
 * - Track selection (audio, subtitles)
 * - Lock state (local lock + external lock from room permissions)
 * - UI state management (PlayerUiState)
 */

/**
 * VideoPlayerViewModel - Bridge between UI and PlayerEngine
 * 
 * This is equivalent to VLC's PlaybackService in terms of responsibility,
 * but implemented as a ViewModel for Compose UI.
 * 
 * Architecture mapping:
 * - VLC: VideoPlayerActivity → PlaybackService → PlayerController → MediaPlayer
 * - This: VideoPlayerScreen → VideoPlayerViewModel → PlayerEngine → LibVLCVideoPlayer → MediaPlayer
 */
class VideoPlayerViewModel(application: Application) : AndroidViewModel(application) {
    
    private var playerEngine: PlayerEngine? = null
    private var videoLayout: VLCVideoLayout? = null
    
    // UI State
    // Initial state: controls hidden (VLC-style: tap to show)
    private val _uiState = MutableStateFlow(PlayerUiState(showControls = false))
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    
    // Surface dimensions for aspect ratio calculations
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0
    
    // External lock state (e.g., from room permissions - non-host users are locked)
    private var externalLocked: Boolean = false
    
    // Sync controller reference (set by RoomViewModel after initialization)
    // Used to notify sync when host performs actions (seek, etc.)
    private var syncController: com.withyou.app.sync.PlaybackSyncController? = null
    
    // Control overlay auto-hide management
    private var controlsAutoHideJob: kotlinx.coroutines.Job? = null
    private val controlsAutoHideDelayMs = 4000L // 4 seconds (VLC-style)
    
    /**
     * Initialize the player with a VLCVideoLayout
     * Called from Compose when the layout is ready
     * If player already exists, re-attaches to the new layout (e.g., after orientation change)
     * 
     * Maps to: VLC's initPlayer() in RoomViewModel
     */
    fun initPlayer(videoLayout: VLCVideoLayout) {
        this.videoLayout = videoLayout
        val context = getApplication<Application>()
        
        if (playerEngine != null) {
            // Player already exists - re-attach to new layout (e.g., orientation change)
            Timber.i("PlayerEngine already initialized - re-attaching to new VLCVideoLayout")
            playerEngine?.attachToLayout(videoLayout)
            return
        }
        
        playerEngine = PlayerEngine(context, videoLayout).apply {
            // Wire up state changes
            onStateChanged = { state ->
                viewModelScope.launch {
                    _uiState.update { current ->
                        current.copy(
                            isPlaying = state == PlayerEngine.State.PLAYING,
                            isBuffering = state == PlayerEngine.State.BUFFERING,
                            hasError = state == PlayerEngine.State.ERROR
                        )
                    }
                }
            }
            
            // Wire up position updates
            onPositionChanged = { timeMs, lengthMs ->
                viewModelScope.launch {
                    _uiState.update { current ->
                        current.copy(
                            position = timeMs,
                            duration = lengthMs
                        )
                    }
                }
            }
            
            // Wire up error callback
            onError = { errorMsg ->
                viewModelScope.launch {
                    Timber.e("VideoPlayerViewModel: Player error: $errorMsg")
                    _uiState.update { current ->
                        current.copy(
                            hasError = true,
                            errorMessage = errorMsg
                        )
                    }
                }
            }
            
            // Wire up tracks changed callback - refresh tracks when they become available
            onTracksChanged = {
                viewModelScope.launch {
                    Timber.d("VideoPlayerViewModel: Tracks changed, refreshing")
                    refreshTracks()
                }
            }
        }
        
        // Start observing player state flows
        observePlayerState()
    }
    
    /**
     * Observe player state flows and update UI state
     */
    private fun observePlayerState() {
        val engine = playerEngine ?: return
        
        viewModelScope.launch {
            engine.isPlaying.collect { isPlaying ->
                _uiState.update { it.copy(isPlaying = isPlaying) }
                // Auto-hide controls when playing starts (VLC-style)
                if (isPlaying && !_uiState.value.isLocked) {
                    scheduleControlsAutoHide()
                }
            }
        }
        
        viewModelScope.launch {
            engine.position.collect { position ->
                _uiState.update { it.copy(position = position) }
            }
        }
        
        viewModelScope.launch {
            engine.duration.collect { duration ->
                _uiState.update { it.copy(duration = duration) }
            }
        }
        
        viewModelScope.launch {
            engine.isBuffering.collect { isBuffering ->
                _uiState.update { it.copy(isBuffering = isBuffering) }
            }
        }
        
        viewModelScope.launch {
            engine.playbackRate.collect { rate ->
                _uiState.update { it.copy(playbackRate = rate) }
            }
        }
    }
    
    /**
     * Update surface dimensions (for aspect ratio calculations)
     */
    fun updateSurfaceDimensions(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
    }
    
    /**
     * Load a media file
     * @param uri Media URI (as String or android.net.Uri)
     * 
     * Maps to: VLC's loadVideo() → player.setMediaUri()
     */
    fun loadMedia(uri: String) {
        playerEngine?.loadMedia(uri)
        // Refresh tracks after loading
        viewModelScope.launch {
            delay(500) // Wait a bit for tracks to be available
            refreshTracks()
        }
    }
    
    /**
     * Load a media file from Uri
     * @param uri Media URI
     */
    fun loadMedia(uri: android.net.Uri) {
        playerEngine?.loadMedia(uri)
        // Refresh tracks after loading
        viewModelScope.launch {
            delay(500) // Wait a bit for tracks to be available
            refreshTracks()
        }
    }
    
    /**
     * Play/Pause toggle
     * Maps to: VLC's doPlayPause() → service.play() / service.pause()
     * 
     * @param isHost Whether user is host (has permission to control)
     */
    fun togglePlayPause(isHost: Boolean = true) {
        if (!canControlPlayback(isHost)) {
            Timber.d("VideoPlayerViewModel: togglePlayPause() blocked - isHost=$isHost, externalLocked=$externalLocked")
            return
        }
        val wasPlaying = _uiState.value.isPlaying
        Timber.d("VideoPlayerViewModel: togglePlayPause() -> wasPlaying=$wasPlaying")
        
        // Engine layer handles end state - just delegate to it
        playerEngine?.togglePlayPause()
        
        // Show controls when user starts playback (controls will auto-hide after delay)
        if (!wasPlaying) {
            onUserInteraction()
        }
    }
    
    /**
     * Play
     * Maps to: VLC's play() → service.play()
     */
    fun play() {
        // Engine layer handles end state - just delegate to it
        playerEngine?.play()
    }
    
    /**
     * Pause
     * Maps to: VLC's pause() → service.pause()
     */
    fun pause() {
        playerEngine?.pause()
    }
    
    /**
     * Seek to a specific time
     * Maps to: VLC's seek() → service.seek() → player.setTime()
     * 
     * @param timeMs Time in milliseconds
     * @param fast Whether to use fast seeking (keyframe-based)
     * @param isHost Whether user is host (has permission to control)
     * @param fromUser Whether this seek is from user interaction (vs sync)
     */
    fun seekTo(timeMs: Long, fast: Boolean = false, isHost: Boolean = true, fromUser: Boolean = true) {
        if (!canControlPlayback(isHost)) {
            Timber.d("VideoPlayerViewModel: seekTo() blocked - isHost=$isHost, externalLocked=$externalLocked")
            return
        }
        val duration = _uiState.value.duration
        Timber.d("VideoPlayerViewModel: seekTo($timeMs, fast=$fast, fromUser=$fromUser) -> duration=$duration")
        
        // If this is a user-initiated seek from host, notify sync controller
        if (fromUser && isHost) {
            syncController?.sendSeekEvent(timeMs)
        }
        
        // Engine layer handles end state and auto-resume - just delegate to it
        if (duration > 0 && timeMs in 0..duration) {
            playerEngine?.seekTo(timeMs, fast)
        } else if (duration <= 0 && timeMs >= 0) {
            // Allow seeking even if duration not known yet (for live streams)
            playerEngine?.seekTo(timeMs, fast)
        }
    }
    
    /**
     * Seek to a position as a fraction (0.0 to 1.0)
     * Maps to: VLC's seek bar → player.setPosition()
     * 
     * @param positionFraction Fraction of total duration (0.0 = start, 1.0 = end)
     */
    fun onUserSeek(positionFraction: Float) {
        val duration = _uiState.value.duration
        if (duration > 0) {
            val timeMs = (positionFraction * duration).toLong().coerceIn(0, duration)
            seekTo(timeMs, fast = false)
        }
    }
    
    /**
     * Seek forward by a number of seconds
     * @param isHost Whether user is host (has permission to control)
     */
    fun seekForward(seconds: Int, isHost: Boolean = true) {
        if (!canControlPlayback(isHost)) return
        val current = _uiState.value.position
        val duration = _uiState.value.duration
        val newPosition = (current + seconds * 1000L).coerceAtMost(duration)
        Timber.d("VideoPlayerViewModel: seekForward($seconds) -> $current -> $newPosition")
        // Use fast seek for smoother double-tap seeking
        seekTo(newPosition, fast = true, isHost = isHost)
    }
    
    /**
     * Seek backward by a number of seconds
     * @param isHost Whether user is host (has permission to control)
     */
    fun seekBackward(seconds: Int, isHost: Boolean = true) {
        if (!canControlPlayback(isHost)) return
        val current = _uiState.value.position
        val newPosition = (current - seconds * 1000L).coerceAtLeast(0L)
        Timber.d("VideoPlayerViewModel: seekBackward($seconds) -> $current -> $newPosition")
        // Use fast seek for smoother double-tap seeking
        // seekTo() will handle auto-resuming playback if we're seeking backward from end
        seekTo(newPosition, fast = true, isHost = isHost)
    }
    
    /**
     * Set playback rate
     * Maps to: VLC's setRate() → service.setRate() → player.setRate()
     * 
     * @param rate Playback rate (1.0 = normal, 2.0 = 2x speed, 0.5 = half speed)
     * @param isHost Whether user is host (has permission to control)
     */
    fun setPlaybackRate(rate: Float, isHost: Boolean = true) {
        if (!canControlPlayback(isHost)) {
            Timber.d("VideoPlayerViewModel: setPlaybackRate() blocked - isHost=$isHost, externalLocked=$externalLocked")
            return
        }
        Timber.d("VideoPlayerViewModel: setPlaybackRate($rate) -> current=${_uiState.value.playbackRate}")
        playerEngine?.setRate(rate)
    }
    
    /**
     * Set aspect ratio mode
     * Maps to: VLC's resizeVideo() → service.setVideoAspectRatio()
     * 
     * @param mode Aspect ratio mode
     * @param customRatio Custom ratio string (e.g., "16:9") if mode is CUSTOM
     * @param isHost Whether user is host (has permission to control)
     */
    fun setAspectMode(mode: AspectMode, customRatio: String? = null, isHost: Boolean = true) {
        if (!canControlPlayback(isHost)) {
            Timber.d("VideoPlayerViewModel: setAspectMode() blocked - isHost=$isHost, externalLocked=$externalLocked")
            return
        }
        val engine = playerEngine ?: return
        
        Timber.d("VideoPlayerViewModel: setAspectMode($mode, customRatio=$customRatio)")
        
        when (mode) {
            AspectMode.FIT -> {
                engine.setScaleMode(
                    LibVLCVideoPlayer.VideoScaleMode.FIT,
                    surfaceWidth,
                    surfaceHeight
                )
            }
            AspectMode.FILL -> {
                engine.setScaleMode(
                    LibVLCVideoPlayer.VideoScaleMode.FILL,
                    surfaceWidth,
                    surfaceHeight
                )
            }
            AspectMode.FIT_SCREEN -> {
                engine.setScaleMode(
                    LibVLCVideoPlayer.VideoScaleMode.FIT_SCREEN,
                    surfaceWidth,
                    surfaceHeight
                )
            }
            AspectMode.ORIGINAL -> {
                engine.setScaleMode(
                    LibVLCVideoPlayer.VideoScaleMode.ORIGINAL,
                    surfaceWidth,
                    surfaceHeight
                )
            }
            AspectMode.CUSTOM -> {
                customRatio?.let { engine.setAspectRatio(it) }
            }
        }
        
        _uiState.update { it.copy(aspectMode = mode) }
        onUserInteraction() // Show controls and reset auto-hide when aspect ratio changes
    }
    
    /**
     * Toggle lock screen (local lock button)
     * Maps to: VLC's lock button → isLocked state
     */
    fun toggleLock() {
        val newLocked = !_uiState.value.isLocked
        Timber.d("VideoPlayerViewModel: toggleLock() -> isLocked=$newLocked (externalLocked=$externalLocked)")
        _uiState.update { it.copy(isLocked = newLocked) }
        // Hide controls when locking
        if (newLocked) {
            _uiState.update { it.copy(showControls = false) }
        }
    }
    
    /**
     * Show controls overlay (called on user interaction)
     * Resets auto-hide timer
     */
    fun showControls() {
        _uiState.update { it.copy(showControls = true) }
        scheduleControlsAutoHide()
    }
    
    /**
     * Hide controls overlay
     */
    fun hideControls() {
        _uiState.update { it.copy(showControls = false) }
        controlsAutoHideJob?.cancel()
    }
    
    /**
     * Toggle controls visibility (single tap anywhere)
     * VLC-style: tap to show/hide controls
     */
    fun toggleControls() {
        val currentShow = _uiState.value.showControls
        if (currentShow) {
            hideControls()
        } else {
            showControls()
        }
    }
    
    /**
     * User interaction detected - show controls and reset auto-hide timer
     * Called from UI when user interacts with controls or gestures
     */
    fun onUserInteraction() {
        if (!_uiState.value.isLocked) {
            showControls()
        }
    }
    
    /**
     * Schedule auto-hide of controls after delay
     * Only hides if playing and not locked
     */
    private fun scheduleControlsAutoHide() {
        controlsAutoHideJob?.cancel()
        controlsAutoHideJob = viewModelScope.launch {
            delay(controlsAutoHideDelayMs)
            // Only auto-hide if playing and not locked
            val state = _uiState.value
            if (state.isPlaying && !state.isLocked && state.showControls) {
                _uiState.update { it.copy(showControls = false) }
            }
        }
    }
    
    /**
     * Set external lock state (e.g., from room permissions)
     * When externalLocked=true, user cannot control playback (non-host in room)
     * This is separate from local lock button
     * 
     * @param locked Whether controls should be locked externally
     */
    fun setExternalLocked(locked: Boolean) {
        Timber.d("VideoPlayerViewModel: setExternalLocked($locked)")
        externalLocked = locked
        // Update UI state - locked if either local lock OR external lock is active
        // If external lock is removed, preserve local lock state
        val currentLocalLock = if (externalLocked) false else _uiState.value.isLocked
        _uiState.update { it.copy(isLocked = currentLocalLock || externalLocked) }
    }
    
    /**
     * Check if user can control playback (not locked externally and has permission)
     * @param isHost Whether user is host (has control permission)
     */
    fun canControlPlayback(isHost: Boolean): Boolean {
        return isHost && !externalLocked
    }
    
    /**
     * Refresh audio and subtitle tracks
     * Following VLC's pattern: refresh tracks when they become available
     */
    private suspend fun refreshTracks() {
        val engine = playerEngine ?: return
        
        // Try multiple times if tracks are empty (they might not be ready yet)
        var audioTracks = engine.getAudioTracks()
        var subtitleTracks = engine.getSubtitleTracks()
        
        // If tracks are empty, wait a bit and try again (VLC pattern: tracks load asynchronously)
        if (audioTracks.isEmpty() && subtitleTracks.isEmpty()) {
            delay(300)
            audioTracks = engine.getAudioTracks()
            subtitleTracks = engine.getSubtitleTracks()
        }
        
        val currentAudioTrack = engine.getCurrentAudioTrack() // Returns TrackDescription?
        val currentSubtitleTrack = engine.getCurrentSubtitleTrack() // Returns TrackDescription?
        
        Timber.d("VideoPlayerViewModel: refreshTracks() -> audioTracks=${audioTracks.size}, subtitleTracks=${subtitleTracks.size}, currentAudioTrack=${currentAudioTrack?.id ?: -1}, currentSubtitleTrack=${currentSubtitleTrack?.id ?: -1}")
        
        // Only update if we have tracks or if we're explicitly clearing them
        _uiState.update {
            it.copy(
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                currentAudioTrack = currentAudioTrack,
                currentSubtitleTrack = currentSubtitleTrack
            )
        }
    }
    
    /**
     * Set audio track
     * Maps to: VLC's setAudioTrack() → service.setAudioTrack()
     * 
     * @param trackId Track ID from MediaPlayer.TrackDescription
     * @param isHost Whether user is host (has permission to control)
     */
    fun setAudioTrack(trackId: Int, isHost: Boolean = true) {
        if (!canControlPlayback(isHost)) {
            Timber.d("VideoPlayerViewModel: setAudioTrack() blocked - isHost=$isHost, externalLocked=$externalLocked")
            return
        }
        Timber.d("VideoPlayerViewModel: setAudioTrack($trackId)")
        playerEngine?.setAudioTrack(trackId)
        viewModelScope.launch {
            refreshTracks()
        }
    }
    
    /**
     * Set subtitle track
     * Maps to: VLC's setSubtitleTrack() → service.setSpuTrack()
     * 
     * @param trackId Track ID from MediaPlayer.TrackDescription
     * @param isHost Whether user is host (has permission to control)
     */
    fun setSubtitleTrack(trackId: Int, isHost: Boolean = true) {
        if (!canControlPlayback(isHost)) {
            Timber.d("VideoPlayerViewModel: setSubtitleTrack() blocked - isHost=$isHost, externalLocked=$externalLocked")
            return
        }
        Timber.d("VideoPlayerViewModel: setSubtitleTrack($trackId)")
        playerEngine?.setSubtitleTrack(trackId)
        viewModelScope.launch {
            refreshTracks()
        }
    }
    
    /**
     * Get underlying PlayerEngine (for advanced operations or sync)
     * Use sparingly - prefer VideoPlayerViewModel methods
     * 
     * This is used by RoomViewModel to get the shared player instance for sync.
     */
    fun getPlayerEngine(): PlayerEngine? {
        return playerEngine
    }
    
    /**
     * Set sync controller reference (called by RoomViewModel after initialization)
     * This allows VideoPlayerViewModel to notify sync when host performs actions
     * 
     * @param controller PlaybackSyncController instance from RoomViewModel
     */
    fun setSyncController(controller: com.withyou.app.sync.PlaybackSyncController?) {
        Timber.d("VideoPlayerViewModel: setSyncController(${controller != null})")
        syncController = controller
    }
    
    /**
     * Notify sync controller that user started scrubbing
     * This blocks incoming seek events temporarily to avoid conflicts
     */
    fun onUserScrubbingStart() {
        syncController?.onUserScrubbingStart()
    }
    
    /**
     * Notify sync controller that user finished scrubbing
     * This allows incoming seek events after a short window
     */
    fun onUserScrubbingEnd() {
        syncController?.onUserScrubbingEnd()
    }
    
    /**
     * Check if player is initialized
     */
    fun isPlayerInitialized(): Boolean {
        return playerEngine != null
    }
    
    /**
     * Cleanup on ViewModel destruction
     */
    override fun onCleared() {
        super.onCleared()
        playerEngine?.release()
        playerEngine = null
    }
}

