package com.withyou.app.sync

import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import kotlinx.coroutines.*
import timber.log.Timber
import kotlin.math.abs

/**
 * Synchronization engine for video playback
 * Implements the sync algorithm with nudge and hard seek
 */
class SyncEngine(
    private val player: Player,
    private val scope: CoroutineScope
) {
    
    // Sync thresholds (in seconds)
    private val NUDGE_THRESHOLD_MIN = 0.1
    private val NUDGE_THRESHOLD_MAX = 0.6
    private val HARD_SEEK_THRESHOLD = 0.6
    
    // Playback rate adjustments for nudging
    private val NUDGE_RATE_FAST = 1.04f
    private val NUDGE_RATE_SLOW = 0.96f
    private val NUDGE_DURATION_MS = 1500L
    
    // RTT (Round Trip Time) in milliseconds
    private var currentRtt = 0L
    
    // Track last sync time to avoid too frequent adjustments
    private var lastSyncTime = 0L
    private val MIN_SYNC_INTERVAL_MS = 200L
    
    private var nudgeJob: Job? = null
    
    /**
     * Update current RTT measurement
     */
    fun updateRtt(rtt: Long) {
        currentRtt = rtt
        Timber.d("RTT updated: ${rtt}ms")
    }
    
    /**
     * Handle host play event
     */
    fun handleHostPlay(positionSec: Double, hostTimestampMs: Long, playbackRate: Float = 1.0f) {
        val now = System.currentTimeMillis()
        
        // Compute expected position with 0ms sync delay (no network delay compensation)
        val networkDelayMs = 0L  // Set to 0ms for instant sync
        val timeElapsedMs = now - hostTimestampMs
        val expectedPosSec = positionSec + (timeElapsedMs - networkDelayMs) / 1000.0
        
        val currentPosSec = player.currentPosition / 1000.0
        val diff = expectedPosSec - currentPosSec
        
        Timber.d("Play sync: expected=$expectedPosSec, current=$currentPosSec, diff=$diff")
        
        when {
            abs(diff) > HARD_SEEK_THRESHOLD -> {
                // Large difference - hard seek
                Timber.i("Hard seek: diff=${diff}s")
                hardSeek(expectedPosSec)
                player.play()
            }
            abs(diff) > NUDGE_THRESHOLD_MIN -> {
                // Small difference - gentle nudge
                Timber.i("Nudge: diff=${diff}s")
                nudge(diff)
                player.play()
            }
            else -> {
                // Within tolerance - just play
                Timber.d("Within tolerance, playing normally")
                player.playbackParameters = PlaybackParameters(playbackRate)
                player.play()
            }
        }
        
        lastSyncTime = now
    }
    
    /**
     * Handle host pause event
     */
    fun handleHostPause(positionSec: Double, hostTimestampMs: Long) {
        val now = System.currentTimeMillis()
        val networkDelayMs = 0L  // Set to 0ms for instant sync
        val expectedPosSec = positionSec
        
        val currentPosSec = player.currentPosition / 1000.0
        val diff = abs(expectedPosSec - currentPosSec)
        
        Timber.d("Pause sync: expected=$expectedPosSec, current=$currentPosSec, diff=$diff")
        
        // Pause immediately
        cancelNudge()
        player.pause()
        
        // Seek if position difference is significant
        if (diff > 0.3) {
            Timber.i("Correcting position on pause: diff=${diff}s")
            hardSeek(expectedPosSec)
        }
        
        lastSyncTime = now
    }
    
    /**
     * Handle host seek event
     */
    fun handleHostSeek(positionSec: Double, hostTimestampMs: Long) {
        val now = System.currentTimeMillis()
        val networkDelayMs = 0L  // Set to 0ms for instant sync
        val timeElapsedMs = now - hostTimestampMs
        val expectedPosSec = positionSec + (timeElapsedMs - networkDelayMs) / 1000.0
        
        Timber.i("Seek: position=${expectedPosSec}s")
        
        cancelNudge()
        hardSeek(expectedPosSec)
        
        lastSyncTime = now
    }
    
    /**
     * Handle periodic time sync from host
     */
    fun handleTimeSync(positionSec: Double, hostTimestampMs: Long, isPlaying: Boolean) {
        val now = System.currentTimeMillis()
        
        // Don't sync too frequently
        if (now - lastSyncTime < MIN_SYNC_INTERVAL_MS) {
            return
        }
        
        if (!isPlaying) {
            return
        }
        
        val networkDelayMs = 0L  // Set to 0ms for instant sync
        val timeElapsedMs = now - hostTimestampMs
        val expectedPosSec = positionSec + (timeElapsedMs - networkDelayMs) / 1000.0
        
        val currentPosSec = player.currentPosition / 1000.0
        val diff = expectedPosSec - currentPosSec
        
        Timber.v("Time sync: expected=$expectedPosSec, current=$currentPosSec, diff=$diff")
        
        when {
            abs(diff) > HARD_SEEK_THRESHOLD -> {
                Timber.w("Large drift detected: ${diff}s, hard seeking")
                hardSeek(expectedPosSec)
            }
            abs(diff) > NUDGE_THRESHOLD_MIN -> {
                Timber.d("Small drift detected: ${diff}s, nudging")
                nudge(diff)
            }
        }
        
        lastSyncTime = now
    }
    
    /**
     * Perform hard seek to exact position
     */
    private fun hardSeek(positionSec: Double) {
        cancelNudge()
        val positionMs = (positionSec * 1000).toLong().coerceAtLeast(0)
        player.seekTo(positionMs)
        player.playbackParameters = PlaybackParameters(1.0f)
    }
    
    /**
     * Apply gentle playback rate nudge
     * @param diff Difference in seconds (positive = behind, negative = ahead)
     */
    private fun nudge(diff: Double) {
        cancelNudge()
        
        val rate = if (diff > 0) NUDGE_RATE_FAST else NUDGE_RATE_SLOW
        player.playbackParameters = PlaybackParameters(rate)
        
        Timber.d("Applying nudge: rate=$rate for ${NUDGE_DURATION_MS}ms")
        
        // Reset to normal speed after nudge duration
        nudgeJob = scope.launch {
            delay(NUDGE_DURATION_MS)
            if (player.isPlaying) {
                player.playbackParameters = PlaybackParameters(1.0f)
                Timber.d("Nudge complete, restored normal speed")
            }
        }
    }
    
    /**
     * Cancel any active nudge
     */
    private fun cancelNudge() {
        nudgeJob?.cancel()
        nudgeJob = null
    }
    
    /**
     * Clean up resources
     */
    fun release() {
        cancelNudge()
    }
}

