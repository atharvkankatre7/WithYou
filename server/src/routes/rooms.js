/**
 * REST API routes for room management
 */

const express = require('express');
const router = express.Router();
const { verifyFirebaseToken } = require('../auth/firebase');
const { validateRequest } = require('../utils/validation');
const RoomService = require('../services/RoomService');
const logger = require('../utils/logger');

/**
 * Middleware to verify Firebase token on REST endpoints
 */
async function authMiddleware(req, res, next) {
  try {
    const authHeader = req.headers.authorization;
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'Unauthorized: No token provided' });
    }

    const token = authHeader.substring(7);
    const decodedToken = await verifyFirebaseToken(token);

    req.user = {
      uid: decodedToken.uid,
      email: decodedToken.email,
      phone: decodedToken.phone_number
    };

    next();
  } catch (error) {
    logger.error('Auth middleware error', { error: error.message });
    res.status(401).json({ error: 'Unauthorized: Invalid token' });
  }
}

/**
 * POST /api/rooms/create
 * Create a new room
 */
router.post('/create', authMiddleware, async (req, res) => {
  try {
    const { error, value } = validateRequest('createRoom', req.body);
    if (error) {
      return res.status(400).json({
        error: 'Validation failed',
        details: error.details[0].message
      });
    }

    const {
      file_hash,
      duration_ms,
      file_size,
      codec,
      expires_in_days,
      passcode
    } = value;

    // Ensure user exists in database (create if not)
    await RoomService.ensureUser(req.user);

    // Create room
    const room = await RoomService.createRoom({
      host_user_id: req.user.uid,
      file_hash,
      duration_ms,
      file_size,
      codec,
      expires_in_days: expires_in_days || parseInt(process.env.ROOM_EXPIRY_DAYS) || 7,
      passcode
    });

    logger.info('Room created', { roomId: room.id, hostUserId: req.user.uid });

    res.status(201).json({
      roomId: room.id,
      shareUrl: `${process.env.APP_SHARE_URL || 'https://app.contentsync.com'}/room/${room.id}`,
      expiresAt: room.expires_at
    });

  } catch (error) {
    logger.error('Error creating room', { error: error.message, userId: req.user?.uid });
    res.status(500).json({ error: 'Failed to create room' });
  }
});

/**
 * POST /api/rooms/:id/validate
 * Validate and get room metadata before joining
 */
router.post('/:id/validate', authMiddleware, async (req, res) => {
  try {
    const { id: roomId } = req.params;
    const { file_hash, passcode } = req.body;

    const room = await RoomService.getRoom(roomId);

    if (!room) {
      return res.status(404).json({ error: 'Room not found' });
    }

    if (!room.is_active || new Date(room.expires_at) < new Date()) {
      return res.status(410).json({ error: 'Room has expired' });
    }

    // Check passcode if required
    if (room.passcode) {
      if (!passcode) {
        return res.status(401).json({ error: 'Passcode required' });
      }

      const bcrypt = require('bcrypt');
      const isValid = await bcrypt.compare(passcode, room.passcode);
      if (!isValid) {
        return res.status(401).json({ error: 'Invalid passcode' });
      }
    }

    // Validate file hash if provided
    const hashMatches = file_hash === room.host_file_hash;

    res.json({
      roomId: room.id,
      hostFileMetadata: {
        hash: room.host_file_hash,
        duration_ms: room.host_file_duration_ms,
        file_size: room.host_file_size,
        codec: room.host_file_codec
      },
      hashMatches,
      expiresAt: room.expires_at,
      requiresPasscode: !!room.passcode
    });

  } catch (error) {
    logger.error('Error validating room', { error: error.message, roomId: req.params.id });
    res.status(500).json({ error: 'Failed to validate room' });
  }
});

/**
 * POST /api/rooms/:id/close
 * Close a room (host only)
 */
router.post('/:id/close', authMiddleware, async (req, res) => {
  try {
    const { id: roomId } = req.params;

    const room = await RoomService.getRoom(roomId);

    if (!room) {
      return res.status(404).json({ error: 'Room not found' });
    }

    // Verify user is host
    if (room.host_user_id !== req.user.uid) {
      return res.status(403).json({ error: 'Only the host can close the room' });
    }

    await RoomService.closeRoom(roomId);

    logger.info('Room closed by host', { roomId, hostUserId: req.user.uid });

    res.json({ message: 'Room closed successfully' });

  } catch (error) {
    logger.error('Error closing room', { error: error.message, roomId: req.params.id });
    res.status(500).json({ error: 'Failed to close room' });
  }
});

/**
 * GET /api/rooms/:id
 * Get room details
 */
router.get('/:id', authMiddleware, async (req, res) => {
  try {
    const { id: roomId } = req.params;

    const room = await RoomService.getRoom(roomId);

    if (!room) {
      return res.status(404).json({ error: 'Room not found' });
    }

    const participants = await RoomService.getParticipants(roomId);

    res.json({
      roomId: room.id,
      isActive: room.is_active,
      hostUserId: room.host_user_id,
      createdAt: room.created_at,
      expiresAt: room.expires_at,
      requiresPasscode: !!room.passcode,
      participants: participants.map(p => ({
        userId: p.user_id,
        role: p.role,
        isConnected: p.is_connected,
        joinedAt: p.joined_at
      }))
    });

  } catch (error) {
    logger.error('Error getting room', { error: error.message, roomId: req.params.id });
    res.status(500).json({ error: 'Failed to get room details' });
  }
});

const { pauseRoom, getRoomState } = require('../socket/handlers');

/**
 * POST /api/rooms/:id/leave-temporary
 * Mark user as offline and pause room (temporary leave)
 */
router.post('/:id/leave-temporary', authMiddleware, async (req, res) => {
  try {
    const { id: roomId } = req.params;

    // 1. Mark in DB as disconnected
    await RoomService.updateParticipantStatus(roomId, req.user.uid, false);

    // 2. Pause room playback via socket handler (if active in memory)
    const paused = pauseRoom(roomId);

    logger.info('User marked as temporarily left', {
      roomId,
      userId: req.user.uid,
      pausedRoom: paused
    });

    res.json({ success: true, paused });

  } catch (error) {
    logger.error('Error in leave-temporary', { error: error.message, roomId: req.params.id });
    res.status(500).json({ error: 'Failed to leave room' });
  }
});

/**
 * POST /api/rooms/:id/rejoin
 * Rejoin room and get sync state
 */
router.post('/:id/rejoin', authMiddleware, async (req, res) => {
  try {
    const { id: roomId } = req.params;

    // 1. Check if room exists and is active
    const room = await RoomService.getRoom(roomId);
    if (!room || !room.is_active || new Date(room.expires_at) < new Date()) {
      return res.status(404).json({ error: 'Room not found or expired' });
    }

    // 2. Mark in DB as connected
    await RoomService.updateParticipantStatus(roomId, req.user.uid, true);

    // 3. Get realtime state
    const rtState = getRoomState(roomId);

    let responseState = {
      roomId: room.id,
      videoId: room.host_file_hash, // Using file hash as videoId identifier
      hostFileMetadata: {
        duration_ms: room.host_file_duration_ms,
        size: room.host_file_size,
        codec: room.host_file_codec
      },
      playbackState: 'paused',
      currentPosition: 0,
      participants: []
    };

    if (rtState) {
      responseState.playbackState = rtState.isPlaying ? 'playing' : 'paused';
      responseState.currentPosition = rtState.currentPosition;

      // Convert Map participants to array
      responseState.participants = Array.from(rtState.participants.values()).map(p => ({
        userId: p.userId,
        role: p.role,
        isOnline: true // Active in memory means online
      }));
    } else {
      // Room not in memory (maybe server restart), fetch basic participant list from DB
      const dbParticipants = await RoomService.getParticipants(roomId);
      responseState.participants = dbParticipants.map(p => ({
        userId: p.user_id,
        role: p.role,
        isOnline: p.is_connected
      }));
    }

    logger.info('User rejoined room', { roomId, userId: req.user.uid });
    res.json(responseState);

  } catch (error) {
    logger.error('Error in rejoin', { error: error.message, roomId: req.params.id });
    res.status(500).json({ error: 'Failed to rejoin room' });
  }
});

module.exports = router;
