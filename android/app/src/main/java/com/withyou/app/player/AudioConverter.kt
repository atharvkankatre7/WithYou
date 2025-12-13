package com.withyou.app.player

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * Audio converter using GStreamer for Android
 * 
 * Converts videos with unsupported audio formats (EAC3, AC3, DTS) to AAC format
 * using GStreamer pipeline framework.
 * 
 * GStreamer Pipeline:
 * filesrc -> decodebin -> audioconvert -> audioresample -> 
 * avenc_aac (or fdkaacenc) -> mp4mux -> filesink
 * 
 * Setup Requirements:
 * 1. Include GStreamer Android binaries in app/libs/
 * 2. Add native libraries (.so files) for target architectures
 * 3. Ensure GStreamer plugins are available:
 *    - gst-plugins-base (audioconvert, audioresample, mp4mux)
 *    - gst-plugins-good (filesrc, filesink)
 *    - gst-plugins-bad (decodebin, avenc_aac, fdkaacenc)
 *    - gst-plugins-ugly (for AC3/DTS decoding if needed)
 */
class AudioConverter(private val context: Context) {
    
    companion object {
        private const val TAG = "AudioConverter"
        
        // Unsupported audio codecs that need conversion
        private val UNSUPPORTED_CODECS = listOf(
            "eac3", "ac3", "dts", "truehd", "mlp", "dts-hd",
            "e-ac-3", "a52", "dca"
        )
        
        /**
         * Check if an audio codec needs conversion
         */
        fun needsConversion(codec: String?): Boolean {
            return codec?.lowercase()?.let { c ->
                UNSUPPORTED_CODECS.any { c.contains(it) }
            } ?: false
        }
        
        /**
         * Get codec display name
         */
        fun getCodecDisplayName(codec: String?): String {
            return when {
                codec?.contains("eac3", ignoreCase = true) == true -> "Dolby Digital Plus (EAC3)"
                codec?.contains("ac3", ignoreCase = true) == true -> "Dolby Digital (AC3)"
                codec?.contains("a52", ignoreCase = true) == true -> "Dolby Digital (AC3)"
                codec?.contains("dts", ignoreCase = true) == true -> "DTS"
                codec?.contains("dca", ignoreCase = true) == true -> "DTS"
                codec?.contains("truehd", ignoreCase = true) == true -> "Dolby TrueHD"
                codec?.contains("aac", ignoreCase = true) == true -> "AAC"
                codec?.contains("mp4a", ignoreCase = true) == true -> "AAC"
                else -> codec ?: "Unknown"
            }
        }
    }
    
    data class ConversionResult(
        val success: Boolean,
        val outputUri: Uri? = null,
        val errorMessage: String? = null
    )
    
    data class AudioInfo(
        val codec: String?,
        val channels: Int,
        val sampleRate: Int,
        val bitrate: Long,
        val language: String?,
        val needsConversion: Boolean
    )
    
    private var gstreamerConverter: GStreamerConverter? = null
    private var isGStreamerAvailable = false
    private var libVLCConverter: LibVLCConverter? = null
    private var isLibVLCAvailable = false
    
    init {
        // Try GStreamer first (preferred)
        try {
            gstreamerConverter = GStreamerConverter(context)
            isGStreamerAvailable = gstreamerConverter?.initialize() == true
            if (isGStreamerAvailable) {
                Timber.i("$TAG: GStreamer initialized successfully")
            } else {
                Timber.w("$TAG: GStreamer not available")
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to initialize GStreamer")
            isGStreamerAvailable = false
        }
        
        // Fallback to LibVLC if GStreamer not available
        if (!isGStreamerAvailable) {
            try {
                libVLCConverter = LibVLCConverter(context)
                isLibVLCAvailable = libVLCConverter?.initialize() == true
                if (isLibVLCAvailable) {
                    Timber.i("$TAG: LibVLC initialized as fallback")
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to initialize LibVLC fallback")
            }
        }
    }
    
    /**
     * Analyze video file to get audio track information
     */
    suspend fun analyzeAudio(videoUri: Uri): List<AudioInfo> = withContext(Dispatchers.IO) {
        val audioTracks = mutableListOf<AudioInfo>()
        
        try {
            val extractor = MediaExtractor()
            
            // Handle content:// URIs
            if (videoUri.scheme == "content") {
                context.contentResolver.openFileDescriptor(videoUri, "r")?.use { pfd ->
                    extractor.setDataSource(pfd.fileDescriptor)
                }
            } else {
                extractor.setDataSource(context, videoUri, null)
            }
            
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                
                if (mime.startsWith("audio/")) {
                    val codec = mime.lowercase()
                    val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    val bitrate = format.getInteger(MediaFormat.KEY_BIT_RATE).toLong()
                    
                    audioTracks.add(
                        AudioInfo(
                            codec = codec,
                            channels = channels,
                            sampleRate = sampleRate,
                            bitrate = bitrate,
                            language = format.getString(MediaFormat.KEY_LANGUAGE),
                            needsConversion = needsConversion(codec)
                        )
                    )
                    
                    Timber.d("$TAG: Audio track $i: codec=$codec, channels=$channels, rate=$sampleRate")
                }
            }
            
            extractor.release()
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error analyzing audio")
        }
        
        audioTracks
    }
    
    /**
     * Convert video audio to AAC format
     * Tries GStreamer first, falls back to LibVLC if GStreamer not available
     * 
     * @param videoUri The input video URI
     * @param audioTrackIndex Which audio track to convert (0-indexed)
     * @param onProgress Progress callback (0-100)
     * @return ConversionResult with the output file URI or error message
     */
    suspend fun convertAudio(
        videoUri: Uri,
        audioTrackIndex: Int = 0,
        onProgress: (Int) -> Unit
    ): ConversionResult = withContext(Dispatchers.IO) {
        
        // Try GStreamer first (preferred)
        if (isGStreamerAvailable && gstreamerConverter != null) {
            try {
                Timber.i("$TAG: Using GStreamer for conversion")
                val result = gstreamerConverter!!.convertAudio(
                    videoUri,
                    audioTrackIndex,
                    onProgress
                )
                
                if (result.success) {
                    Timber.i("$TAG: GStreamer conversion successful")
                    return@withContext result
                } else {
                    Timber.w("$TAG: GStreamer conversion failed, trying LibVLC fallback")
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: GStreamer conversion error, trying LibVLC fallback")
            }
        }
        
        // Fallback to LibVLC
        if (isLibVLCAvailable && libVLCConverter != null) {
            try {
                Timber.i("$TAG: Using LibVLC fallback for conversion")
                val result = libVLCConverter!!.convertAudio(
                    videoUri,
                    audioTrackIndex,
                    onProgress
                )
                
                if (result.success) {
                    Timber.i("$TAG: LibVLC conversion successful")
                    return@withContext result
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: LibVLC conversion error")
            }
        }
        
        // Both failed or not available
        ConversionResult(
            success = false,
            errorMessage = buildSetupInstructions()
        )
    }
    
    /**
     * Build setup instructions for GStreamer
     */
    private fun buildSetupInstructions(): String {
        return """
            GStreamer is not available in this app.
            
            To enable audio conversion, you need to:
            
            1. Download GStreamer Android binaries:
               - Visit: https://gstreamer.freedesktop.org/download/
               - Download Android binaries for your target architectures
            
            2. Add to your app:
               - Copy .so files to app/src/main/jniLibs/<arch>/
               - Include GStreamer plugins in assets/
            
            3. Required plugins:
               - gst-plugins-base (audioconvert, audioresample, mp4mux)
               - gst-plugins-good (filesrc, filesink)
               - gst-plugins-bad (decodebin, avenc_aac)
               - gst-plugins-ugly (for AC3/DTS decoding)
            
            Alternative: Use LibVLC (already included) which has built-in support.
        """.trimIndent()
    }
    
    /**
     * Clean up converted files
     */
    fun cleanup() {
        try {
            gstreamerConverter?.cleanup()
            libVLCConverter?.cleanup()
            
            val convertedDir = File(context.cacheDir, "converted")
            convertedDir.listFiles()?.forEach { it.delete() }
            
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("temp_input_")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error cleaning up")
        }
    }
    
    /**
     * Release resources
     */
    fun release() {
        cleanup()
        gstreamerConverter?.release()
        gstreamerConverter = null
        libVLCConverter?.release()
        libVLCConverter = null
    }
}
