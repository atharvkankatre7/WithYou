package com.withyou.app.player

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import timber.log.Timber
import java.io.File

/**
 * Extracts audio track from video (FAST - no video re-encoding)
 * Only extracts audio, converts to AAC for playback
 */
class AudioExtractor(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioExtractor"
    }
    
    private var libVLC: LibVLC? = null
    
    fun initialize(): Boolean {
        return try {
            // Optimized options for maximum extraction speed
            val options = arrayListOf(
                "--intf", "dummy",              // No interface (faster)
                "--no-drop-late-frames",
                "--no-skip-frames",
                "--rtsp-tcp",
                "--avcodec-fast",              // Fast codec processing
                "--avcodec-threads=4",         // Multi-threaded (if available)
                "--sout-mux-caching=0",        // No caching for speed
                "--no-audio-time-stretch"      // Disable time stretching
            )
            libVLC = LibVLC(context, options)
            Timber.i("$TAG: LibVLC initialized with speed optimizations")
            true
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to initialize LibVLC")
            false
        }
    }
    
    /**
     * Analyze video to get all audio track information
     */
    suspend fun analyzeAllAudioTracks(videoUri: Uri): List<AudioTrackInfo> = withContext(Dispatchers.IO) {
        val vlc = libVLC ?: return@withContext emptyList()
        val inputPath = getInputPath(videoUri) ?: return@withContext emptyList()
        
        val media = Media(vlc, Uri.parse("file://$inputPath"))
        media.parse(IMedia.Parse.ParseLocal)
        
        var attempts = 0
        while (!media.isParsed && attempts < 50) {
            delay(100)
            attempts++
        }
        
        val tracks = mutableListOf<AudioTrackInfo>()
        var audioIndex = 0
        
        for (i in 0 until media.trackCount) {
            val track = media.getTrack(i)
            if (track?.type == IMedia.Track.Type.Audio) {
                // LibVLC Track doesn't expose audioChannels/audioRate directly
                // We'll use defaults - the actual values will be determined during extraction
                tracks.add(
                    AudioTrackInfo(
                        index = audioIndex,
                        trackId = track.id,
                        codec = track.codec ?: "unknown",
                        language = track.language ?: "",
                        channels = 2,  // Default to stereo (will be determined during extraction)
                        sampleRate = 48000  // Default to 48kHz (will be determined during extraction)
                    )
                )
                audioIndex++
            }
        }
        
        media.release()
        Timber.i("$TAG: Found ${tracks.size} audio tracks")
        tracks
    }
    
    /**
     * Extract all audio tracks in parallel for maximum speed
     */
    suspend fun extractAllAudioTracks(
        videoUri: Uri,
        audioTracks: List<AudioTrackInfo>,
        onProgress: (Int, Int) -> Unit  // (overallProgress, completedTracks)
    ): Map<Int, Uri> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<Int, Uri>()
        val completedCount = AtomicInteger(0)
        val totalTracks = audioTracks.size
        
        // Extract tracks in parallel for maximum speed
        coroutineScope {
            val deferredResults = audioTracks.map { trackInfo ->
                async(Dispatchers.IO) {
                    val result = extractAudio(videoUri, trackInfo.index) { trackProgress ->
                        // Calculate overall progress: (completed tracks * 100 + current track progress) / total tracks
                        val completed = completedCount.get()
                        val overallProgress = ((completed * 100 + trackProgress) / totalTracks).coerceIn(0, 100)
                        onProgress(overallProgress, completed)
                    }
                    if (result.success && result.outputUri != null) {
                        synchronized(results) {
                            results[trackInfo.index] = result.outputUri!!
                        }
                        val completed = completedCount.incrementAndGet()
                        onProgress(100, completed)  // Track completed
                        Timber.i("$TAG: ✓ Track ${trackInfo.index} extracted ($completed/$totalTracks)")
                    } else {
                        val completed = completedCount.incrementAndGet()
                        Timber.e("$TAG: ✗ Track ${trackInfo.index} failed: ${result.errorMessage} ($completed/$totalTracks)")
                    }
                }
            }
            deferredResults.awaitAll()
        }
        
        Timber.i("$TAG: Extracted ${results.size}/${audioTracks.size} audio tracks")
        results
    }
    
    data class AudioTrackInfo(
        val index: Int,
        val trackId: Int,
        val codec: String,
        val language: String,
        val channels: Int,
        val sampleRate: Int
    )
    
    /**
     * Extract audio track from video (audio only - very fast!)
     * 
     * @param videoUri Input video URI
     * @param audioTrackIndex Which audio track to extract (0-indexed)
     * @param onProgress Progress callback (0-100)
     * @return URI of extracted audio file (AAC format)
     */
    suspend fun extractAudio(
        videoUri: Uri,
        audioTrackIndex: Int = 0,
        onProgress: (Int) -> Unit
    ): AudioConverter.ConversionResult = withContext(Dispatchers.IO) {
        
        val vlc = libVLC ?: return@withContext AudioConverter.ConversionResult(
            success = false,
            errorMessage = "LibVLC not initialized"
        )
        
        val inputPath = getInputPath(videoUri)
        if (inputPath == null) {
            return@withContext AudioConverter.ConversionResult(
                success = false,
                errorMessage = "Could not access video file"
            )
        }
        
        // Create output audio file (AAC in MP4 container)
        val outputDir = File(context.cacheDir, "extracted_audio")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "audio_track_${audioTrackIndex}_${System.currentTimeMillis()}.m4a")
        val outputPath = outputFile.absolutePath
        
        try {
            Timber.i("$TAG: Extracting audio track $audioTrackIndex (AUDIO ONLY - FAST!)")
            onProgress(0)
            
            val media = Media(vlc, Uri.parse("file://$inputPath"))
            
            // Extract ONLY audio - optimized for MAXIMUM SPEED
            // Use MP4 muxer (most compatible, available in all LibVLC builds)
            // Note: We'll add track selection after parsing, so we build the base sout string first
            val baseSoutOptions = buildString {
                append(":sout=#transcode{")
                append("vcodec=none,")                    // Skip video entirely (FAST!)
                append("acodec=mp4a,")                    // Convert to AAC
                append("ab=128,")                         // Lower bitrate = faster (still good quality)
                append("channels=2,")                      // Stereo
                append("samplerate=48000,")                // 48kHz
                append("acompress=0,")                    // No compression overhead
                append("athreads=4")                      // Multi-threaded audio encoding
                append("}:std{")
                append("access=file,")
                append("mux=mp4,")                        // MP4 muxer (always available, works for audio-only)
                append("dst=$outputPath")
                append("}")
            }
            
            // Parse media FIRST to get track information BEFORE configuring sout
            media.parse(IMedia.Parse.ParseLocal)
            var attempts = 0
            while (!media.isParsed && attempts < 50) {
                delay(100)
                attempts++
            }
            
            if (!media.isParsed) {
                Timber.w("$TAG: Media parsing timed out, proceeding anyway")
            }
            
            val durationMs = media.duration
            Timber.i("$TAG: Media duration: ${durationMs}ms, track count: ${media.trackCount}")
            
            // Find the correct audio track
            var audioTrackFound = false
            var audioTrackCount = 0
            var selectedTrackId: Int? = null
            var selectedTrackLanguage: String? = null
            var selectedTrackCodec: String? = null
            
            for (i in 0 until media.trackCount) {
                val track = media.getTrack(i)
                if (track?.type == IMedia.Track.Type.Audio) {
                    Timber.d("$TAG: Found audio track $audioTrackCount: codec=${track.codec}, lang=${track.language}, trackId=${track.id}, mediaIndex=$i")
                    if (audioTrackCount == audioTrackIndex) {
                        selectedTrackId = track.id
                        selectedTrackLanguage = track.language
                        selectedTrackCodec = track.codec
                        audioTrackFound = true
                        Timber.i("$TAG: ✓✓✓ SELECTING audio track $audioTrackIndex: lang=${track.language}, codec=${track.codec}, trackId=${track.id}, mediaIndex=$i")
                        break
                    }
                    audioTrackCount++
                }
            }
            
            if (!audioTrackFound) {
                Timber.e("$TAG: ❌ Audio track $audioTrackIndex not found (found $audioTrackCount tracks)!")
            }
            
            // Build sout string with track selection in transcode filter
            // Try using audio-track parameter in transcode filter (more reliable for stream output)
            // Note: audio-track in transcode filter uses the audio track index (0, 1, 2...), not the track ID
            val soutOptions = if (audioTrackFound && selectedTrackId != null) {
                buildString {
                    append(":sout=#transcode{")
                    append("vcodec=none,")
                    append("acodec=mp4a,")
                    append("ab=128,")
                    append("channels=2,")
                    append("samplerate=48000,")
                    append("acompress=0,")
                    append("athreads=4,")
                    append("audio-track=$audioTrackIndex")  // Use audio track index (0, 1, 2...)
                    append("}:std{")
                    append("access=file,")
                    append("mux=mp4,")
                    append("dst=$outputPath")
                    append("}")
                }
            } else {
                baseSoutOptions
            }
            
            Timber.i("$TAG: Extracting audio track index=$audioTrackIndex, trackId=$selectedTrackId, lang=$selectedTrackLanguage")
            
            // Add sout options
            media.addOption(soutOptions)
            media.addOption(":sout-all")
            media.addOption(":sout-keep")
            media.addOption(":sout-audio-sync")
            media.addOption(":sout-mux-caching=1000")
            media.addOption(":avcodec-fast")
            media.addOption(":avcodec-threads=4")
            media.addOption(":no-sout-video")
            media.addOption(":sout-audio")
            
            // Also add :audio-track option as fallback (use track ID for more reliable selection)
            if (audioTrackFound && selectedTrackId != null) {
                // Try using track ID first (more reliable)
                media.addOption(":audio-track=$selectedTrackId")
                // Also add index-based option as fallback
                media.addOption(":audio-track=$audioTrackIndex")
                Timber.i("$TAG: Configured track selection: audio-track=$audioTrackIndex (ID=$selectedTrackId), lang=$selectedTrackLanguage, codec=$selectedTrackCodec")
            } else if (audioTrackFound) {
                media.addOption(":audio-track=$audioTrackIndex")
                Timber.i("$TAG: Configured track selection: audio-track=$audioTrackIndex (no ID), lang=$selectedTrackLanguage, codec=$selectedTrackCodec")
            }
            
            val mediaPlayer = MediaPlayer(vlc)
            mediaPlayer.media = media
            
            var isCompleted = false
            var hasError = false
            var errorMessage: String? = null
            var lastProgress = 0
            
            mediaPlayer.setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Playing -> {
                        Timber.i("$TAG: Audio extraction started - playing")
                    }
                    MediaPlayer.Event.PositionChanged -> {
                        val progress = (event.positionChanged * 100).toInt()
                        if (progress != lastProgress) {
                            onProgress(progress.coerceIn(0, 99))
                            lastProgress = progress
                        }
                    }
                    MediaPlayer.Event.TimeChanged -> {
                        if (durationMs > 0) {
                            val progress = ((event.timeChanged.toDouble() / durationMs) * 100).toInt()
                            if (progress != lastProgress) {
                                onProgress(progress.coerceIn(0, 99))
                                lastProgress = progress
                            }
                        }
                    }
                    MediaPlayer.Event.EndReached -> {
                        Timber.i("$TAG: ✓ Audio extraction completed (EndReached)")
                        isCompleted = true
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        Timber.e("$TAG: ✗ Audio extraction error encountered")
                        hasError = true
                        errorMessage = "VLC extraction error"
                    }
                    MediaPlayer.Event.Stopped -> {
                        Timber.d("$TAG: Extraction stopped")
                    }
                    else -> {
                        Timber.d("$TAG: Event: ${event.type}")
                    }
                }
            }
            
            Timber.i("$TAG: Starting playback for extraction...")
            mediaPlayer.play()
            
            // Monitor progress and file growth
            var lastFileSize = 0L
            var noProgressCount = 0
            var waitTime = 0L
            val maxWaitTime = 10 * 60 * 1000L  // 10 minutes max (audio only is fast)
            
            while (!isCompleted && !hasError && waitTime < maxWaitTime) {
                delay(1000)  // Check every second
                waitTime += 1000
                
                // Check if file is growing (extraction is progressing)
                if (outputFile.exists()) {
                    val currentSize = outputFile.length()
                    if (currentSize > lastFileSize) {
                        noProgressCount = 0
                        lastFileSize = currentSize
                        Timber.d("$TAG: File growing: ${currentSize} bytes")
                    } else if (currentSize == lastFileSize && waitTime > 10000) {
                        // No progress for 10 seconds after initial start
                        noProgressCount++
                        if (noProgressCount > 5) {
                            Timber.w("$TAG: No file growth for ${noProgressCount * 1000}ms, checking status...")
                            // Check if player is still playing
                            if (!mediaPlayer.isPlaying && !isCompleted) {
                                Timber.e("$TAG: Player stopped but not completed - possible error")
                                hasError = true
                                errorMessage = "Extraction stalled - no progress"
                                break
                            }
                        }
                    }
                } else if (waitTime > 5000) {
                    // File should exist after 5 seconds
                    Timber.w("$TAG: Output file not created after 5 seconds")
                }
            }
            
            Timber.i("$TAG: Extraction loop ended - completed: $isCompleted, hasError: $hasError, waitTime: ${waitTime}ms")
            
            mediaPlayer.stop()
            mediaPlayer.release()
            media.release()
            
            if (hasError || !isCompleted) {
                outputFile.delete()
                val finalErrorMessage = errorMessage ?: if (hasError) "Audio extraction error" else "Extraction timed out or incomplete"
                Timber.e("$TAG: ✗ Extraction failed: $finalErrorMessage")
                AudioConverter.ConversionResult(
                    success = false,
                    errorMessage = finalErrorMessage
                )
            } else if (outputFile.exists() && outputFile.length() > 1000) {
                onProgress(100)
                Timber.i("$TAG: ✓ Audio extracted successfully: ${outputFile.length()} bytes, URI: ${Uri.fromFile(outputFile)}")
                AudioConverter.ConversionResult(
                    success = true,
                    outputUri = Uri.fromFile(outputFile)
                )
            } else {
                val fileSize = if (outputFile.exists()) outputFile.length() else 0L
                Timber.e("$TAG: ✗ Extraction produced invalid output: file exists=${outputFile.exists()}, size=$fileSize bytes")
                outputFile.delete()
                AudioConverter.ConversionResult(
                    success = false,
                    errorMessage = "Extraction produced invalid output (size: $fileSize bytes)"
                )
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error during audio extraction")
            outputFile.delete()
            AudioConverter.ConversionResult(
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
    
    private fun getInputPath(uri: Uri): String? {
        return try {
            if (uri.scheme == "content") {
                // Use a more specific temp directory and clean up old temp files
                val tempDir = File(context.cacheDir, "temp_audio_input")
                if (!tempDir.exists()) {
                    tempDir.mkdirs()
                }
                
                // Clean up old temp files (older than 1 hour)
                cleanupOldTempFiles(tempDir, 3600000L) // 1 hour
                
                val tempFile = File(tempDir, "temp_input_${System.currentTimeMillis()}.tmp")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
                tempFile.absolutePath
            } else {
                uri.path
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error getting input path")
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
    
    fun cleanup() {
        try {
            // Clean up extracted audio directory
            val audioDir = File(context.cacheDir, "extracted_audio")
            audioDir.listFiles()?.forEach { it.delete() }
            
            // Clean up temp input files
            val tempDir = File(context.cacheDir, "temp_audio_input")
            tempDir.listFiles()?.forEach { it.delete() }
            
            Timber.d("$TAG: Cleaned up temporary files")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error cleaning up")
        }
    }
    
    fun release() {
        cleanup()
        libVLC?.release()
        libVLC = null
    }
}

