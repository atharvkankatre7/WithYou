# ContentSync Deployment Guide

Complete guide for deploying the ContentSync backend and publishing the Android app.

## Prerequisites

- Node.js 18+ installed
- PostgreSQL database or Supabase account
- Firebase project set up
- Railway/Render account (for hosting)
- Google Play Console account (for Android)
- Domain name (optional, recommended)

## Step 1: Database Setup (Supabase)

### 1.1 Create Supabase Project

1. Go to [supabase.com](https://supabase.com)
2. Click "New Project"
3. Choose organization and project name
4. Set database password (save this securely)
5. Select region closest to your users
6. Wait for project to be provisioned

### 1.2 Run Migrations

1. Go to SQL Editor in Supabase Dashboard
2. Copy contents of `migrations/001_initial_schema.sql`
3. Paste and execute
4. Copy contents of `migrations/002_add_rls_policies.sql`
5. Paste and execute

### 1.3 Get Connection String

1. Go to Project Settings → Database
2. Copy "Connection string" (URI format)
3. Replace `[YOUR-PASSWORD]` with your database password
4. Save this as `DATABASE_URL` for later

## Step 2: Firebase Setup

### 2.1 Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com)
2. Click "Add project"
3. Enter project name: "ContentSync"
4. Disable Google Analytics (optional)
5. Click "Create project"

### 2.2 Enable Authentication

1. Go to Authentication → Sign-in method
2. Enable "Email/Password"
3. Enable "Phone" (optional, requires verification)

### 2.3 Add Android App

1. Go to Project Settings → General
2. Click "Add app" → Android
3. Package name: `com.contentsync.app`
4. Download `google-services.json`
5. Place in `android/app/google-services.json`

### 2.4 Generate Service Account Key

1. Go to Project Settings → Service accounts
2. Click "Generate new private key"
3. Download JSON file
4. Rename to `firebase-sa.json`
5. Place in `server/firebase-sa.json`
6. **Never commit this to Git!**

### 2.5 Enable Crashlytics

1. Go to Crashlytics in Firebase Console
2. Click "Enable Crashlytics"
3. Follow setup instructions

## Step 3: Server Deployment (Railway)

### 3.1 Prepare Server

1. Ensure `server/firebase-sa.json` is in place (gitignored)
2. Test server locally:

```bash
cd server
npm install
cp env.example .env
# Edit .env with your values
npm run dev
```

3. Verify it starts without errors

### 3.2 Deploy to Railway

#### Option A: Railway CLI

```bash
# Install Railway CLI
npm install -g @railway/cli

# Login
railway login

# Initialize project
cd server
railway init

# Add environment variables
railway variables set DATABASE_URL="your-supabase-connection-string"
railway variables set FIREBASE_PROJECT_ID="your-firebase-project-id"
railway variables set NODE_ENV="production"
railway variables set CORS_ORIGINS="https://yourdomain.com"

# Upload firebase-sa.json securely
# In Railway dashboard: Variables → RAW Editor → Add file content as FIREBASE_SA_JSON

# Deploy
railway up
```

#### Option B: Railway Dashboard

1. Go to [railway.app](https://railway.app)
2. Click "New Project" → "Deploy from GitHub repo"
3. Connect your GitHub account
4. Select your ContentSync repository
5. Set root directory to `server`
6. Add environment variables:
   - `DATABASE_URL`
   - `FIREBASE_PROJECT_ID`
   - `NODE_ENV=production`
   - `CORS_ORIGINS`
   - `PORT=3000`
7. For `firebase-sa.json`, add as environment variable `FIREBASE_SA_JSON` (entire JSON content)
8. Update `server/src/index.js` to read from env var if file doesn't exist
9. Deploy

### 3.3 Get Server URL

1. Railway will provide a URL: `https://your-app.railway.app`
2. Save this URL for Android app configuration

### 3.4 Optional: Custom Domain

1. In Railway project settings → Domains
2. Add custom domain: `api.contentsync.com`
3. Configure DNS:
   - Add CNAME record pointing to Railway-provided domain
4. Wait for SSL certificate provisioning

## Step 4: Android App Configuration

### 4.1 Update API URL

Edit `android/app/build.gradle`:

```gradle
buildTypes {
    release {
        buildConfigField "String", "API_BASE_URL", "\"https://your-server.railway.app\""
    }
}
```

### 4.2 Add Signing Configuration

1. Generate keystore:

```bash
keytool -genkey -v -keystore contentsync-release.keystore -alias contentsync -keyalg RSA -keysize 2048 -validity 10000
```

2. Create `android/keystore.properties`:

```properties
storePassword=YOUR_STORE_PASSWORD
keyPassword=YOUR_KEY_PASSWORD
keyAlias=contentsync
storeFile=/path/to/contentsync-release.keystore
```

3. Update `android/app/build.gradle`:

```gradle
def keystorePropertiesFile = rootProject.file("keystore.properties")
def keystoreProperties = new Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(new FileInputStream(keystorePropertiesFile))
}

android {
    signingConfigs {
        release {
            storeFile file(keystoreProperties['storeFile'])
            storePassword keystoreProperties['storePassword']
            keyAlias keystoreProperties['keyAlias']
            keyPassword keystoreProperties['keyPassword']
        }
    }
    buildTypes {
        release {
            signingConfig signingConfigs.release
            ...
        }
    }
}
```

### 4.3 Build Release APK/AAB

```bash
cd android
./gradlew clean
./gradlew bundleRelease  # For AAB (recommended)
# OR
./gradlew assembleRelease  # For APK

# Output location:
# AAB: app/build/outputs/bundle/release/app-release.aab
# APK: app/build/outputs/apk/release/app-release.apk
```

## Step 5: Google Play Store Submission

### 5.1 Create Play Console Account

1. Go to [Google Play Console](https://play.google.com/console)
2. Sign up ($25 one-time fee)
3. Complete account setup

### 5.2 Create App

1. Click "Create app"
2. Enter details:
   - App name: ContentSync
   - Default language: English (US)
   - App or game: App
   - Free or paid: Free
3. Accept declarations
4. Click "Create app"

### 5.3 Complete Store Listing

#### Main Store Listing

1. App name: **ContentSync**
2. Short description (80 chars):
   ```
   Watch videos together in perfect sync with someone you love
   ```
3. Full description (4000 chars):
   ```
   ContentSync lets you watch videos together with someone special, perfectly synchronized across devices.

   ✨ FEATURES
   • Perfect sync: Sub-second synchronization
   • Privacy first: No uploads, files stay on your device
   • Reactions & chat: Real-time reactions while watching
   • Easy sharing: Create room, share code, watch together

   🔒 PRIVACY
   We NEVER upload your video files. Only file metadata (hash, duration) is exchanged for synchronization. Your content stays on your device.

   📱 HOW IT WORKS
   1. Host creates a room and selects a video from their device
   2. Host shares the room code
   3. Guest joins with the code and selects the same video
   4. Watch together with perfect sync!

   ⚠️ IMPORTANT
   Both users must have the same video file on their devices. This app does not host or distribute content. You must own or have rights to the content you watch.

   Built with ❤️ for couples and friends who want to share moments together.
   ```

4. App icon: 512x512 PNG (create in Figma or Canva)
5. Feature graphic: 1024x500 PNG
6. Screenshots: At least 2, recommended 8
   - 1080x1920 or larger
   - Show key features: home screen, room screen, sync status

#### Screenshots to Create

- Home screen with "Create Room" and "Join Room"
- Room screen with video player
- Sync status indicator
- Reactions/chat interface
- File selection screen
- Room sharing dialog

### 5.4 Content Rating

1. Go to Content rating
2. Start questionnaire
3. Select category: Communication, Social
4. Answer questions honestly:
   - No violence, sexual content, etc.
   - User-generated content: Yes (chat)
   - Content moderation: Describe your moderation approach
5. Calculate rating
6. Apply rating

### 5.5 Target Audience

1. Target age: 13 and older
2. Appeal to children: No

### 5.6 Privacy Policy

1. Upload `PRIVACY.md` to your website
2. Enter URL: `https://yourdomain.com/privacy`
3. Alternative: Use GitHub Pages or Notion

### 5.7 App Access

1. Provide test credentials if applicable
2. For ContentSync: "No special access needed"

### 5.8 Ads Declaration

1. Does your app contain ads? No (unless you add them later)

### 5.9 Complete Other Sections

- Data safety: Fill based on PRIVACY.md
- App category: Social
- Contact details: support@contentsync.app
- Store presence: All countries

### 5.10 Upload AAB

1. Go to Production → Create new release
2. Upload `app-release.aab`
3. Release name: 1.0.0
4. Release notes:
   ```
   🎉 Initial release of ContentSync!
   
   • Watch videos together in perfect sync
   • Real-time reactions and chat
   • Privacy-first: No uploads, local files only
   • Easy room creation and sharing
   ```
5. Save and review

### 5.11 Submit for Review

1. Review all sections (should all be green checkmarks)
2. Click "Send for review"
3. Review time: Usually 1-7 days

## Step 6: Post-Launch Monitoring

### 6.1 Set Up Monitoring

1. Enable Firebase Crashlytics monitoring
2. Monitor Railway/Render logs:
   ```bash
   railway logs
   ```
3. Set up Supabase monitoring alerts

### 6.2 Monitor Metrics

- Active users (Firebase Analytics)
- Crash rate (Crashlytics)
- Server response times (Railway)
- Database queries (Supabase)

### 6.3 Update Strategy

1. Test updates internally first (Internal testing track)
2. Roll out to beta testers (Closed testing)
3. Staged rollout to production (10% → 50% → 100%)

## Step 7: CI/CD (Optional)

### 7.1 GitHub Actions for Server

Create `.github/workflows/deploy-server.yml`:

```yaml
name: Deploy Server

on:
  push:
    branches: [main]
    paths:
      - 'server/**'

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Deploy to Railway
        env:
          RAILWAY_TOKEN: ${{ secrets.RAILWAY_TOKEN }}
        run: |
          npm install -g @railway/cli
          railway up --service server
```

### 7.2 GitHub Actions for Android

Create `.github/workflows/build-android.yml`:

```yaml
name: Build Android

on:
  push:
    branches: [main]
    paths:
      - 'android/**'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Build AAB
        run: |
          cd android
          ./gradlew bundleRelease
      - name: Upload AAB
        uses: actions/upload-artifact@v3
        with:
          name: app-release
          path: android/app/build/outputs/bundle/release/app-release.aab
```

## Troubleshooting

### Server Issues

**Problem**: Server won't start
- Check `DATABASE_URL` is correct
- Verify `firebase-sa.json` is present and valid
- Check Railway logs: `railway logs`

**Problem**: Socket connections failing
- Verify CORS_ORIGINS includes your domain
- Check firewall/network settings
- Ensure WebSocket support enabled

### Android Issues

**Problem**: Build fails
- Run `./gradlew clean`
- Check `google-services.json` is present
- Verify all dependencies are compatible

**Problem**: App crashes on launch
- Check Crashlytics for stack traces
- Verify API_BASE_URL is correct
- Test with debug build first

## Scaling Considerations

### When to Scale

- > 1000 concurrent users: Add Redis for session management
- > 10,000 daily active users: Consider horizontal scaling
- > 100 GB database: Upgrade Supabase plan

### Scaling Options

1. **Database**: Upgrade Supabase plan or migrate to managed PostgreSQL
2. **Server**: Add more Railway/Render instances with load balancer
3. **Caching**: Add Redis for room state caching
4. **CDN**: Use Cloudflare for static assets

## Security Checklist

- [ ] Database has backups enabled
- [ ] Environment variables are secure
- [ ] `firebase-sa.json` never committed to Git
- [ ] HTTPS/TLS enabled on all endpoints
- [ ] Rate limiting configured
- [ ] Input validation on all endpoints
- [ ] Keystore backed up securely
- [ ] Firebase auth properly configured
- [ ] Supabase RLS policies active

## Support

- Documentation: See `README.md`
- Issues: GitHub Issues
- Email: support@contentsync.app

---

**Congratulations!** Your ContentSync app is now deployed and live! 🎉

