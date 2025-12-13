package com.withyou.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * Utility for extracting video thumbnails
 */
object ThumbnailExtractor {
    
    /**
     * Extract thumbnail from video at a specific time (in milliseconds)
     * Returns null if extraction fails
     */
    suspend fun extractThumbnail(
        context: Context,
        videoUri: Uri,
        timeUs: Long = 1_000_000L // Default: 1 second into video
    ): Bitmap? = withContext(Dispatchers.IO) {
        var retriever: MediaMetadataRetriever? = null
        try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, videoUri)
            
            // Get frame at specified time (in microseconds)
            val bitmap = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            
            if (bitmap != null) {
                Timber.d("Successfully extracted thumbnail for $videoUri")
            } else {
                Timber.w("Failed to extract thumbnail for $videoUri")
            }
            
            bitmap
        } catch (e: Exception) {
            Timber.e(e, "Error extracting thumbnail for $videoUri")
            null
        } finally {
            try {
                retriever?.release()
            } catch (e: Exception) {
                Timber.e(e, "Error releasing MediaMetadataRetriever")
            }
        }
    }
    
    /**
     * Get thumbnail cache file path for a video URI
     */
    private fun getThumbnailCachePath(context: Context, videoUri: Uri): File {
        val cacheDir = File(context.cacheDir, "thumbnails")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        
        // Use URI hash as filename
        val uriHash = videoUri.toString().hashCode().toString()
        return File(cacheDir, "$uriHash.jpg")
    }
    
    /**
     * Save thumbnail to cache
     */
    suspend fun saveThumbnailToCache(
        context: Context,
        videoUri: Uri,
        bitmap: Bitmap
    ): File? = withContext(Dispatchers.IO) {
        try {
            val cacheFile = getThumbnailCachePath(context, videoUri)
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            Timber.d("Saved thumbnail to cache: ${cacheFile.absolutePath}")
            cacheFile
        } catch (e: Exception) {
            Timber.e(e, "Error saving thumbnail to cache")
            null
        }
    }
    
    /**
     * Get thumbnail from cache if exists
     */
    suspend fun getThumbnailFromCache(
        context: Context,
        videoUri: Uri
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val cacheFile = getThumbnailCachePath(context, videoUri)
            if (cacheFile.exists()) {
                android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "Error loading thumbnail from cache")
            null
        }
    }
    
    /**
     * Extract and cache thumbnail for a video
     * Returns the cached file path or null if extraction fails
     */
    suspend fun extractAndCacheThumbnail(
        context: Context,
        videoUri: Uri,
        timeUs: Long = 1_000_000L
    ): File? = withContext(Dispatchers.IO) {
        // Check cache first
        val cacheFile = getThumbnailCachePath(context, videoUri)
        if (cacheFile.exists()) {
            Timber.d("Thumbnail already cached for $videoUri")
            return@withContext cacheFile
        }
        
        // Extract thumbnail
        val bitmap = extractThumbnail(context, videoUri, timeUs)
        if (bitmap != null) {
            saveThumbnailToCache(context, videoUri, bitmap)
        } else {
            null
        }
    }
    
    /**
     * Clear thumbnail cache
     */
    suspend fun clearThumbnailCache(context: Context) = withContext(Dispatchers.IO) {
        try {
            val cacheDir = File(context.cacheDir, "thumbnails")
            if (cacheDir.exists()) {
                cacheDir.listFiles()?.forEach { it.delete() }
                Timber.d("Cleared thumbnail cache")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error clearing thumbnail cache")
        }
    }
}

