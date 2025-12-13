package com.withyou.app.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.withyou.app.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * ViewModel for media library screen
 */
class MediaLibraryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val audioCacheManager = AudioCacheManager(application)
    
    // State
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _scanProgress = MutableStateFlow(0)
    val scanProgress: StateFlow<Int> = _scanProgress.asStateFlow()
    
    private val _videos = MutableStateFlow<List<VideoFile>>(emptyList())
    val videos: StateFlow<List<VideoFile>> = _videos.asStateFlow()
    
    private val _groups = MutableStateFlow<List<VideoGroup>>(emptyList())
    val groups: StateFlow<List<VideoGroup>> = _groups.asStateFlow()
    
    private val _selectedVideo = MutableStateFlow<VideoFile?>(null)
    val selectedVideo: StateFlow<VideoFile?> = _selectedVideo.asStateFlow()
    
    private val _conversionStatus = MutableStateFlow<Map<String, ConversionStatus>>(emptyMap())
    val conversionStatus: StateFlow<Map<String, ConversionStatus>> = _conversionStatus.asStateFlow()
    
    /**
     * Conversion status for a video
     */
    data class ConversionStatus(
        val fileHash: String,
        val isConverting: Boolean,
        val progress: Int,
        val isComplete: Boolean,
        val error: String? = null
    )
    
    init {
        // Don't scan on init - wait for permissions to be granted
        // Scanning will be triggered from UI when permissions are available
        
        // Clean up old cache on app start (runs in background)
        viewModelScope.launch {
            try {
                val deletedCount = audioCacheManager.cleanupOldCache(7) // Clean files older than 7 days
                if (deletedCount > 0) {
                    Timber.i("Cleaned up $deletedCount old cache files on app start")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error cleaning up cache on app start")
            }
        }
    }
    
    /**
     * Scan device for video files
     */
    fun scanVideos() {
        // Prevent multiple simultaneous scans
        if (_isScanning.value) {
            Timber.w("Scan already in progress, skipping...")
            return
        }
        
        viewModelScope.launch {
            try {
                _isScanning.value = true
                _scanProgress.value = 0
                
                Timber.i("🔄 Starting video scan...")
                val scannedVideos = MediaScanner.scanVideos(getApplication())
                
                Timber.i("📹 Found ${scannedVideos.size} videos")
                _videos.value = scannedVideos
                _scanProgress.value = 50
                
                // Group videos
                Timber.i("📁 Grouping ${scannedVideos.size} videos...")
                val groupedVideos = VideoGrouping.groupVideos(scannedVideos)
                _groups.value = groupedVideos
                
                _scanProgress.value = 100
                Timber.i("✅ Video scan complete: ${scannedVideos.size} videos in ${groupedVideos.size} groups")
                
                // Note: No conversion needed with LibVLC - all codecs supported natively!
                // Conversion status checking removed
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Error scanning videos: ${e.message}")
                e.printStackTrace()
            } finally {
                _isScanning.value = false
            }
        }
    }
    
    /**
     * Check conversion status for all videos
     * NOTE: With LibVLC, no conversion is needed - all codecs supported natively!
     * This method is kept for backward compatibility but does nothing.
     */
    private fun checkConversionStatus() {
        // No-op: LibVLC supports all codecs natively, no conversion needed
        _conversionStatus.value = emptyMap()
    }
    
    /**
     * Select a video
     */
    fun selectVideo(video: VideoFile) {
        _selectedVideo.value = video
    }
    
    /**
     * Get conversion status for a video
     */
    fun getConversionStatus(video: VideoFile): ConversionStatus? {
        return _conversionStatus.value[video.path]
    }
    
    /**
     * Update conversion status
     */
    fun updateConversionStatus(videoPath: String, status: ConversionStatus) {
        val currentStatus = _conversionStatus.value.toMutableMap()
        currentStatus[videoPath] = status
        _conversionStatus.value = currentStatus
    }
    
    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(): AudioCacheManager.CacheStats {
        return audioCacheManager.getCacheStats()
    }
    
    /**
     * Clear cache
     */
    fun clearCache() {
        viewModelScope.launch {
            audioCacheManager.clearAllCache()
            // No conversion status to update
        }
    }
    
    /**
     * Cleanup old cache
     */
    fun cleanupOldCache(daysOld: Int = 30) {
        viewModelScope.launch {
            audioCacheManager.cleanupOldCache(daysOld)
            // No conversion status to update
        }
    }
}

