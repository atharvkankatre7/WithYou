package com.withyou.app.sync

import com.withyou.app.player.PlayerEngine
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * PlaybackSyncController - Coordinates sync operations with a shared PlayerEngine
 * 
 * This component decouples sync logic from ViewModels and ensures all sync operations
 * use the same PlayerEngine instance that the UI uses.
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
 * CONFLICT HANDLING:
 * - If isHost == false, never send outgoing play/pause/seek; only apply incoming commands
 * - If isHost == true, outgoing actions are sent to server; incoming actions may be ignored if they conflict
 * - Local user scrubbing temporarily blocks incoming seek events (1-2 second window)
 */
class PlaybackSyncController(
    private val playerEngine: PlayerEngine,
    private val scope: CoroutineScope
) {
    private var syncEngine: LibVLCSyncEngine? = null
    private var isHost: Boolean = false
    private var roomId: String? = null
    private var socketManager: com.withyou.app.network.SocketManager? = null
    
    // Track if user is actively scrubbing (to ignore incoming seeks)
    private var isUserScrubbing: Boolean = false
    private var scrubbingEndTime: Long = 0
    private val SCRUBBING_BLOCK_WINDOW_MS = 2000L // Ignore incoming seeks for 2 seconds after local scrub
    
    // State observation jobs
    private var stateObservationJob: Job? = null
    private var positionSyncJob: Job? = null
    private var rateObservationJob: Job? = null
    
    /**
     * Initialize sync controller with socket manager and room info
     */
    fun initialize(
        socketManager: com.withyou.app.network.SocketManager,
        roomId: String,
        isHost: Boolean
    ) {
        this.socketManager = socketManager
        this.roomId = roomId
        this.isHost = isHost
        
        // Create sync engine for incoming sync operations
        syncEngine = LibVLCSyncEngine(playerEngine, scope)
        
        if (isHost) {
            setupHostSync()
        }
        
        Timber.d("PlaybackSyncController: Initialized (isHost=$isHost, roomId=$roomId)")
    }
    
    /**
     * Setup sync for host (outgoing events)
     */
    private fun setupHostSync() {
        // Observe player state changes and send sync events
        stateObservationJob = scope.launch {
            playerEngine.isPlaying.collect { isPlaying ->
                if (!isHost) return@collect
                
                val position = playerEngine.position.value
                val positionSec = position / 1000.0
                val playbackRate = playerEngine.playbackRate.value
                
                when {
                    isPlaying -> {
                        Timber.i("Sync: Host playing - sending hostPlay: position=$positionSec, rate=$playbackRate")
                        socketManager?.hostPlay(roomId ?: return@collect, positionSec, playbackRate)
                    }
                    else -> {
                        Timber.i("Sync: Host paused - sending hostPause: position=$positionSec")
                        socketManager?.hostPause(roomId ?: return@collect, positionSec)
                    }
                }
            }
        }
        
        // Periodic position sync (every 1 second when playing)
        positionSyncJob = scope.launch {
            while (isActive) {
                delay(1000)
                if (isHost && playerEngine.isPlaying.value) {
                    val positionSec = playerEngine.position.value / 1000.0
                    socketManager?.hostTimeSync(roomId ?: return@launch, positionSec, true)
                    Timber.v("Sync: Sent hostTimeSync: position=$positionSec")
                }
            }
        }
        
        // Observe playback rate changes
        rateObservationJob = scope.launch {
            playerEngine.playbackRate.collect { rate ->
                if (!isHost) return@collect
                if (rate != 1.0f) { // Only send if rate is not default
                    Timber.i("Sync: Host speed change - sending hostSpeedChange: rate=$rate")
                    socketManager?.hostSpeedChange(roomId ?: return@collect, rate)
                }
            }
        }
    }
    
    /**
     * Send seek event (called when host seeks)
     */
    fun sendSeekEvent(positionMs: Long) {
        if (!isHost) {
            Timber.d("Sync: Ignored sendSeekEvent - not host")
            return
        }
        
        val positionSec = positionMs / 1000.0
        Timber.i("Sync: Host seek - sending hostSeek: position=$positionSec")
        socketManager?.hostSeek(roomId ?: return, positionSec)
    }
    
    /**
     * Apply remote play command (incoming from server)
     */
    fun applyRemotePlay(positionSec: Double, hostTimestampMs: Long, playbackRate: Float = 1.0f) {
        if (isHost) {
            Timber.d("Sync: Ignored applyRemotePlay - is host (should not receive own events)")
            return
        }
        
        Timber.i("Sync: applyRemotePlay(positionSec=$positionSec, rate=$playbackRate)")
        syncEngine?.handleHostPlay(positionSec, hostTimestampMs, playbackRate)
    }
    
    /**
     * Apply remote pause command (incoming from server)
     */
    fun applyRemotePause(positionSec: Double, hostTimestampMs: Long) {
        if (isHost) {
            Timber.d("Sync: Ignored applyRemotePause - is host (should not receive own events)")
            return
        }
        
        Timber.i("Sync: applyRemotePause(positionSec=$positionSec)")
        syncEngine?.handleHostPause(positionSec, hostTimestampMs)
    }
    
    /**
     * Apply remote seek command (incoming from server)
     */
    fun applyRemoteSeek(positionSec: Double, hostTimestampMs: Long) {
        if (isHost) {
            Timber.d("Sync: Ignored applyRemoteSeek - is host (should not receive own events)")
            return
        }
        
        // Check if user is actively scrubbing
        val now = System.currentTimeMillis()
        if (isUserScrubbing || (now - scrubbingEndTime) < SCRUBBING_BLOCK_WINDOW_MS) {
            Timber.d("Sync: Ignored applyRemoteSeek - user is scrubbing (blocked for ${SCRUBBING_BLOCK_WINDOW_MS}ms)")
            return
        }
        
        Timber.i("Sync: applyRemoteSeek(positionSec=$positionSec)")
        syncEngine?.handleHostSeek(positionSec, hostTimestampMs)
    }
    
    /**
     * Apply remote playback rate change (incoming from server)
     */
    fun applyRemoteRate(rate: Float) {
        if (isHost) {
            Timber.d("Sync: Ignored applyRemoteRate - is host (should not receive own events)")
            return
        }
        
        Timber.i("Sync: applyRemoteRate(rate=$rate)")
        playerEngine.setRate(rate)
    }
    
    /**
     * Apply time sync (continuous position sync during playback)
     */
    fun applyTimeSync(positionSec: Double, hostTimestampMs: Long) {
        if (isHost) {
            Timber.d("Sync: Ignored applyTimeSync - is host (should not receive own events)")
            return
        }
        
        // Check if user is actively scrubbing
        val now = System.currentTimeMillis()
        if (isUserScrubbing || (now - scrubbingEndTime) < SCRUBBING_BLOCK_WINDOW_MS) {
            Timber.v("Sync: Ignored applyTimeSync - user is scrubbing")
            return
        }
        
        Timber.v("Sync: applyTimeSync(positionSec=$positionSec)")
        syncEngine?.handleTimeSync(positionSec, hostTimestampMs)
    }
    
    /**
     * Mark that user started scrubbing (blocks incoming seeks temporarily)
     */
    fun onUserScrubbingStart() {
        isUserScrubbing = true
        Timber.d("Sync: User scrubbing started - blocking incoming seeks")
    }
    
    /**
     * Mark that user finished scrubbing (allows incoming seeks after window)
     */
    fun onUserScrubbingEnd() {
        isUserScrubbing = false
        scrubbingEndTime = System.currentTimeMillis()
        Timber.d("Sync: User scrubbing ended - will allow incoming seeks after ${SCRUBBING_BLOCK_WINDOW_MS}ms")
    }
    
    /**
     * Update RTT for sync engine
     */
    fun updateRtt(rtt: Long) {
        syncEngine?.updateRtt(rtt)
    }
    
    /**
     * Update host status (e.g., when user becomes host or stops being host)
     */
    fun updateHostStatus(isHost: Boolean) {
        val wasHost = this.isHost
        this.isHost = isHost
        
        if (wasHost != isHost) {
            // Cancel old jobs
            stateObservationJob?.cancel()
            positionSyncJob?.cancel()
            rateObservationJob?.cancel()
            
            if (isHost) {
                // Start host sync
                setupHostSync()
                Timber.i("Sync: User became host - starting host sync")
            } else {
                // Stop host sync
                Timber.i("Sync: User is no longer host - stopping host sync")
            }
        }
    }
    
    /**
     * Release resources
     */
    fun release() {
        stateObservationJob?.cancel()
        positionSyncJob?.cancel()
        rateObservationJob?.cancel()
        syncEngine?.release()
        syncEngine = null
        Timber.d("PlaybackSyncController: Released")
    }
}

