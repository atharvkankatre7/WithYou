/**
 * Socket.IO Event Handler
 * Manages all real-time WebSocket events for video synchronization
 */

const logger = require('../utils/logger');
const db = require('../db');
const { validateEvent } = require('../utils/validation');

// In-memory room state for quick access (sync with DB periodically)
const rooms = new Map();

// Rate limiting per socket
const rateLimits = new Map();

function checkRateLimit(socketId, eventType, limit = 10, windowMs = 1000) {
  const key = `${socketId}:${eventType}`;
  const now = Date.now();
  
  if (!rateLimits.has(key)) {
    rateLimits.set(key, { count: 1, resetAt: now + windowMs });
    return true;
  }
  
  const limiter = rateLimits.get(key);
  
  if (now > limiter.resetAt) {
    limiter.count = 1;
    limiter.resetAt = now + windowMs;
    return true;
  }
  
  if (limiter.count >= limit) {
    return false;
  }
  
  limiter.count++;
  return true;
}

function socketHandler(io) {
  io.on('connection', (socket) => {
    logger.info('Client connected', {
      socketId: socket.id,
      uid: socket.user.uid
    });

    // Join Room
    socket.on('joinRoom', async (data) => {
      try {
        const { error, value } = validateEvent.joinRoom(data);
        if (error) {
          socket.emit('error', { code: 'INVALID_DATA', message: error.details[0].message });
          return;
        }

        const { roomId, role, file_hash } = value;

        // Check if room exists
        const roomQuery = await db.query(
          'SELECT * FROM rooms WHERE id = $1 AND (expires_at IS NULL OR expires_at > NOW())',
          [roomId]
        );

        if (roomQuery.rows.length === 0) {
          socket.emit('error', { code: 'ROOM_NOT_FOUND', message: 'Room not found or expired' });
          return;
        }

        const room = roomQuery.rows[0];

        // Validate file hash match (for followers)
        if (role === 'follower' && room.host_file_hash !== file_hash) {
          socket.emit('error', {
            code: 'FILE_MISMATCH',
            message: 'File hash does not match host file',
            expected: room.host_file_hash,
            received: file_hash
          });
          return;
        }

        // Join socket room
        socket.join(roomId);
        socket.currentRoom = roomId;
        socket.currentRole = role;

        // Update room state
        if (!rooms.has(roomId)) {
          rooms.set(roomId, {
            hostSocketId: null,
            participants: new Map(),
            currentState: {
              isPlaying: false,
              positionSec: 0,
              lastUpdateMs: Date.now()
            }
          });
        }

        const roomState = rooms.get(roomId);
        
        if (role === 'host') {
          roomState.hostSocketId = socket.id;
        }

        roomState.participants.set(socket.id, {
          userId: socket.user.uid,
          role,
          joinedAt: Date.now()
        });

        // Add participant to DB
        await db.query(
          'INSERT INTO participants (room_id, user_id, role) VALUES ($1, $2, $3)',
          [roomId, socket.user.uid, role]
        );

        // Get all participants
        const participants = Array.from(roomState.participants.values());

        // Emit joined event to all in room
        io.in(roomId).emit('joined', {
          roomId,
          participants: participants.map(p => ({ userId: p.userId, role: p.role })),
          currentState: roomState.currentState
        });

        logger.info('User joined room', {
          socketId: socket.id,
          roomId,
          role,
          participantCount: participants.length
        });

      } catch (error) {
        logger.error('Error in joinRoom', error);
        socket.emit('error', { code: 'INTERNAL_ERROR', message: 'Failed to join room' });
      }
    });

    // Leave Room
    socket.on('leaveRoom', async (data) => {
      try {
        const { roomId } = data;
        handleLeaveRoom(socket, roomId);
      } catch (error) {
        logger.error('Error in leaveRoom', error);
      }
    });

    // Host Play Event
    socket.on('hostPlay', async (data) => {
      try {
        if (!checkRateLimit(socket.id, 'hostPlay', 10, 1000)) {
          socket.emit('error', { code: 'RATE_LIMIT', message: 'Too many requests' });
          return;
        }

        const { error, value } = validateEvent.hostPlay(data);
        if (error) {
          socket.emit('error', { code: 'INVALID_DATA', message: error.details[0].message });
          return;
        }

        const { roomId, positionSec, hostTimestampMs, playbackRate } = value;

        // Verify sender is host
        const roomState = rooms.get(roomId);
        if (!roomState || roomState.hostSocketId !== socket.id) {
          socket.emit('error', { code: 'UNAUTHORIZED', message: 'Only host can control playback' });
          return;
        }

        // Update room state
        roomState.currentState.isPlaying = true;
        roomState.currentState.positionSec = positionSec;
        roomState.currentState.lastUpdateMs = hostTimestampMs;

        // Relay to all followers in room
        socket.to(roomId).emit('hostPlay', {
          roomId,
          positionSec,
          hostTimestampMs,
          playbackRate: playbackRate || 1.0
        });

        logger.debug('Host play event', { roomId, positionSec });

      } catch (error) {
        logger.error('Error in hostPlay', error);
      }
    });

    // Host Pause Event
    socket.on('hostPause', async (data) => {
      try {
        if (!checkRateLimit(socket.id, 'hostPause', 10, 1000)) {
          socket.emit('error', { code: 'RATE_LIMIT', message: 'Too many requests' });
          return;
        }

        const { error, value } = validateEvent.hostPause(data);
        if (error) {
          socket.emit('error', { code: 'INVALID_DATA', message: error.details[0].message });
          return;
        }

        const { roomId, positionSec, hostTimestampMs } = value;

        const roomState = rooms.get(roomId);
        if (!roomState || roomState.hostSocketId !== socket.id) {
          socket.emit('error', { code: 'UNAUTHORIZED', message: 'Only host can control playback' });
          return;
        }

        roomState.currentState.isPlaying = false;
        roomState.currentState.positionSec = positionSec;
        roomState.currentState.lastUpdateMs = hostTimestampMs;

        socket.to(roomId).emit('hostPause', {
          roomId,
          positionSec,
          hostTimestampMs
        });

        logger.debug('Host pause event', { roomId, positionSec });

      } catch (error) {
        logger.error('Error in hostPause', error);
      }
    });

    // Host Seek Event
    socket.on('hostSeek', async (data) => {
      try {
        if (!checkRateLimit(socket.id, 'hostSeek', 20, 1000)) {
          socket.emit('error', { code: 'RATE_LIMIT', message: 'Too many seek requests' });
          return;
        }

        const { error, value } = validateEvent.hostSeek(data);
        if (error) {
          socket.emit('error', { code: 'INVALID_DATA', message: error.details[0].message });
          return;
        }

        const { roomId, positionSec, hostTimestampMs } = value;

        const roomState = rooms.get(roomId);
        if (!roomState || roomState.hostSocketId !== socket.id) {
          socket.emit('error', { code: 'UNAUTHORIZED', message: 'Only host can control playback' });
          return;
        }

        roomState.currentState.positionSec = positionSec;
        roomState.currentState.lastUpdateMs = hostTimestampMs;

        socket.to(roomId).emit('hostSeek', {
          roomId,
          positionSec,
          hostTimestampMs
        });

        logger.debug('Host seek event', { roomId, positionSec });

      } catch (error) {
        logger.error('Error in hostSeek', error);
      }
    });

    // Host Time Sync (periodic heartbeat)
    socket.on('hostTimeSync', async (data) => {
      try {
        const { roomId, positionSec, hostTimestampMs } = data;

        const roomState = rooms.get(roomId);
        if (!roomState || roomState.hostSocketId !== socket.id) {
          return; // Silent fail for heartbeat
        }

        roomState.currentState.positionSec = positionSec;
        roomState.currentState.lastUpdateMs = hostTimestampMs;

        socket.to(roomId).emit('hostTimeSync', {
          positionSec,
          hostTimestampMs
        });

      } catch (error) {
        logger.error('Error in hostTimeSync', error);
      }
    });

    // Ping (for RTT measurement)
    socket.on('ping', (data) => {
      try {
        const { nonce, ts } = data;
        socket.emit('pong', {
          nonce,
          clientTs: ts,
          serverTs: Date.now()
        });
      } catch (error) {
        logger.error('Error in ping', error);
      }
    });

    // Reaction
    socket.on('reaction', (data) => {
      try {
        if (!checkRateLimit(socket.id, 'reaction', 5, 1000)) {
          return; // Silent fail for reactions
        }

        const { roomId, type } = data;
        
        if (socket.currentRoom !== roomId) {
          return;
        }

        io.in(roomId).emit('reaction', {
          type,
          userId: socket.user.uid,
          ts: Date.now()
        });

      } catch (error) {
        logger.error('Error in reaction', error);
      }
    });

    // Chat Message
    socket.on('chatMessage', (data) => {
      try {
        if (!checkRateLimit(socket.id, 'chatMessage', 10, 1000)) {
          socket.emit('error', { code: 'RATE_LIMIT', message: 'Too many messages' });
          return;
        }

        const { roomId, text } = data;
        
        if (socket.currentRoom !== roomId || !text || text.length > 500) {
          return;
        }

        io.in(roomId).emit('chatMessage', {
          text: text.trim(),
          userId: socket.user.uid,
          ts: Date.now()
        });

      } catch (error) {
        logger.error('Error in chatMessage', error);
      }
    });

    // Request Room State (for reconnection)
    socket.on('requestRoomState', (data) => {
      try {
        const { roomId } = data;
        const roomState = rooms.get(roomId);
        
        if (!roomState) {
          socket.emit('error', { code: 'ROOM_NOT_FOUND', message: 'Room state not available' });
          return;
        }

        socket.emit('roomState', {
          roomId,
          currentState: roomState.currentState,
          participants: Array.from(roomState.participants.values()).map(p => ({
            userId: p.userId,
            role: p.role
          }))
        });

      } catch (error) {
        logger.error('Error in requestRoomState', error);
      }
    });

    // Disconnect
    socket.on('disconnect', () => {
      handleDisconnect(socket);
    });

  });

  // Helper function to handle leaving a room
  function handleLeaveRoom(socket, roomId) {
    if (!roomId) {
      roomId = socket.currentRoom;
    }

    if (!roomId) {
      return;
    }

    socket.leave(roomId);

    const roomState = rooms.get(roomId);
    if (roomState) {
      roomState.participants.delete(socket.id);

      // If host left, notify others
      if (roomState.hostSocketId === socket.id) {
        io.in(roomId).emit('hostDisconnected', {
          roomId,
          message: 'Host has left the room'
        });
        
        // Optionally close room or assign new host
        rooms.delete(roomId);
        
        logger.info('Host left room', { roomId, socketId: socket.id });
      }

      // If no participants left, clean up
      if (roomState.participants.size === 0) {
        rooms.delete(roomId);
        logger.info('Room cleaned up (no participants)', { roomId });
      }
    }

    socket.currentRoom = null;
    socket.currentRole = null;

    logger.info('User left room', { socketId: socket.id, roomId });
  }

  // Helper function to handle disconnect
  function handleDisconnect(socket) {
    logger.info('Client disconnected', {
      socketId: socket.id,
      uid: socket.user.uid
    });

    // Clean up rate limits
    for (const [key] of rateLimits) {
      if (key.startsWith(socket.id)) {
        rateLimits.delete(key);
      }
    }

    // Leave current room if any
    if (socket.currentRoom) {
      handleLeaveRoom(socket, socket.currentRoom);
    }
  }

  // Periodic cleanup of expired rate limits
  setInterval(() => {
    const now = Date.now();
    for (const [key, limiter] of rateLimits) {
      if (now > limiter.resetAt + 60000) { // Clean up after 1 minute
        rateLimits.delete(key);
      }
    }
  }, 60000);

  logger.info('Socket.IO handler initialized');
}

module.exports = { socketHandler };

