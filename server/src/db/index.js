/**
 * Database Connection Pool
 * PostgreSQL client using pg library
 */

const { Pool } = require('pg');
const logger = require('../utils/logger');

// Create connection pool
const pool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: process.env.NODE_ENV === 'production' ? {
    rejectUnauthorized: false
  } : false,
  max: 20,
  idleTimeoutMillis: 30000,
  connectionTimeoutMillis: 2000,
});

// Test connection
pool.on('connect', () => {
  logger.info('Database connected');
});

pool.on('error', (err) => {
  logger.error('Unexpected database error', err);
});

// Test initial connection
pool.query('SELECT NOW()', (err, res) => {
  if (err) {
    logger.error('Database connection test failed', err);
  } else {
    logger.info('Database connection test successful', { serverTime: res.rows[0].now });
  }
});

module.exports = {
  query: (text, params) => pool.query(text, params),
  getClient: () => pool.connect(),
  end: () => pool.end()
};

