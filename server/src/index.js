/**
 * ContentSync Signaling Server
 * WebSocket-based signaling for video synchronization
 */

require('dotenv').config();
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const cors = require('cors');
const helmet = require('helmet');
const rateLimit = require('express-rate-limit');

const { initializeFirebase, verifyFirebaseToken } = require('./auth/firebase');
const { connectDatabase } = require('./db/connection');
const roomRoutes = require('./routes/rooms');
const { setupSocketHandlers } = require('./socket/handlers');
const logger = require('./utils/logger');

// Initialize Express app
const app = express();
const server = http.createServer(app);

// Initialize Socket.IO with CORS
const io = new Server(server, {
  cors: {
    origin: process.env.CORS_ORIGIN?.split(',') || ['http://localhost:3000'],
    methods: ['GET', 'POST'],
    credentials: true
  },
  pingInterval: parseInt(process.env.SOCKET_PING_INTERVAL) || 25000,
  pingTimeout: parseInt(process.env.SOCKET_PING_TIMEOUT) || 60000,
  transports: ['websocket', 'polling']
});

// Middleware
app.use(helmet());
app.use(cors({
  origin: process.env.CORS_ORIGIN?.split(',') || ['http://localhost:3000'],
  credentials: true
}));
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Rate limiting
const apiLimiter = rateLimit({
  windowMs: parseInt(process.env.RATE_LIMIT_WINDOW_MS) || 60000,
  max: parseInt(process.env.RATE_LIMIT_MAX_REQUESTS) || 100,
  message: 'Too many requests from this IP, please try again later.'
});
app.use('/api/', apiLimiter);

// Health check endpoint
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    uptime: process.uptime()
  });
});

// API routes
app.use('/api/rooms', roomRoutes);

// Socket.IO authentication middleware
io.use(async (socket, next) => {
  try {
    const token = socket.handshake.auth?.token;
    
    if (!token) {
      logger.warn('Socket connection attempt without token', { socketId: socket.id });
      return next(new Error('Authentication token required'));
    }

    const decodedToken = await verifyFirebaseToken(token);
    socket.user = {
      uid: decodedToken.uid,
      email: decodedToken.email,
      phone: decodedToken.phone_number
    };

    logger.info('Socket authenticated', { 
      socketId: socket.id, 
      userId: socket.user.uid 
    });
    
    next();
  } catch (error) {
    logger.error('Socket authentication failed', { 
      error: error.message, 
      socketId: socket.id 
    });
    next(new Error('Authentication failed'));
  }
});

// Setup Socket.IO event handlers
setupSocketHandlers(io);

// Error handling middleware
app.use((err, req, res, next) => {
  logger.error('Express error', { error: err.message, stack: err.stack });
  res.status(err.status || 500).json({
    error: {
      message: err.message || 'Internal server error',
      ...(process.env.NODE_ENV === 'development' && { stack: err.stack })
    }
  });
});

// 404 handler
app.use((req, res) => {
  res.status(404).json({ error: 'Not found' });
});

// Initialize and start server
async function startServer() {
  try {
    // Initialize Firebase
    await initializeFirebase();
    logger.info('Firebase Admin SDK initialized');

    // Connect to database (optional for now)
    try {
      await connectDatabase();
      logger.info('Database connected');
    } catch (dbError) {
      logger.warn('Database connection failed - running without database', { 
        error: dbError.message 
      });
      logger.warn('Room data will not be persisted');
    }

    // Start server
    const PORT = process.env.PORT || 3000;
    const HOST = '0.0.0.0'; // Listen on all interfaces for physical device access
    server.listen(PORT, HOST, () => {
      logger.info(`ContentSync server listening on ${HOST}:${PORT}`, {
        environment: process.env.NODE_ENV,
        port: PORT,
        host: HOST
      });
      logger.info('Server is accessible from your local network');
    });
  } catch (error) {
    logger.error('Failed to start server', { error: error.message });
    process.exit(1);
  }
}

// Graceful shutdown
process.on('SIGTERM', () => {
  logger.info('SIGTERM received, shutting down gracefully');
  server.close(() => {
    logger.info('Server closed');
    process.exit(0);
  });
});

process.on('SIGINT', () => {
  logger.info('SIGINT received, shutting down gracefully');
  server.close(() => {
    logger.info('Server closed');
    process.exit(0);
  });
});

// Start the server
startServer();

module.exports = { app, server, io };
