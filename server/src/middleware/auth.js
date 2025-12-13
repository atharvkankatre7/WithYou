/**
 * Authentication Middleware
 * Verifies Firebase ID tokens for REST API
 */

const admin = require('firebase-admin');
const logger = require('../utils/logger');

async function authenticateToken(req, res, next) {
  try {
    const authHeader = req.headers.authorization;

    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return res.status(401).json({
        error: 'UNAUTHORIZED',
        message: 'Missing or invalid authorization header'
      });
    }

    const token = authHeader.substring(7);

    try {
      // Verify Firebase token
      const decodedToken = await admin.auth().verifyIdToken(token);
      
      req.user = {
        uid: decodedToken.uid,
        email: decodedToken.email,
        phone: decodedToken.phone_number
      };

      next();
    } catch (authError) {
      // Development fallback
      if (process.env.NODE_ENV === 'development' && token === 'dev-token') {
        req.user = {
          uid: 'dev-user-' + Math.random().toString(36).substr(2, 9),
          email: 'dev@test.com'
        };
        logger.warn('Using development token for REST API');
        next();
      } else {
        logger.error('Token verification failed', authError);
        res.status(401).json({
          error: 'UNAUTHORIZED',
          message: 'Invalid authentication token'
        });
      }
    }
  } catch (error) {
    logger.error('Authentication error', error);
    res.status(500).json({
      error: 'INTERNAL_ERROR',
      message: 'Authentication failed'
    });
  }
}

module.exports = { authenticateToken };

