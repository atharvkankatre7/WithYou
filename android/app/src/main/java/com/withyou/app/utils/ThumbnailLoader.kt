package com.withyou.app.utils

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * Utility class for generating and caching video thumbnails
 */
object ThumbnailLoader {
    
    private const val THUMBNAIL_DIR = "video_thumbnails"
    private const val THUMBNAIL_WIDTH = 320
    private const val THUMBNAIL_HEIGHT = 180
    
    /**
     * Get thumbnail for a video URI
     * Returns cached version if available, otherwise generates new one
     */
    suspend fun getThumbnail(context: Context, videoUri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Check cache first
            val cacheFile = getCacheFile(context, videoUri)
            if (cacheFile.exists()) {
                return@withContext android.graphics.BitmapFactory.decodeFile(cacheFile.absolutePath)
            }
            
            // Generate thumbnail
            val bitmap = generateThumbnail(context, videoUri)
            
            // Cache it
            bitmap?.let { saveThumbnailToCache(it, cacheFile) }
            
            bitmap
        } catch (e: Exception) {
            Timber.w(e, "Failed to get thumbnail for $videoUri")
            null
        }
    }
    
    /**
     * Generate thumbnail from video at given position
     */
    private fun generateThumbnail(context: Context, videoUri: Uri, positionMs: Long = 1000): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            
            // Get frame at position (1 second by default to skip black intro frames)
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    positionMs * 1000, // Convert to microseconds
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                    THUMBNAIL_WIDTH,
                    THUMBNAIL_HEIGHT
                )
            } else {
                retriever.getFrameAtTime(
                    positionMs * 1000,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )?.let { frame ->
                    // Scale down manually for older devices
                    Bitmap.createScaledBitmap(frame, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, true)
                }
            }
            
            bitmap
        } catch (e: Exception) {
            Timber.w(e, "Failed to generate thumbnail")
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
    }
    
    /**
     * Save thumbnail to cache directory
     */
    private fun saveThumbnailToCache(bitmap: Bitmap, cacheFile: File) {
        try {
            cacheFile.parentFile?.mkdirs()
            FileOutputStream(cacheFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to cache thumbnail")
        }
    }
    
    /**
     * Get cache file path for a video URI
     */
    private fun getCacheFile(context: Context, videoUri: Uri): File {
        val cacheDir = File(context.cacheDir, THUMBNAIL_DIR)
        val filename = videoUri.toString().hashCode().toString() + ".jpg"
        return File(cacheDir, filename)
    }
    
    /**
     * Clear all cached thumbnails
     */
    fun clearCache(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, THUMBNAIL_DIR)
            cacheDir.deleteRecursively()
        } catch (e: Exception) {
            Timber.w(e, "Failed to clear thumbnail cache")
        }
    }
    
    /**
     * Get the path to cached thumbnail if it exists
     */
    fun getCachedThumbnailPath(context: Context, videoUri: Uri): String? {
        val cacheFile = getCacheFile(context, videoUri)
        return if (cacheFile.exists()) cacheFile.absolutePath else null
    }
}
