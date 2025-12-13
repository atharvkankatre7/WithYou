/**
 * Global Error Handler Middleware
 */

const logger = require('../utils/logger');

function errorHandler(err, req, res, next) {
  logger.error('Unhandled error', {
    error: err.message,
    stack: err.stack,
    path: req.path,
    method: req.method
  });

  // Don't leak error details in production
  const message = process.env.NODE_ENV === 'production' 
    ? 'Internal server error' 
    : err.message;

  res.status(err.status || 500).json({
    error: 'INTERNAL_ERROR',
    message
  });
}

module.exports = { errorHandler };

