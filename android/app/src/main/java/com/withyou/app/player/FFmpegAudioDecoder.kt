package com.withyou.app.player

import android.content.Context
import android.net.Uri
import timber.log.Timber

/**
 * Helper class for detecting and handling unsupported audio formats
 * 
 * Works with AudioConverter (LibVLC) to convert EAC3/AC3/DTS audio to AAC
 */
class FFmpegAudioDecoder(private val context: Context) {
    
    private val audioConverter = AudioConverter(context)
    
    companion object {
        // Check if audio format needs conversion (not natively supported by most devices)
        fun needsConversion(mimeType: String?): Boolean {
            return AudioConverter.needsConversion(mimeType)
        }
        
        /**
         * Get a user-friendly message about unsupported audio format
         */
        fun getUnsupportedAudioMessage(mimeType: String?): String {
            return when {
                mimeType?.contains("eac3", ignoreCase = true) == true -> 
                    "Dolby Digital Plus (EAC3) audio detected - converting to AAC..."
                mimeType?.contains("ac3", ignoreCase = true) == true -> 
                    "Dolby Digital (AC3) audio detected - converting to AAC..."
                mimeType?.contains("dts", ignoreCase = true) == true -> 
                    "DTS audio detected - converting to AAC..."
                mimeType?.contains("truehd", ignoreCase = true) == true -> 
                    "Dolby TrueHD audio detected - converting to AAC..."
                mimeType?.contains("a52", ignoreCase = true) == true ->
                    "Dolby Digital (AC3) audio detected - converting to AAC..."
                mimeType?.contains("dca", ignoreCase = true) == true ->
                    "DTS audio detected - converting to AAC..."
                else -> 
                    "Unsupported audio format detected - converting..."
            }
        }
        
        /**
         * Get codec display name
         */
        fun getCodecDisplayName(mimeType: String?): String {
            return when {
                mimeType?.contains("eac3", ignoreCase = true) == true -> "Dolby Digital Plus (EAC3)"
                mimeType?.contains("ac3", ignoreCase = true) == true -> "Dolby Digital (AC3)"
                mimeType?.contains("a52", ignoreCase = true) == true -> "Dolby Digital (AC3)"
                mimeType?.contains("dts", ignoreCase = true) == true -> "DTS"
                mimeType?.contains("dca", ignoreCase = true) == true -> "DTS"
                mimeType?.contains("truehd", ignoreCase = true) == true -> "Dolby TrueHD"
                mimeType?.contains("aac", ignoreCase = true) == true -> "AAC"
                mimeType?.contains("mp4a", ignoreCase = true) == true -> "AAC"
                mimeType?.contains("mp3", ignoreCase = true) == true -> "MP3"
                mimeType?.contains("opus", ignoreCase = true) == true -> "Opus"
                mimeType?.contains("vorbis", ignoreCase = true) == true -> "Vorbis"
                mimeType?.contains("flac", ignoreCase = true) == true -> "FLAC"
                else -> mimeType ?: "Unknown"
            }
        }
    }
    
    /**
     * Analyze video for audio track information
     */
    suspend fun analyzeAudio(videoUri: Uri): List<AudioConverter.AudioInfo> {
        return audioConverter.analyzeAudio(videoUri)
    }
    
    /**
     * Convert audio to a supported format (AAC) using LibVLC
     * 
     * @param videoUri The input video URI
     * @param audioTrackIndex Which audio track to convert
     * @param onProgress Progress callback (0-100)
     * @return The converted video URI, or null if conversion failed
     */
    suspend fun convertAudio(
        videoUri: Uri,
        audioTrackIndex: Int = 0,
        onProgress: (Int) -> Unit
    ): Uri? {
        Timber.i("Starting audio conversion for track $audioTrackIndex using LibVLC")
        
        val result = audioConverter.convertAudio(videoUri, audioTrackIndex, onProgress)
        
        return if (result.success) {
            Timber.i("Audio conversion successful: ${result.outputUri}")
            result.outputUri
        } else {
            Timber.e("Audio conversion failed: ${result.errorMessage}")
            null
        }
    }
    
    /**
     * Clean up temporary files
     */
    fun cleanup() {
        audioConverter.cleanup()
    }
    
    /**
     * Release resources
     */
    fun release() {
        audioConverter.release()
    }
}
