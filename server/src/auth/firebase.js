/**
 * Firebase Admin SDK initialization and token verification
 */

const admin = require('firebase-admin');
const logger = require('../utils/logger');

let firebaseApp = null;

/**
 * Initialize Firebase Admin SDK
 */
async function initializeFirebase() {
  try {
    if (firebaseApp) {
      return firebaseApp;
    }

    // Check for service account file path
    if (process.env.FIREBASE_SERVICE_ACCOUNT_PATH) {
      const serviceAccount = require(process.env.FIREBASE_SERVICE_ACCOUNT_PATH);
      firebaseApp = admin.initializeApp({
        credential: admin.credential.cert(serviceAccount)
      });
    } 
    // Or use individual environment variables
    else if (process.env.FIREBASE_PROJECT_ID && 
             process.env.FIREBASE_CLIENT_EMAIL && 
             process.env.FIREBASE_PRIVATE_KEY) {
      firebaseApp = admin.initializeApp({
        credential: admin.credential.cert({
          projectId: process.env.FIREBASE_PROJECT_ID,
          clientEmail: process.env.FIREBASE_CLIENT_EMAIL,
          privateKey: process.env.FIREBASE_PRIVATE_KEY.replace(/\\n/g, '\n')
        })
      });
    } else {
      throw new Error('Firebase configuration not found in environment variables');
    }

    logger.info('Firebase Admin SDK initialized successfully');
    return firebaseApp;
  } catch (error) {
    logger.error('Failed to initialize Firebase Admin SDK', { error: error.message });
    throw error;
  }
}

/**
 * Verify Firebase ID token
 * @param {string} idToken - Firebase ID token from client
 * @returns {Promise<admin.auth.DecodedIdToken>} Decoded token
 */
async function verifyFirebaseToken(idToken) {
  try {
    if (!firebaseApp) {
      await initializeFirebase();
    }

    const decodedToken = await admin.auth().verifyIdToken(idToken);
    return decodedToken;
  } catch (error) {
    logger.error('Token verification failed', { error: error.message });
    throw new Error('Invalid authentication token');
  }
}

/**
 * Get user by UID
 * @param {string} uid - User ID
 * @returns {Promise<admin.auth.UserRecord>} User record
 */
async function getUserByUid(uid) {
  try {
    if (!firebaseApp) {
      await initializeFirebase();
    }

    const userRecord = await admin.auth().getUser(uid);
    return userRecord;
  } catch (error) {
    logger.error('Failed to get user by UID', { uid, error: error.message });
    throw error;
  }
}

module.exports = {
  initializeFirebase,
  verifyFirebaseToken,
  getUserByUid
};

