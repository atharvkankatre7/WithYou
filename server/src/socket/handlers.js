/**
 * Socket.IO event handlers
 */

const logger = require('../utils/logger');
const { validateSocketEvent } = require('../utils/validation');
const RoomService = require('../services/RoomService');

// In-memory store for active rooms (replace with Redis for scaling)
const activeRooms = new Map(); // roomId -> { hostSocketId, hostUserId, participants: Map<socketId, userData>, hostDisconnectedAt: timestamp }
const HOST_RECONNECT_GRACE_PERIOD_MS = 5 * 60 * 1000; // 5 minutes grace period for host reconnection

/**
 * Setup all Socket.IO event handlers
 * @param {Server} io - Socket.IO server instance
 */
function setupSocketHandlers(io) {
  io.on('connection', (socket) => {
    logger.info('Client connected', { 
      socketId: socket.id, 
      userId: socket.user.uid 
    });

    // Join room
    socket.on('joinRoom', async (payload) => {
      try {
        const { error, value } = validateSocketEvent('joinRoom', payload);
        if (error) {
          socket.emit('error', { code: 'INVALID_PAYLOAD', message: error.details[0].message });
          return;
        }

        const { roomId, role, file_hash } = value;

        // Verify room exists in database
        const room = await RoomService.getRoom(roomId);
        if (!room) {
          socket.emit('error', { code: 'ROOM_NOT_FOUND', message: 'Room does not exist' });
          return;
        }

        // Verify file hash matches for followers
        if (role === 'follower' && file_hash !== room.host_file_hash) {
          socket.emit('error', { 
            code: 'FILE_MISMATCH', 
            message: 'File hash does not match. Ensure you selected the same file as the host.',
            expected: room.host_file_hash,
            received: file_hash
          });
          return;
        }

        // Initialize room in memory if not exists
        if (!activeRooms.has(roomId)) {
          activeRooms.set(roomId, {
            hostSocketId: role === 'host' ? socket.id : null,
            hostUserId: room.host_user_id,
            participants: new Map(),
            currentPosition: 0,
            isPlaying: false,
            hostDisconnectedAt: null
          });
        }

        const roomData = activeRooms.get(roomId);

        // Update host socket if host joins (reconnection)
        if (role === 'host') {
          // Verify this is the actual host user
          if (roomData.hostUserId !== socket.user.uid) {
            socket.emit('error', { 
              code: 'UNAUTHORIZED', 
              message: 'Only the original host can rejoin as host' 
            });
            return;
          }
          
          // If host was disconnected, this is a reconnection
          if (roomData.hostDisconnectedAt) {
            logger.info('Host reconnected to room', { roomId, socketId: socket.id, userId: socket.user.uid });
            roomData.hostDisconnectedAt = null; // Clear disconnection timestamp
            
            // Notify participants that host reconnected
            socket.to(roomId).emit('hostReconnected', {
              hostUserId: socket.user.uid,
              message: 'Host has reconnected'
            });
          }
          roomData.hostSocketId = socket.id;
        }

        // Add participant
        roomData.participants.set(socket.id, {
          userId: socket.user.uid,
          role,
          joinedAt: Date.now()
        });

        // Join socket room
        socket.join(roomId);

        // Store room info on socket for cleanup
        socket.roomId = roomId;
        socket.role = role;

        // Add participant to database
        await RoomService.addParticipant(roomId, socket.user.uid, role, socket.id);

        // Notify all participants
        const participants = Array.from(roomData.participants.values()).map(p => ({
          userId: p.userId,
          role: p.role
        }));

        io.in(roomId).emit('joined', {
          roomId,
          participants,
          hostFileMetadata: {
            duration_ms: room.host_file_duration_ms,
            size: room.host_file_size,
            codec: room.host_file_codec
          }
        });

        logger.info('User joined room', { 
          socketId: socket.id, 
          userId: socket.user.uid, 
          roomId, 
          role 
        });

      } catch (error) {
        logger.error('Error in joinRoom', { error: error.message, socketId: socket.id });
        socket.emit('error', { code: 'JOIN_FAILED', message: 'Failed to join room' });
      }
    });

    // Host play event
    socket.on('hostPlay', async (payload) => {
      try {
        const { error, value } = validateSocketEvent('hostPlay', payload);
        if (error) {
          socket.emit('error', { code: 'INVALID_PAYLOAD', message: error.details[0].message });
          return;
        }

        const { roomId, positionSec, hostTimestampMs, playbackRate } = value;
        const roomData = activeRooms.get(roomId);

        // Verify sender is host
        if (!roomData || roomData.hostSocketId !== socket.id) {
          socket.emit('error', { code: 'UNAUTHORIZED', message: 'Only host can control playback' });
          return;
        }

        // Update room state
        roomData.currentPosition = positionSec;
        roomData.isPlaying = true;

        // Relay to all followers
        socket.to(roomId).emit('hostPlay', {
          positionSec,
          hostTimestampMs,
          playbackRate: playbackRate || 1.0
        });

        // Log event
        await RoomService.logEvent(roomId, socket.user.uid, 'play', { positionSec, hostTimestampMs });

        logger.debug('Host play event', { roomId, positionSec, socketId: socket.id });

      } catch (error) {
        logger.error('Error in hostPlay', { error: error.message, socketId: socket.id });
      }
    });

    // Host pause event
    socket.on('hostPause', async (payload) => {
      try {
        const { error, value } = validateSocketEvent('hostPause', payload);
        if (error) return;

        const { roomId, positionSec, hostTimestampMs } = value;
        const roomData = activeRooms.get(roomId);

        if (!roomData || roomData.hostSocketId !== socket.id) {
          socket.emit('error', { code: 'UNAUTHORIZED', message: 'Only host can control playback' });
          return;
        }

        roomData.currentPosition = positionSec;
        roomData.isPlaying = false;

        socket.to(roomId).emit('hostPause', { positionSec, hostTimestampMs });

        await RoomService.logEvent(roomId, socket.user.uid, 'pause', { positionSec, hostTimestampMs });

        logger.debug('Host pause event', { roomId, positionSec, socketId: socket.id });

      } catch (error) {
        logger.error('Error in hostPause', { error: error.message, socketId: socket.id });
      }
    });

    // Host seek event
    socket.on('hostSeek', async (payload) => {
      try {
        const { error, value } = validateSocketEvent('hostSeek', payload);
        if (error) return;

        const { roomId, positionSec, hostTimestampMs } = value;
        const roomData = activeRooms.get(roomId);

        if (!roomData || roomData.hostSocketId !== socket.id) {
          socket.emit('error', { code: 'UNAUTHORIZED', message: 'Only host can control playback' });
          return;
        }

        roomData.currentPosition = positionSec;

        socket.to(roomId).emit('hostSeek', { positionSec, hostTimestampMs });

        await RoomService.logEvent(roomId, socket.user.uid, 'seek', { positionSec, hostTimestampMs });

        logger.debug('Host seek event', { roomId, positionSec, socketId: socket.id });

      } catch (error) {
        logger.error('Error in hostSeek', { error: error.message, socketId: socket.id });
      }
    });

    // Host time sync event (periodic position updates for continuous sync)
    socket.on('hostTimeSync', async (payload) => {
      try {
        const { error, value } = validateSocketEvent('hostTimeSync', payload);
        if (error) return;

        const { roomId, positionSec, hostTimestampMs, isPlaying } = value;
        const roomData = activeRooms.get(roomId);

        if (!roomData || roomData.hostSocketId !== socket.id) {
          // Silently ignore - this is a frequent event, don't spam errors
          return;
        }

        // Update room state
        roomData.currentPosition = positionSec;
        roomData.isPlaying = isPlaying;

        // Relay to all followers in room (rate-limited to avoid flooding)
        socket.to(roomId).emit('hostTimeSync', { 
          positionSec, 
          hostTimestampMs,
          isPlaying 
        });

        // Don't log every sync event to avoid log spam
        // logger.debug('Host time sync', { roomId, positionSec, socketId: socket.id });

      } catch (error) {
        logger.error('Error in hostTimeSync', { error: error.message, socketId: socket.id });
      }
    });

    // Ping-pong for RTT measurement
    socket.on('ping', (payload) => {
      const { nonce, ts } = payload;
      socket.emit('pong', {
        nonce,
        clientTs: ts,
        serverTs: Date.now()
      });
    });

    // Reaction
    socket.on('reaction', async (payload) => {
      try {
        const { roomId, type } = payload;
        if (!socket.roomId || socket.roomId !== roomId) return;

        socket.to(roomId).emit('reaction', {
          userId: socket.user.uid,
          type,
          ts: Date.now()
        });

        await RoomService.logEvent(roomId, socket.user.uid, 'reaction', { type });

      } catch (error) {
        logger.error('Error in reaction', { error: error.message, socketId: socket.id });
      }
    });

    // Host playback speed change
    socket.on('hostSpeedChange', async (payload) => {
      try {
        const { roomId, playbackRate } = payload;
        const roomData = activeRooms.get(roomId);

        // Verify sender is host
        if (!roomData || roomData.hostSocketId !== socket.id) {
          socket.emit('error', { code: 'UNAUTHORIZED', message: 'Only host can change playback speed' });
          return;
        }

        // Relay to all followers
        socket.to(roomId).emit('hostSpeedChange', {
          playbackRate: playbackRate || 1.0
        });

        logger.debug('Host speed change', { roomId, playbackRate, socketId: socket.id });

      } catch (error) {
        logger.error('Error in hostSpeedChange', { error: error.message, socketId: socket.id });
      }
    });

    // Chat message
    socket.on('chatMessage', async (payload) => {
      try {
        const { roomId, text } = payload;
        if (!socket.roomId || socket.roomId !== roomId) return;
        if (!text || text.length > 500) return;

        const message = {
          userId: socket.user.uid,
          text,
          ts: Date.now()
        };

        io.in(roomId).emit('chatMessage', message);

        await RoomService.logEvent(roomId, socket.user.uid, 'chat', { text });

      } catch (error) {
        logger.error('Error in chatMessage', { error: error.message, socketId: socket.id });
      }
    });

    // Leave room
    socket.on('leaveRoom', async ({ roomId }) => {
      await handleLeaveRoom(socket, roomId);
    });

    // Disconnect
    socket.on('disconnect', async () => {
      logger.info('Client disconnected', { socketId: socket.id, userId: socket.user.uid });

      if (socket.roomId) {
        await handleLeaveRoom(socket, socket.roomId);
      }
    });
  });
}

/**
 * Handle user leaving room
 */
async function handleLeaveRoom(socket, roomId) {
  try {
    const roomData = activeRooms.get(roomId);
    if (!roomData) return;

    // Remove participant
    roomData.participants.delete(socket.id);

    // Update database
    await RoomService.removeParticipant(roomId, socket.id);

    // If host left, mark as disconnected but don't close room immediately
    // Allow grace period for reconnection
    if (roomData.hostSocketId === socket.id) {
      // Mark host as disconnected with timestamp
      roomData.hostSocketId = null;
      roomData.hostDisconnectedAt = Date.now();
      
      logger.info('Host disconnected from room (grace period started)', { 
        roomId, 
        participants: roomData.participants.size,
        gracePeriodMs: HOST_RECONNECT_GRACE_PERIOD_MS 
      });
      
      // Notify participants that host disconnected (but room is still active)
      socket.to(roomId).emit('hostDisconnected', {
        message: 'Host disconnected. Room will remain active for reconnection.',
        gracePeriodMs: HOST_RECONNECT_GRACE_PERIOD_MS
      });
      
      // Schedule room cleanup after grace period if host doesn't reconnect
      setTimeout(async () => {
        try {
          const room = activeRooms.get(roomId);
          if (room && !room.hostSocketId && room.hostDisconnectedAt) {
            // Host didn't reconnect within grace period
            if (room.participants.size === 0) {
              // No participants left, close room
              activeRooms.delete(roomId);
              const closed = await RoomService.closeRoom(roomId);
              if (closed) {
                logger.info('Room closed (host did not reconnect, no participants)', { roomId });
              } else {
                logger.warn('Room removed from memory but database close failed', { roomId });
              }
            } else {
            // Transfer host to first participant
            const newHostSocketId = Array.from(room.participants.keys())[0];
            const newHost = room.participants.get(newHostSocketId);
            room.hostSocketId = newHostSocketId;
            newHost.role = 'host';
            room.hostDisconnectedAt = null;

            io.in(roomId).emit('hostTransferred', {
              newHostUserId: newHost.userId,
              reason: 'Host did not reconnect within grace period'
            });

            logger.info('Host transferred (after grace period)', { roomId, newHostUserId: newHost.userId });
          }
        }
        } catch (error) {
          logger.error('Error in grace period cleanup', { 
            error: error.message,
            roomId 
          });
          // Don't crash - just log the error
        }
      }, HOST_RECONNECT_GRACE_PERIOD_MS);
    }

    socket.leave(roomId);

    // Notify remaining participants with enhanced message
    if (activeRooms.has(roomId)) {
      const participants = Array.from(roomData.participants.values()).map(p => ({
        userId: p.userId,
        role: p.role
      }));

      // Get participant info for better notification
      const leavingParticipant = roomData.participants.get(socket.id);
      const wasHost = roomData.hostSocketId === socket.id;
      
      socket.to(roomId).emit('participantLeft', {
        userId: socket.user.uid,
        participants,
        message: wasHost 
          ? 'Host has left the room. Playback will pause.' 
          : 'A participant has left the room.',
        wasHost: wasHost
      });
      
      // If a participant (not host) left, pause playback for others
      if (!wasHost && roomData.isPlaying) {
        roomData.isPlaying = false;
        socket.to(roomId).emit('hostPause', {
          positionSec: roomData.currentPosition,
          hostTimestampMs: Date.now(),
          reason: 'Participant left'
        });
      }
    }

    logger.info('User left room', { socketId: socket.id, userId: socket.user.uid, roomId });

  } catch (error) {
    logger.error('Error in handleLeaveRoom', { error: error.message, socketId: socket.id, roomId });
  }
}

module.exports = {
  setupSocketHandlers
};

