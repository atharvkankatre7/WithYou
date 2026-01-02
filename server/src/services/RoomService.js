/**
 * Room service - Business logic for room management
 */

const { query, getClient, pool } = require('../db/connection');
const bcrypt = require('bcrypt');
const logger = require('../utils/logger');

// In-memory storage for when database is unavailable
const inMemoryRooms = new Map();
const inMemoryUsers = new Set();

class RoomService {
  /**
   * Generate random room ID
   */
  static generateRoomId() {
    const length = parseInt(process.env.ROOM_ID_LENGTH) || 6;
    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; // Remove ambiguous chars
    let result = '';
    for (let i = 0; i < length; i++) {
      result += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return result;
  }

  /**
   * Ensure user exists in database
   */
  static async ensureUser(user) {
    try {
      // Check if database is available
      if (!pool() || !pool()?._connected) {
        // Use in-memory storage
        inMemoryUsers.add(user.uid);
        logger.info('User ensured in memory', { userId: user.uid });
        return { id: user.uid };
      }

      const result = await query(
        `INSERT INTO users (id, email, phone, display_name, last_login)
         VALUES ($1, $2, $3, $4, NOW())
         ON CONFLICT (id) DO UPDATE 
         SET last_login = NOW()
         RETURNING id`,
        [user.uid, user.email, user.phone, user.displayName || user.email || user.phone]
      );
      return result.rows[0];
    } catch (error) {
      // Fallback to in-memory
      logger.warn('Database unavailable, using in-memory user storage', { userId: user.uid });
      inMemoryUsers.add(user.uid);
      return { id: user.uid };
    }
  }

  /**
   * Create a new room
   */
  static async createRoom(params) {
    const {
      host_user_id,
      file_hash,
      duration_ms,
      file_size,
      codec,
      expires_in_days,
      passcode
    } = params;

    // Try database first, fallback to in-memory
    const useDatabase = pool() && pool()?._connected;

    try {
      // Generate unique room ID
      let roomId;
      let attempts = 0;
      while (attempts < 10) {
        roomId = this.generateRoomId();

        if (useDatabase) {
          const client = await getClient();
          try {
            const existing = await client.query('SELECT id FROM rooms WHERE id = $1', [roomId]);
            client.release();
            if (existing.rows.length === 0) break;
          } catch (err) {
            client.release();
            throw err;
          }
        } else {
          // Check in-memory
          if (!inMemoryRooms.has(roomId)) break;
        }
        attempts++;
      }

      if (attempts >= 10) {
        throw new Error('Failed to generate unique room ID');
      }

      // Hash passcode if provided
      let hashedPasscode = null;
      if (passcode) {
        hashedPasscode = await bcrypt.hash(passcode, 10);
      }

      // Calculate expiry
      const expiresAt = new Date();
      expiresAt.setDate(expiresAt.getDate() + expires_in_days);

      const roomData = {
        id: roomId,
        host_user_id,
        host_file_hash: file_hash,
        host_file_duration_ms: duration_ms,
        host_file_size: file_size,
        host_file_codec: codec,
        expires_at: expiresAt,
        passcode: hashedPasscode,
        is_active: true,
        created_at: new Date()
      };

      if (useDatabase) {
        // Insert into database
        const client = await getClient();
        try {
          await client.query('BEGIN');
          const roomResult = await client.query(
            `INSERT INTO rooms 
             (id, host_user_id, host_file_hash, host_file_duration_ms, host_file_size, 
              host_file_codec, expires_at, passcode, is_active)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, TRUE)
             RETURNING *`,
            [roomId, host_user_id, file_hash, duration_ms, file_size, JSON.stringify(codec), expiresAt, hashedPasscode]
          );
          await client.query('COMMIT');
          logger.info('Room created in database', { roomId, host_user_id });
          return roomResult.rows[0];
        } catch (error) {
          await client.query('ROLLBACK');
          throw error;
        } finally {
          client.release();
        }
      } else {
        // Store in memory
        inMemoryRooms.set(roomId, roomData);
        logger.info('Room created in memory', { roomId, host_user_id });
        return roomData;
      }

    } catch (error) {
      logger.error('Error creating room', { error: error.message, host_user_id });
      throw error;
    }
  }

  /**
   * Get room by ID
   */
  static async getRoom(roomId) {
    try {
      // Try database first
      if (pool() && pool()?._connected) {
        const result = await query(
          'SELECT * FROM rooms WHERE id = $1',
          [roomId]
        );
        return result.rows[0];
      }

      // Fallback to in-memory
      return inMemoryRooms.get(roomId);
    } catch (error) {
      logger.warn('Database error, checking in-memory storage', { error: error.message, roomId });
      return inMemoryRooms.get(roomId);
    }
  }

  /**
   * Close room
   * Returns true if successful, false otherwise (doesn't throw)
   */
  static async closeRoom(roomId) {
    try {
      await query(
        `UPDATE rooms 
         SET is_active = FALSE, closed_at = NOW()
         WHERE id = $1`,
        [roomId]
      );

      // Mark all participants as disconnected
      await query(
        `UPDATE participants 
         SET is_connected = FALSE, left_at = NOW()
         WHERE room_id = $1 AND is_connected = TRUE`,
        [roomId]
      );

      logger.info('Room closed', { roomId });
      return true;
    } catch (error) {
      logger.error('Error closing room', {
        error: error.message,
        code: error.code,
        roomId
      });
      // Don't throw - return false to indicate failure
      // This prevents server crashes on database errors
      return false;
    }
  }

  /**
   * Add participant to room
   */
  static async addParticipant(roomId, userId, role, socketId) {
    try {
      if (pool() && pool()?._connected) {
        await query(
          `INSERT INTO participants (room_id, user_id, role, socket_id, is_connected)
           VALUES ($1, $2, $3, $4, TRUE)
           ON CONFLICT (room_id, user_id) DO UPDATE
           SET is_connected = TRUE, socket_id = $4, joined_at = NOW(), left_at = NULL`,
          [roomId, userId, role, socketId]
        );
      } else {
        logger.debug('Skipping addParticipant - no database', { roomId, userId, role });
      }
    } catch (error) {
      logger.warn('Error adding participant to database, continuing anyway', { error: error.message, roomId, userId });
      // Don't throw - socket handler manages participants in memory
    }
  }

  /**
   * Remove participant from room
   */
  static async removeParticipant(roomId, socketId) {
    try {
      if (pool() && pool()?._connected) {
        await query(
          `UPDATE participants 
           SET is_connected = FALSE, left_at = NOW()
           WHERE room_id = $1 AND socket_id = $2`,
          [roomId, socketId]
        );
      } else {
        logger.debug('Skipping removeParticipant - no database', { roomId, socketId });
      }
    } catch (error) {
      logger.warn('Error removing participant from database, continuing anyway', { error: error.message, roomId, socketId });
      // Don't throw - socket handler manages participants in memory
    }
  }

  /**
   * Update participant connection status (by User ID)
   */
  static async updateParticipantStatus(roomId, userId, isConnected) {
    try {
      if (pool() && pool()?._connected) {
        await query(
          `UPDATE participants 
           SET is_connected = $3, left_at = ${isConnected ? 'NULL' : 'NOW()'}, joined_at = ${isConnected ? 'NOW()' : 'joined_at'}
           WHERE room_id = $1 AND user_id = $2`,
          [roomId, userId, isConnected]
        );
      }
    } catch (error) {
      logger.error('Error updating participant status', { error: error.message, roomId, userId });
    }
  }

  /**
   * Get participants in room
   */
  static async getParticipants(roomId) {
    try {
      const result = await query(
        `SELECT * FROM participants 
         WHERE room_id = $1
         ORDER BY joined_at ASC`,
        [roomId]
      );
      return result.rows;
    } catch (error) {
      logger.error('Error getting participants', { error: error.message, roomId });
      throw error;
    }
  }

  /**
   * Log room event
   */
  static async logEvent(roomId, userId, eventType, payload) {
    try {
      if (pool() && pool()?._connected) {
        await query(
          `INSERT INTO room_events (room_id, user_id, event_type, payload)
           VALUES ($1, $2, $3, $4)`,
          [roomId, userId, eventType, JSON.stringify(payload)]
        );
      }
      // Silently skip logging if no database - not critical
    } catch (error) {
      // Don't throw on logging errors
      logger.debug('Skipped logging event - no database', { roomId, eventType });
    }
  }

  /**
   * Clean up expired rooms (call periodically)
   */
  static async cleanupExpiredRooms() {
    try {
      const result = await query(
        `UPDATE rooms 
         SET is_active = FALSE, closed_at = NOW()
         WHERE is_active = TRUE AND expires_at < NOW()`
      );

      if (result.rowCount > 0) {
        logger.info('Cleaned up expired rooms', { count: result.rowCount });
      }

      return result.rowCount;
    } catch (error) {
      logger.error('Error cleaning up expired rooms', { error: error.message });
      throw error;
    }
  }
}

module.exports = RoomService;

