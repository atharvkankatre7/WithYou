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
    
    // Duration threshold for filename-based grouping priority (20 minutes in milliseconds)
    private const val FILENAME_PRIORITY_DURATION_MS = 20 * 60 * 1000L // 20 minutes
    
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
     * Priority logic:
     * - Videos > 20 minutes: Filename-based grouping first (TV shows/movies)
     * - Videos <= 20 minutes: Source-based grouping first (Camera, WhatsApp, etc.)
     * - Recently Added folder always appears first
     */
    fun groupVideos(videos: List<VideoFile>): List<VideoGroup> {
        // Step 1: Create "Recently Added" folder (most recent videos, regardless of grouping)
        val recentlyAddedVideos = videos
            .sortedByDescending { it.dateAdded }
            .take(RECENTLY_ADDED_COUNT)
        
        // Step 2: Split videos by duration
        val longVideos = videos.filter { it.duration > FILENAME_PRIORITY_DURATION_MS } // > 20 minutes
        val shortVideos = videos.filter { it.duration <= FILENAME_PRIORITY_DURATION_MS } // <= 20 minutes
        
        // Step 3: Group long videos (filename-based first, then source-based)
        val longFilenameGroups = mutableMapOf<String, MutableList<VideoFile>>()
        val longFilenameMetadata = mutableMapOf<String, Pair<VideoGroupType, Int?>>()
        val longVideosInFilenameGroups = mutableSetOf<VideoFile>()
        
        longVideos.forEach { video ->
            val (groupKey, type, season) = extractGroupInfo(video.name)
            if (groupKey.isNotEmpty() && type != VideoGroupType.UNKNOWN) {
                if (!longFilenameGroups.containsKey(groupKey)) {
                    longFilenameGroups[groupKey] = mutableListOf()
                    longFilenameMetadata[groupKey] = Pair(type, season)
                }
                longFilenameGroups[groupKey]?.add(video)
                longVideosInFilenameGroups.add(video)
            }
        }
        
        // Long videos not in filename groups go to source-based groups
        val longSourceGroups = mutableMapOf<String, MutableList<VideoFile>>()
        val longSourceMetadata = mutableMapOf<String, VideoGroupType>()
        
        longVideos.forEach { video ->
            if (longVideosInFilenameGroups.contains(video)) {
                return@forEach
            }
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
            if (sourceType != VideoGroupType.OTHER) {
                if (!longSourceGroups.containsKey(sourceKey)) {
                    longSourceGroups[sourceKey] = mutableListOf()
                    longSourceMetadata[sourceKey] = sourceType
                }
                longSourceGroups[sourceKey]?.add(video)
            }
        }
        
        // Step 4: Group short videos (source-based first, then filename-based)
        val shortSourceGroups = mutableMapOf<String, MutableList<VideoFile>>()
        val shortSourceMetadata = mutableMapOf<String, VideoGroupType>()
        val shortVideosInSourceGroups = mutableSetOf<VideoFile>()
        
        shortVideos.forEach { video ->
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
            if (sourceType != VideoGroupType.OTHER) {
                if (!shortSourceGroups.containsKey(sourceKey)) {
                    shortSourceGroups[sourceKey] = mutableListOf()
                    shortSourceMetadata[sourceKey] = sourceType
                }
                shortSourceGroups[sourceKey]?.add(video)
                shortVideosInSourceGroups.add(video)
            }
        }
        
        // Short videos not in source groups can be grouped by filename
        val shortFilenameGroups = mutableMapOf<String, MutableList<VideoFile>>()
        val shortFilenameMetadata = mutableMapOf<String, Pair<VideoGroupType, Int?>>()
        
        shortVideos.forEach { video ->
            if (shortVideosInSourceGroups.contains(video)) {
                return@forEach
            }
            val (groupKey, type, season) = extractGroupInfo(video.name)
            if (groupKey.isNotEmpty() && type != VideoGroupType.UNKNOWN) {
                if (!shortFilenameGroups.containsKey(groupKey)) {
                    shortFilenameGroups[groupKey] = mutableListOf()
                    shortFilenameMetadata[groupKey] = Pair(type, season)
                }
                shortFilenameGroups[groupKey]?.add(video)
            }
        }
        
        // Step 5: Merge source groups from long and short videos
        val allSourceGroups = mutableMapOf<String, MutableList<VideoFile>>()
        val allSourceMetadata = mutableMapOf<String, VideoGroupType>()
        
        // Add short video source groups
        shortSourceGroups.forEach { (key, videoList) ->
            if (!allSourceGroups.containsKey(key)) {
                allSourceGroups[key] = mutableListOf()
                allSourceMetadata[key] = shortSourceMetadata[key] ?: VideoGroupType.OTHER
            }
            allSourceGroups[key]?.addAll(videoList)
        }
        
        // Add long video source groups
        longSourceGroups.forEach { (key, videoList) ->
            if (!allSourceGroups.containsKey(key)) {
                allSourceGroups[key] = mutableListOf()
                allSourceMetadata[key] = longSourceMetadata[key] ?: VideoGroupType.OTHER
            }
            allSourceGroups[key]?.addAll(videoList)
        }
        
        // Step 6: Merge filename groups from long and short videos
        val allFilenameGroups = mutableMapOf<String, MutableList<VideoFile>>()
        val allFilenameMetadata = mutableMapOf<String, Pair<VideoGroupType, Int?>>()
        
        // Add long video filename groups (these have priority)
        longFilenameGroups.forEach { (key, videoList) ->
            allFilenameGroups[key] = videoList.toMutableList()
            allFilenameMetadata[key] = longFilenameMetadata[key] ?: Pair(VideoGroupType.UNKNOWN, null)
        }
        
        // Add short video filename groups (only if not already exists)
        shortFilenameGroups.forEach { (key, videoList) ->
            if (!allFilenameGroups.containsKey(key)) {
                allFilenameGroups[key] = videoList.toMutableList()
                allFilenameMetadata[key] = shortFilenameMetadata[key] ?: Pair(VideoGroupType.UNKNOWN, null)
            } else {
                // Merge into existing group
                allFilenameGroups[key]?.addAll(videoList)
            }
        }
        
        // Step 7: Group remaining videos into "Other"
        val allGroupedVideos = mutableSetOf<VideoFile>()
        allSourceGroups.values.forEach { allGroupedVideos.addAll(it) }
        allFilenameGroups.values.forEach { allGroupedVideos.addAll(it) }
        
        val otherVideos = videos.filter { !allGroupedVideos.contains(it) }
        if (otherVideos.isNotEmpty()) {
            allSourceGroups["Other"] = otherVideos.toMutableList()
            allSourceMetadata["Other"] = VideoGroupType.OTHER
        }
        
        // Sort videos within each group
        allSourceGroups.forEach { (_, videoList) ->
            videoList.sortByDescending { it.dateAdded } // Sort by date for source groups
        }
        allFilenameGroups.forEach { (_, videoList) ->
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
        
        // 2. Filename-based groups from long videos (TV shows, movies > 20 min) - HIGHEST PRIORITY
        longFilenameGroups.forEach { (key, videoList) ->
            val (type, season) = longFilenameMetadata[key] ?: Pair(VideoGroupType.UNKNOWN, null)
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
        
        // 3. Source-based groups (Camera, WhatsApp, etc.) - for short videos and long videos not in filename groups
        allSourceGroups.forEach { (key, videoList) ->
            val type = allSourceMetadata[key] ?: VideoGroupType.OTHER
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
        
        // 4. Filename-based groups from short videos (only if not already in a group)
        shortFilenameGroups.forEach { (key, videoList) ->
            // Only add if not already added from long videos
            if (!longFilenameGroups.containsKey(key)) {
                val (type, season) = shortFilenameMetadata[key] ?: Pair(VideoGroupType.UNKNOWN, null)
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

