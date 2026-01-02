package com.withyou.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.withyou.app.R
import com.withyou.app.ui.MainActivity
import timber.log.Timber

/**
 * Foreground service for room session persistence.
 * 
 * This service keeps the room alive when app goes to background:
 * - Shows a notification indicating active room session
 * - Allows user to return to room via notification
 * - Keeps socket connection alive
 * 
 * Usage:
 * - Start when entering a room: RoomService.start(context, roomId)
 * - Stop when leaving room: RoomService.stop(context)
 */
class RoomService : Service() {
    
    companion object {
        private const val CHANNEL_ID = "room_service_channel"
        private const val NOTIFICATION_ID = 2
        
        const val ACTION_START = "com.withyou.app.service.START_ROOM"
        const val ACTION_STOP = "com.withyou.app.service.STOP_ROOM"
        const val EXTRA_ROOM_ID = "room_id"
        const val EXTRA_IS_HOST = "is_host"
        
        /**
         * Start the room service with room details.
         * Wrapped in try-catch to prevent crash on permission issues.
         */
        fun start(context: Context, roomId: String, isHost: Boolean) {
            try {
                val intent = Intent(context, RoomService::class.java).apply {
                    action = ACTION_START
                    putExtra(EXTRA_ROOM_ID, roomId)
                    putExtra(EXTRA_IS_HOST, isHost)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                Timber.d("RoomService: Starting service for room $roomId")
            } catch (e: Exception) {
                Timber.e(e, "RoomService: Failed to start service - permission issue?")
                // Don't crash the app if service fails to start
            }
        }
        
        /**
         * Stop the room service.
         */
        fun stop(context: Context) {
            val intent = Intent(context, RoomService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
            Timber.d("RoomService: Stopping service")
        }
    }
    
    private var currentRoomId: String? = null
    private var isHost: Boolean = false
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Timber.d("RoomService: Created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                currentRoomId = intent.getStringExtra(EXTRA_ROOM_ID)
                isHost = intent.getBooleanExtra(EXTRA_IS_HOST, false)
                startForeground(NOTIFICATION_ID, createNotification())
                Timber.i("RoomService: Started for room $currentRoomId (host: $isHost)")
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Timber.i("RoomService: Stopped")
            }
        }
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Timber.d("RoomService: Destroyed")
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Room Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active room session notification"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        // Create intent to return to app
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ROOM_ID, currentRoomId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val roleText = if (isHost) "Host" else "Viewer"
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WithYou - In Room")
            .setContentText("$roleText • Room: ${currentRoomId?.take(8) ?: "Unknown"}")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
