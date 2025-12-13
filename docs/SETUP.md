# ContentSync Setup Guide

Complete guide to set up and deploy ContentSync from scratch.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Firebase Setup](#firebase-setup)
3. [Database Setup (Supabase)](#database-setup)
4. [Server Deployment](#server-deployment)
5. [Android App Setup](#android-app-setup)
6. [Testing](#testing)

## Prerequisites

- Node.js 18+
- Android Studio Hedgehog or later
- Firebase account
- Supabase account (or PostgreSQL database)
- Railway/Render account (for server hosting)

## Firebase Setup

### 1. Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Add project"
3. Name it "ContentSync" (or your choice)
4. Enable Google Analytics (optional)

### 2. Enable Authentication

1. In Firebase console, go to Authentication
2. Click "Get Started"
3. Enable "Anonymous" authentication (for MVP)
4. Optional: Enable Phone and/or Email authentication

### 3. Add Android App

1. In Firebase console, click "Add app" → Android
2. Package name: `com.contentsync.app`
3. Download `google-services.json`
4. Place it in `android/app/google-services.json`

### 4. Get Service Account for Server

1. Go to Project Settings → Service Accounts
2. Click "Generate new private key"
3. Save the JSON file as `server/sa.json`
4. **Important**: Add `sa.json` to `.gitignore`

For production, use environment variables instead:

```env
FIREBASE_PROJECT_ID=your-project-id
FIREBASE_CLIENT_EMAIL=firebase-adminsdk@...iam.gserviceaccount.com
FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
```

## Database Setup

### Option A: Supabase (Recommended - Free Tier)

1. Go to [supabase.com](https://supabase.com/)
2. Create new project
3. Name: "contentsync"
4. Database password: (save this securely)
5. Region: Choose closest to your users

#### Run Migrations

1. In Supabase dashboard → SQL Editor
2. Copy contents of `migrations/001_initial_schema.sql`
3. Paste and click "Run"
4. Verify tables created under "Table Editor"

#### Get Connection String

1. Go to Project Settings → Database
2. Copy "Connection string" (URI mode)
3. Replace `[YOUR-PASSWORD]` with your actual password
4. Save as `DATABASE_URL` environment variable

### Option B: Local PostgreSQL

```bash
# Install PostgreSQL
# macOS
brew install postgresql@15
brew services start postgresql@15

# Create database
createdb contentsync

# Run migrations
psql -d contentsync -f migrations/001_initial_schema.sql
```

## Server Deployment

### Local Development

```bash
# Navigate to server directory
cd server

# Install dependencies
npm install

# Create .env file
cp .env.example .env

# Edit .env with your credentials
nano .env

# Start development server
npm run dev
```

Server will run on http://localhost:3000

Test health endpoint:
```bash
curl http://localhost:3000/health
```

### Production Deployment (Railway)

1. **Install Railway CLI**
   ```bash
   npm install -g @railway/cli
   ```

2. **Login**
   ```bash
   railway login
   ```

3. **Initialize Project**
   ```bash
   railway init
   ```

4. **Set Environment Variables**
   ```bash
   railway variables set DATABASE_URL="postgresql://..."
   railway variables set FIREBASE_PROJECT_ID="your-project"
   railway variables set FIREBASE_CLIENT_EMAIL="..."
   railway variables set FIREBASE_PRIVATE_KEY="..."
   railway variables set CORS_ORIGIN="https://yourapp.com"
   ```

5. **Deploy**
   ```bash
   railway up
   ```

6. **Get Public URL**
   ```bash
   railway domain
   ```

### Alternative: Render

1. Connect GitHub repository to Render
2. Create new Web Service
3. Build command: `cd server && npm install`
4. Start command: `cd server && npm start`
5. Add environment variables in Render dashboard
6. Deploy

## Android App Setup

### 1. Open in Android Studio

```bash
# Open Android Studio
# File → Open → Select android/ directory
```

### 2. Add google-services.json

Place the `google-services.json` from Firebase in:
```
android/app/google-services.json
```

### 3. Configure Server URLs

Edit `android/app/build.gradle`:

```groovy
buildTypes {
    release {
        buildConfigField "String", "SERVER_URL", "\"https://your-server.railway.app\""
        buildConfigField "String", "SOCKET_URL", "\"wss://your-server.railway.app\""
    }
    debug {
        buildConfigField "String", "SERVER_URL", "\"http://10.0.2.2:3000\""
        buildConfigField "String", "SOCKET_URL", "\"ws://10.0.2.2:3000\""
    }
}
```

### 4. Sync and Build

1. Click "Sync Project with Gradle Files"
2. Wait for sync to complete
3. Build → Make Project
4. Fix any errors

### 5. Run on Device/Emulator

1. Connect Android device via USB (enable USB debugging)
   - OR -
   Start Android emulator (API 24+)

2. Click Run (▶️) in Android Studio
3. Select device
4. App should launch

### 6. Test Basic Flow

1. Launch app
2. Click "Continue" (anonymous auth)
3. Click "Create Room"
4. Select a video file
5. Wait for processing
6. Room should be created with ID displayed

## Testing

### Test Server Locally

```bash
cd server
npm test
```

### Test with Two Devices

1. **Host Device:**
   - Create room
   - Note room ID
   - Play video

2. **Follower Device:**
   - Join room with room ID
   - Select SAME video file
   - Playback should sync automatically

### Test Sync Algorithm

- Play/pause on host → follower should sync
- Seek on host → follower should jump
- Check sync status indicator on follower

## Troubleshooting

### Common Issues

**Server won't start:**
- Check `DATABASE_URL` is correct
- Verify Firebase credentials
- Check logs: `railway logs` or check Render dashboard

**Android build fails:**
- Sync Gradle files
- Invalidate caches: File → Invalidate Caches / Restart
- Check `google-services.json` is present
- Verify internet connection for dependency downloads

**Socket won't connect:**
- Check `SERVER_URL` and `SOCKET_URL` in build.gradle
- For local testing, use `10.0.2.2:3000` (Android emulator)
- For physical device on same network, use computer's local IP
- Check firewall settings

**File hash mismatch:**
- Ensure EXACT same file on both devices
- File must be byte-identical (same encoding, no re-encoding)
- Check file size matches

**Authentication fails:**
- Verify Firebase Auth is enabled
- Check `google-services.json` matches Firebase console
- Check package name matches: `com.contentsync.app`

## Next Steps

- [ ] Set up crash reporting (Firebase Crashlytics)
- [ ] Configure analytics (Firebase Analytics)
- [ ] Test on multiple devices
- [ ] Prepare Play Store assets (screenshots, icon, description)
- [ ] Write privacy policy
- [ ] Submit for internal testing
- [ ] Collect beta feedback
- [ ] Launch! 🚀

## Support

For issues, check:
- GitHub Issues
- Server logs: `railway logs` or Render dashboard
- Android Logcat in Android Studio

## Security Checklist

- [ ] Never commit `sa.json`, `google-services.json`, or `.env` files
- [ ] Use environment variables for production secrets
- [ ] Enable TLS/SSL for server (automatic on Railway/Render)
- [ ] Review Firebase security rules
- [ ] Set up Supabase Row Level Security (RLS)
- [ ] Add rate limiting on sensitive endpoints
- [ ] Keep dependencies updated

