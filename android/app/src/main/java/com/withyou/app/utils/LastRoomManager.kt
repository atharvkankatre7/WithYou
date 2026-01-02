package com.withyou.app.utils

import android.content.Context
import androidx.core.content.edit
import com.google.gson.Gson
import timber.log.Timber

data class LastRoomInfo(
    val roomId: String,
    val userId: String,
    val role: String,
    val videoId: String?,
    val position: Long,
    val savedAt: Long
)

class LastRoomManager(context: Context) {
    private val prefs = context.getSharedPreferences("last_room_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveLastRoom(roomId: String, userId: String, role: String, videoId: String?, position: Long) {
        val info = LastRoomInfo(
            roomId = roomId,
            userId = userId,
            role = role,
            videoId = videoId,
            position = position,
            savedAt = System.currentTimeMillis()
        )
        val json = gson.toJson(info)
        prefs.edit {
            putString("last_room_info", json)
        }
        Timber.d("Last room info saved: $roomId, $role")
    }

    fun getLastRoom(): LastRoomInfo? {
        val json = prefs.getString("last_room_info", null) ?: return null
        return try {
            gson.fromJson(json, LastRoomInfo::class.java)
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse last room info")
            null
        }
    }

    fun clearLastRoom() {
        prefs.edit {
            remove("last_room_info")
        }
        Timber.d("Last room info cleared")
    }
}
