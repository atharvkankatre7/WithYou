package com.withyou.app.utils

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.InputStream
import java.security.MessageDigest

/**
 * File metadata
 */
data class FileMetadata(
    val hash: String,
    val durationMs: Long,
    val fileSize: Long,
    val codec: CodecInfo,
    val displayName: String
)

/**
 * Codec information
 */
data class CodecInfo(
    val video: String,
    val audio: String,
    val resolution: String
)

/**
 * Compute SHA-256 hash of a file from URI
 */
suspend fun computeSHA256(contentResolver: ContentResolver, uri: Uri): String = withContext(Dispatchers.IO) {
    try {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 1024) // 1MB buffer for efficiency
        
        contentResolver.openInputStream(uri)?.use { inputStream ->
            var bytesRead = inputStream.read(buffer)
            while (bytesRead != -1) {
                digest.update(buffer, 0, bytesRead)
                bytesRead = inputStream.read(buffer)
            }
        }
        
        val hashBytes = digest.digest()
        hashBytes.joinToString("") { "%02x".format(it) }
    } catch (e: Exception) {
        Timber.e(e, "Error computing SHA-256")
        throw e
    }
}

/**
 * Extract file metadata including codec info and duration
 */
suspend fun extractFileMetadata(
    context: Context,
    uri: Uri,
    progressCallback: ((Int) -> Unit)? = null
): FileMetadata = withContext(Dispatchers.IO) {
    try {
        val contentResolver = context.contentResolver
        
        // Get file size and name
        val fileSize = getFileSize(contentResolver, uri)
        val fileName = getFileName(contentResolver, uri)
        
        Timber.d("Processing file: $fileName, size: $fileSize bytes")
        
        // Extract codec and duration using MediaMetadataRetriever
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: "unknown"
        
        retriever.release()
        
        // Parse codec info from mime type
        val codecInfo = parseCodecInfo(mimeType, width, height)
        
        Timber.d("File metadata: duration=${durationMs}ms, resolution=${width}x${height}")
        
        // Compute hash (with progress callback)
        progressCallback?.invoke(0)
        val hash = computeSHA256WithProgress(contentResolver, uri, fileSize, progressCallback)
        progressCallback?.invoke(100)
        
        FileMetadata(
            hash = hash,
            durationMs = durationMs,
            fileSize = fileSize,
            codec = codecInfo,
            displayName = fileName
        )
    } catch (e: Exception) {
        Timber.e(e, "Error extracting file metadata")
        throw e
    }
}

/**
 * Compute SHA-256 with progress reporting (handles both content:// and file:// URIs)
 */
private suspend fun computeSHA256WithProgress(
    contentResolver: ContentResolver,
    uri: Uri,
    fileSize: Long,
    progressCallback: ((Int) -> Unit)?
): String = withContext(Dispatchers.IO) {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(1024 * 1024) // 1MB buffer
    var totalBytesRead = 0L
    
    // Get input stream based on URI scheme
    val inputStream: InputStream? = when (uri.scheme) {
        "file" -> {
            val path = uri.path
            if (path != null) {
                java.io.FileInputStream(path)
            } else {
                null
            }
        }
        "content" -> contentResolver.openInputStream(uri)
        else -> contentResolver.openInputStream(uri)
    }
    
    inputStream?.use { stream ->
        var bytesRead = stream.read(buffer)
        while (bytesRead != -1) {
            digest.update(buffer, 0, bytesRead)
            totalBytesRead += bytesRead
            
            // Report progress (0-100)
            if (fileSize > 0 && progressCallback != null) {
                val progress = ((totalBytesRead.toDouble() / fileSize) * 100).toInt()
                progressCallback(progress)
            }
            
            bytesRead = stream.read(buffer)
        }
    }
    
    val hashBytes = digest.digest()
    hashBytes.joinToString("") { "%02x".format(it) }
}

/**
 * Get file size from URI (handles both content:// and file:// URIs)
 */
private fun getFileSize(contentResolver: ContentResolver, uri: Uri): Long {
    return when (uri.scheme) {
        "file" -> {
            // For file:// URIs, use File class
            val path = uri.path
            if (path != null) {
                java.io.File(path).length()
            } else {
                0L
            }
        }
        "content" -> {
            // For content:// URIs, query ContentResolver
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getLong(sizeIndex)
                } else {
                    0L
                }
            } ?: 0L
        }
        else -> 0L
    }
}

/**
 * Get file name from URI (handles both content:// and file:// URIs)
 */
private fun getFileName(contentResolver: ContentResolver, uri: Uri): String {
    return when (uri.scheme) {
        "file" -> {
            // For file:// URIs, extract name from path
            val path = uri.path
            if (path != null) {
                java.io.File(path).name
            } else {
                "unknown"
            }
        }
        "content" -> {
            // For content:// URIs, query ContentResolver
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex) ?: "unknown"
                } else {
                    "unknown"
                }
            } ?: "unknown"
        }
        else -> "unknown"
    }
}

/**
 * Parse codec info from mime type
 */
private fun parseCodecInfo(mimeType: String, width: Int, height: Int): CodecInfo {
    val video = when {
        mimeType.contains("h264", ignoreCase = true) -> "h264"
        mimeType.contains("h265", ignoreCase = true) || mimeType.contains("hevc", ignoreCase = true) -> "h265"
        mimeType.contains("vp9", ignoreCase = true) -> "vp9"
        mimeType.contains("vp8", ignoreCase = true) -> "vp8"
        mimeType.contains("av1", ignoreCase = true) -> "av1"
        else -> "unknown"
    }
    
    val audio = when {
        mimeType.contains("aac", ignoreCase = true) -> "aac"
        mimeType.contains("mp3", ignoreCase = true) -> "mp3"
        mimeType.contains("opus", ignoreCase = true) -> "opus"
        mimeType.contains("vorbis", ignoreCase = true) -> "vorbis"
        else -> "unknown"
    }
    
    val resolution = if (width > 0 && height > 0) "${width}x${height}" else "unknown"
    
    return CodecInfo(video, audio, resolution)
}

/**
 * Format file size to human readable string
 */
fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.2f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.2f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}

/**
 * Format duration to human readable string
 */
fun formatDuration(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes % 60, seconds % 60)
        minutes > 0 -> "%d:%02d".format(minutes, seconds % 60)
        else -> "0:%02d".format(seconds)
    }
}

