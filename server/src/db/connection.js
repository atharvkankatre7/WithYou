/**
 * PostgreSQL database connection
 */

const { Pool } = require('pg');
const logger = require('../utils/logger');

let pool = null;

/**
 * Create and return database connection pool
 */
function createPool() {
  if (pool) {
    return pool;
  }

  // Determine SSL configuration based on environment
  // Set DB_SSL=true in production if your database supports SSL
  const useSSL = process.env.DB_SSL === 'true';
  const sslConfig = useSSL 
    ? { rejectUnauthorized: false }
    : false;

  const config = process.env.DATABASE_URL 
    ? { 
        connectionString: process.env.DATABASE_URL, 
        ssl: sslConfig
      }
    : {
        host: process.env.DB_HOST,
        port: parseInt(process.env.DB_PORT) || 5432,
        database: process.env.DB_NAME,
        user: process.env.DB_USER,
        password: process.env.DB_PASSWORD,
        ssl: sslConfig,
        max: 20,
        idleTimeoutMillis: 30000,
        connectionTimeoutMillis: 2000,
        // Remove idle connections after 10 seconds to prevent stale connections
        maxUses: 7500,
      };

  pool = new Pool(config);

  pool.on('error', (err) => {
    logger.error('Unexpected database pool error', { 
      error: err.message,
      code: err.code 
    });
    // If it's an authentication error, reset the pool to force reconnection
    if (err.code === '28P01' || err.message.includes('password authentication failed')) {
      logger.warn('Authentication error detected, resetting pool');
      pool = null;
    }
  });

  pool.on('connect', () => {
    logger.debug('New database connection established');
  });

  return pool;
}

/**
 * Connect to database and verify connection
 */
async function connectDatabase() {
  try {
    const db = createPool();
    const client = await db.connect();
    
    // Test connection
    const result = await client.query('SELECT NOW()');
    logger.info('Database connection successful', { serverTime: result.rows[0].now });
    
    client.release();
    return db;
  } catch (error) {
    logger.error('Database connection failed', { error: error.message });
    throw error;
  }
}

/**
 * Execute a query with retry logic for connection errors
 */
async function query(text, params, retries = 1) {
  const start = Date.now();
  
  for (let attempt = 0; attempt <= retries; attempt++) {
    try {
      // Recreate pool if it was reset due to auth error
      const db = createPool();
      const result = await db.query(text, params);
      const duration = Date.now() - start;
      
      logger.debug('Query executed', { 
        duration, 
        rows: result.rowCount,
        query: text.substring(0, 100) 
      });
      
      return result;
    } catch (error) {
      // Check if it's an authentication error
      if ((error.code === '28P01' || error.message.includes('password authentication failed')) && attempt < retries) {
        logger.warn('Authentication error, resetting pool and retrying', { 
          attempt: attempt + 1,
          error: error.message 
        });
        // Reset pool to force reconnection with fresh credentials
        if (pool) {
          try {
            await pool.end();
          } catch (e) {
            // Ignore errors when closing
          }
          pool = null;
        }
        // Wait a bit before retrying
        await new Promise(resolve => setTimeout(resolve, 1000));
        continue;
      }
      
      logger.error('Query failed', { 
        error: error.message,
        code: error.code,
        query: text.substring(0, 100) 
      });
      throw error;
    }
  }
}

/**
 * Get a client from the pool for transactions
 */
async function getClient() {
  const db = createPool();
  return await db.connect();
}

/**
 * Close database connection pool
 */
async function closePool() {
  if (pool) {
    await pool.end();
    pool = null;
    logger.info('Database connection pool closed');
  }
}

module.exports = {
  connectDatabase,
  query,
  getClient,
  closePool,
  pool: () => pool
};

