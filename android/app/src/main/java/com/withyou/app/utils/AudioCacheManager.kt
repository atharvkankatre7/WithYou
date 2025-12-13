package com.withyou.app.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Audio cache entry information
 */
data class AudioCacheEntry(
    val fileHash: String,
    val trackIndex: Int,
    val audioUri: Uri,
    val fileSize: Long,
    val createdAt: Long
)

/**
 * Manages permanent audio cache storage
 * Stores converted audio files with hash-based naming for fast lookup
 */
class AudioCacheManager(private val context: Context) {
    
    companion object {
        private const val CACHE_DIR_NAME = "audio_cache"
        private const val CACHE_METADATA_FILE = "cache_metadata.json"
        
        // Cache size limits - made more generous to avoid affecting UX
        private const val MAX_CACHE_SIZE_MB = 2000L // 2 GB max cache size (increased from 500 MB)
        private const val MAX_CACHE_SIZE_BYTES = MAX_CACHE_SIZE_MB * 1024 * 1024
        
        // Cleanup thresholds - more conservative to preserve user's converted videos
        private const val CLEANUP_THRESHOLD_MB = 1500L // Start cleanup at 1.5 GB (increased from 400 MB)
        private const val CLEANUP_THRESHOLD_BYTES = CLEANUP_THRESHOLD_MB * 1024 * 1024
        private const val DEFAULT_CLEANUP_DAYS = 30 // Clean up files older than 30 days (increased from 7)
        
        // Minimum free space to maintain (100 MB)
        private const val MIN_FREE_SPACE_MB = 100L
        private const val MIN_FREE_SPACE_BYTES = MIN_FREE_SPACE_MB * 1024 * 1024
    }
    
    private val cacheDir: File by lazy {
        val dir = File(context.getExternalFilesDir(null), CACHE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }
    
    /**
     * Get cache directory path
     */
    fun getCacheDirectory(): File = cacheDir
    
    /**
     * Get audio file path for a given hash and track index
     */
    private fun getAudioFilePath(fileHash: String, trackIndex: Int): File {
        return File(cacheDir, "${fileHash}_track_${trackIndex}.aac")
    }
    
    /**
     * Check if audio is cached for a file hash and track index
     */
    suspend fun isAudioCached(fileHash: String, trackIndex: Int): Boolean = withContext(Dispatchers.IO) {
        val file = getAudioFilePath(fileHash, trackIndex)
        file.exists() && file.length() > 0
    }
    
    /**
     * Get cached audio URI for a file hash and track index
     */
    suspend fun getCachedAudioUri(fileHash: String, trackIndex: Int): Uri? = withContext(Dispatchers.IO) {
        val file = getAudioFilePath(fileHash, trackIndex)
        if (file.exists() && file.length() > 0) {
            Uri.fromFile(file)
        } else {
            null
        }
    }
    
    /**
     * Get all cached audio tracks for a file hash
     */
    suspend fun getCachedAudioTracks(fileHash: String): Map<Int, Uri> = withContext(Dispatchers.IO) {
        val tracks = mutableMapOf<Int, Uri>()
        
        cacheDir.listFiles()?.forEach { file ->
            val fileName = file.name
            if (fileName.startsWith("${fileHash}_track_") && fileName.endsWith(".aac")) {
                try {
                    // Extract track index from filename: "hash_track_0.aac" -> 0
                    val trackIndexStr = fileName
                        .removePrefix("${fileHash}_track_")
                        .removeSuffix(".aac")
                    val trackIndex = trackIndexStr.toIntOrNull()
                    
                    if (trackIndex != null && file.exists() && file.length() > 0) {
                        tracks[trackIndex] = Uri.fromFile(file)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing track index from filename: $fileName")
                }
            }
        }
        
        tracks
    }
    
    /**
     * Get available storage space in bytes
     */
    private fun getAvailableStorageSpace(): Long {
        return try {
            val cacheDir = context.cacheDir
            val stat = android.os.StatFs(cacheDir.path)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR2) {
                stat.availableBytes
            } else {
                @Suppress("DEPRECATION")
                stat.availableBlocks.toLong() * stat.blockSize
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting available storage space")
            Long.MAX_VALUE // Return max value if we can't determine
        }
    }
    
    /**
     * Save audio file to cache
     * Automatically manages cache size by cleaning up old files if needed
     * Uses smart eviction: only deletes if really necessary
     */
    suspend fun saveAudioToCache(
        fileHash: String,
        trackIndex: Int,
        audioUri: Uri
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            // Check available storage first
            val availableSpace = getAvailableStorageSpace()
            if (availableSpace < MIN_FREE_SPACE_BYTES) {
                Timber.w("Low storage space (${availableSpace / 1024 / 1024} MB), cleaning up cache...")
                cleanupOldCache(7) // More aggressive cleanup if storage is low
            }
            
            // Check cache size and cleanup if needed before saving
            val currentSize = getCacheSize()
            if (currentSize >= CLEANUP_THRESHOLD_BYTES) {
                Timber.i("Cache size (${currentSize / 1024 / 1024} MB) exceeds threshold, cleaning up old files...")
                // Only clean up very old files (60+ days) to preserve user's recent conversions
                cleanupOldCache(60)
                
                // If still too large, enforce max size limit (but only delete oldest, least-used files)
                val newSize = getCacheSize()
                if (newSize >= MAX_CACHE_SIZE_BYTES) {
                    Timber.w("Cache size (${newSize / 1024 / 1024} MB) exceeds max limit, enforcing size limit...")
                    enforceCacheSizeLimit()
                }
            }
            
            val outputFile = getAudioFilePath(fileHash, trackIndex)
            
            // Copy audio file to cache
            context.contentResolver.openInputStream(audioUri)?.use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            if (outputFile.exists() && outputFile.length() > 0) {
                val fileSizeMB = outputFile.length() / 1024.0 / 1024.0
                Timber.i("Audio cached: ${outputFile.name} (${String.format("%.2f", fileSizeMB)} MB)")
                
                // Update file modification time to mark as recently used
                outputFile.setLastModified(System.currentTimeMillis())
                
                Uri.fromFile(outputFile)
            } else {
                Timber.e("Failed to save audio to cache")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error saving audio to cache")
            null
        }
    }
    
    /**
     * Enforce cache size limit by deleting oldest, least-used files first
     * Uses LRU (Least Recently Used) strategy: deletes files that haven't been accessed recently
     */
    private suspend fun enforceCacheSizeLimit(): Int = withContext(Dispatchers.IO) {
        var deletedCount = 0
        var currentSize = getCacheSize()
        
        if (currentSize <= MAX_CACHE_SIZE_BYTES) {
            return@withContext 0
        }
        
        // Calculate target size (80% of max to leave some buffer)
        val targetSize = (MAX_CACHE_SIZE_BYTES * 0.8).toLong()
        
        // Get all cache files sorted by last modified (oldest/least recently used first)
        val cacheFiles = cacheDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".aac") }
            ?.sortedBy { it.lastModified() } // Oldest first = least recently used
            ?: emptyList()
        
        // Delete oldest files until we're under the target
        // Only delete files older than 14 days to avoid deleting recently used files
        val minAge = System.currentTimeMillis() - (14 * 24 * 60 * 60 * 1000L) // 14 days
        
        for (file in cacheFiles) {
            if (currentSize <= targetSize) {
                break
            }
            
            // Only delete files that are old enough (at least 14 days)
            if (file.lastModified() < minAge) {
                val fileSize = file.length()
                if (file.delete()) {
                    currentSize -= fileSize
                    deletedCount++
                    val ageDays = (System.currentTimeMillis() - file.lastModified()) / (24 * 60 * 60 * 1000)
                    Timber.d("Deleted old cache file (${ageDays} days old): ${file.name} (${fileSize / 1024 / 1024} MB)")
                }
            }
        }
        
        val freedSpaceMB = (getCacheSize() - currentSize) / 1024.0 / 1024.0
        Timber.i("Enforced cache size limit: deleted $deletedCount files, freed ${String.format("%.2f", freedSpaceMB)} MB, new size: ${currentSize / 1024 / 1024} MB")
        deletedCount
    }
    
    /**
     * Delete cached audio for a file hash
     */
    suspend fun deleteCachedAudio(fileHash: String): Boolean = withContext(Dispatchers.IO) {
        try {
            var deleted = false
            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("${fileHash}_track_")) {
                    if (file.delete()) {
                        deleted = true
                        Timber.d("Deleted cached audio: ${file.name}")
                    }
                }
            }
            deleted
        } catch (e: Exception) {
            Timber.e(e, "Error deleting cached audio")
            false
        }
    }
    
    /**
     * Get cache size in bytes
     */
    suspend fun getCacheSize(): Long = withContext(Dispatchers.IO) {
        var totalSize = 0L
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile) {
                totalSize += file.length()
            }
        }
        totalSize
    }
    
    /**
     * Get cache entry count
     */
    suspend fun getCacheEntryCount(): Int = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.count { it.isFile && it.name.endsWith(".aac") } ?: 0
    }
    
    /**
     * Clean up old cache files (older than specified days)
     */
    suspend fun cleanupOldCache(daysOld: Int = DEFAULT_CLEANUP_DAYS): Int = withContext(Dispatchers.IO) {
        val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000L)
        var deletedCount = 0
        var freedSpace = 0L
        
        cacheDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.endsWith(".aac") && file.lastModified() < cutoffTime) {
                val fileSize = file.length()
                if (file.delete()) {
                    deletedCount++
                    freedSpace += fileSize
                    Timber.d("Deleted old cache file: ${file.name} (${fileSize / 1024 / 1024} MB)")
                }
            }
        }
        
        val freedSpaceMB = freedSpace / 1024.0 / 1024.0
        Timber.i("Cleaned up $deletedCount old cache files, freed ${String.format("%.2f", freedSpaceMB)} MB")
        deletedCount
    }
    
    /**
     * Get cache size in human-readable format
     */
    suspend fun getCacheSizeFormatted(): String = withContext(Dispatchers.IO) {
        val size = getCacheSize()
        when {
            size >= 1024 * 1024 * 1024 -> String.format("%.2f GB", size / 1024.0 / 1024.0 / 1024.0)
            size >= 1024 * 1024 -> String.format("%.2f MB", size / 1024.0 / 1024.0)
            size >= 1024 -> String.format("%.2f KB", size / 1024.0)
            else -> "$size bytes"
        }
    }
    
    /**
     * Clear all cache
     */
    suspend fun clearAllCache(): Boolean = withContext(Dispatchers.IO) {
        try {
            var deleted = false
            cacheDir.listFiles()?.forEach { file ->
                if (file.isFile && file.delete()) {
                    deleted = true
                }
            }
            Timber.i("Cache cleared: $deleted files deleted")
            deleted
        } catch (e: Exception) {
            Timber.e(e, "Error clearing cache")
            false
        }
    }
    
    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        val size = getCacheSize()
        val count = getCacheEntryCount()
        
        CacheStats(
            totalSize = size,
            fileCount = count,
            cacheDir = cacheDir.absolutePath
        )
    }
    
    /**
     * Cache statistics
     */
    data class CacheStats(
        val totalSize: Long,
        val fileCount: Int,
        val cacheDir: String
    )
}

