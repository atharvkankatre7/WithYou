package com.withyou.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.withyou.app.ContentSyncApplication
import com.withyou.app.R
import com.withyou.app.player.AudioExtractor
import com.withyou.app.ui.MainActivity
import com.withyou.app.utils.AudioCacheManager
import com.withyou.app.utils.computeSHA256
import com.withyou.app.utils.extractFileMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Foreground service for background audio conversion
 */
class BackgroundAudioConverterService : Service() {
    
    companion object {
        private const val CHANNEL_ID = "audio_converter_channel"
        private const val NOTIFICATION_ID = 1001
        
        private const val ACTION_CONVERT_VIDEO = "com.withyou.app.CONVERT_VIDEO"
        private const val EXTRA_VIDEO_URI = "video_uri"
        private const val EXTRA_VIDEO_PATH = "video_path"
        
        /**
         * Start conversion for a video
         */
        fun startConversion(context: Context, videoUri: Uri, videoPath: String) {
            val intent = Intent(context, BackgroundAudioConverterService::class.java).apply {
                action = ACTION_CONVERT_VIDEO
                putExtra(EXTRA_VIDEO_URI, videoUri.toString())
                putExtra(EXTRA_VIDEO_PATH, videoPath)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
    
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val audioCacheManager = AudioCacheManager(this)
    private var audioExtractor: AudioExtractor? = null
    private var currentConversionJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Timber.i("BackgroundAudioConverterService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONVERT_VIDEO -> {
                val videoUriString = intent.getStringExtra(EXTRA_VIDEO_URI)
                val videoPath = intent.getStringExtra(EXTRA_VIDEO_PATH) ?: ""
                
                if (videoUriString != null) {
                    val videoUri = Uri.parse(videoUriString)
                    startForeground(NOTIFICATION_ID, createNotification("Preparing conversion...", 0))
                    convertVideoAudio(videoUri, videoPath)
                }
            }
        }
        
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    /**
     * Convert video audio in background
     */
    private fun convertVideoAudio(videoUri: Uri, videoPath: String) {
        currentConversionJob?.cancel()
        
        currentConversionJob = serviceScope.launch {
            try {
                Timber.i("Starting background audio conversion for: $videoPath")
                
                // Compute file hash
                val hash = withContext(Dispatchers.IO) {
                    computeSHA256(contentResolver, videoUri)
                }
                
                // Check if already cached
                val cachedTracks = audioCacheManager.getCachedAudioTracks(hash)
                if (cachedTracks.isNotEmpty()) {
                    Timber.i("Audio already cached for this file")
                    stopSelf()
                    return@launch
                }
                
                // Initialize audio extractor
                if (audioExtractor == null) {
                    audioExtractor = AudioExtractor(this@BackgroundAudioConverterService)
                    if (audioExtractor?.initialize() != true) {
                        Timber.e("Failed to initialize audio extractor")
                        stopSelf()
                        return@launch
                    }
                }
                
                // Extract file metadata to check audio codec
                val metadata = withContext(Dispatchers.IO) {
                    extractFileMetadata(this@BackgroundAudioConverterService, videoUri) { progress ->
                        updateNotification("Analyzing video...", progress)
                    }
                }
                
                // Check if audio needs conversion (unsupported formats)
                val needsConversion = metadata.codec.audio.lowercase() in listOf(
                    "eac3", "ac3", "dts", "truehd", "dts-hd"
                )
                
                if (!needsConversion) {
                    Timber.i("Audio format is supported, no conversion needed")
                    stopSelf()
                    return@launch
                }
                
                // Analyze audio tracks
                updateNotification("Analyzing audio tracks...", 10)
                val audioTracks = audioExtractor!!.analyzeAllAudioTracks(videoUri)
                
                if (audioTracks.isEmpty()) {
                    Timber.w("No audio tracks found")
                    stopSelf()
                    return@launch
                }
                
                // Extract all audio tracks
                updateNotification("Converting audio...", 20)
                val extractedTracks = audioExtractor!!.extractAllAudioTracks(videoUri, audioTracks) { progress, completed ->
                    val overallProgress = 20 + ((progress * 0.7).toInt()) // 20-90%
                    updateNotification(
                        "Converting audio track ${completed + 1}/${audioTracks.size}...",
                        overallProgress
                    )
                }
                
                // Save to cache
                updateNotification("Saving to cache...", 90)
                extractedTracks.forEach { (trackIndex, audioUri) ->
                    audioCacheManager.saveAudioToCache(hash, trackIndex, audioUri)
                }
                
                updateNotification("Conversion complete!", 100)
                Timber.i("Background conversion complete: ${extractedTracks.size} tracks cached")
                
                // Wait a bit then stop
                delay(2000)
                stopSelf()
                
            } catch (e: Exception) {
                Timber.e(e, "Error in background audio conversion")
                updateNotification("Conversion failed: ${e.message}", 0)
                delay(3000)
                stopSelf()
            }
        }
    }
    
    /**
     * Update notification
     */
    private fun updateNotification(text: String, progress: Int) {
        val notification = createNotification(text, progress)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Create notification
     */
    private fun createNotification(text: String, progress: Int): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Converting Audio")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // You may need to add this icon
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
    
    /**
     * Create notification channel
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Audio Conversion",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background audio conversion notifications"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        currentConversionJob?.cancel()
        audioExtractor?.release()
        serviceScope.cancel()
        Timber.i("BackgroundAudioConverterService destroyed")
    }
}

