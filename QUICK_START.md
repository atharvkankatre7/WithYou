# ContentSync - Quick Start Guide

**Get up and running in 15 minutes**

## Prerequisites

- Node.js 18+ installed
- Android Studio installed
- Firebase account (free)
- Supabase account (free)

---

## Step 1: Firebase Setup (3 minutes)

1. Go to https://console.firebase.google.com/
2. Create new project: "ContentSync"
3. Enable **Authentication** → **Anonymous** sign-in
4. Add Android app:
   - Package name: `com.contentsync.app`
   - Download `google-services.json`
5. Go to Project Settings → Service Accounts
6. Click "Generate new private key"
7. Save as `server/sa.json`

---

## Step 2: Database Setup (2 minutes)

1. Go to https://supabase.com/
2. Create new project: "contentsync"
3. Set strong password
4. Wait for project to initialize (~2 min)
5. Go to SQL Editor
6. Copy/paste contents of `migrations/001_initial_schema.sql`
7. Click "Run"
8. Verify tables created in Table Editor

---

## Step 3: Server Setup (3 minutes)

```bash
# Navigate to server directory
cd server

# Install dependencies
npm install

# Create environment file
cp .env.example .env

# Edit .env (use your editor)
nano .env
```

**Required .env values:**
```env
PORT=3000
NODE_ENV=development

# From Supabase (Settings → Database → Connection String)
DATABASE_URL=postgresql://postgres:[YOUR-PASSWORD]@db.[PROJECT-REF].supabase.co:5432/postgres

# From Firebase service account JSON
FIREBASE_PROJECT_ID=your-project-id
FIREBASE_CLIENT_EMAIL=firebase-adminsdk@...iam.gserviceaccount.com
FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
```

**Start server:**
```bash
npm run dev
```

**Verify:** Open http://localhost:3000/health
Should see: `{"status":"ok",...}`

---

## Step 4: Android Setup (5 minutes)

1. **Copy google-services.json**
   ```bash
   cp /path/to/downloaded/google-services.json android/app/
   ```

2. **Open in Android Studio**
   - File → Open → Select `android/` folder
   - Wait for Gradle sync (~2-3 minutes)

3. **Configure Server URL** (for emulator testing)
   - File already configured for `10.0.2.2:3000` in debug mode
   - No changes needed for local testing

4. **Build**
   - Build → Make Project
   - Fix any errors (should build cleanly)

5. **Run**
   - Click Run ▶️
   - Select emulator or connected device
   - App should launch

---

## Step 5: Test It! (2 minutes)

### Test with Two Emulators

**Terminal 1:**
```bash
# Start first emulator
emulator -avd Pixel_6_API_34 -port 5554
```

**Terminal 2:**
```bash
# Start second emulator
emulator -avd Pixel_6_API_34 -port 5556
```

**In Android Studio:**
1. Run app on first emulator (port 5554)
2. Run app on second emulator (port 5556)

### Test Flow

**Emulator 1 (Host):**
1. Click "Continue" (auth)
2. Click "Create Room"
3. Click "Select Video" → Choose any video
4. Wait for hash computation
5. Click "Create Room"
6. Note the Room ID (e.g., "A7B3XK")

**Emulator 2 (Follower):**
1. Click "Continue" (auth)
2. Click "Join Room"
3. Enter Room ID from Host
4. Click "Next"
5. Select THE SAME video file
6. Wait for hash computation
7. Room should join automatically

**Emulator 1 (Host):**
- Press Play ▶️
- Video starts playing

**Emulator 2 (Follower):**
- Video should start playing automatically
- Check sync status indicator (should show "Synced")
- Position should match Host (±1 second)

**Test Sync:**
- Host: Pause → Follower should pause
- Host: Seek to 1:00 → Follower should seek
- Host: Play → Follower should play

✅ **Success!** Both devices are synced!

---

## Troubleshooting

### Server won't start
- Check `DATABASE_URL` is correct
- Verify Firebase credentials in `.env`
- Check port 3000 is not in use: `lsof -i :3000`

### Android build fails
- File → Invalidate Caches / Restart
- Verify `google-services.json` is in `android/app/`
- Check internet connection (Gradle downloads dependencies)

### App crashes on launch
- Check Logcat in Android Studio
- Verify Firebase Auth is enabled
- Ensure `google-services.json` matches Firebase console

### Socket connection fails
- Verify server is running (check health endpoint)
- For emulator, use `10.0.2.2:3000` (already configured)
- Check `BuildConfig.SOCKET_URL` in build.gradle

### File hash mismatch
- Use EXACT same file on both devices
- File must be byte-identical (copy via ADB)
- Check file size matches

**Copy file to both emulators:**
```bash
# Push to emulator 1
adb -s emulator-5554 push /path/to/video.mp4 /sdcard/Download/

# Push to emulator 2
adb -s emulator-5556 push /path/to/video.mp4 /sdcard/Download/
```

---

## Next Steps

- ✅ **Test on physical devices** (see `docs/TESTING.md`)
- ✅ **Deploy server** (see `docs/SETUP.md` → Railway section)
- ✅ **Prepare for Play Store** (see `PROJECT_SUMMARY.md`)
- ✅ **Read full documentation** (see `README.md`)

---

## Quick Reference

### Start Local Development

**Terminal 1 (Server):**
```bash
cd server && npm run dev
```

**Terminal 2 (Android):**
```bash
# Open Android Studio → Run
```

### Check Logs

**Server:**
```bash
# In server terminal
# Logs show connection events, room creation, etc.
```

**Android:**
```bash
# In Android Studio → Logcat
# Filter by "ContentSync" or "Timber"
```

### Stop Everything

```bash
# Server: Ctrl+C in terminal
# Android: Stop button in Android Studio
# Emulators: Close emulator windows
```

---

## Useful Commands

```bash
# Check server health
curl http://localhost:3000/health

# List Android emulators
emulator -list-avds

# List connected devices
adb devices

# View Android logs
adb logcat | grep -i contentsync

# Push file to device
adb push video.mp4 /sdcard/Download/

# Build Android APK
cd android && ./gradlew assembleDebug
```

---

## Need Help?

1. Check `docs/SETUP.md` for detailed setup
2. Check `docs/TESTING.md` for test scenarios
3. Check `PROJECT_SUMMARY.md` for architecture
4. Check server logs and Android Logcat
5. File GitHub issue with logs and steps

---

**Happy Syncing! 🎉**

You now have a working synchronized video app! Both devices can watch together in perfect sync. Test it, customize it, and deploy it! 🚀

