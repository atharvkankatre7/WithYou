package com.withyou.app.utils

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Video file information
 */
data class VideoFile(
    val uri: Uri,
    val name: String,
    val path: String,
    val size: Long,
    val duration: Long,
    val dateAdded: Long,
    val mimeType: String,
    val relativePath: String? = null // For source-based grouping (Android 10+)
)

/**
 * Media scanner utility to scan device for video files
 */
object MediaScanner {
    
    /**
     * Check if we have permission to read videos
     */
    private fun hasVideoPermission(context: Context): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
        Timber.d("Permission check: hasPermission=$hasPermission, SDK=${Build.VERSION.SDK_INT}")
        return hasPermission
    }
    
    /**
     * Scan device for all video files
     */
    suspend fun scanVideos(context: Context): List<VideoFile> = withContext(Dispatchers.IO) {
        val videos = mutableListOf<VideoFile>()
        
        // Check permission first
        if (!hasVideoPermission(context)) {
            Timber.w("No permission to read videos")
            return@withContext videos
        }
        
        try {
            val contentResolver = context.contentResolver
            
            // Build projection - DATA column is deprecated on Android 10+, but we'll try to use it
            val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - DATA might not be available, use RELATIVE_PATH instead
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.RELATIVE_PATH,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.DATE_ADDED,
                    MediaStore.Video.Media.MIME_TYPE
                )
            } else {
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DATA,
                    MediaStore.Video.Media.SIZE,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.DATE_ADDED,
                    MediaStore.Video.Media.MIME_TYPE
                )
            }
            
            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
            
            Timber.d("Querying MediaStore for videos...")
            val cursor = contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )
            
            if (cursor == null) {
                Timber.e("MediaStore query returned null - permission issue?")
                return@withContext videos
            }
            
            cursor.use {
                Timber.d("Cursor returned ${cursor.count} rows")
                
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                
                // Try to get DATA column (might be -1 on Android 10+)
                val dataColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else {
                    cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                }
                
                val isAndroid10Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                
                var processedCount = 0
                var skippedCount = 0
                while (cursor.moveToNext()) {
                    try {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn) ?: "Unknown"
                        val size = cursor.getLong(sizeColumn)
                        val duration = cursor.getLong(durationColumn)
                        val dateAdded = cursor.getLong(dateAddedColumn)
                        val mimeType = cursor.getString(mimeTypeColumn) ?: "video/*"
                        
                        // Build URI first
                        val uri = Uri.withAppendedPath(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )
                        
                        // Get path information for source detection
                        val relativePath: String? = if (isAndroid10Plus && dataColumn >= 0) {
                            cursor.getString(dataColumn) // RELATIVE_PATH on Android 10+
                        } else {
                            null
                        }
                        
                        // On Android 10+, scoped storage prevents direct file access
                        // We should trust MediaStore and use URI-based access
                        val filePath: String = if (isAndroid10Plus) {
                            // Android 10+: Use URI as identifier (scoped storage)
                            // Don't check file existence - MediaStore is the source of truth
                            uri.toString()
                        } else {
                            // Android < 10: Try to get actual file path
                            val path = if (dataColumn >= 0) {
                                cursor.getString(dataColumn) ?: ""
                            } else {
                                ""
                            }
                            
                            // On older Android, verify file exists
                            if (path.isNotEmpty() && File(path).exists()) {
                                path
                            } else {
                                // Path doesn't exist - skip this file
                                skippedCount++
                                if (skippedCount <= 5) {
                                    Timber.w("Skipping video (path doesn't exist): $name, path=$path")
                                }
                                processedCount++
                                continue
                            }
                        }
                        
                        videos.add(
                            VideoFile(
                                uri = uri,
                                name = name,
                                path = filePath,
                                size = size,
                                duration = duration,
                                dateAdded = dateAdded * 1000, // Convert to milliseconds
                                mimeType = mimeType,
                                relativePath = relativePath // Store for source detection
                            )
                        )
                        processedCount++
                        
                        if (processedCount % 50 == 0) {
                            Timber.d("Processed $processedCount videos so far...")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error reading video file from cursor at position ${cursor.position}")
                    }
                }
                
                Timber.i("Processed $processedCount videos, added ${videos.size} to list (skipped: $skippedCount)")
            }
            
            Timber.i("✅ Scanned ${videos.size} video files successfully")
        } catch (e: SecurityException) {
            Timber.e(e, "SecurityException - Permission denied for reading videos")
        } catch (e: Exception) {
            Timber.e(e, "Error scanning videos: ${e.message}")
            e.printStackTrace()
        }
        
        videos
    }
    
    /**
     * Get video file by URI
     */
    suspend fun getVideoFile(context: Context, uri: Uri): VideoFile? = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATA,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.MIME_TYPE
            )
            
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
                    val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                    val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                    val mimeTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                    
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: "Unknown"
                    val path = cursor.getString(dataColumn) ?: ""
                    val size = cursor.getLong(sizeColumn)
                    val duration = cursor.getLong(durationColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)
                    val mimeType = cursor.getString(mimeTypeColumn) ?: "video/*"
                    
                    VideoFile(
                        uri = uri,
                        name = name,
                        path = path,
                        size = size,
                        duration = duration,
                        dateAdded = dateAdded * 1000,
                        mimeType = mimeType
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting video file")
            null
        }
    }
}

