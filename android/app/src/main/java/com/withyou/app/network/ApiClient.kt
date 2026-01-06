package com.withyou.app.network

import com.withyou.app.BuildConfig
import com.withyou.app.utils.CodecInfo
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * REST API client for room management
 */
class ApiClient(private val authToken: String) {
    
    companion object {
        private val sharedClient = OkHttpClient.Builder()
            .connectTimeout(90, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(90, TimeUnit.SECONDS)
            .build()
        
        /**
         * Check server health / wake up server from cold start
         * Call this early (e.g., on app launch) to avoid timeout when creating room
         * @return true if server is healthy, false if unreachable
         */
        suspend fun healthCheck(): Boolean = withContext(Dispatchers.IO) {
            try {
                val url = "${BuildConfig.SERVER_URL}/health"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                Timber.d("Performing health check to wake up server...")
                val response = sharedClient.newCall(request).execute()
                val isHealthy = response.isSuccessful
                Timber.i("Server health check: ${if (isHealthy) "OK" else "FAILED"}")
                isHealthy
            } catch (e: Exception) {
                Timber.w("Server health check failed: ${e.message}")
                false
            }
        }
        
        /**
         * Quick check if a room exists and is active (for auto-nav validation)
         * This doesn't require an auth token - uses GET endpoint
         * @return true if room exists and is active, false otherwise
         */
        suspend fun checkRoomExists(roomId: String): Boolean = withContext(Dispatchers.IO) {
            try {
                val url = "${BuildConfig.SERVER_URL}/api/rooms/$roomId"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .build()
                
                Timber.d("Checking if room exists: $roomId")
                val response = sharedClient.newCall(request).execute()
                
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    val json = org.json.JSONObject(body ?: "{}")
                    val isActive = json.optBoolean("is_active", false)
                    Timber.i("Room $roomId exists, is_active: $isActive")
                    isActive
                } else {
                    Timber.w("Room $roomId not found (HTTP ${response.code})")
                    false
                }
            } catch (e: Exception) {
                Timber.w("Failed to check room existence: ${e.message}")
                false
            }
        }
    }
    
    private val gson = Gson()
    private val client = sharedClient
    
    /**
     * Create a new room
     */
    suspend fun createRoom(
        fileHash: String,
        durationMs: Long,
        fileSize: Long,
        codec: CodecInfo,
        expiresInDays: Int = 7,
        passcode: String? = null
    ): CreateRoomResponse = withContext(Dispatchers.IO) {
        val url = "${BuildConfig.SERVER_URL}/api/rooms/create"
        
        val codecJson = JSONObject().apply {
            put("video", codec.video)
            put("audio", codec.audio)
            put("resolution", codec.resolution)
        }
        
        val requestBody = JSONObject().apply {
            put("file_hash", fileHash)
            put("duration_ms", durationMs)
            put("file_size", fileSize)
            put("codec", codecJson)
            put("expires_in_days", expiresInDays)
            if (passcode != null) {
                put("passcode", passcode)
            }
        }
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        Timber.d("Creating room...")
        
        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseBody).optString("error", "Unknown error")
                } catch (e: Exception) {
                    "HTTP ${response.code}"
                }
                throw ApiException("Failed to create room: $errorMsg", response.code)
            }
            
            val json = JSONObject(responseBody)
            CreateRoomResponse(
                roomId = json.getString("roomId"),
                shareUrl = json.getString("shareUrl"),
                expiresAt = json.optString("expiresAt")
            )
        } catch (e: ConnectException) {
            Timber.e(e, "Connection refused when creating room")
            throw ApiException(
                "Cannot connect to server. Please check that the server is running and accessible.",
                0
            )
        } catch (e: SocketTimeoutException) {
            Timber.e(e, "Connection timeout when creating room")
            throw ApiException(
                "Connection timeout. The server may be starting up (cold start). Please wait a moment and try again.",
                0
            )
        } catch (e: UnknownHostException) {
            Timber.e(e, "Unknown host when creating room")
            throw ApiException(
                "Cannot reach server. Please check your internet connection and server address.",
                0
            )
        } catch (e: IOException) {
            Timber.e(e, "Network error when creating room")
            throw ApiException(
                "Network error: ${e.message ?: "Unable to connect to server"}",
                0
            )
        } catch (e: ApiException) {
            // Re-throw ApiException as-is
            throw e
        }
        
    }
    
    /**
     * Validate room and check file hash
     */
    suspend fun validateRoom(
        roomId: String,
        fileHash: String? = null,
        passcode: String? = null
    ): ValidateRoomResponse = withContext(Dispatchers.IO) {
        val url = "${BuildConfig.SERVER_URL}/api/rooms/$roomId/validate"
        
        val requestBody = JSONObject().apply {
            if (fileHash != null) {
                put("file_hash", fileHash)
            }
            if (passcode != null) {
                put("passcode", passcode)
            }
        }
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()
        
        Timber.d("Validating room: $roomId")
        
        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(responseBody).optString("error", "Unknown error")
                } catch (e: Exception) {
                    "HTTP ${response.code}"
                }
                throw ApiException("Failed to validate room: $errorMsg", response.code)
            }
            
            val json = JSONObject(responseBody)
            val metadata = json.getJSONObject("hostFileMetadata")
            
            ValidateRoomResponse(
                roomId = json.getString("roomId"),
                hostFileHash = metadata.getString("hash"),
                hostFileDurationMs = metadata.getLong("duration_ms"),
                hostFileSize = metadata.getLong("file_size"),
                hashMatches = json.getBoolean("hashMatches"),
                expiresAt = json.optString("expiresAt"),
                requiresPasscode = json.getBoolean("requiresPasscode")
            )
        } catch (e: ConnectException) {
            Timber.e(e, "Connection refused when validating room")
            throw ApiException(
                "Cannot connect to server. Please check that the server is running and accessible.",
                0
            )
        } catch (e: SocketTimeoutException) {
            Timber.e(e, "Connection timeout when validating room")
            throw ApiException(
                "Connection timeout. The server took too long to respond. Please try again.",
                0
            )
        } catch (e: UnknownHostException) {
            Timber.e(e, "Unknown host when validating room")
            throw ApiException(
                "Cannot reach server. Please check your internet connection and server address.",
                0
            )
        } catch (e: IOException) {
            Timber.e(e, "Network error when validating room")
            throw ApiException(
                "Network error: ${e.message ?: "Unable to connect to server"}",
                0
            )
        } catch (e: ApiException) {
            // Re-throw ApiException as-is
            throw e
        }
    }
    
    /**
     * Close a room
     */
    suspend fun closeRoom(roomId: String): Unit = withContext(Dispatchers.IO) {
        val url = "${BuildConfig.SERVER_URL}/api/rooms/$roomId/close"
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        
        Timber.d("Closing room: $roomId")
        
        try {
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                val responseBody = response.body?.string() ?: ""
                val errorMsg = try {
                    JSONObject(responseBody).optString("error", "Unknown error")
                } catch (e: Exception) {
                    "HTTP ${response.code}"
                }
                throw ApiException("Failed to close room: $errorMsg", response.code)
            }
        } catch (e: ConnectException) {
            Timber.e(e, "Connection refused when closing room")
            throw ApiException(
                "Cannot connect to server. Please check that the server is running and accessible.",
                0
            )
        } catch (e: SocketTimeoutException) {
            Timber.e(e, "Connection timeout when closing room")
            throw ApiException(
                "Connection timeout. The server took too long to respond. Please try again.",
                0
            )
        } catch (e: UnknownHostException) {
            Timber.e(e, "Unknown host when closing room")
            throw ApiException(
                "Cannot reach server. Please check your internet connection and server address.",
                0
            )
        } catch (e: IOException) {
            Timber.e(e, "Network error when closing room")
            throw ApiException(
                "Network error: ${e.message ?: "Unable to connect to server"}",
                0
            )
        } catch (e: ApiException) {
            // Re-throw ApiException as-is
            throw e
        }
    }
    /**
     * Leave room temporarily (pause and mark offline)
     */
    suspend fun leaveTemporary(roomId: String): Unit = withContext(Dispatchers.IO) {
        val url = "${BuildConfig.SERVER_URL}/api/rooms/$roomId/leave-temporary"
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        
        Timber.d("Leaving room temporarily: $roomId")
        
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                // Log but don't crash - this is a best-effort call often made during shutdown
                Timber.w("Failed to leave room temporarily: ${response.code}")
            }
        } catch (e: Exception) {
            Timber.w("Error leaving room temporarily: ${e.message}")
        }
    }

    /**
     * Rejoin room and get state
     */
    suspend fun rejoinRoom(roomId: String): RejoinRoomResponse = withContext(Dispatchers.IO) {
        val url = "${BuildConfig.SERVER_URL}/api/rooms/$roomId/rejoin"
        
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $authToken")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()
        
        Timber.d("Rejoining room: $roomId")
        
        try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (!response.isSuccessful) {
                throw ApiException("Failed to rejoin room: ${response.code}", response.code)
            }
            
            val json = JSONObject(responseBody)
            val participantsJson = json.getJSONArray("participants")
            val participantsList = mutableListOf<ParticipantInfo>()
            
            for (i in 0 until participantsJson.length()) {
                val p = participantsJson.getJSONObject(i)
                participantsList.add(
                    ParticipantInfo(
                        userId = p.getString("userId"),
                        role = p.getString("role"),
                        isOnline = p.optBoolean("isOnline", false)
                    )
                )
            }
            
            RejoinRoomResponse(
                roomId = json.getString("roomId"),
                videoId = json.getString("videoId"),
                playbackState = json.getString("playbackState"),
                currentPosition = json.optLong("currentPosition", 0),
                participants = participantsList
            )
        } catch (e: Exception) {
            Timber.e(e, "Error rejoining room")
            throw if (e is ApiException) e else ApiException("Error rejoining room: ${e.message}", 0)
        }
    }
}

// Response models
data class CreateRoomResponse(
    val roomId: String,
    val shareUrl: String,
    val expiresAt: String
)

data class ValidateRoomResponse(
    val roomId: String,
    val hostFileHash: String,
    val hostFileDurationMs: Long,
    val hostFileSize: Long,
    val hashMatches: Boolean,
    val expiresAt: String,
    val requiresPasscode: Boolean
)

data class RejoinRoomResponse(
    val roomId: String,
    val videoId: String,
    val playbackState: String,
    val currentPosition: Long,
    val participants: List<ParticipantInfo>
)

data class ParticipantInfo(
    val userId: String,
    val role: String,
    val isOnline: Boolean
)

class ApiException(message: String, val code: Int) : Exception(message)

