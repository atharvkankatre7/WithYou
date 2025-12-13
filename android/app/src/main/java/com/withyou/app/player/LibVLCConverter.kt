package com.withyou.app.player

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import timber.log.Timber
import java.io.File

/**
 * LibVLC-based audio converter (fallback when GStreamer not available)
 * 
 * Uses LibVLC's transcoding capabilities to convert audio to AAC
 */
class LibVLCConverter(private val context: Context) {
    
    companion object {
        private const val TAG = "LibVLCConverter"
    }
    
    private var libVLC: LibVLC? = null
    
    fun initialize(): Boolean {
        return try {
            val options = arrayListOf(
                "--no-drop-late-frames",
                "--no-skip-frames",
                "--rtsp-tcp"
            )
            libVLC = LibVLC(context, options)
            Timber.i("$TAG: LibVLC initialized")
            true
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to initialize LibVLC")
            false
        }
    }
    
    suspend fun convertAudio(
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
        
        val outputDir = File(context.cacheDir, "converted")
        outputDir.mkdirs()
        val outputFile = File(outputDir, "converted_${System.currentTimeMillis()}.mkv")
        val outputPath = outputFile.absolutePath
        
        try {
            Timber.i("$TAG: Starting LibVLC conversion")
            onProgress(0)
            
            val media = Media(vlc, Uri.parse("file://$inputPath"))
            
            // Optimized: Fastest video encoding + audio conversion
            // Note: VLC doesn't support true video stream copy, so we use fastest encoding
            // Using ultrafast preset with multiple threads for speed
            val soutOptions = buildString {
                append(":sout=#gather:transcode{")
                append("vcodec=h264,")                    // H.264 video codec
                append("venc=x264{preset=ultrafast,tune=zerolatency,threads=4},")  // Fastest encoding
                append("vb=2000,")                        // Lower video bitrate for speed
                append("acodec=mp4a,")                    // Convert audio to AAC
                append("ab=128,")                          // Audio bitrate
                append("channels=2,")                      // Stereo
                append("samplerate=44100")                 // 44.1kHz
                append("}:std{")
                append("access=file,")
                append("mux=mp4,")                         // MP4 muxer
                append("dst=$outputPath")
                append("}")
            }
            
            Timber.i("$TAG: Using optimized pipeline (fastest video encoding + audio conversion)")
            Timber.i("$TAG: Video will be preserved with fastest encoding settings")
            
            media.addOption(soutOptions)
            media.addOption(":sout-all")
            media.addOption(":sout-keep")
            media.addOption(":sout-mux-caching=10000")  // Larger cache for stability
            
            // Speed optimizations for fastest encoding
            media.addOption(":no-audio-time-stretch")
            media.addOption(":avcodec-fast")
            media.addOption(":avcodec-threads=4")  // Use multiple threads
            media.addOption(":avcodec-hw=any")     // Use hardware acceleration if available
            media.addOption(":avcodec-skip-frame=0")  // Don't skip frames
            media.addOption(":avcodec-skip-idct=0")    // Don't skip IDCT
            media.addOption(":avcodec-dr=1")           // Direct rendering
            
            val mediaPlayer = MediaPlayer(vlc)
            mediaPlayer.media = media
            
            media.parse(IMedia.Parse.ParseLocal)
            var attempts = 0
            while (!media.isParsed && attempts < 30) {
                delay(100)
                attempts++
            }
            val durationMs = media.duration
            
            var isCompleted = false
            var hasError = false
            
            mediaPlayer.setEventListener { event ->
                when (event.type) {
                    MediaPlayer.Event.Playing -> {
                        Timber.d("$TAG: Transcoding started")
                    }
                    MediaPlayer.Event.PositionChanged -> {
                        val progress = (event.positionChanged * 100).toInt()
                        onProgress(progress.coerceIn(0, 99))
                    }
                    MediaPlayer.Event.TimeChanged -> {
                        if (durationMs > 0) {
                            val progress = ((event.timeChanged.toDouble() / durationMs) * 100).toInt()
                            onProgress(progress.coerceIn(0, 99))
                        }
                    }
                    MediaPlayer.Event.EndReached -> {
                        Timber.i("$TAG: Transcoding completed")
                        isCompleted = true
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        Timber.e("$TAG: Transcoding error")
                        hasError = true
                    }
                    else -> {}
                }
            }
            
            mediaPlayer.play()
            
            // Monitor progress and file growth
            var lastFileSize = 0L
            var noProgressCount = 0
            var lastProgress = 0
            
            var waitTime = 0L
            val maxWaitTime = 20 * 60 * 1000L  // 20 minutes max (reduced from 30)
            while (!isCompleted && !hasError && waitTime < maxWaitTime) {
                delay(1000)  // Check every second
                waitTime += 1000
                
                // Check if file is growing (conversion is progressing)
                if (outputFile.exists()) {
                    val currentSize = outputFile.length()
                    if (currentSize == lastFileSize && waitTime > 60000) {  // After 1 minute
                        noProgressCount++
                        if (noProgressCount > 30) {  // No progress for 30 seconds
                            Timber.w("$TAG: Conversion appears stalled, checking...")
                            // Check if we're making progress via events
                            val currentProgress = (mediaPlayer.position * 100).toInt()
                            if (currentProgress == lastProgress && currentProgress > 0) {
                                Timber.e("$TAG: Conversion stalled, aborting")
                                hasError = true
                                break
                            }
                            lastProgress = currentProgress
                            noProgressCount = 0
                        }
                    } else {
                        noProgressCount = 0
                    }
                    lastFileSize = currentSize
                }
            }
            
            mediaPlayer.stop()
            mediaPlayer.release()
            media.release()
            
            if (hasError || !isCompleted) {
                outputFile.delete()
                AudioConverter.ConversionResult(
                    success = false,
                    errorMessage = if (hasError) "LibVLC transcoding error" else "Transcoding timed out"
                )
            } else if (outputFile.exists() && outputFile.length() > 1000) {
                onProgress(100)
                AudioConverter.ConversionResult(
                    success = true,
                    outputUri = Uri.fromFile(outputFile)
                )
            } else {
                outputFile.delete()
                AudioConverter.ConversionResult(
                    success = false,
                    errorMessage = "Conversion produced invalid output"
                )
            }
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error during conversion")
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
                val tempFile = File(context.cacheDir, "temp_input_${System.currentTimeMillis()}.tmp")
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
    
    fun cleanup() {
        // Cleanup handled by release()
    }
    
    fun release() {
        libVLC?.release()
        libVLC = null
    }
}

