/**
 * Request and event validation using Joi
 */

const Joi = require('joi');

// Validation schemas
const schemas = {
  createRoom: Joi.object({
    file_hash: Joi.string().hex().length(64).required(),
    duration_ms: Joi.number().integer().positive().required(),
    file_size: Joi.number().integer().positive().required(),
    codec: Joi.object({
      video: Joi.string().required(),
      audio: Joi.string().required(),
      resolution: Joi.string().optional()
    }).optional(),
    expires_in_days: Joi.number().integer().min(1).max(30).optional(),
    passcode: Joi.string().min(4).max(20).optional()
  }),

  joinRoom: Joi.object({
    roomId: Joi.string().min(6).max(8).required(),
    role: Joi.string().valid('host', 'follower').required(),
    file_hash: Joi.string().hex().length(64).required()
  }),

  hostPlay: Joi.object({
    roomId: Joi.string().required(),
    positionSec: Joi.number().min(0).required(),
    hostTimestampMs: Joi.number().integer().positive().required(),
    playbackRate: Joi.number().min(0.25).max(2.0).optional()
  }),

  hostPause: Joi.object({
    roomId: Joi.string().required(),
    positionSec: Joi.number().min(0).required(),
    hostTimestampMs: Joi.number().integer().positive().required()
  }),

  hostSeek: Joi.object({
    roomId: Joi.string().required(),
    positionSec: Joi.number().min(0).required(),
    hostTimestampMs: Joi.number().integer().positive().required()
  }),

  hostTimeSync: Joi.object({
    roomId: Joi.string().required(),
    positionSec: Joi.number().min(0).required(),
    hostTimestampMs: Joi.number().integer().positive().required(),
    isPlaying: Joi.boolean().required()
  }),

  reaction: Joi.object({
    roomId: Joi.string().required(),
    type: Joi.string().valid('heart', 'laugh', 'wow', 'sad', 'fire').required()
  }),

  chatMessage: Joi.object({
    roomId: Joi.string().required(),
    text: Joi.string().min(1).max(500).required()
  })
};

/**
 * Validate REST API request body
 */
function validateRequest(schemaName, data) {
  const schema = schemas[schemaName];
  if (!schema) {
    throw new Error(`Validation schema '${schemaName}' not found`);
  }
  return schema.validate(data, { abortEarly: false });
}

/**
 * Validate Socket.IO event payload
 */
function validateSocketEvent(eventName, data) {
  const schema = schemas[eventName];
  if (!schema) {
    throw new Error(`Validation schema '${eventName}' not found`);
  }
  return schema.validate(data, { abortEarly: false });
}

module.exports = {
  validateRequest,
  validateSocketEvent
};
