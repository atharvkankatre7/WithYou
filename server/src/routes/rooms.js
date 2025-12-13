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

module.exports = router;
