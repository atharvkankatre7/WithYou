package com.withyou.app.network

import com.withyou.app.BuildConfig
import com.google.gson.Gson
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONObject
import timber.log.Timber
import java.net.URISyntaxException

/**
 * Socket.IO manager for real-time communication
 */
class SocketManager(private val authToken: String) {
    
    private var socket: Socket? = null
    private val gson = Gson()
    
    // RTT tracking
    private var lastPingNonce: String? = null
    private var lastPingSentAt: Long = 0
    private var currentRtt: Long = 0
    
    /**
     * Connect to Socket.IO server
     */
    fun connect(): Flow<SocketEvent> = callbackFlow {
        try {
            val opts = IO.Options().apply {
                auth = mapOf("token" to authToken)
                // Try websocket first, fallback to polling if websocket fails
                transports = arrayOf("websocket", "polling")
                reconnection = true
                reconnectionDelay = 1000
                reconnectionDelayMax = 5000
                reconnectionAttempts = Int.MAX_VALUE // Keep trying indefinitely
                timeout = 20000 // 20 second timeout
                forceNew = false // Reuse existing connection if possible
            }
            
            socket = IO.socket(BuildConfig.SOCKET_URL, opts)
            
            // Connection events
            socket?.on(Socket.EVENT_CONNECT) {
                Timber.i("Socket connected")
                trySend(SocketEvent.Connected)
            }
            
            socket?.on(Socket.EVENT_DISCONNECT) {
                Timber.w("Socket disconnected")
                trySend(SocketEvent.Disconnected)
            }
            
            socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
                val error = args.firstOrNull()?.toString() ?: "Unknown error"
                Timber.e("Socket connection error: $error")
                // Don't send error event on connection errors - let reconnection handle it
                // The socket will automatically try to reconnect
            }
            
            // Use string literals for reconnection events (Socket.IO v2+)
            socket?.on("reconnect") { args ->
                Timber.i("Socket reconnected after error")
                trySend(SocketEvent.Connected)
            }
            
            socket?.on("reconnect_error") { args ->
                val error = args.firstOrNull()?.toString() ?: "Unknown error"
                Timber.e("Socket reconnection error: $error")
                // Still don't send error - let it keep trying
            }
            
            socket?.on("reconnect_attempt") {
                Timber.d("Attempting to reconnect socket...")
            }
            
            // Room events
            socket?.on("joined") { args ->
                val data = args.firstOrNull() as? JSONObject
                if (data != null) {
                    Timber.d("Joined room: ${data.optString("roomId")}")
                    trySend(SocketEvent.Joined(data.toString()))
                }
            }
            
            socket?.on("hostPlay") { args ->
                val data = args.firstOrNull() as? JSONObject
                if (data != null) {
                    val event = HostPlayEvent(
                        positionSec = data.getDouble("positionSec"),
                        hostTimestampMs = data.getLong("hostTimestampMs"),
                        playbackRate = data.optDouble("playbackRate", 1.0).toFloat()
                    )
                    Timber.d("Host play: position=${event.positionSec}")
                    trySend(SocketEvent.HostPlay(event))
                }
            }
            
            socket?.on("hostPause") { args ->
                val data = args.firstOrNull() as? JSONObject
                if (data != null) {
                    val event = HostPauseEvent(
                        positionSec = data.getDouble("positionSec"),
                        hostTimestampMs = data.getLong("hostTimestampMs")
                    )
                    Timber.d("Host pause: position=${event.positionSec}")
                    trySend(SocketEvent.HostPause(event))
                }
            }
            
            socket?.on("hostSeek") { args ->
                val data = args.firstOrNull() as? JSONObject
                if (data != null) {
                    val event = HostSeekEvent(
                        positionSec = data.getDouble("positionSec"),
                        hostTimestampMs = data.getLong("hostTimestampMs")
                    )
                    Timber.d("Host seek: position=${event.positionSec}")
                    trySend(SocketEvent.HostSeek(event))
                }
            }
            
            socket?.on("pong") { args ->
                val data = args.firstOrNull() as? JSONObject
                if (data != null) {
                    val nonce = data.optString("nonce")
                    val clientTs = data.optLong("clientTs", 0)
                    val serverTs = data.optLong("serverTs", 0)
                    
                    if (nonce == lastPingNonce) {
                        val rtt = System.currentTimeMillis() - clientTs
                        currentRtt = rtt
                        Timber.v("Pong received: RTT=${rtt}ms")
                        trySend(SocketEvent.Pong(rtt))
                    }
                }
            }
            
            socket?.on("reaction") { args ->
                val data = args.firstOrNull() as? JSONObject
                if (data != null) {
                    val reaction = ReactionEvent(
                        userId = data.optString("userId"),
                        type = data.optString("type"),
                        ts = data.optLong("ts", 0)
                    )
                    trySend(SocketEvent.Reaction(reaction))
                }
            }
            
            socket?.on("chatMessage") { args ->
                Timber.i("🟡 [SOCKET DEBUG] 'chatMessage' event received from server")
                Timber.i("🟡 [SOCKET DEBUG] Args count: ${args.size}")
                args.forEachIndexed { index, arg ->
                    Timber.i("🟡 [SOCKET DEBUG]   Arg[$index]: ${arg?.javaClass?.simpleName} = $arg")
                }
                
                val data = args.firstOrNull() as? JSONObject
                if (data != null) {
                    Timber.i("🟡 [SOCKET DEBUG] Parsed JSONObject successfully")
                    val userId = data.optString("userId")
                    val text = data.optString("text")
                    val ts = data.optLong("ts", 0)
                    Timber.i("🟡 [SOCKET DEBUG] Extracted: userId=$userId, text='$text', ts=$ts")
                    
                    val message = ChatMessageEvent(
                        userId = userId,
                        text = text,
                        ts = ts
                    )
                    Timber.i("🟡 [SOCKET DEBUG] Created ChatMessageEvent, sending to Flow")
                    trySend(SocketEvent.ChatMessage(message))
                    Timber.i("🟡 [SOCKET DEBUG] Sent SocketEvent.ChatMessage to Flow")
                } else {
                    Timber.e("🟡 [SOCKET DEBUG] Failed to parse chatMessage data as JSONObject")
                }
            }
            
            socket?.on("error") { args ->
                val data = args.firstOrNull() as? JSONObject
                val errorMsg = data?.optString("message") ?: "Unknown error"
                Timber.e("Socket error: $errorMsg")
                trySend(SocketEvent.Error(errorMsg))
            }
            
            socket?.on("participantLeft") { args ->
                val data = args.firstOrNull() as? JSONObject
                if (data != null) {
                    val userId = data.optString("userId")
                    val message = data.optString("message", "Participant left")
                    val wasHost = data.optBoolean("wasHost", false)
                    Timber.i("Participant left: $userId (wasHost: $wasHost, message: $message)")
                    trySend(SocketEvent.ParticipantLeft(userId, message, wasHost))
                }
            }
            
            socket?.on("hostDisconnected") { args ->
                val data = args.firstOrNull() as? JSONObject
                if (data != null) {
                    val message = data.optString("message", "Host disconnected")
                    val gracePeriodMs = data.optLong("gracePeriodMs", 300000) // 5 minutes default
                    Timber.w("Host disconnected: $message (grace period: ${gracePeriodMs}ms)")
                    trySend(SocketEvent.HostDisconnected(message, gracePeriodMs))
                }
            }
            
            socket?.on("hostReconnected") { args ->
                val data = args.firstOrNull() as? JSONObject
                if (data != null) {
                    val message = data.optString("message", "Host reconnected")
                    Timber.i("Host reconnected: $message")
                    trySend(SocketEvent.HostReconnected(message))
                }
            }
            
            socket?.on("hostTransferred") { args ->
                val data = args.firstOrNull() as? JSONObject
                if (data != null) {
                    val newHostUserId = data.optString("newHostUserId", "")
                    val reason = data.optString("reason", "")
                    Timber.i("Host transferred to: $newHostUserId (reason: $reason)")
                    trySend(SocketEvent.HostTransferred(newHostUserId, reason))
                }
            }
            
            socket?.on("hostSpeedChange") { args ->
                val data = args.firstOrNull() as? JSONObject
                if (data != null) {
                    val playbackRate = data.optDouble("playbackRate", 1.0).toFloat()
                    Timber.d("Host speed change: $playbackRate")
                    trySend(SocketEvent.HostSpeedChange(playbackRate))
                }
            }
            
            socket?.on("hostTimeSync") { args ->
                val data = args.firstOrNull() as? JSONObject
                if (data != null) {
                    val event = HostTimeSyncEvent(
                        positionSec = data.getDouble("positionSec"),
                        hostTimestampMs = data.getLong("hostTimestampMs"),
                        isPlaying = data.optBoolean("isPlaying", true)
                    )
                    Timber.v("Host time sync: position=${event.positionSec}, playing=${event.isPlaying}")
                    trySend(SocketEvent.HostTimeSync(event))
                }
            }
            
            // Connect socket
            socket?.connect()
            
        } catch (e: URISyntaxException) {
            Timber.e(e, "Invalid socket URL")
            trySend(SocketEvent.Error("Invalid server URL"))
        } catch (e: Exception) {
            Timber.e(e, "Socket connection error")
            trySend(SocketEvent.Error(e.message ?: "Connection failed"))
        }
        
        awaitClose {
            disconnect()
        }
    }
    
    /**
     * Join a room
     */
    fun joinRoom(roomId: String, role: String, fileHash: String) {
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("role", role)
            put("file_hash", fileHash)
        }
        socket?.emit("joinRoom", data)
        Timber.d("Emitted joinRoom: $roomId as $role")
    }
    
    /**
     * Host sends play command
     */
    fun hostPlay(roomId: String, positionSec: Double, playbackRate: Float = 1.0f) {
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("positionSec", positionSec)
            put("hostTimestampMs", System.currentTimeMillis())
            put("playbackRate", playbackRate.toDouble())
        }
        socket?.emit("hostPlay", data)
        Timber.d("Emitted hostPlay: position=$positionSec, rate=$playbackRate")
    }
    
    /**
     * Host sends playback speed change
     */
    fun hostSpeedChange(roomId: String, playbackRate: Float) {
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("playbackRate", playbackRate.toDouble())
            put("hostTimestampMs", System.currentTimeMillis())
        }
        socket?.emit("hostSpeedChange", data)
        Timber.d("Emitted hostSpeedChange: rate=$playbackRate")
    }
    
    /**
     * Host sends pause command
     */
    fun hostPause(roomId: String, positionSec: Double) {
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("positionSec", positionSec)
            put("hostTimestampMs", System.currentTimeMillis())
        }
        socket?.emit("hostPause", data)
        Timber.d("Emitted hostPause: position=$positionSec")
    }
    
    /**
     * Host sends seek command
     */
    fun hostSeek(roomId: String, positionSec: Double) {
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("positionSec", positionSec)
            put("hostTimestampMs", System.currentTimeMillis())
        }
        socket?.emit("hostSeek", data)
        Timber.d("Emitted hostSeek: position=$positionSec")
    }
    
    /**
     * Host sends periodic time sync for continuous synchronization
     */
    fun hostTimeSync(roomId: String, positionSec: Double, isPlaying: Boolean) {
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("positionSec", positionSec)
            put("hostTimestampMs", System.currentTimeMillis())
            put("isPlaying", isPlaying)
        }
        socket?.emit("hostTimeSync", data)
        Timber.v("Emitted hostTimeSync: position=$positionSec, playing=$isPlaying")
    }
    
    /**
     * Send ping for RTT measurement
     */
    fun sendPing() {
        val nonce = java.util.UUID.randomUUID().toString()
        lastPingNonce = nonce
        lastPingSentAt = System.currentTimeMillis()
        
        val data = JSONObject().apply {
            put("nonce", nonce)
            put("ts", lastPingSentAt)
        }
        socket?.emit("ping", data)
        Timber.v("Sent ping: nonce=$nonce")
    }
    
    /**
     * Send reaction
     */
    fun sendReaction(roomId: String, type: String) {
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("type", type)
        }
        socket?.emit("reaction", data)
    }
    
    /**
     * Send chat message
     */
    fun sendChatMessage(roomId: String, text: String) {
        Timber.i("🟠 [SOCKET DEBUG] sendChatMessage called: roomId=$roomId, text='$text'")
        
        if (socket == null) {
            Timber.e("🟠 [SOCKET DEBUG] Cannot send chat message: socket is null")
            return
        }
        
        val isConnected = try {
            socket?.connected() ?: false
        } catch (e: Exception) {
            Timber.e(e, "🟠 [SOCKET DEBUG] Error checking socket connection")
            false
        }
        
        Timber.i("🟠 [SOCKET DEBUG] Socket is connected: $isConnected")
        
        val data = JSONObject().apply {
            put("roomId", roomId)
            put("text", text)
        }
        
        Timber.i("🟠 [SOCKET DEBUG] Created JSONObject: $data")
        
        try {
            Timber.i("🟠 [SOCKET DEBUG] Emitting 'chatMessage' event to server...")
            socket?.emit("chatMessage", data)
            Timber.i("🟠 [SOCKET DEBUG] Successfully emitted 'chatMessage' event to server")
        } catch (e: Exception) {
            Timber.e(e, "🟠 [SOCKET DEBUG] Error emitting chat message")
        }
    }
    
    /**
     * Leave room
     */
    fun leaveRoom(roomId: String) {
        val data = JSONObject().apply {
            put("roomId", roomId)
        }
        socket?.emit("leaveRoom", data)
        Timber.d("Emitted leaveRoom: $roomId")
    }
    
    /**
     * Disconnect socket
     */
    fun disconnect() {
        socket?.disconnect()
        socket?.off()
        socket = null
        Timber.i("Socket disconnected and cleaned up")
    }
    
    /**
     * Get current RTT
     */
    fun getCurrentRtt(): Long = currentRtt
}

// Socket events
sealed class SocketEvent {
    object Connected : SocketEvent()
    object Disconnected : SocketEvent()
    data class Joined(val data: String) : SocketEvent()
    data class HostPlay(val event: HostPlayEvent) : SocketEvent()
    data class HostPause(val event: HostPauseEvent) : SocketEvent()
    data class HostSeek(val event: HostSeekEvent) : SocketEvent()
    data class Pong(val rtt: Long) : SocketEvent()
    data class Reaction(val event: ReactionEvent) : SocketEvent()
    data class ChatMessage(val event: ChatMessageEvent) : SocketEvent()
    data class ParticipantLeft(val userId: String, val message: String = "Participant left", val wasHost: Boolean = false) : SocketEvent()
    data class HostDisconnected(val message: String, val gracePeriodMs: Long) : SocketEvent()
    data class HostReconnected(val message: String) : SocketEvent()
    data class HostTransferred(val newHostUserId: String, val reason: String) : SocketEvent()
    data class HostSpeedChange(val playbackRate: Float) : SocketEvent()
    data class HostTimeSync(val event: HostTimeSyncEvent) : SocketEvent()
    data class Error(val message: String) : SocketEvent()
}

// Event data classes
data class HostPlayEvent(
    val positionSec: Double,
    val hostTimestampMs: Long,
    val playbackRate: Float
)

data class HostPauseEvent(
    val positionSec: Double,
    val hostTimestampMs: Long
)

data class HostSeekEvent(
    val positionSec: Double,
    val hostTimestampMs: Long
)

data class ReactionEvent(
    val userId: String,
    val type: String,
    val ts: Long
)

data class ChatMessageEvent(
    val userId: String,
    val text: String,
    val ts: Long
)

data class HostTimeSyncEvent(
    val positionSec: Double,
    val hostTimestampMs: Long,
    val isPlaying: Boolean
)

