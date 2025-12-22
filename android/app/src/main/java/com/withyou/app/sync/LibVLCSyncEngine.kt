package com.withyou.app.sync

import com.withyou.app.player.PlayerEngine
import kotlinx.coroutines.*
import timber.log.Timber
import kotlin.math.abs

/**
 * Synchronization engine for LibVLC video playback
 * Implements the sync algorithm with intelligent drift correction strategy:
 * 
 * - Large drift (>600ms): Hard seek for instant correction
 * - Medium drift (100-600ms): Gentle nudge for smooth correction
 * - Small drift (<100ms): Ignored (within natural tolerance window)
 * 
 * This prevents viewer from experiencing jerky jumps when drifts are tiny
 * and provides smooth gradual catch-up for medium drifts.
 * 
 * Now works with PlayerEngine instead of LibVLCVideoPlayer directly.
 * This ensures sync uses the same player instance as the UI.
 */
class LibVLCSyncEngine(
    private val playerEngine: PlayerEngine,
    private val scope: CoroutineScope
) {
    
    // Sync thresholds (in seconds)
    // DRIFT_TOLERANCE: Ignore corrections smaller than this (natural network jitter)
    private val DRIFT_TOLERANCE = 0.1  // 100ms - within this, no correction needed
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
     * When host starts playing, sync viewer to host's position with smart drift tolerance
     */
    fun handleHostPlay(positionSec: Double, hostTimestampMs: Long, playbackRate: Float = 1.0f) {
        val now = System.currentTimeMillis()
        
        // Compute expected position with 0ms sync delay (no network delay compensation)
        val networkDelayMs = 0L  // Set to 0ms for instant sync
        val timeElapsedMs = now - hostTimestampMs
        val expectedPosSec = positionSec + (timeElapsedMs - networkDelayMs) / 1000.0
        
        val currentPosSec = playerEngine.position.value / 1000.0
        val diff = expectedPosSec - currentPosSec
        
        Timber.d("Play sync: expected=$expectedPosSec, current=$currentPosSec, diff=$diff")
        
        when {
            // Skip small drifts - within natural tolerance
            kotlin.math.abs(diff) <= DRIFT_TOLERANCE -> {
                Timber.d("Play sync: Within tolerance, playing normally (diff=$diff <= $DRIFT_TOLERANCE)")
                playerEngine.setRate(playbackRate)
                playerEngine.play()
            }
            // Large difference - hard seek
            kotlin.math.abs(diff) > HARD_SEEK_THRESHOLD -> {
                Timber.i("Play sync: Hard seek (diff=${diff}s > $HARD_SEEK_THRESHOLD)")
                hardSeek(expectedPosSec)
                playerEngine.setRate(playbackRate)
                playerEngine.play()
            }
            // Medium difference - gentle nudge
            else -> {
                Timber.i("Play sync: Nudge (diff=${diff}s)")
                nudge(diff)
                playerEngine.setRate(playbackRate)
                playerEngine.play()
            }
        }
        
        lastSyncTime = now
    }
    
    /**
     * Handle host pause event
     * When host pauses, pause viewer and sync to exact pause position
     */
    fun handleHostPause(positionSec: Double, hostTimestampMs: Long) {
        val now = System.currentTimeMillis()
        val networkDelayMs = 0L  // Set to 0ms for instant sync
        val expectedPosSec = positionSec
        
        val currentPosSec = playerEngine.position.value / 1000.0
        val diff = kotlin.math.abs(expectedPosSec - currentPosSec)
        
        Timber.d("Pause sync: expected=$expectedPosSec, current=$currentPosSec, diff=$diff")
        
        // Pause immediately
        cancelNudge()
        playerEngine.pause()
        
        // If difference is significant (beyond drift tolerance), seek to expected position
        if (diff > DRIFT_TOLERANCE) {
            Timber.i("Pause sync: Seeking to exact position (diff=$diff > $DRIFT_TOLERANCE)")
            hardSeek(expectedPosSec)
        } else {
            Timber.d("Pause sync: Position within tolerance, no seek needed")
        }
        
        lastSyncTime = now
    }
    
    /**
     * Handle host seek event
     * For seek events, we seek directly to the requested position (no elapsed time compensation)
     */
    fun handleHostSeek(positionSec: Double, hostTimestampMs: Long) {
        val now = System.currentTimeMillis()
        
        Timber.d("Seek sync: seeking to exact position=$positionSec (no elapsed time compensation)")
        
        cancelNudge()
        hardSeek(positionSec)
        
        lastSyncTime = now
    }
    
    /**
     * Handle time sync event
     * This is the most frequent sync event (every ~500ms when playing)
     * Uses drift tolerance to avoid unnecessary corrections for tiny drifts
     */
    fun handleTimeSync(positionSec: Double, hostTimestampMs: Long) {
        val now = System.currentTimeMillis()
        
        // Skip if too soon since last sync
        if (now - lastSyncTime < MIN_SYNC_INTERVAL_MS) {
            return
        }
        
        val networkDelayMs = 0L  // Set to 0ms for instant sync
        val timeElapsedMs = now - hostTimestampMs
        val expectedPosSec = positionSec + (timeElapsedMs - networkDelayMs) / 1000.0
        
        val currentPosSec = playerEngine.position.value / 1000.0
        val diff = expectedPosSec - currentPosSec
        
        Timber.d("Time sync: expected=$expectedPosSec, current=$currentPosSec, diff=$diff")
        
        when {
            // Skip small drifts (within tolerance) - natural network jitter
            kotlin.math.abs(diff) <= DRIFT_TOLERANCE -> {
                Timber.v("Time sync: Drift within tolerance (${kotlin.math.abs(diff)}s <= ${DRIFT_TOLERANCE}s), no correction needed")
                // No correction needed - drift is natural
            }
            // Large difference - hard seek
            kotlin.math.abs(diff) > HARD_SEEK_THRESHOLD -> {
                Timber.i("Hard seek: diff=${diff}s (exceeded hard threshold)")
                hardSeek(expectedPosSec)
            }
            // Medium difference - gentle nudge for smooth catch-up
            kotlin.math.abs(diff) > NUDGE_THRESHOLD_MIN -> {
                Timber.i("Nudge: diff=${diff}s (smooth correction)")
                nudge(diff)
            }
        }
        
        lastSyncTime = now
    }
    
    /**
     * Hard seek to position
     */
    private fun hardSeek(positionSec: Double) {
        val positionMs = (positionSec * 1000).toLong()
        playerEngine.seekTo(positionMs)
        Timber.d("Hard seek to: ${positionMs}ms")
    }
    
    /**
     * Gentle nudge using playback rate adjustment
     */
    private fun nudge(diff: Double) {
        cancelNudge()
        
        val rate = if (diff > 0) NUDGE_RATE_FAST else NUDGE_RATE_SLOW
        val originalRate = playerEngine.playbackRate.value // Get current rate
        
        Timber.d("Nudging: diff=$diff, rate=$rate (original=$originalRate)")
        
        // Apply nudge rate
        playerEngine.setRate(rate)
        
        // Restore original rate after nudge duration
        nudgeJob = scope.launch {
            delay(NUDGE_DURATION_MS)
            playerEngine.setRate(originalRate)
            Timber.d("Nudge complete, restored rate: $originalRate")
        }
    }
    
    /**
     * Cancel ongoing nudge
     */
    private fun cancelNudge() {
        nudgeJob?.cancel()
        nudgeJob = null
    }
    
    /**
     * Release resources
     */
    fun release() {
        cancelNudge()
        Timber.d("LibVLCSyncEngine released")
    }
}

