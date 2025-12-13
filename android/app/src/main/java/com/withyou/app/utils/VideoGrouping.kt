package com.withyou.app.utils

import android.net.Uri
import timber.log.Timber
import java.util.regex.Pattern

/**
 * Video group types
 */
enum class VideoGroupType {
    RECENTLY_ADDED,  // Recently Added folder (global, always on top)
    TV_SHOW,         // TV series with episodes (filename-based)
    MOVIE,           // Single movies (filename-based)
    CAMERA,          // Camera videos (source-based)
    SCREEN_RECORDING,// Screen recordings (source-based)
    WHATSAPP,        // WhatsApp videos (source-based)
    TELEGRAM,        // Telegram videos (source-based)
    INSTAGRAM,       // Instagram videos (source-based)
    DOWNLOADS,       // Downloads (source-based)
    OTHER,           // Other/Unknown source (source-based)
    UNKNOWN          // Internal use only - videos that don't match filename patterns (will be grouped by source)
}

/**
 * Video group information
 */
data class VideoGroup(
    val type: VideoGroupType,
    val title: String,
    val season: Int? = null,
    val videos: List<VideoFile>,
    val thumbnailUri: Uri? = null
)

/**
 * Utility to group videos by naming patterns and source (like gallery apps)
 * 
 * Sorting Priority:
 * 1. Recently Added (global folder, always on top)
 * 2. Filename-based grouping (TV shows, movies - highest priority)
 * 3. Source-based grouping (only for videos not already grouped by filename)
 */
object VideoGrouping {
    
    // Number of recent videos to show in "Recently Added" folder
    private const val RECENTLY_ADDED_COUNT = 20
    
    // Common TV show patterns
    private val TV_SHOW_PATTERNS = listOf(
        // "Stranger Things S01E01" or "Stranger Things Season 1 Episode 1"
        Pattern.compile("""(.+?)\s*[Ss](\d+)[Ee](\d+)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(.+?)\s*Season\s*(\d+)\s*Episode\s*(\d+)""", Pattern.CASE_INSENSITIVE),
        // "Stranger Things - 1x01" or "Stranger Things 1x01"
        Pattern.compile("""(.+?)\s*[-]?\s*(\d+)x(\d+)""", Pattern.CASE_INSENSITIVE),
        // "Stranger Things 101" (S01E01 format)
        Pattern.compile("""(.+?)\s*(\d)(\d{2})""", Pattern.CASE_INSENSITIVE),
        // "Stranger Things - Episode 1" or "Stranger Things E01"
        Pattern.compile("""(.+?)\s*[-]?\s*[Ee]pisode\s*(\d+)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(.+?)\s*[-]?\s*[Ee](\d+)""", Pattern.CASE_INSENSITIVE),
        // "Stranger Things Part 1" or "Stranger Things Chapter 1"
        Pattern.compile("""(.+?)\s*[-]?\s*(?:Part|Chapter)\s*(\d+)""", Pattern.CASE_INSENSITIVE)
    )
    
    // Movie patterns (usually just the title)
    private val MOVIE_PATTERNS = listOf(
        // Year at the end: "Movie Name (2023)"
        Pattern.compile("""(.+?)\s*\((\d{4})\)""", Pattern.CASE_INSENSITIVE),
        // Just the title (no episode indicators)
        Pattern.compile("""^(.+?)(?:\s*[-]?\s*(?:Part|Chapter|Episode)\s*\d+)?$""", Pattern.CASE_INSENSITIVE)
    )
    
    /**
     * Group videos by source and naming patterns
     * Priority: Recently Added > Source-based > Filename-based
     * This ensures all videos from the same source (Camera, WhatsApp, etc.) are grouped together first
     */
    fun groupVideos(videos: List<VideoFile>): List<VideoGroup> {
        // Step 1: Create "Recently Added" folder (most recent videos, regardless of grouping)
        val recentlyAddedVideos = videos
            .sortedByDescending { it.dateAdded }
            .take(RECENTLY_ADDED_COUNT)
        
        // Step 2: Apply source-based grouping FIRST (highest priority after Recently Added)
        val sourceGroups = mutableMapOf<String, MutableList<VideoFile>>()
        val sourceGroupMetadata = mutableMapOf<String, VideoGroupType>()
        val videosInSourceGroups = mutableSetOf<VideoFile>() // Track which videos are grouped by source
        
        videos.forEach { video ->
            // Detect source
            val sourceType = detectSource(video)
            val sourceKey = when (sourceType) {
                VideoGroupType.CAMERA -> "Camera"
                VideoGroupType.SCREEN_RECORDING -> "Screen Recordings"
                VideoGroupType.WHATSAPP -> "WhatsApp"
                VideoGroupType.TELEGRAM -> "Telegram"
                VideoGroupType.INSTAGRAM -> "Instagram"
                VideoGroupType.DOWNLOADS -> "Downloads"
                else -> "Other"
            }
            
            // Only group by source if it's not "Other" (we'll handle "Other" separately)
            if (sourceType != VideoGroupType.OTHER) {
                if (!sourceGroups.containsKey(sourceKey)) {
                    sourceGroups[sourceKey] = mutableListOf()
                    sourceGroupMetadata[sourceKey] = sourceType
                }
                sourceGroups[sourceKey]?.add(video)
                videosInSourceGroups.add(video)
            }
        }
        
        // Step 3: Apply filename-based grouping (only for videos NOT already grouped by source)
        val filenameGroups = mutableMapOf<String, MutableList<VideoFile>>()
        val filenameGroupMetadata = mutableMapOf<String, Pair<VideoGroupType, Int?>>()
        
        videos.forEach { video ->
            // Skip if already grouped by source
            if (videosInSourceGroups.contains(video)) {
                return@forEach
            }
            
            val (groupKey, type, season) = extractGroupInfo(video.name)
            
            if (groupKey.isNotEmpty() && type != VideoGroupType.UNKNOWN) {
                // This video matches a filename pattern (TV show or movie)
                if (!filenameGroups.containsKey(groupKey)) {
                    filenameGroups[groupKey] = mutableListOf()
                    filenameGroupMetadata[groupKey] = Pair(type, season)
                }
                filenameGroups[groupKey]?.add(video)
            }
        }
        
        // Step 4: Group remaining videos (not in source groups, not matching filename patterns) into "Other"
        val otherVideos = videos.filter { 
            !videosInSourceGroups.contains(it) && 
            !filenameGroups.values.flatten().contains(it)
        }
        
        if (otherVideos.isNotEmpty()) {
            sourceGroups["Other"] = otherVideos.toMutableList()
            sourceGroupMetadata["Other"] = VideoGroupType.OTHER
        }
        
        // Sort videos within each group
        sourceGroups.forEach { (_, videoList) ->
            videoList.sortByDescending { it.dateAdded } // Sort by date for source groups
        }
        filenameGroups.forEach { (_, videoList) ->
            videoList.sortBy { it.name } // Sort by name for filename groups
        }
        
        // Build final list of groups in priority order
        val resultGroups = mutableListOf<VideoGroup>()
        
        // 1. Recently Added (always first)
        if (recentlyAddedVideos.isNotEmpty()) {
            resultGroups.add(
                VideoGroup(
                    type = VideoGroupType.RECENTLY_ADDED,
                    title = "Recently Added",
                    season = null,
                    videos = recentlyAddedVideos,
                    thumbnailUri = recentlyAddedVideos.firstOrNull()?.uri
                )
            )
        }
        
        // 2. Source-based groups (Camera, WhatsApp, etc.) - NOW FIRST PRIORITY
        sourceGroups.forEach { (key, videoList) ->
            val type = sourceGroupMetadata[key] ?: VideoGroupType.OTHER
            resultGroups.add(
                VideoGroup(
                    type = type,
                    title = key,
                    season = null,
                    videos = videoList,
                    thumbnailUri = videoList.firstOrNull()?.uri
                )
            )
        }
        
        // 3. Filename-based groups (TV shows, movies) - only for videos not in source groups
        filenameGroups.forEach { (key, videoList) ->
            val (type, season) = filenameGroupMetadata[key] ?: Pair(VideoGroupType.UNKNOWN, null)
            resultGroups.add(
                VideoGroup(
                    type = type,
                    title = key,
                    season = season,
                    videos = videoList,
                    thumbnailUri = videoList.firstOrNull()?.uri
                )
            )
        }
        
        // Sort groups by priority (source-based groups come before filename-based groups)
        return resultGroups.sortedBy {
            when (it.type) {
                VideoGroupType.RECENTLY_ADDED -> 0
                VideoGroupType.CAMERA -> 1
                VideoGroupType.SCREEN_RECORDING -> 2
                VideoGroupType.WHATSAPP -> 3
                VideoGroupType.TELEGRAM -> 4
                VideoGroupType.INSTAGRAM -> 5
                VideoGroupType.DOWNLOADS -> 6
                VideoGroupType.OTHER -> 7
                VideoGroupType.TV_SHOW -> 8
                VideoGroupType.MOVIE -> 9
                VideoGroupType.UNKNOWN -> 10 // Should never appear in final groups, but required for exhaustive when
            }
        }
    }
    
    /**
     * Detect video source based on path
     * All videos from the same source should be grouped together in one meaningful folder
     */
    private fun detectSource(video: VideoFile): VideoGroupType {
        // Use relativePath if available (Android 10+), otherwise use path
        // For Android 10+, path might be a URI string, so prefer relativePath
        val pathToCheck = when {
            video.relativePath != null -> video.relativePath.lowercase()
            video.path.startsWith("content://") -> {
                // Android 10+ URI - try to extract path from URI or use relativePath
                (video.relativePath ?: video.path).lowercase()
            }
            else -> video.path.lowercase()
        }
        
        // Camera videos - check multiple patterns to catch all camera videos
        if (pathToCheck.contains("dcim/camera") || 
            pathToCheck.contains("dcim\\camera") ||
            pathToCheck.contains("/camera/") || 
            pathToCheck.contains("\\camera\\") ||
            pathToCheck.contains("/camera") ||
            pathToCheck.contains("\\camera") ||
            pathToCheck.endsWith("/camera") ||
            pathToCheck.endsWith("\\camera")) {
            return VideoGroupType.CAMERA
        }
        
        // WhatsApp videos - check all WhatsApp paths
        if (pathToCheck.contains("whatsapp") || 
            pathToCheck.contains("whats app") ||
            pathToCheck.contains("com.whatsapp")) {
            return VideoGroupType.WHATSAPP
        }
        
        // Telegram videos
        if (pathToCheck.contains("telegram") || 
            pathToCheck.contains("org.telegram")) {
            return VideoGroupType.TELEGRAM
        }
        
        // Instagram videos
        if (pathToCheck.contains("instagram") || 
            pathToCheck.contains("com.instagram")) {
            return VideoGroupType.INSTAGRAM
        }
        
        // Screen recordings - check various patterns
        if (pathToCheck.contains("screenrecord") || 
            pathToCheck.contains("screen_recorder") || 
            pathToCheck.contains("screen recorder") ||
            pathToCheck.contains("screenrecorder") ||
            pathToCheck.contains("screen-recorder") ||
            pathToCheck.contains("screencapture") ||
            pathToCheck.contains("screen capture")) {
            return VideoGroupType.SCREEN_RECORDING
        }
        
        // Downloads
        if (pathToCheck.contains("download") ||
            pathToCheck.contains("downloads")) {
            return VideoGroupType.DOWNLOADS
        }
        
        // Default to OTHER for unknown sources
        return VideoGroupType.OTHER
    }
    
    /**
     * Extract group information from video filename
     * Returns: (groupKey, type, season)
     */
    private fun extractGroupInfo(filename: String): Triple<String, VideoGroupType, Int?> {
        // Try TV show patterns first
        for (pattern in TV_SHOW_PATTERNS) {
            val matcher = pattern.matcher(filename)
            if (matcher.find()) {
                val title = cleanTitle(matcher.group(1) ?: "")
                val season = matcher.group(2)?.toIntOrNull()
                
                if (title.isNotEmpty()) {
                    val groupKey = if (season != null) {
                        "$title - Season $season"
                    } else {
                        title
                    }
                    return Triple(groupKey, VideoGroupType.TV_SHOW, season)
                }
            }
        }
        
        // Try movie patterns
        for (pattern in MOVIE_PATTERNS) {
            val matcher = pattern.matcher(filename)
            if (matcher.find()) {
                val title = cleanTitle(matcher.group(1) ?: "")
                if (title.isNotEmpty() && !hasEpisodeIndicators(title)) {
                    return Triple(title, VideoGroupType.MOVIE, null)
                }
            }
        }
        
        // Fallback: use filename without extension as title
        val cleanName = cleanTitle(filename.substringBeforeLast("."))
        return Triple(cleanName, VideoGroupType.UNKNOWN, null)
    }
    
    /**
     * Clean title (remove common prefixes/suffixes)
     */
    private fun cleanTitle(title: String): String {
        return title.trim()
            .replace(Regex("""\s+"""), " ") // Multiple spaces to single
            .replace(Regex("""^[-_\s]+|[-_\s]+$"""), "") // Trim dashes/underscores
    }
    
    /**
     * Check if title has episode indicators (likely a TV show)
     */
    private fun hasEpisodeIndicators(title: String): Boolean {
        val episodePatterns = listOf(
            "episode", "ep", "part", "chapter", "season", "s\\d+e\\d+", "\\d+x\\d+"
        )
        return episodePatterns.any { 
            Regex(it, RegexOption.IGNORE_CASE).containsMatchIn(title) 
        }
    }
    
    /**
     * Get episode number from filename (for sorting)
     */
    fun getEpisodeNumber(filename: String): Int? {
        // Try to extract episode number
        val patterns = listOf(
            Pattern.compile("""[Ee](\d+)"""),
            Pattern.compile("""[Ee]pisode\s*(\d+)"""),
            Pattern.compile("""\d+x(\d+)"""),
            Pattern.compile("""[Ss]\d+[Ee](\d+)""")
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(filename)
            if (matcher.find()) {
                return matcher.group(1)?.toIntOrNull()
            }
        }
        
        return null
    }
}

