package com.withyou.app.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import timber.log.Timber

/**
 * Data class for a recently played video
 */
data class RecentVideo(
    val uri: String,
    val name: String,
    val duration: Long,
    val position: Long,      // Last playback position in ms
    val lastPlayed: Long,    // Timestamp when last played
    val thumbnailPath: String? = null
)

/**
 * Manager for storing and retrieving recently played videos
 * Uses SharedPreferences with JSON serialization
 */
class RecentlyPlayedManager(context: Context) {
    
    companion object {
        private const val PREFS_NAME = "recently_played"
        private const val KEY_RECENT_VIDEOS = "recent_videos"
        private const val MAX_RECENT_VIDEOS = 10
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * Save or update a recently played video
     * If video already exists, updates position and moves to top
     */
    fun saveRecentVideo(video: RecentVideo) {
        try {
            val videos = getRecentVideos().toMutableList()
            
            // Remove if already exists (will re-add at top)
            videos.removeAll { it.uri == video.uri }
            
            // Add to top
            videos.add(0, video.copy(lastPlayed = System.currentTimeMillis()))
            
            // Trim to max size
            val trimmed = videos.take(MAX_RECENT_VIDEOS)
            
            // Save
            val json = gson.toJson(trimmed)
            prefs.edit().putString(KEY_RECENT_VIDEOS, json).apply()
            
            Timber.d("Saved recent video: ${video.name}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save recent video")
        }
    }
    
    /**
     * Update just the position of a video (for periodic saves)
     */
    fun updatePosition(uri: String, position: Long) {
        try {
            val videos = getRecentVideos().toMutableList()
            val index = videos.indexOfFirst { it.uri == uri }
            
            if (index >= 0) {
                videos[index] = videos[index].copy(position = position)
                val json = gson.toJson(videos)
                prefs.edit().putString(KEY_RECENT_VIDEOS, json).apply()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update video position")
        }
    }
    
    /**
     * Get all recently played videos
     */
    fun getRecentVideos(): List<RecentVideo> {
        return try {
            val json = prefs.getString(KEY_RECENT_VIDEOS, null) ?: return emptyList()
            val type = object : TypeToken<List<RecentVideo>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get recent videos")
            emptyList()
        }
    }
    
    /**
     * Get a specific recent video by URI
     */
    fun getRecentVideo(uri: String): RecentVideo? {
        return getRecentVideos().find { it.uri == uri }
    }
    
    /**
     * Remove a specific video from recents
     */
    fun removeRecent(uri: String) {
        try {
            val videos = getRecentVideos().toMutableList()
            videos.removeAll { it.uri == uri }
            val json = gson.toJson(videos)
            prefs.edit().putString(KEY_RECENT_VIDEOS, json).apply()
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove recent video")
        }
    }
    
    /**
     * Clear all recently played videos
     */
    fun clearAll() {
        prefs.edit().remove(KEY_RECENT_VIDEOS).apply()
    }
}
