package com.withyou.app.player

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import org.videolan.libvlc.interfaces.IVLCVout
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

/**
 * LibVLC Video Player using VLCVideoLayout for proper screen fitting
 * 
 * VLCVideoLayout handles video rendering internally and uses MediaPlayer.ScaleType:
 * - SURFACE_BEST_FIT: Shows whole video with black bars (no distortion)
 * - SURFACE_FILL: Fills screen, may crop/distort
 */
class LibVLCVideoPlayer(
    context: Context,
    private var videoLayout: VLCVideoLayout
) : IVLCVout.Callback, MediaPlayer.EventListener {

    companion object {
        private const val TAG = "LibVLCVideoPlayer"
    }

    // --- Core LibVLC objects ---
    private val libVLC: LibVLC
    private val mediaPlayer: MediaPlayer
    private val context: Context
    
    // Track temp files for cleanup
    private var currentTempFile: File? = null
    
    // Keep ParcelFileDescriptor alive to prevent GC from closing the FD
    private var currentParcelFileDescriptor: ParcelFileDescriptor? = null
    
    // Video dimensions
    private var videoWidth: Int = 0
    private var videoHeight: Int = 0
    private var currentMediaUri: Uri? = null
    
    // Track if video has ended - critical for restarting playback
    private var hasEnded: Boolean = false
    
    // Handler for delayed operations
    private val mainHandler = Handler(Looper.getMainLooper())
    
    // Track if media parsing is in progress (for timeout detection)
    private var mediaParseTimeoutRunnable: Runnable? = null
    
    // Pending restart state - used to apply seek once playing resumes
    private var pendingRestart: Boolean = false
    private var pendingRestartSeek: Long? = null
    private var recoveryAttempted: Boolean = false // Prevent recovery loop
    
    // Timeout handler for restart verification
    private var restartVerificationRunnable: Runnable? = null
    private val RESTART_VERIFICATION_TIMEOUT_MS = 3000L // 3 seconds

    // Simple state callbacks for your ViewModel / UI
    enum class State {
        IDLE, BUFFERING, PLAYING, PAUSED, ENDED, ERROR
    }
    
    enum class VideoScaleMode {
        FIT,    // best fit inside, keep aspect (no overflow) - uses SURFACE_BEST_FIT
        FILL,   // fill screen, crop if needed - uses SURFACE_FILL
        FIT_SCREEN, // force fit to screen dimensions (stretch)
        ORIGINAL // no scaling, use video pixel size
    }

    var onStateChanged: ((State) -> Unit)? = null
    var onPositionChanged: ((timeMs: Long, lengthMs: Long) -> Unit)? = null
    var onVideoSizeChanged: ((width: Int, height: Int) -> Unit)? = null
    var onSurfacesReady: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null // Error callback with message
    var onTracksChanged: (() -> Unit)? = null // Callback when audio/subtitle tracks become available

    init {
        this.context = context.applicationContext
        
        val args = arrayListOf(
            "--file-caching=300",
            "--network-caching=300",
            "--clock-jitter=0",
            "--clock-synchro=0",
            "--drop-late-frames",
            "--skip-frames"
            // Note: Not using --avcodec-hw=any as it might cause black screen issues
            // Hardware acceleration will be enabled per-media if supported
        )

        libVLC = LibVLC(this.context, args)
        mediaPlayer = MediaPlayer(libVLC).apply {
            setEventListener(this@LibVLCVideoPlayer)
        }

        // VLCVideoLayout handles the SurfaceView internally
        // We need to attach the MediaPlayer to VLCVideoLayout
        attachToVLCVideoLayout(videoLayout)

        Timber.i("$TAG: LibVLC + MediaPlayer created, attaching to VLCVideoLayout")
        onStateChanged?.invoke(State.IDLE)
    }
    
    /**
     * Attach MediaPlayer to VLCVideoLayout
     * VLCVideoLayout is passed directly - no SurfaceView needed!
     */
    private fun attachToVLCVideoLayout(layout: VLCVideoLayout) {
        // Wait for layout to be attached to window and laid out
        if (layout.isAttachedToWindow && layout.width > 0 && layout.height > 0) {
            doAttachToVLCVideoLayout(layout)
        } else {
            // Wait for layout to be ready
            layout.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (layout.isAttachedToWindow && layout.width > 0 && layout.height > 0) {
                        layout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        doAttachToVLCVideoLayout(layout)
                    }
                }
            })
        }
    }
    
    /**
     * Actually attach MediaPlayer to VLCVideoLayout
     * Uses MediaPlayer.attachViews() - VLCVideoLayout is passed directly, NO SurfaceView needed!
     */
    private fun doAttachToVLCVideoLayout(layout: VLCVideoLayout, retryCount: Int = 0) {
        Timber.d("$TAG: doAttachToVLCVideoLayout attempt ${retryCount + 1}, isAttached=${layout.isAttachedToWindow}, size=${layout.width}x${layout.height}")
        
        try {
            // CRITICAL: Use MediaPlayer.attachViews() with VLCVideoLayout directly
            // This is the proper way - no SurfaceView needed at all!
            // Parameters: (videoLayout, subtitlesSurface, useSubtitles, useChapters)
            mediaPlayer.attachViews(layout, null, false, false)
            
            // Add callback for video output events
            mediaPlayer.vlcVout.addCallback(this)
            
            // Set default scale type to SURFACE_BEST_FIT (fit with black bars, no distortion)
            mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
            
            Timber.i("$TAG: ✅✅✅ MediaPlayer attached to VLCVideoLayout using attachViews() - NO SurfaceView!")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: ❌ Error attaching MediaPlayer to VLCVideoLayout: ${e.message}")
            Timber.e(e, "$TAG: Exception details", e)
            if (retryCount < 5) {
                layout.postDelayed({
                    doAttachToVLCVideoLayout(layout, retryCount + 1)
                }, 500)
                Timber.d("$TAG: Retrying attachment... (attempt ${retryCount + 2}/5)")
            } else {
                Timber.e("$TAG: ❌❌❌ Failed to attach after ${retryCount + 1} attempts")
            }
        }
    }

    // --- Public API ---

    /**
     * Set media URI for playback.
     * 
     * For content:// URIs, we use ParcelFileDescriptor to get a file descriptor.
     * We keep the PFD alive to prevent GC from closing it while LibVLC is using it.
     */
    fun setMediaUri(uri: Uri) {
        Timber.i("$TAG: setMediaUri called with URI=$uri, scheme=${uri.scheme}")

        // Stop & release any existing media properly
        // Wrap in try-catch to handle cases where MediaPlayer native object is invalid
        try {
            // Check if MediaPlayer is in a valid state before calling stop()
            // If media is null or player is already stopped, skip stop()
            val hasMedia = try {
                mediaPlayer.media != null
            } catch (e: Exception) {
                Timber.w(e, "$TAG: Error checking media in setMediaUri")
                false
            }
            
            if (hasMedia) {
                try {
                    mediaPlayer.stop()
                } catch (e: IllegalStateException) {
                    // MediaPlayer native object might be invalid - log and continue
                    Timber.w(e, "$TAG: MediaPlayer.stop() failed (native object invalid?), continuing anyway")
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: Error calling stop() in setMediaUri")
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Error in setMediaUri cleanup phase")
        }
        
        // Release existing media
        try {
            mediaPlayer.media?.let { media ->
                try {
                    media.setEventListener(null)
                    media.release()
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: Error releasing media in setMediaUri")
                }
            }
            mediaPlayer.media = null
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Error accessing media in setMediaUri")
        }
        
        // Clean up previous temp file if any
        currentTempFile?.let { file ->
            try {
                if (file.exists()) {
                    file.delete()
                    Timber.d("$TAG: Deleted previous temp file: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error deleting previous temp file")
            }
            currentTempFile = null
        }
        
        // Close previous PFD if any
        try {
            currentParcelFileDescriptor?.close()
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Error closing previous ParcelFileDescriptor")
        }
        currentParcelFileDescriptor = null

        val media: Media = when (uri.scheme) {
            "content" -> {
                // For content:// URIs, try ParcelFileDescriptor first
                try {
                    val contentResolver: ContentResolver = context.contentResolver
                    val pfd: ParcelFileDescriptor? = contentResolver.openFileDescriptor(uri, "r")
                    
                    if (pfd != null) {
                        Timber.i("$TAG: Using FileDescriptor from ParcelFileDescriptor (fd=${pfd.fileDescriptor})")
                        
                        // Keep reference so GC doesn't close it
                        currentParcelFileDescriptor = pfd
                        
                        Media(libVLC, pfd.fileDescriptor).apply {
                            setHWDecoderEnabled(true, false)
                            addOption(":file-caching=300")
                            addOption(":network-caching=300")
                        }
                    } else {
                        // Fallback: try Uri directly (LibVLC may handle it)
                        Timber.w("$TAG: ParcelFileDescriptor failed, trying Uri directly")
                        Media(libVLC, uri).apply {
                            setHWDecoderEnabled(true, false)
                            addOption(":file-caching=300")
                            addOption(":network-caching=300")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Error opening ParcelFileDescriptor, trying Uri directly")
                    // Fallback: try Uri directly
                    Media(libVLC, uri).apply {
                        setHWDecoderEnabled(true, false)
                        addOption(":file-caching=300")
                        addOption(":network-caching=300")
                    }
                }
            }
            
            "file" -> {
                // Direct file:// uri is fine
                Media(libVLC, uri).apply {
                    setHWDecoderEnabled(true, false)
                    addOption(":file-caching=300")
                    addOption(":network-caching=300")
                }
            }
            
            else -> {
                // Network or other schemes: just pass the Uri
                Media(libVLC, uri).apply {
                    setHWDecoderEnabled(true, false)
                    addOption(":file-caching=300")
                    addOption(":network-caching=300")
                }
            }
        }

        mediaPlayer.media = media
        
        // Parse async to get duration/metadata
        try {
            media.parseAsync()
            Timber.d("$TAG: Called media.parseAsync() to get metadata")
        } catch (e: Exception) {
            Timber.w(e, "$TAG: media.parseAsync() failed")
        }
        
        media.release()
        
        // Store URI for dimension extraction
        currentMediaUri = uri
        
        // Extract video dimensions asynchronously (don't block playback)
        extractVideoDimensions(uri)

        val initialLength = mediaPlayer.length
        Timber.i("$TAG: Media set on player (length=$initialLength), URI=$uri, scheme=${uri.scheme}")
        
        // Set a guard: if no length or opening within 3s -> log warning
        mediaParseTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        mediaParseTimeoutRunnable = Runnable {
            val length = mediaPlayer.length
            if (length <= 0L) {
                Timber.w("$TAG: Media length still unknown after 3s timeout, URI=$uri")
                // If libvlc hasn't signaled Opening/LengthChanged, there may be an issue
                // But don't treat as error yet - let EncounteredError handle it
            } else {
                Timber.d("$TAG: Media length resolved: $length ms")
            }
        }
        mainHandler.postDelayed(mediaParseTimeoutRunnable!!, 3000)
        
        // Do NOT mark as prepared yourself – LibVLC will emit events
    }
    
    /**
     * Extract video dimensions using MediaMetadataRetriever
     * Runs on a background thread to avoid blocking
     */
    private fun extractVideoDimensions(uri: Uri) {
        Thread {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                
                val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                
                retriever.release()
                
                val width = widthStr?.toIntOrNull() ?: 0
                val height = heightStr?.toIntOrNull() ?: 0
                
                if (width > 0 && height > 0 && (width != videoWidth || height != videoHeight)) {
                    videoWidth = width
                    videoHeight = height
                    Timber.d("$TAG: Video size extracted = ${width}x$height")
                    // Call callback on main thread
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        onVideoSizeChanged?.invoke(width, height)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e, "$TAG: Could not extract video dimensions from URI")
            }
        }.start()
    }

    /**
     * Check if video is at end by time (only when duration is known)
     */
    private fun isAtEndByTime(): Boolean {
        val dur = mediaPlayer.length
        if (dur <= 0L) return false // Don't trust duration when unknown
        val pos = mediaPlayer.time
        return pos >= dur - 500 || (pos > 0 && pos * 100 >= dur * 99)
    }
    
    /**
     * Check if we should restart - either hasEnded flag is set, or we're at end by time
     * Note: We do NOT check pendingRestart here - if a restart is already pending, we should
     * let it complete rather than starting a new restart cycle
     */
    private fun shouldRestart(): Boolean {
        // If hasEnded flag is set, we should restart
        if (hasEnded) return true
        // Check if we're at end by time
        return isAtEndByTime()
    }
    
    /**
     * Helper for debugging pending state
     */
    private fun dumpPendingState(prefix: String = "") {
        val mpMedia = try { mediaPlayer.media != null } catch (e: Exception) { false }
        val time = try { mediaPlayer.time } catch (e: Exception) { -1L }
        val atEndByTime = isAtEndByTime()
        Timber.d("$TAG: $prefix pendingRestart=$pendingRestart pendingRestartSeek=$pendingRestartSeek hasEnded=$hasEnded atEndByTime=$atEndByTime position=$time mediaPresent=$mpMedia")
    }
    
    fun play() {
        // #region agent log
        try {
            val logFile = File(context.filesDir.parentFile, ".cursor/debug.log")
            PrintWriter(FileWriter(logFile, true)).use { pw ->
                pw.println("""{"sessionId":"debug-session","runId":"run1","hypothesisId":"A","location":"LibVLCVideoPlayer.kt:333","message":"play() entry","data":{"hasEnded":$hasEnded,"isPlaying":${mediaPlayer.isPlaying},"hasMedia":${mediaPlayer.media != null},"position":${mediaPlayer.time},"duration":${mediaPlayer.length}},"timestamp":${System.currentTimeMillis()}}""")
            }
        } catch (e: Exception) {}
        // #endregion
        
        val position = mediaPlayer.time
        val duration = mediaPlayer.length
        val atEndByTime = isAtEndByTime()
        
        Timber.d(
            "$TAG: play() called, isPlaying=${mediaPlayer.isPlaying}, " +
            "hasMedia=${mediaPlayer.media != null}, hasEnded=$hasEnded, " +
            "position=$position, duration=$duration, atEndByTime=$atEndByTime"
        )
        
        // If we know the player ended OR we are at the end (and duration known) -> restart from beginning
        // Use Option B: stop() + pendingRestart pattern (robust, avoids race conditions)
        // But if a restart is already pending, don't start a new one - let the current one complete
        if (shouldRestart() && !pendingRestart) {
            Timber.d("$TAG: play() called while ended/near-end. hasEnded=$hasEnded atEndByTime=$atEndByTime")

            // Capture state BEFORE calling stop() because mediaPlayer.time will often become 0 after stop()
            val wasEnded = hasEnded
            val capturedPosition = try { position } catch (e: Exception) { 0L } // position is a getter that uses mediaPlayer.time
            val mediaUriSafe = currentMediaUri // read into local var for Kotlin smart-cast safety

            // Decide the desired seek:
            // - if seek already requested (e.g., from seekTo), preserve it
            // - else if the video actually reached End (wasEnded) -> restart from 0
            // - else (near-end) -> preserve capturedPosition (user might have seeked near-end)
            val desiredSeek = when {
                pendingRestartSeek != null -> pendingRestartSeek!!
                wasEnded -> 0L
                else -> capturedPosition.coerceAtLeast(0L)
            }

            // Set pending BEFORE stop() — crucial to avoid races where events clear the seek
            pendingRestart = true
            pendingRestartSeek = desiredSeek
            Timber.d("$TAG: Scheduling restart (pre-stop). desiredSeek=$desiredSeek (wasEnded=$wasEnded)")

            // Stop the player to force restart semantics (you can choose to avoid stop() if you want Option A).
            hasEnded = false
            try {
                mediaPlayer.stop()
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error calling stop() before restart: ${e.message}")
            }

            // Now re-initialize media if it was cleared by stop(), then call play().
            mainHandler.post {
                try {
                    dumpPendingState("restart: before media check")
                    
                    if (mediaPlayer.media == null && mediaUriSafe != null) {
                        Timber.d("$TAG: Media cleared by stop(); re-setting mediaUri for restart: $mediaUriSafe")
                        setMediaUri(mediaUriSafe)
                        // Give a small delay for media to be set
                        mainHandler.postDelayed({
                            attemptRestartPlay(mediaUriSafe)
                        }, 100)
                    } else {
                        attemptRestartPlay(mediaUriSafe)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "$TAG: Failed to schedule restart: ${e.message}")
                    // avoid stuck states
                    pendingRestart = false
                    pendingRestartSeek = null
                    cancelRestartVerification()
                }
            }
            return
        } else if (pendingRestart) {
            // A restart is already in progress, just log and return
            Timber.d("$TAG: play() called but restart already pending - waiting for Playing event")
            dumpPendingState("play() called while restart pending")
            return
        }
        
        // If length unknown and not ended, just call play() — don't treat as end
        if (!mediaPlayer.isPlaying) {
            try {
                Timber.d("$TAG: Normal play() call - not ended, not pending restart")
                mediaPlayer.play()
                // Verify playback started after a short delay
                mainHandler.postDelayed({
                    if (!mediaPlayer.isPlaying && !pendingRestart) {
                        Timber.w("$TAG: play() called but player not playing after delay. State check:")
                        dumpPendingState("play() verification failed")
                    }
                }, 500)
            } catch (e: Exception) {
                Timber.e(e, "$TAG: play() failed")
                dumpPendingState("play() exception")
            }
        } else {
            Timber.d("$TAG: play() called but already playing")
        }
    }
    
    /**
     * Helper to attempt restart playback with verification
     */
    private fun attemptRestartPlay(mediaUriSafe: Uri?) {
        try {
            dumpPendingState("attemptRestartPlay: before play()")
            
            // Verify media is set
            if (mediaPlayer.media == null && mediaUriSafe != null) {
                Timber.w("$TAG: Media still null in attemptRestartPlay, re-setting")
                setMediaUri(mediaUriSafe)
                // Retry after media is set
                mainHandler.postDelayed({
                    attemptRestartPlay(mediaUriSafe)
                }, 150)
                return
            }
            
            // Kick off play() — the Playing event will apply pendingRestartSeek.
            try {
                Timber.d("$TAG: Calling mediaPlayer.play() for restart")
                mediaPlayer.play()
                
                // Schedule verification that Playing event fires
                scheduleRestartVerification()
            } catch (e: Exception) {
                Timber.e(e, "$TAG: mediaPlayer.play() failed while scheduling restart: ${e.message}")
                dumpPendingState("play() exception in restart")
                // Clear pending on failure to allow retry
                pendingRestart = false
                pendingRestartSeek = null
                cancelRestartVerification()
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: attemptRestartPlay exception: ${e.message}")
            dumpPendingState("attemptRestartPlay exception")
            pendingRestart = false
            pendingRestartSeek = null
            cancelRestartVerification()
        }
    }
    
    /**
     * Schedule verification that restart actually started playing
     */
    private fun scheduleRestartVerification() {
        cancelRestartVerification()
        restartVerificationRunnable = Runnable {
            if (pendingRestart && !mediaPlayer.isPlaying) {
                Timber.e("$TAG: RESTART VERIFICATION FAILED - Playing event did not fire within timeout!")
                dumpPendingState("restart verification timeout")
                
                // Try to recover - force play again
                val mediaUriSafe = currentMediaUri
                if (mediaUriSafe != null) {
                    Timber.w("$TAG: Attempting recovery play() after verification timeout")
                    try {
                        if (mediaPlayer.media == null) {
                            Timber.w("$TAG: Media is null, re-setting")
                            setMediaUri(mediaUriSafe)
                            mainHandler.postDelayed({
                                try {
                                    mediaPlayer.play()
                                    scheduleRestartVerification()
                                } catch (e: Exception) {
                                    Timber.e(e, "$TAG: Recovery play() failed")
                                    pendingRestart = false
                                    pendingRestartSeek = null
                                }
                            }, 200)
                        } else {
                            mediaPlayer.play()
                            scheduleRestartVerification()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "$TAG: Recovery attempt failed: ${e.message}")
                        pendingRestart = false
                        pendingRestartSeek = null
                    }
                } else {
                    Timber.e("$TAG: Cannot recover - no media URI")
                    pendingRestart = false
                    pendingRestartSeek = null
                }
            } else if (pendingRestart && mediaPlayer.isPlaying) {
                Timber.d("$TAG: Restart verification passed - player is playing")
            }
        }
        mainHandler.postDelayed(restartVerificationRunnable!!, RESTART_VERIFICATION_TIMEOUT_MS)
    }
    
    /**
     * Cancel restart verification timeout
     */
    private fun cancelRestartVerification() {
        restartVerificationRunnable?.let {
            mainHandler.removeCallbacks(it)
            restartVerificationRunnable = null
        }
    }

    fun pause() {
        Timber.d("$TAG: pause() called")
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            onStateChanged?.invoke(State.PAUSED)
        }
    }

    fun stop() {
        Timber.d("$TAG: stop() called")
        mediaPlayer.stop()
        onStateChanged?.invoke(State.IDLE)
    }

    fun togglePlayPause() {
        if (mediaPlayer.isPlaying) pause() else play()
    }

    fun seekBy(deltaMs: Long) {
        val length = mediaPlayer.length
        val newTime = if (length > 0) {
            (mediaPlayer.time + deltaMs).coerceIn(0, length)
        } else {
            (mediaPlayer.time + deltaMs).coerceAtLeast(0)
        }
        Timber.d("$TAG: seekBy($deltaMs) -> $newTime (length=$length)")
        mediaPlayer.time = newTime
    }

    fun seekTo(positionMs: Long) {
        val length = mediaPlayer.length
        val currentPos = mediaPlayer.time
        val wasAtEnd = hasEnded || (length > 0 && currentPos >= length - 500)
        
        Timber.d(
            "$TAG: seekTo($positionMs) -> " +
            "(length=$length, current=$currentPos, hasEnded=$hasEnded, wasAtEnd=$wasAtEnd)"
        )
        
        // If duration unknown, still allow seek but don't coerce to duration
        val safePos = if (length > 0L) {
            positionMs.coerceIn(0L, length)
        } else {
            positionMs.coerceAtLeast(0L)
        }
        
        // Normal seek (not in ENDED state)
        if (!wasAtEnd) {
            try {
                mediaPlayer.time = safePos
                Timber.d("$TAG: seekTo() normal seek to $safePos")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: seekTo() failed (normal case)")
            }
            return
        }
        
        // seekTo() ended/near-end case
        val atEndByTime = wasAtEnd
        Timber.d("$TAG: seekTo($positionMs) called while ended/near-end. hasEnded=$hasEnded atEndByTime=$atEndByTime")
        // safePos already calculated above - reuse it
        val mediaUriSafe = currentMediaUri

        // Set pending BEFORE stop() to preserve the seek
        pendingRestart = true
        pendingRestartSeek = safePos
        dumpPendingState("seekTo pre-stop (scheduling restart+seek)")

        hasEnded = false
        try {
            mediaPlayer.stop()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error calling stop() before restart on seek: ${e.message}")
        }

        mainHandler.post {
            try {
                dumpPendingState("seekTo: after stop, before play")
                if (mediaPlayer.media == null && mediaUriSafe != null) {
                    Timber.d("$TAG: seekTo: media cleared; re-set mediaUri for restart")
                    setMediaUri(mediaUriSafe)
                    // Give a small delay for media to be set
                    mainHandler.postDelayed({
                        attemptRestartPlay(mediaUriSafe)
                    }, 100)
                } else {
                    attemptRestartPlay(mediaUriSafe)
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to schedule restart+seek after end: ${e.message}")
                dumpPendingState("seekTo: exception")
                pendingRestart = false
                pendingRestartSeek = null
                cancelRestartVerification()
            }
        }
    }

    fun setRate(rate: Float) {
        Timber.i("$TAG: setRate($rate)")
        mediaPlayer.rate = rate
    }

    /**
     * Set video scale mode using MediaPlayer.ScaleType
     * This is the proper way to handle video fitting on Android
     */
    fun setScaleMode(mode: VideoScaleMode, surfaceWidth: Int = 0, surfaceHeight: Int = 0) {
        when (mode) {
            VideoScaleMode.FIT -> {
                // FIT: setAspectRatio(null) + setScale(0f) for best fit
                mediaPlayer.aspectRatio = null
                mediaPlayer.scale = 0f
                Timber.d("$TAG: Scale mode = FIT (aspectRatio=null, scale=0f)")
            }
            VideoScaleMode.FILL -> {
                // FILL: Force video to fill the surface using aspect ratio (this effectively stretches/zooms)
                val w = if (surfaceWidth > 0) surfaceWidth + 4 else videoLayout.width + 4
                val h = if (surfaceHeight > 0) surfaceHeight + 4 else videoLayout.height + 4
                
                if (w > 0 && h > 0) {
                    mediaPlayer.aspectRatio = "$w:$h"
                    mediaPlayer.scale = 0f
                    Timber.d("$TAG: Scale mode = FILL (aspectRatio=$w:$h, scale=0f)")
                } else {
                    mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_FILL)
                    Timber.d("$TAG: Scale mode = FILL (SURFACE_FILL fallback)")
                }
            }
            VideoScaleMode.FIT_SCREEN -> {
                // FIT_SCREEN: Analyzes phone screen size and fits content perfectly (Stretch)
                val w = if (surfaceWidth > 0) surfaceWidth else videoLayout.width
                val h = if (surfaceHeight > 0) surfaceHeight else videoLayout.height
                
                if (w > 0 && h > 0) {
                    // Force aspect ratio to match screen exactly
                    mediaPlayer.aspectRatio = "$w:$h"
                    mediaPlayer.scale = 0f
                    Timber.d("$TAG: Scale mode = FIT_SCREEN (aspectRatio=$w:$h)")
                } else {
                    Timber.w("$TAG: Cannot set FIT_SCREEN - dimensions unknown")
                }
            }
            VideoScaleMode.ORIGINAL -> {
                // Use video's original aspect ratio
                if (videoWidth > 0 && videoHeight > 0) {
                    mediaPlayer.aspectRatio = "${videoWidth}:${videoHeight}"
                    mediaPlayer.scale = 0f
                    Timber.d("$TAG: Scale mode = ORIGINAL (aspectRatio=${videoWidth}:${videoHeight}, scale=0f)")
                } else {
                    Timber.w("$TAG: Cannot set ORIGINAL mode - video size unknown, using FIT")
                    mediaPlayer.aspectRatio = null
                    mediaPlayer.scale = 0f
                }
            }
        }
    }
    
    /**
     * Set custom aspect ratio (e.g., "16:9", "4:3")
     * Used for fixed aspect ratio options from settings
     */
    fun setCustomAspectRatio(ratio: String) {
        mediaPlayer.aspectRatio = ratio
        mediaPlayer.scale = 0f // Auto-fit the ratio inside surface
        Timber.d("$TAG: Custom aspect ratio = $ratio, scale=0f")
    }
    
    val videoSize: Pair<Int, Int>
        get() = Pair(videoWidth, videoHeight)

    val duration: Long
        get() = mediaPlayer.length

    val position: Long
        get() = mediaPlayer.time

    val isPlaying: Boolean
        get() = mediaPlayer.isPlaying
    
    /**
     * Get available audio tracks
     * Following VLC's pattern: check if media is available and not released
     */
    fun getAudioTracks(): List<MediaPlayer.TrackDescription> {
        return try {
            // VLC pattern: check if mediaplayer has media and is not released
            if (mediaPlayer.media == null) {
                Timber.d("$TAG: getAudioTracks() -> no media, returning empty list")
                return emptyList()
            }
            
            // audioTracks can be null - check before calling toList()
            val audioTracks = mediaPlayer.audioTracks
            if (audioTracks == null) {
                Timber.d("$TAG: getAudioTracks() -> audioTracks is null, returning empty list")
                return emptyList()
            }
            
            val tracks = audioTracks.toList()
            Timber.d("$TAG: getAudioTracks() -> found ${tracks.size} tracks")
            tracks.forEachIndexed { index, track ->
                Timber.d("$TAG:   Audio track $index: id=${track.id}, name='${track.name}'")
            }
            tracks
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error getting audio tracks: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get available subtitle tracks
     * Following VLC's pattern: check if media is available and not released
     */
    fun getSubtitleTracks(): List<MediaPlayer.TrackDescription> {
        return try {
            // VLC pattern: check if mediaplayer has media and is not released
            if (mediaPlayer.media == null) {
                Timber.d("$TAG: getSubtitleTracks() -> no media, returning empty list")
                return emptyList()
            }
            
            // spuTracks can be null - check before calling toList()
            val spuTracks = mediaPlayer.spuTracks
            if (spuTracks == null) {
                Timber.d("$TAG: getSubtitleTracks() -> spuTracks is null, returning empty list")
                return emptyList()
            }
            
            val tracks = spuTracks.toList()
            Timber.d("$TAG: getSubtitleTracks() -> found ${tracks.size} tracks")
            tracks.forEachIndexed { index, track ->
                Timber.d("$TAG:   Subtitle track $index: id=${track.id}, name='${track.name}'")
            }
            tracks
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error getting subtitle tracks: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get current audio track ID
     */
    fun getCurrentAudioTrack(): Int {
        return try {
            mediaPlayer.audioTrack
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error getting current audio track")
            -1
        }
    }
    
    /**
     * Get current subtitle track ID
     */
    fun getCurrentSubtitleTrack(): Int {
        return try {
            mediaPlayer.spuTrack
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error getting current subtitle track")
            -1
        }
    }
    
    /**
     * Set audio track by ID
     */
    fun setAudioTrack(trackId: Int) {
        try {
            mediaPlayer.audioTrack = trackId
            Timber.d("$TAG: Set audio track to $trackId")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error setting audio track")
        }
    }
    
    /**
     * Set subtitle track by ID (-1 to disable)
     */
    fun setSubtitleTrack(trackId: Int) {
        try {
            mediaPlayer.spuTrack = trackId
            Timber.d("$TAG: Set subtitle track to $trackId")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error setting subtitle track")
        }
    }

    // --- Debug helper: HTTP test ---

    fun testWithHttpStream() {
        val testUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        Timber.i("$TAG: testWithHttpStream() using $testUrl")

        mediaPlayer.stop()
        mediaPlayer.media?.release()
        mediaPlayer.media = null

        val media = Media(libVLC, testUrl)
        mediaPlayer.media = media
        media.release()

        mediaPlayer.play()
    }


    // --- LibVLC callbacks ---

    override fun onEvent(event: MediaPlayer.Event) {
        // Log all events with their numeric value for debugging
        val eventName = when (event.type) {
            MediaPlayer.Event.Opening -> "Opening"
            MediaPlayer.Event.Buffering -> "Buffering"
            MediaPlayer.Event.Playing -> "Playing"
            MediaPlayer.Event.Paused -> "Paused"
            MediaPlayer.Event.Stopped -> "Stopped"
            MediaPlayer.Event.EndReached -> "EndReached"
            MediaPlayer.Event.EncounteredError -> "EncounteredError"
            MediaPlayer.Event.TimeChanged -> "TimeChanged"
            MediaPlayer.Event.PositionChanged -> "PositionChanged"
            MediaPlayer.Event.MediaChanged -> "MediaChanged"
            MediaPlayer.Event.ESAdded -> "ESAdded"
            MediaPlayer.Event.ESDeleted -> "ESDeleted"
            MediaPlayer.Event.ESSelected -> "ESSelected"
            else -> "Unknown(${event.type})"
        }
        Timber.d("$TAG: onEvent type=${event.type} ($eventName)")

        when (event.type) {
            MediaPlayer.Event.Opening -> {
                Timber.i("$TAG: Opening media - clearing stale flags")
                // Clear any stale flags when opening new media
                hasEnded = false
                // Reset recovery flag when opening succeeds
                recoveryAttempted = false
                // Clear pending restart if we're opening new media (not a restart)
                // Note: We keep pendingRestart if it was set before Opening (for restart scenarios)
                // Cancel timeout since media is opening
                mediaParseTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                mediaParseTimeoutRunnable = null
                // Only show buffering if we're not already playing
                // This prevents buffering animation from showing on first play if media is ready
                if (!mediaPlayer.isPlaying) {
                    onStateChanged?.invoke(State.BUFFERING)
                }
            }

            MediaPlayer.Event.Buffering -> {
                Timber.d("$TAG: Media buffering...")
                // Only show buffering if we're not already playing
                // The Playing event will clear this state
                if (!mediaPlayer.isPlaying) {
                    onStateChanged?.invoke(State.BUFFERING)
                }
            }

            MediaPlayer.Event.Playing -> {
                Timber.d("$TAG: Event.Playing received")
                hasEnded = false
                
                // Cancel restart verification since Playing event fired
                cancelRestartVerification()
                
                onStateChanged?.invoke(State.PLAYING)

                // re-attach surface/view if needed (keep existing logic)
                videoLayout.post {
                    try {
                        val vout = mediaPlayer.vlcVout
                        if (!vout.areViewsAttached()) {
                            Timber.w("$TAG: Views not attached during Playing event, re-attaching.")
                            mediaPlayer.attachViews(videoLayout, null, false, false)
                            mediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
                            Timber.i("$TAG: Re-attached VLCVideoLayout during Playing event")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "$TAG: Error re-attaching during Playing event")
                    }
                }

                // Apply pending restart/seek (only when player is actually playing)
                if (pendingRestart) {
                    Timber.d("$TAG: Playing event: pendingRestart=true, applying seek")
                    dumpPendingState("Playing event: before applying seek")
                    try {
                        val seek = pendingRestartSeek ?: 0L
                        val length = try { mediaPlayer.length } catch (e: Exception) { -1L }
                        val finalSeek = if (length > 0) seek.coerceAtMost(length - 1L).coerceAtLeast(0L) else seek
                        Timber.d("$TAG: Applying pending restart seek=$seek finalSeek=$finalSeek length=$length")

                        if (length > 0) {
                            mediaPlayer.time = finalSeek
                            Timber.d("$TAG: Applied seek to $finalSeek, clearing pendingRestart")
                            // Clear pending after applying seek
                            pendingRestart = false
                            pendingRestartSeek = null
                            dumpPendingState("Playing event: after applying seek")
                        } else {
                            // If length unknown and finalSeek == 0, set position to 0 to at least start from beginning.
                            if (finalSeek == 0L) {
                                try { 
                                    mediaPlayer.position = 0f 
                                    Timber.d("$TAG: Applied position=0 (length unknown), clearing pendingRestart")
                                } catch (e: Exception) { 
                                    Timber.e(e, "$TAG: Failed to set position=0")
                                }
                                // Clear pending because we applied 0
                                pendingRestart = false
                                pendingRestartSeek = null
                                dumpPendingState("Playing event: after applying position=0")
                            } else {
                                // Wait for LengthChanged to apply the actual seek
                                Timber.d("$TAG: Length unknown; deferring applying pendingRestartSeek ($finalSeek) until length is available")
                                dumpPendingState("Playing event: deferring seek (length unknown)")
                                // Do not clear pending -> will be applied on LengthChanged
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "$TAG: Failed to apply pendingRestartSeek: ${e.message}")
                        dumpPendingState("Playing event: exception applying seek")
                        // clear to avoid stuck state
                        pendingRestart = false
                        pendingRestartSeek = null
                    }
                } else {
                    Timber.d("$TAG: Playing event: no pendingRestart")
                }
                
                // If dimensions weren't extracted yet, try again
                if ((videoWidth == 0 || videoHeight == 0) && currentMediaUri != null) {
                    extractVideoDimensions(currentMediaUri!!)
                }
                
                // Refresh tracks when playing starts (tracks should be available by now)
                // VLC pattern: tracks are available after Playing event
                mainHandler.postDelayed({
                    Timber.d("$TAG: Playing event: refreshing tracks after delay")
                    onTracksChanged?.invoke()
                }, 1000) // Longer delay to ensure all ES streams are registered
            }

            MediaPlayer.Event.Paused -> {
                Timber.d("$TAG: Media paused")
                // Clear end flag when paused (user can still seek/play)
                hasEnded = false
                onStateChanged?.invoke(State.PAUSED)
            }

            MediaPlayer.Event.Stopped -> {
                Timber.d("$TAG: Event.Stopped received. leaving pendingRestart=${pendingRestart} pendingRestartSeek=${pendingRestartSeek}")
                hasEnded = false
                onStateChanged?.invoke(State.IDLE)
            }

            MediaPlayer.Event.EndReached -> {
                Timber.d("$TAG: Event.EndReached received")
                hasEnded = true
                // Keep pendingRestart untouched. UI state may be updated to ENDED.
                onStateChanged?.invoke(State.ENDED)
            }

            MediaPlayer.Event.TimeChanged -> {
                val currentTime = mediaPlayer.time
                val length = mediaPlayer.length
                onPositionChanged?.invoke(currentTime, length)
                
                // Detect if user manually dragged to end (without EndReached event)
                if (length > 0 && currentTime >= length - 100 && !hasEnded && !pendingRestart) {
                    Timber.d("$TAG: TimeChanged: detected near-end position (${currentTime}ms / ${length}ms) without EndReached")
                    // Don't set hasEnded here - let natural EndReached handle it
                    // But log for debugging
                }
            }

            MediaPlayer.Event.EncounteredError -> {
                Timber.e("$TAG: EncounteredError from LibVLC - URI=$currentMediaUri, recoveryAttempted=$recoveryAttempted")
                hasEnded = false
                // Clear pending restart on error
                pendingRestart = false
                pendingRestartSeek = null
                // Cancel timeout
                mediaParseTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
                mediaParseTimeoutRunnable = null
                
                // Ensure player is reset to a clean state
                try {
                    mediaPlayer.stop()
                } catch (e: Exception) {
                    Timber.w(e, "$TAG: Error calling stop() after EncounteredError")
                }
                
                // Release current media properly to avoid finalization errors
                mediaPlayer.media?.let { media ->
                    try {
                        media.setEventListener(null)
                        media.release()
                    } catch (e: Exception) {
                        Timber.w(e, "$TAG: Error releasing media after EncounteredError")
                    }
                }
                mediaPlayer.media = null
                
                // Attempt recovery only once to prevent infinite loop
                val mediaUri = currentMediaUri // Store in local variable for smart cast
                if (mediaUri != null && !recoveryAttempted) {
                    recoveryAttempted = true
                    try {
                        Timber.i("$TAG: Attempting recovery - re-setting media from URI")
                        // Re-set media using the same logic as setMediaUri() to handle content:// properly
                        setMediaUri(mediaUri)
                        Timber.i("$TAG: Recovery media re-set, waiting for Opening event")
                    } catch (e: Exception) {
                        Timber.e(e, "$TAG: Recovery attempt failed", e)
                        recoveryAttempted = false // Reset on failure so user can retry
                        val errorMsg = "Playback error occurred"
                        onStateChanged?.invoke(State.ERROR)
                        onError?.invoke(errorMsg)
                    }
                } else {
                    // Recovery already attempted or no URI - report error
                    if (recoveryAttempted) {
                        Timber.e("$TAG: Recovery already attempted, not retrying to prevent loop")
                    }
                    recoveryAttempted = false // Reset for next attempt
                    val errorMsg = "Playback error occurred"
                    onStateChanged?.invoke(State.ERROR)
                    onError?.invoke(errorMsg)
                }
            }
            
            MediaPlayer.Event.LengthChanged -> {
                val newLen = try { mediaPlayer.length } catch (e: Exception) { -1L }
                Timber.d("$TAG: Event.LengthChanged received length=$newLen")
                // Duration is now known, update position callback
                onPositionChanged?.invoke(mediaPlayer.time, newLen)
                
                // If we couldn't apply pending because length was unknown, try again now
                if (pendingRestart && pendingRestartSeek != null) {
                    // Apply seek now on main thread to be safe
                    mainHandler.post {
                        try {
                            val seek = pendingRestartSeek ?: 0L
                            val length = try { mediaPlayer.length } catch (e: Exception) { -1L }
                            val finalSeek = if (length > 0) seek.coerceAtMost(length - 1L).coerceAtLeast(0L) else seek
                            Timber.d("$TAG: LengthChanged: applying delayed pendingRestartSeek finalSeek=$finalSeek length=$length")
                            if (length > 0) {
                                mediaPlayer.time = finalSeek
                                pendingRestart = false
                                pendingRestartSeek = null
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "$TAG: Error applying pendingRestartSeek on LengthChanged: ${e.message}")
                        }
                    }
                }
            }
            
            MediaPlayer.Event.ESAdded -> {
                Timber.d("$TAG: Event.ESAdded - Elementary stream added, tracks may be available now")
                // Notify that tracks might be available - refresh them with a small delay
                // VLC pattern: tracks become available after ES streams are added
                mainHandler.postDelayed({
                    Timber.d("$TAG: ESAdded: refreshing tracks after delay")
                    onTracksChanged?.invoke()
                }, 200) // Small delay to ensure tracks are registered
            }
            
            MediaPlayer.Event.ESSelected -> {
                Timber.d("$TAG: Event.ESSelected - Elementary stream selected, tracks may be available now")
                // Notify that tracks might be available - refresh them with a small delay
                mainHandler.postDelayed({
                    Timber.d("$TAG: ESSelected: refreshing tracks after delay")
                    onTracksChanged?.invoke()
                }, 200)
            }
            
            MediaPlayer.Event.ESDeleted -> {
                Timber.d("$TAG: Event.ESDeleted - Elementary stream deleted")
                // Tracks might have changed
                mainHandler.postDelayed({
                    Timber.d("$TAG: ESDeleted: refreshing tracks after delay")
                    onTracksChanged?.invoke()
                }, 200)
            }

            else -> {
                // Log unknown events for debugging
                Timber.d("$TAG: Unhandled event type=${event.type} ($eventName)")
            }
        }
    }

    override fun onSurfacesCreated(vlcVout: IVLCVout) {
        Timber.i("$TAG: onSurfacesCreated")
    }

    override fun onSurfacesDestroyed(vlcVout: IVLCVout) {
        Timber.i("$TAG: onSurfacesDestroyed")
    }


    /**
     * Create a temporary file from content URI (fallback when ParcelFileDescriptor fails)
     */
    private fun createTempFileFromUri(uri: Uri): File? {
        return try {
            val tempDir = File(context.cacheDir, "temp_video")
            if (!tempDir.exists()) {
                tempDir.mkdirs()
            }
            
            // Clean up old temp files (older than 1 hour)
            cleanupOldTempFiles(tempDir, 3600000L)
            
            val tempFile = File(tempDir, "temp_video_${System.currentTimeMillis()}.tmp")
            Timber.i("$TAG: Copying content URI to temp file: ${tempFile.absolutePath}")
            
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    val buffer = ByteArray(8192 * 4) // 32KB buffer for faster copying
                    var bytesRead: Int
                    var totalBytes = 0L
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytes += bytesRead
                        if (totalBytes % (10 * 1024 * 1024) == 0L) { // Log every 10MB
                            Timber.d("$TAG: Copied ${totalBytes / (1024 * 1024)}MB to temp file")
                        }
                    }
                }
            }
            
            if (tempFile.exists() && tempFile.length() > 0) {
                Timber.i("$TAG: Successfully created temp file: ${tempFile.absolutePath}, size=${tempFile.length()}")
                tempFile
            } else {
                Timber.e("$TAG: Temp file creation failed or file is empty")
                tempFile.delete()
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error creating temp file from URI")
            null
        }
    }
    
    /**
     * Clean up old temporary files
     */
    private fun cleanupOldTempFiles(tempDir: File, maxAgeMs: Long) {
        try {
            val cutoffTime = System.currentTimeMillis() - maxAgeMs
            tempDir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoffTime) {
                    file.delete()
                    Timber.d("$TAG: Cleaned up old temp file: ${file.name}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error cleaning up temp files")
        }
    }

    /**
     * Re-attach player to a new VLCVideoLayout (e.g., after orientation change)
     */
    fun attachToLayout(newVideoLayout: VLCVideoLayout) {
        Timber.i("$TAG: Re-attaching to new VLCVideoLayout")
        
        // Update reference
        videoLayout = newVideoLayout
        
        // Detach from old layout
        mediaPlayer.detachViews()
        mediaPlayer.vlcVout.removeCallback(this)
        
        // Attach to new VLCVideoLayout using attachViews() - NO SurfaceView!
        attachToVLCVideoLayout(newVideoLayout)
    }
    
    fun release() {
        Timber.i("$TAG: release() called")
        // Cancel timeouts
        mediaParseTimeoutRunnable?.let { mainHandler.removeCallbacks(it) }
        mediaParseTimeoutRunnable = null
        cancelRestartVerification()
        
        mediaPlayer.stop()
        mediaPlayer.detachViews()  // Detach VLCVideoLayout
        mediaPlayer.vlcVout.removeCallback(this)
        mediaPlayer.release()
        libVLC.release()
        
        // Clean up temp file
        currentTempFile?.let { file ->
            try {
                if (file.exists()) {
                    file.delete()
                    Timber.d("$TAG: Deleted temp file: ${file.absolutePath}")
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Error deleting temp file")
            }
            currentTempFile = null
        }
        
        // Close PFD
        try {
            currentParcelFileDescriptor?.close()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error closing ParcelFileDescriptor on release")
        }
        currentParcelFileDescriptor = null
    }
}
