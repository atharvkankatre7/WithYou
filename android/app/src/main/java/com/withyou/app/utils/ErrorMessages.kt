package com.withyou.app.utils

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.IOException

/**
 * Sealed class representing user-friendly error types
 */
sealed class AppError(
    val title: String,
    val message: String,
    val icon: ImageVector,
    val actionLabel: String = "Try Again"
) {
    object NetworkTimeout : AppError(
        title = "Connection Timeout",
        message = "The server is taking too long to respond. It may be waking up from sleep mode. Please wait a moment and try again.",
        icon = Icons.Default.Timer,
        actionLabel = "Retry"
    )
    
    object NoInternet : AppError(
        title = "No Internet Connection",
        message = "Please check your network connection and try again.",
        icon = Icons.Default.WifiOff,
        actionLabel = "Retry"
    )
    
    object ServerUnavailable : AppError(
        title = "Server Unavailable",
        message = "The server is currently unavailable. Please try again later.",
        icon = Icons.Default.CloudOff,
        actionLabel = "Retry"
    )
    
    object FileMismatch : AppError(
        title = "Video File Mismatch",
        message = "The selected video doesn't match the host's video. Please make sure you select the exact same file.",
        icon = Icons.Default.Warning,
        actionLabel = "Select Different File"
    )
    
    object RoomNotFound : AppError(
        title = "Room Not Found",
        message = "This room doesn't exist or has expired. Ask the host to create a new room.",
        icon = Icons.Default.SearchOff,
        actionLabel = "Go Back"
    )
    
    object RoomClosed : AppError(
        title = "Room Closed",
        message = "The host has ended this session.",
        icon = Icons.Default.MeetingRoom,
        actionLabel = "Go Back"
    )
    
    object Unauthorized : AppError(
        title = "Session Expired",
        message = "Your login session has expired. Please sign in again.",
        icon = Icons.Default.Lock,
        actionLabel = "Sign In"
    )
    
    object InvalidRoomCode : AppError(
        title = "Invalid Room Code",
        message = "Please enter a valid room code (4-6 characters).",
        icon = Icons.Default.Error,
        actionLabel = "Try Again"
    )
    
    data class Generic(val errorMessage: String) : AppError(
        title = "Something Went Wrong",
        message = errorMessage,
        icon = Icons.Default.ErrorOutline,
        actionLabel = "Dismiss"
    )
    
    companion object {
        /**
         * Map an exception to a user-friendly AppError
         */
        fun fromException(e: Exception): AppError {
            return when (e) {
                is SocketTimeoutException -> NetworkTimeout
                is ConnectException -> ServerUnavailable
                is UnknownHostException -> NoInternet
                is IOException -> {
                    when {
                        e.message?.contains("timeout", ignoreCase = true) == true -> NetworkTimeout
                        e.message?.contains("network", ignoreCase = true) == true -> NoInternet
                        else -> Generic(e.message ?: "An unknown error occurred")
                    }
                }
                else -> Generic(e.message ?: "An unknown error occurred")
            }
        }
        
        /**
         * Map an API error message to AppError
         */
        fun fromApiError(code: String?, message: String?): AppError {
            return when (code) {
                "ROOM_NOT_FOUND" -> RoomNotFound
                "ROOM_CLOSED" -> RoomClosed
                "FILE_MISMATCH" -> FileMismatch
                "UNAUTHORIZED" -> Unauthorized
                "INVALID_ROOM_CODE" -> InvalidRoomCode
                else -> Generic(message ?: "An error occurred")
            }
        }
    }
}
