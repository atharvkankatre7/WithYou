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
    
    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
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
                "Connection timeout. The server took too long to respond. Please try again.",
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

class ApiException(message: String, val code: Int) : Exception(message)

