package com.withyou.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.viewModelScope
import com.withyou.app.network.*
import com.withyou.app.player.LibVLCVideoPlayer
import com.withyou.app.player.PlayerEngine
import com.withyou.app.sync.PlaybackSyncController
import com.withyou.app.utils.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * ViewModel for room screen - handles playback, sync, and socket communication
 *
 * SYNC BEHAVIOR SUMMARY:
 * 
 * OUTGOING (Host sends to server):
 * - State changes (play/pause): Monitors PlayerEngine state and sends hostPlay/hostPause
 * - Position updates: Periodic loop (every 1 second) sends hostTimeSync when playing
 * - Seek events: Sends hostSeek when host seeks
 * - Speed changes: Sends hostSpeedChange when playback rate changes
 * 
 * INCOMING (Follower receives from server):
 * - applyRemotePlay(): Applies play command with position sync
 * - applyRemotePause(): Applies pause command with position sync
 * - applyRemoteSeek(): Applies seek command
 * - applyRemoteRate(): Applies playback rate change
 * - applyTimeSync(): Continuous position sync during playback
 * 
 * IMPORTANT: This ViewModel now uses a shared PlayerEngine from VideoPlayerViewModel.
 * It no longer creates its own LibVLCVideoPlayer instance. All sync operations
 * go through PlaybackSyncController, which uses the shared PlayerEngine.
 * 
 * Initialization order:
 * - loadVideo(uri) always stores currentVideoUri
 * - setSharedPlayerEngine() must be called with PlayerEngine from VideoPlayerViewModel
 * - If player exists → immediately call loadMedia(uri) on PlayerEngine
 * - If player does not exist → just store URI; will be applied when player is set
 */
class RoomViewModel(application: Application) : AndroidViewModel(application) {
    
    // Shared PlayerEngine from VideoPlayerViewModel (single source of truth)
    private var sharedPlayerEngine: com.withyou.app.player.PlayerEngine? = null
    
    // PlaybackSyncController - coordinates sync with shared PlayerEngine
    private var syncController: com.withyou.app.sync.PlaybackSyncController? = null
    
    // Socket manager
    private var socketManager: SocketManager? = null
    
    // State
    private val _uiState = MutableStateFlow<RoomUiState>(RoomUiState.Idle)
    val uiState: StateFlow<RoomUiState> = _uiState.asStateFlow()
    
    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()
    
    // Track desired playing state for surface recreation recovery
    private var shouldBePlaying: Boolean = false
        private set
    
    private val _roomId = MutableStateFlow<String?>(null)
    val roomId: StateFlow<String?> = _roomId.asStateFlow()
    
    private val _participants = MutableStateFlow<List<String>>(emptyList())
    val participants: StateFlow<List<String>> = _participants.asStateFlow()
    
    private val _connectionStatus = MutableStateFlow("Disconnected")
    val connectionStatus: StateFlow<String> = _connectionStatus.asStateFlow()
    
    private val _rttMs = MutableStateFlow(0L)
    val rttMs: StateFlow<Long> = _rttMs.asStateFlow()
    
    private val _syncStatus = MutableStateFlow("Not synced")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()
    
    // Chat messages - using ChatMessage from ui.components
    private val _chatMessages = MutableStateFlow<List<com.withyou.app.ui.components.ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<com.withyou.app.ui.components.ChatMessage>> = _chatMessages.asStateFlow()
    
    // Current user ID for determining if message is from "me"
    private val currentUserId: String?
        get() = FirebaseAuth.getInstance().currentUser?.uid
    
    // File metadata
    private var fileMetadata: FileMetadata? = null
    private var currentVideoUri: Uri? = null
    
    // Video size for aspect ratio
    data class VideoSize(val width: Int = 0, val height: Int = 0)
    private val _videoSize = MutableStateFlow(VideoSize())
    val videoSize: StateFlow<VideoSize> = _videoSize.asStateFlow()
    
    // Jobs
    private var socketJob: Job? = null
    private var pingJob: Job? = null
    private var playerListenerJob: Job? = null
    private var positionSyncJob: Job? = null
    private var rttMeasurementJob: Job? = null
    
    // App lifecycle tracking
    private var isAppInBackground = false
    private val lifecycleObserver = object : LifecycleEventObserver {
        override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // App went to background
                    isAppInBackground = true
                    Timber.i("App went to background - suspending polling and pausing video")
                    sharedPlayerEngine?.let { engine ->
                        // Suspend polling to save battery and CPU
                        engine.suspendPolling()
                        // Pause video but keep socket connected for sync
                        engine.pause()
                    }
                }
                Lifecycle.Event.ON_START -> {
                    // App came to foreground
                    if (isAppInBackground) {
                        isAppInBackground = false
                        Timber.i("App came to foreground - resuming polling")
                        sharedPlayerEngine?.let { engine ->
                            // Resume polling to sync with host position updates
                            engine.resumePolling()
                            // Don't auto-play - let user control with play button
                        }
                    }
                }
                else -> {}
            }
        }
    }
    
    init {
        // Observe app lifecycle to handle background/foreground
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
    }
    
    /**
     * Set the shared PlayerEngine from VideoPlayerViewModel
     * This must be called after VideoPlayerViewModel initializes its player.
     * 
     * RoomViewModel no longer creates its own player - it uses the shared one.
     */
    fun setSharedPlayerEngine(playerEngine: PlayerEngine) {
        if (sharedPlayerEngine == playerEngine) {
            Timber.d("RoomViewModel: PlayerEngine already set (same instance)")
            return
        }
        
        Timber.i("RoomViewModel: Setting shared PlayerEngine from VideoPlayerViewModel")
        sharedPlayerEngine = playerEngine
        
        // Create PlaybackSyncController with shared PlayerEngine
        syncController = PlaybackSyncController(playerEngine, viewModelScope)
        
        // Initialize sync controller if we're already in a room
        val currentRoomId = _roomId.value
        val currentSocketManager = socketManager
        if (currentRoomId != null && currentSocketManager != null) {
            syncController?.initialize(currentSocketManager, currentRoomId, _isHost.value)
        }
        
        Timber.d("RoomViewModel: PlaybackSyncController created and ready")
        
        // If a video was already selected before player existed, apply it now
        currentVideoUri?.let { uri ->
            Timber.i("Applying pending video URI to shared PlayerEngine")
            playerEngine.loadMedia(uri)
            // Update UI state if needed
            fileMetadata?.let {
                _uiState.value = RoomUiState.FileLoaded(it)
            }
        }
    }
    
    /**
     * @deprecated Use setSharedPlayerEngine() instead. This method is kept for backward compatibility
     * but does nothing - player is now managed by VideoPlayerViewModel.
     */
    @Deprecated("Use setSharedPlayerEngine() instead. Player is now managed by VideoPlayerViewModel.")
    fun initPlayer(videoLayout: org.videolan.libvlc.util.VLCVideoLayout) {
        Timber.w("RoomViewModel.initPlayer() is deprecated - player is now managed by VideoPlayerViewModel")
        // No-op - player is managed by VideoPlayerViewModel
    }
    
    
    /**
     * Load video file - LibVLC supports all codecs natively, no conversion needed!
     */
    fun loadVideo(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.value = RoomUiState.LoadingFile(0)
                
                // Store URI - this is important for when player is initialized later
                currentVideoUri = uri
                
                // Extract file metadata
                fileMetadata = extractFileMetadata(getApplication(), uri) { progress ->
                    _uiState.value = RoomUiState.LoadingFile(progress)
                }
                
                Timber.i("File loaded: ${fileMetadata?.displayName}, hash=${fileMetadata?.hash}")
                
                // Try to load video into shared PlayerEngine if it exists
                val engine = sharedPlayerEngine
                if (engine != null) {
                    // PlayerEngine exists - set media immediately
                    engine.loadMedia(uri)
                    fileMetadata?.let {
                        _uiState.value = RoomUiState.FileLoaded(it)
                        Timber.i("✅ Video loaded with shared PlayerEngine - all codecs supported natively!")
                    }
                } else {
                    // PlayerEngine not set yet - will be applied when setSharedPlayerEngine is called
                    Timber.w("PlayerEngine not set yet - URI stored, will set media when PlayerEngine is ready")
                    // Still update UI state with metadata so user knows file is ready
                    fileMetadata?.let {
                        _uiState.value = RoomUiState.FileLoaded(it)
                        Timber.i("✅ Video metadata loaded - waiting for PlayerEngine")
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error loading file")
                _uiState.value = RoomUiState.Error("Failed to load file: ${e.message}")
            }
        }
    }
    
    // Old methods removed - using new LibVLC track methods below
    
    /**
     * Create room as host
     */
    fun createRoom() {
        viewModelScope.launch {
            try {
                val metadata = fileMetadata ?: throw IllegalStateException("No file loaded")
                
                _uiState.value = RoomUiState.CreatingRoom
                
                val authToken = getFirebaseToken()
                val apiClient = ApiClient(authToken)
                
                val response = apiClient.createRoom(
                    fileHash = metadata.hash,
                    durationMs = metadata.durationMs,
                    fileSize = metadata.fileSize,
                    codec = metadata.codec
                )
                
                _roomId.value = response.roomId
                _isHost.value = true
                
                Timber.i("Room created: ${response.roomId}")
                
                // Connect to socket
                connectSocket(authToken, response.roomId, metadata.hash, isHost = true)
                
                _uiState.value = RoomUiState.RoomReady(response.roomId, response.shareUrl)
                
            } catch (e: Exception) {
                Timber.e(e, "Error creating room")
                _uiState.value = RoomUiState.Error("Failed to create room: ${e.message}")
            }
        }
    }
    
    /**
     * Join room as follower
     */
    fun joinRoom(roomId: String) {
        viewModelScope.launch {
            try {
                val metadata = fileMetadata ?: throw IllegalStateException("No file loaded")
                
                _uiState.value = RoomUiState.JoiningRoom
                
                val authToken = getFirebaseToken()
                val apiClient = ApiClient(authToken)
                
                // Validate room and file hash
                Timber.i("🔷 [FILE DEBUG] ========== Starting file validation ==========")
                Timber.i("🔷 [FILE DEBUG] Room ID: $roomId")
                Timber.i("🔷 [FILE DEBUG] Local file metadata:")
                Timber.i("🔷 [FILE DEBUG]   - Hash: ${metadata.hash}")
                Timber.i("🔷 [FILE DEBUG]   - Size: ${metadata.fileSize} bytes (${metadata.fileSize / 1_000_000.0} MB)")
                Timber.i("🔷 [FILE DEBUG]   - Duration: ${metadata.durationMs} ms (${metadata.durationMs / 1000.0} seconds)")
                Timber.i("🔷 [FILE DEBUG]   - Display name: ${metadata.displayName}")
                
                Timber.i("🔷 [FILE DEBUG] Calling apiClient.validateRoom...")
                val validation = apiClient.validateRoom(roomId, metadata.hash)
                
                Timber.i("🔷 [FILE DEBUG] Validation response received:")
                Timber.i("🔷 [FILE DEBUG]   - Host file hash: ${validation.hostFileHash}")
                Timber.i("🔷 [FILE DEBUG]   - Host file size: ${validation.hostFileSize} bytes (${validation.hostFileSize / 1_000_000.0} MB)")
                Timber.i("🔷 [FILE DEBUG]   - Host file duration: ${validation.hostFileDurationMs} ms (${validation.hostFileDurationMs / 1000.0} seconds)")
                Timber.i("🔷 [FILE DEBUG]   - Hash matches: ${validation.hashMatches}")
                
                // Check if hash matches
                Timber.i("🔷 [FILE DEBUG] Checking hash match...")
                
                // Determine which hash to use for socket connection
                // If hash matches, use our local hash. If not but size/duration match, use host's hash
                val fileHashToUse: String = if (validation.hashMatches) {
                    Timber.i("🔷 [FILE DEBUG] ✅ Hash matches - using local hash")
                    metadata.hash
                } else {
                    Timber.w("🔷 [FILE DEBUG] ❌ Hash does NOT match!")
                    
                    // Fallback: Check if file size and duration match (likely same file, hash mismatch due to URI/temp file)
                    // Allow small tolerance for size (1% or 1MB) and duration (1 second)
                    val sizeDiff = kotlin.math.abs(metadata.fileSize - validation.hostFileSize)
                    val sizeTolerance = kotlin.math.max((metadata.fileSize * 0.01).toLong(), 1_000_000L) // 1% or 1MB
                    val sizeMatches = sizeDiff <= sizeTolerance
                    
                    val durationDiff = kotlin.math.abs(metadata.durationMs - validation.hostFileDurationMs)
                    val durationTolerance = 1000L // 1 second
                    val durationMatches = durationDiff <= durationTolerance
                    
                    Timber.w("🔷 [FILE DEBUG] Fallback validation:")
                    Timber.w("🔷 [FILE DEBUG]   - Size difference: $sizeDiff bytes")
                    Timber.w("🔷 [FILE DEBUG]   - Size tolerance: $sizeTolerance bytes (1% or 1MB)")
                    Timber.w("🔷 [FILE DEBUG]   - Size match: $sizeMatches")
                    Timber.w("🔷 [FILE DEBUG]   - Duration difference: $durationDiff ms")
                    Timber.w("🔷 [FILE DEBUG]   - Duration tolerance: $durationTolerance ms (1 second)")
                    Timber.w("🔷 [FILE DEBUG]   - Duration match: $durationMatches")
                    
                    if (sizeMatches && durationMatches) {
                        // File size and duration match - likely the same file, hash mismatch due to different URI/temp location
                        // Use host's hash for socket connection so server accepts it
                        Timber.w("🔷 [FILE DEBUG] ✅ Size and duration match - using HOST's hash for socket (likely same file from different location)")
                        validation.hostFileHash
                    } else {
                        // File size or duration doesn't match - different file
                        Timber.e("🔷 [FILE DEBUG] ❌ File mismatch: hash, size, or duration don't match")
                        Timber.e("🔷 [FILE DEBUG] ========== File validation FAILED ==========")
                        _uiState.value = RoomUiState.Error(
                            "File mismatch! Please select the same video file as the host.\n\n" +
                            "Host file: ${validation.hostFileSize} bytes, ${validation.hostFileDurationMs} ms\n" +
                            "Your file: ${metadata.fileSize} bytes, ${metadata.durationMs} ms\n\n" +
                            "Note: If you're using the same file, try selecting it from the same location."
                        )
                        return@launch
                    }
                }
                
                Timber.i("🔷 [FILE DEBUG] ========== File validation PASSED ==========")
                Timber.i("🔷 [FILE DEBUG] Using file hash for socket: $fileHashToUse")
                
                _roomId.value = roomId
                _isHost.value = false
                
                Timber.i("Joining room: $roomId")
                
                // Connect to socket - use the appropriate hash (local if matches, host's if size/duration match)
                connectSocket(authToken, roomId, fileHashToUse, isHost = false)
                
                _uiState.value = RoomUiState.RoomReady(roomId, "")
                
            } catch (e: Exception) {
                Timber.e(e, "Error joining room")
                _uiState.value = RoomUiState.Error("Failed to join room: ${e.message}")
            }
        }
    }
    
    /**
     * Connect to socket and setup event handlers
     */
    private fun connectSocket(authToken: String, roomId: String, fileHash: String, isHost: Boolean) {
        socketManager = SocketManager(authToken)
        
        socketJob = viewModelScope.launch {
            socketManager?.connect()?.collect { event ->
                handleSocketEvent(event, roomId, fileHash, isHost)
            }
        }
        
        // Start ping loop for RTT measurement
        startPingLoop()
        
        // If host and sync controller is ready, initialize it
        if (isHost && syncController != null) {
            syncController?.initialize(socketManager!!, roomId, isHost)
        }
    }
    
    /**
     * Handle socket events
     */
    private fun handleSocketEvent(event: SocketEvent, roomId: String, fileHash: String, isHost: Boolean) {
        when (event) {
            is SocketEvent.Connected -> {
                _connectionStatus.value = "Connected"
                // Join room
                socketManager?.joinRoom(roomId, if (_isHost.value) "host" else "follower", fileHash)
            }
            is SocketEvent.Disconnected -> {
                _connectionStatus.value = "Disconnected"
            }
            is SocketEvent.Joined -> {
                _connectionStatus.value = "In Room"
                _syncStatus.value = "Synced"
                Timber.i("Successfully joined room")
                
                // If we're host and sync controller is ready, initialize it
                if (_isHost.value && syncController != null) {
                    Timber.i("Joined as host - initializing sync controller")
                    syncController?.initialize(socketManager!!, roomId, true)
                    
                    // Wire sync controller to VideoPlayerViewModel if not already done
                    // This is a fallback in case RoomScreen didn't wire it yet
                    // Note: RoomScreen should handle this, but this ensures it's wired
                }
            }
            is SocketEvent.HostPlay -> {
                if (!_isHost.value) {
                    Timber.d("Received hostPlay: position=${event.event.positionSec}, timestamp=${event.event.hostTimestampMs}")
                    syncController?.applyRemotePlay(
                        event.event.positionSec,
                        event.event.hostTimestampMs,
                        event.event.playbackRate
                    )
                    _syncStatus.value = "Synced - Playing"
                }
            }
            is SocketEvent.HostPause -> {
                if (!_isHost.value) {
                    Timber.d("Received hostPause: position=${event.event.positionSec}, timestamp=${event.event.hostTimestampMs}")
                    syncController?.applyRemotePause(
                        event.event.positionSec,
                        event.event.hostTimestampMs
                    )
                    _syncStatus.value = "Synced - Paused"
                }
            }
            is SocketEvent.HostSeek -> {
                if (!_isHost.value) {
                    Timber.d("Received hostSeek: position=${event.event.positionSec}, timestamp=${event.event.hostTimestampMs}")
                    syncController?.applyRemoteSeek(
                        event.event.positionSec,
                        event.event.hostTimestampMs
                    )
                    _syncStatus.value = "Synced - Seeking"
                }
            }
            is SocketEvent.Pong -> {
                syncController?.updateRtt(event.rtt)
            }
            is SocketEvent.ParticipantLeft -> {
                Timber.i("Participant left: ${event.userId} - ${event.message}")
                // Pause playback when partner leaves
                sharedPlayerEngine?.pause()
                if (event.wasHost) {
                    _syncStatus.value = "Host left - Playback paused"
                } else {
                    _syncStatus.value = "Partner left - Playback paused"
                }
                // Show notification message (could be displayed in UI)
                Timber.w("${event.message}")
            }
            is SocketEvent.HostDisconnected -> {
                Timber.w("Host disconnected: ${event.message} (grace period: ${event.gracePeriodMs}ms)")
                _connectionStatus.value = "Host Disconnected (Reconnecting...)"
                
                // Pause playback on host disconnect to prevent desync
                // Player will resume when host reconnects
                if (!isHost) {
                    sharedPlayerEngine?.pause()
                    Timber.i("Playback paused due to host disconnection")
                }
            }
            is SocketEvent.HostReconnected -> {
                Timber.i("Sync: Host reconnected: ${event.message}")
                _connectionStatus.value = "Connected"
                // On reconnect, sync controller will resume receiving sync events
                // No need to request full state - continuous sync will catch up
            }
            is SocketEvent.HostTransferred -> {
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                val becameHost = event.newHostUserId == currentUserId
                Timber.i("Sync: Host transferred to: ${event.newHostUserId} (reason: ${event.reason}), becameHost=$becameHost")
                
                if (becameHost) {
                    _isHost.value = true
                    _connectionStatus.value = "You are now the host"
                    // Update sync controller host status - starts sending sync events
                    syncController?.updateHostStatus(true)
                    Timber.i("Sync: User became host - sync controller updated")
                } else {
                    // Someone else became host - we're now a follower
                    if (_isHost.value) {
                        _isHost.value = false
                        syncController?.updateHostStatus(false)
                        Timber.i("Sync: User is no longer host - sync controller updated")
                    }
                }
            }
            is SocketEvent.HostSpeedChange -> {
                if (!_isHost.value) {
                    Timber.i("Host changed playback speed to: ${event.playbackRate}x")
                    syncController?.applyRemoteRate(event.playbackRate)
                }
            }
            is SocketEvent.HostTimeSync -> {
                if (!_isHost.value && event.event.isPlaying) {
                    syncController?.applyTimeSync(
                        event.event.positionSec,
                        event.event.hostTimestampMs
                    )
                }
            }
            is SocketEvent.ChatMessage -> {
                Timber.i("🟢 [CHAT DEBUG] SocketEvent.ChatMessage received")
                val messageEvent = event.event
                val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                val isMe = messageEvent.userId == currentUserId
                
                Timber.i("🟢 [CHAT DEBUG] Message details:")
                Timber.i("🟢 [CHAT DEBUG]   - userId: ${messageEvent.userId}")
                Timber.i("🟢 [CHAT DEBUG]   - text: '${messageEvent.text}'")
                Timber.i("🟢 [CHAT DEBUG]   - timestamp: ${messageEvent.ts}")
                Timber.i("🟢 [CHAT DEBUG]   - currentUserId: $currentUserId")
                Timber.i("🟢 [CHAT DEBUG]   - isMe: $isMe")
                Timber.i("🟢 [CHAT DEBUG]   - Current message count: ${_chatMessages.value.size}")
                
                val chatMessage = com.withyou.app.ui.components.ChatMessage(
                    userId = messageEvent.userId,
                    text = messageEvent.text,
                    timestamp = messageEvent.ts,
                    isMe = isMe
                )
                
                // Add message to list
                val previousCount = _chatMessages.value.size
                _chatMessages.value = _chatMessages.value + chatMessage
                Timber.i("🟢 [CHAT DEBUG] Added message to list. Previous count: $previousCount, New count: ${_chatMessages.value.size}")
                Timber.i("🟢 [CHAT DEBUG] All messages: ${_chatMessages.value.map { "${it.text} (${if (it.isMe) "me" else "other"})" }}")
            }
            is SocketEvent.Error -> {
                Timber.e("Socket error: ${event.message}")
                _connectionStatus.value = "Error: ${event.message}"
            }
            else -> {
                // Handle reactions, etc.
            }
        }
    }
    
    /**
     * @deprecated Replaced by PlaybackSyncController. This method is no longer used.
     */
    @Deprecated("Replaced by PlaybackSyncController")
    private fun setupHostPlayerListener(roomId: String) {
        // No-op - sync is now handled by PlaybackSyncController
    }
    
    /**
     * @deprecated Replaced by PlaybackSyncController. This method is no longer used.
     */
    @Deprecated("Replaced by PlaybackSyncController")
    private fun startPositionSyncLoop(roomId: String) {
        // No-op - sync is now handled by PlaybackSyncController
    }
    
    /**
     * Host seeks to position
     * Now delegates to VideoPlayerViewModel, but also sends sync event
     * 
     * @deprecated This method is kept for backward compatibility but should not be called directly.
     * Use VideoPlayerViewModel.seekTo() instead, which will trigger sync via PlaybackSyncController.
     */
    @Deprecated("Use VideoPlayerViewModel.seekTo() instead. This method only sends sync event.")
    fun seekTo(positionMs: Long) {
        if (_isHost.value) {
            // Send sync event (actual seek is handled by VideoPlayerViewModel)
            val roomId = _roomId.value
            if (roomId != null) {
                syncController?.sendSeekEvent(positionMs)
            }
        }
    }
    
    /**
     * Toggle play/pause
     * 
     * @deprecated This method is kept for backward compatibility but should not be called directly.
     * Use VideoPlayerViewModel.togglePlayPause() instead, which will trigger sync via PlaybackSyncController.
     */
    @Deprecated("Use VideoPlayerViewModel.togglePlayPause() instead. Sync is handled automatically.")
    fun togglePlayPause() {
        // No-op - play/pause is now handled by VideoPlayerViewModel
        // Sync events are automatically sent by PlaybackSyncController
    }
    
    /**
     * @deprecated Use VideoPlayerViewModel.setAspectMode() instead
     */
    @Deprecated("Use VideoPlayerViewModel.setAspectMode() instead")
    fun setScaleMode(mode: LibVLCVideoPlayer.VideoScaleMode, surfaceWidth: Int = 0, surfaceHeight: Int = 0) {
        // No-op - aspect ratio is now handled by VideoPlayerViewModel
    }
    
    /**
     * @deprecated Use VideoPlayerViewModel.setAspectMode() instead
     */
    @Deprecated("Use VideoPlayerViewModel.setAspectMode() instead")
    fun setCustomAspectRatio(ratio: String) {
        // No-op - aspect ratio is now handled by VideoPlayerViewModel
    }
    
    /**
     * Get audio tracks from shared PlayerEngine
     */
    fun getAudioTracks(): List<org.videolan.libvlc.MediaPlayer.TrackDescription> {
        return sharedPlayerEngine?.getAudioTracks() ?: emptyList()
    }
    
    /**
     * Get subtitle tracks from shared PlayerEngine
     */
    fun getSubtitleTracks(): List<org.videolan.libvlc.MediaPlayer.TrackDescription> {
        return sharedPlayerEngine?.getSubtitleTracks() ?: emptyList()
    }
    
    /**
     * Get current audio track ID from shared PlayerEngine
     */
    fun getCurrentAudioTrack(): Int {
        return sharedPlayerEngine?.getCurrentAudioTrackId() ?: -1
    }
    
    /**
     * Get current subtitle track ID from shared PlayerEngine
     */
    fun getCurrentSubtitleTrack(): Int {
        return sharedPlayerEngine?.getCurrentSubtitleTrackId() ?: -1
    }
    
    /**
     * @deprecated Use VideoPlayerViewModel.setAudioTrack() instead
     */
    @Deprecated("Use VideoPlayerViewModel.setAudioTrack() instead")
    fun setAudioTrack(trackId: Int) {
        // No-op - audio track is now handled by VideoPlayerViewModel
    }
    
    /**
     * @deprecated Use VideoPlayerViewModel.setSubtitleTrack() instead
     */
    @Deprecated("Use VideoPlayerViewModel.setSubtitleTrack() instead")
    fun setSubtitleTrack(trackId: Int) {
        // No-op - subtitle track is now handled by VideoPlayerViewModel
    }
    
    /**
     * @deprecated Use VideoPlayerViewModel.seekForward() instead
     */
    @Deprecated("Use VideoPlayerViewModel.seekForward() instead")
    fun seekForward(seconds: Int) {
        // No-op - seeking is now handled by VideoPlayerViewModel
    }
    
    /**
     * @deprecated Use VideoPlayerViewModel.seekBackward() instead
     */
    @Deprecated("Use VideoPlayerViewModel.seekBackward() instead")
    fun seekBackward(seconds: Int) {
        // No-op - seeking is now handled by VideoPlayerViewModel
    }
    
    /**
     * Get PlaybackSyncController (for wiring to VideoPlayerViewModel)
     * This allows VideoPlayerViewModel to notify sync when host performs actions
     */
    fun getSyncController(): com.withyou.app.sync.PlaybackSyncController? {
        return syncController
    }
    
    /**
     * Leave room (explicitly called by user)
     */
    fun leaveRoom() {
        val roomId = _roomId.value
        if (roomId != null) {
            socketManager?.leaveRoom(roomId)
            socketManager?.disconnect()
            socketManager = null
        }
        cleanup()
    }
    
    /**
     * Send reaction
     */
    fun sendReaction(roomId: String, type: String) {
        socketManager?.sendReaction(roomId, type)
        Timber.d("Sent reaction: $type")
    }
    
    /**
     * Send chat message
     * Note: The message will appear in the list when received via SocketEvent.ChatMessage
     * This ensures all messages (sent and received) go through the same flow
     */
    fun sendChatMessage(roomId: String, text: String) {
        Timber.i("🔵 [CHAT DEBUG] sendChatMessage called: roomId=$roomId, text='$text'")
        Timber.i("🔵 [CHAT DEBUG] socketManager is null: ${socketManager == null}")
        Timber.i("🔵 [CHAT DEBUG] connectionStatus: ${_connectionStatus.value}")
        Timber.i("🔵 [CHAT DEBUG] Current chatMessages count: ${_chatMessages.value.size}")
        
        if (socketManager == null) {
            Timber.e("🔵 [CHAT DEBUG] ❌ Cannot send: socketManager is null")
            return
        }
        
        socketManager?.sendChatMessage(roomId, text)
        Timber.i("🔵 [CHAT DEBUG] sendChatMessage completed, waiting for server echo")
        // Don't add to list here - wait for server to echo it back via SocketEvent.ChatMessage
        // This ensures message ordering and confirms delivery
    }
    
    /**
     * Get Firebase auth token
     */
    private suspend fun getFirebaseToken(): String {
        val user = FirebaseAuth.getInstance().currentUser
            ?: throw IllegalStateException("User not authenticated")
        
        return user.getIdToken(false).await().token
            ?: throw IllegalStateException("Failed to get auth token")
    }
    
    /**
     * Start RTT measurement ping loop
     * Periodically sends ping events to measure network latency
     * Results are stored in _rttMs for display/monitoring
     */
    private fun startPingLoop() {
        rttMeasurementJob = viewModelScope.launch {
            var nonce = 0L
            while (isActive) {
                try {
                    delay(5000) // Ping every 5 seconds
                    val ts = System.currentTimeMillis()
                    nonce++
                    socketManager?.sendPing(nonce, ts) { rtt ->
                        // Update RTT display
                        _rttMs.value = rtt
                        syncController?.updateRtt(rtt)
                        Timber.d("RTT measured: ${rtt}ms")
                    }
                } catch (e: Exception) {
                    Timber.w("Error sending ping: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Reset state to idle (cancel file selection)
     */
    fun resetState() {
        _uiState.value = RoomUiState.Idle
        sharedPlayerEngine?.pause() // Pause instead of stop (PlayerEngine doesn't have stop)
        fileMetadata = null
        currentVideoUri = null
    }
    
    /**
     * Cleanup resources
     */
    private fun cleanup() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(lifecycleObserver)
        
        socketJob?.cancel()
        pingJob?.cancel()
        playerListenerJob?.cancel()
        positionSyncJob?.cancel()
        rttMeasurementJob?.cancel()
        
        // Suspend polling to free resources
        sharedPlayerEngine?.suspendPolling()
        
        // Only disconnect socket if explicitly leaving room (not just backgrounding)
        // socketManager?.disconnect()  // Commented out to keep connection alive
        // socketManager = null
        
        syncController?.release()
        syncController = null
        
        _uiState.value = RoomUiState.Idle
        _connectionStatus.value = "Disconnected"
        _chatMessages.value = emptyList() // Clear chat messages on cleanup
    }
    
    override fun onCleared() {
        super.onCleared()
        cleanup()
        // Note: PlayerEngine is released by VideoPlayerViewModel, not here
        Timber.d("RoomViewModel cleared")
    }
}

// UI States
sealed class RoomUiState {
    object Idle : RoomUiState()
    data class LoadingFile(val progress: Int) : RoomUiState()
    object CheckingAudio : RoomUiState()  // Checking if audio codec is supported
    data class ConvertingAudio(val progress: Int) : RoomUiState()  // FFmpeg converting audio
    data class ExtractingAllAudioTracks(val progress: Int, val totalTracks: Int) : RoomUiState()  // Extracting all tracks upfront
    data class FileLoaded(val metadata: FileMetadata) : RoomUiState()
    object CreatingRoom : RoomUiState()
    object JoiningRoom : RoomUiState()
    data class RoomReady(val roomId: String, val shareUrl: String) : RoomUiState()
    data class Error(val message: String) : RoomUiState()
}


