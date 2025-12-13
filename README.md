# ContentSync - Synchronized Video Watching App

A lightweight mobile app that lets two users watch the same local video file in perfect sync.

## Overview

ContentSync enables couples or friends to watch videos together remotely while maintaining perfect synchronization. The app uses local files (no upload required) and exchanges only metadata and playback events through a WebSocket signaling server.

## Features

- 🎥 **Local File Sync** - Watch videos already on your device
- 🔄 **Perfect Synchronization** - Advanced sync algorithm with < 100ms accuracy
- 💬 **Chat & Reactions** - Interactive overlay during playback
- 🔒 **Privacy-First** - No video uploads, only metadata exchange
- 📱 **Android Native** - Built with Kotlin & ExoPlayer

## Architecture

### Components

- **Android App** (Kotlin + ExoPlayer) - Primary client
- **Signaling Server** (Node.js + Socket.IO) - Event relay
- **Database** (PostgreSQL via Supabase) - Room & user metadata
- **Authentication** (Firebase Auth) - Phone/email login

### Tech Stack

**Backend:**
- Node.js v18+
- Express.js
- Socket.IO
- Firebase Admin SDK
- PostgreSQL (Supabase)

**Android:**
- Kotlin
- ExoPlayer 2.18+
- Socket.IO Client
- Firebase Auth SDK
- Jetpack Compose
- Kotlin Coroutines

## Project Structure

```
ContentSync/
├── server/                 # Node.js signaling server
│   ├── src/
│   ├── package.json
│   └── Dockerfile
├── android/               # Android app
│   ├── app/
│   ├── build.gradle
│   └── gradle/
├── migrations/            # Database migrations
├── docs/                  # Documentation
└── deploy/               # Deployment configs
```

## Quick Start

### Prerequisites

- Node.js 18+
- Android Studio Hedgehog+
- Firebase project
- Supabase account

### Server Setup

```bash
cd server
npm install
cp .env.example .env
# Configure environment variables
npm run dev
```

### Android Setup

1. Open `android/` in Android Studio
2. Add `google-services.json` from Firebase
3. Sync Gradle
4. Run on device/emulator

## Sync Algorithm

The app uses a sophisticated sync algorithm:

1. **Time Sync**: Host sends position + timestamp with each event
2. **RTT Compensation**: Periodic ping/pong measures network latency
3. **Smart Correction**:
   - `|diff| > 0.6s`: Hard seek to correct position
   - `0.1s < |diff| ≤ 0.6s`: Gentle playback rate nudge (1.04x or 0.96x)
   - `|diff| ≤ 0.1s`: No correction needed

## Database Schema

See `migrations/001_initial_schema.sql` for complete schema.

Core tables:
- `users` - User accounts
- `rooms` - Sync rooms with file metadata
- `participants` - Room membership
- `room_events` - Event log (optional)

## API Documentation

### REST Endpoints

- `POST /api/rooms/create` - Create new room
- `POST /api/rooms/:id/validate` - Validate file metadata
- `POST /api/rooms/:id/close` - Close room

### Socket.IO Events

**Client → Server:**
- `authenticate` - Initial auth with Firebase token
- `joinRoom` - Join a room
- `hostPlay` / `hostPause` / `hostSeek` - Playback controls (host only)
- `ping` - RTT measurement
- `reaction` - Send reaction
- `chatMessage` - Send chat message

**Server → Client:**
- `joined` - Room join confirmation
- `hostPlay` / `hostPause` / `hostSeek` - Relayed playback events
- `hostTimeSync` - Periodic sync heartbeat
- `pong` - RTT response
- `reaction` / `chatMessage` - Relayed messages
- `error` - Error notification

## Deployment

### Server (Railway/Render)

```bash
# Railway
railway login
railway init
railway up

# Or use Render - connect GitHub repo
```

### Database (Supabase)

1. Create project at supabase.com
2. Run migrations from `migrations/` folder
3. Copy connection string to `.env`

### Android (Play Store)

```bash
cd android
./gradlew bundleRelease
# Upload AAB to Play Console
```

## Security

- ✅ JWT authentication on all socket connections
- ✅ Room IDs are randomized 6-8 char strings
- ✅ Optional passcode protection
- ✅ Rate limiting on events
- ✅ Input validation & sanitization
- ✅ TLS/WSS encryption

## Privacy

- No video files uploaded to servers
- Only SHA-256 hash, duration, codec metadata stored
- Rooms auto-expire after 7 days
- GDPR compliant data deletion

## Testing

```bash
# Server tests
cd server
npm test

# Android tests
cd android
./gradlew test
./gradlew connectedAndroidTest
```

## Roadmap

- [x] MVP: Local file sync for 2 users
- [ ] iOS app (Swift + AVPlayer)
- [ ] Public URL support (mp4 links)
- [ ] Group rooms (3+ users)
- [ ] Cloud upload option
- [ ] Web client

## License

MIT License - See LICENSE file

## Support

For issues or questions:
- Email: support@contentsync.app
- GitHub Issues: [github.com/yourorg/contentsync](https://github.com)

## Credits

Built with ❤️ for couples watching together, apart.
