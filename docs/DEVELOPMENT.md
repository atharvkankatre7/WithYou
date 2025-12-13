# ContentSync Development Guide

Guide for developers working on the ContentSync codebase.

## Development Setup

### Prerequisites

- Node.js 18+
- Android Studio (latest stable)
- JDK 17
- PostgreSQL (local) or Supabase account
- Git
- Firebase CLI (optional)

### Initial Setup

1. **Clone Repository**
   ```bash
   git clone https://github.com/yourusername/contentsync.git
   cd contentsync
   ```

2. **Server Setup**
   ```bash
   cd server
   npm install
   cp env.example .env
   # Edit .env with your local config
   ```

3. **Database Setup**
   ```bash
   # If using local PostgreSQL:
   createdb contentsync_dev
   
   # Run migrations
   psql -d contentsync_dev -f ../migrations/001_initial_schema.sql
   psql -d contentsync_dev -f ../migrations/002_add_rls_policies.sql
   ```

4. **Start Server**
   ```bash
   npm run dev
   # Server runs on http://localhost:3000
   ```

5. **Android Setup**
   ```bash
   cd ../android
   # Open in Android Studio
   # Sync Gradle files
   # Run on emulator or device
   ```

## Project Structure

```
ContentSync/
├── server/                 # Node.js backend
│   ├── src/
│   │   ├── index.js       # Server entry point
│   │   ├── socket/        # Socket.IO handlers
│   │   ├── routes/        # REST API routes
│   │   ├── db/            # Database client
│   │   ├── middleware/    # Auth, error handling
│   │   └── utils/         # Validation, logging
│   ├── package.json
│   └── Dockerfile
├── android/               # Android app
│   └── app/
│       └── src/main/kotlin/com/contentsync/app/
│           ├── data/      # Models, network, repositories
│           ├── player/    # ExoPlayer, sync engine
│           ├── ui/        # Compose UI, screens
│           └── utils/     # File hash, metadata
├── migrations/            # SQL migrations
├── docs/                  # Documentation
├── README.md
├── PRIVACY.md
└── TERMS_OF_SERVICE.md
```

## Development Workflow

### Backend Development

#### Running Server Locally

```bash
cd server
npm run dev  # Nodemon for auto-reload
```

#### Testing REST API

```bash
# Health check
curl http://localhost:3000/health

# Create room (requires auth token)
curl -X POST http://localhost:3000/api/rooms/create \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "host_user_id": "test-user",
    "file_hash": "abc123...",
    "duration_ms": 120000,
    "file_size": 50000000,
    "codec": {"video":"h264","audio":"aac","resolution":"1920x1080"}
  }'
```

#### Testing Socket.IO

Use a Socket.IO client tool or write a test script:

```javascript
const io = require('socket.io-client');

const socket = io('http://localhost:3000', {
  transports: ['websocket'],
  auth: { token: 'dev-token' }
});

socket.on('connect', () => {
  console.log('Connected!');
  socket.emit('joinRoom', {
    roomId: 'TEST123',
    role: 'host',
    file_hash: 'abc123...'
  });
});

socket.on('joined', (data) => {
  console.log('Joined:', data);
});
```

### Android Development

#### Running App

1. Open `android/` in Android Studio
2. Select emulator or connected device
3. Click Run (Shift+F10)

#### Debug Build vs Release Build

- **Debug**: Uses `http://10.0.2.2:3000` (localhost for emulator)
- **Release**: Uses production API URL

#### Testing Sync Algorithm

1. Run two emulators or one emulator + one physical device
2. Create room on device 1
3. Join room on device 2
4. Select same video file on both
5. Test play/pause/seek sync

#### Useful Logcat Filters

```bash
# View all ContentSync logs
adb logcat -s ContentSync VideoSyncEngine SocketIOClient

# View only sync events
adb logcat *:S VideoSyncEngine:D

# View crashes
adb logcat -s AndroidRuntime
```

### Code Style

#### Kotlin

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable names
- Document public APIs with KDoc
- Max line length: 120 characters

#### JavaScript

- Use ES6+ features
- Async/await over callbacks
- JSDoc for public functions
- ESLint for linting

### Git Workflow

1. **Create Feature Branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make Changes**
   ```bash
   git add .
   git commit -m "Add: brief description of changes"
   ```

3. **Push and Create PR**
   ```bash
   git push origin feature/your-feature-name
   ```

4. **Commit Message Format**
   ```
   Type: Short description

   Longer description if needed.

   Types: Add, Fix, Update, Remove, Refactor, Docs
   ```

## Testing

### Backend Tests

```bash
cd server
npm test
npm run test:coverage
```

#### Writing Tests

```javascript
// server/tests/routes.test.js
const request = require('supertest');
const { app } = require('../src/index');

describe('Room API', () => {
  test('POST /api/rooms/create requires auth', async () => {
    const res = await request(app)
      .post('/api/rooms/create')
      .send({});
    expect(res.status).toBe(401);
  });
});
```

### Android Tests

#### Unit Tests

```bash
cd android
./gradlew test
```

#### Instrumented Tests

```bash
./gradlew connectedAndroidTest
```

#### Writing Tests

```kotlin
// FileHashUtilTest.kt
import org.junit.Test
import org.junit.Assert.*

class FileHashUtilTest {
    @Test
    fun verifyHash_identicalHashes_returnsTrue() {
        val hash1 = "abc123"
        val hash2 = "abc123"
        assertTrue(FileHashUtil.verifyHash(hash1, hash2))
    }
}
```

## Debugging

### Backend Debugging

1. Add breakpoints in VS Code
2. Launch with debugger:
   ```json
   // .vscode/launch.json
   {
     "type": "node",
     "request": "launch",
     "name": "Debug Server",
     "program": "${workspaceFolder}/server/src/index.js",
     "restart": true
   }
   ```

### Android Debugging

1. Set breakpoints in Android Studio
2. Run in debug mode (Shift+F9)
3. Use Android Profiler for performance
4. Use Database Inspector for local data

### Common Issues

**Issue**: Socket not connecting from Android emulator
- Solution: Use `10.0.2.2` instead of `localhost`
- Check firewall isn't blocking port 3000

**Issue**: File hash taking too long
- Solution: Increase buffer size in `FileHashUtil.kt`
- Test with smaller files first

**Issue**: Sync drift over time
- Solution: Adjust `TIME_SYNC_INTERVAL_MS` in `RoomSyncManager.kt`
- Check RTT measurement accuracy

## Performance Optimization

### Backend

- Use connection pooling (already configured)
- Cache room state in Redis (for scaling)
- Add database indexes for frequent queries
- Enable gzip compression

### Android

- Use hardware acceleration for ExoPlayer
- Lazy load UI components
- Optimize file hash computation with parallel processing
- Profile with Android Profiler

## API Documentation

### REST Endpoints

See `server/src/routes/rooms.js` for full API.

**Base URL**: `https://your-server.com/api`

#### POST /rooms/create
Creates a new room.

**Headers:**
- `Authorization: Bearer <firebase-token>`

**Body:**
```json
{
  "host_user_id": "string",
  "file_hash": "string (64 hex)",
  "duration_ms": "number",
  "file_size": "number",
  "codec": {
    "video": "string",
    "audio": "string",
    "resolution": "string"
  }
}
```

**Response:**
```json
{
  "room_id": "ABC123",
  "share_url": "https://app/room/ABC123",
  "expires_at": "2025-12-04T00:00:00Z"
}
```

### Socket Events

See blueprint section 4 for complete event specifications.

## Contributing

### Before Submitting PR

- [ ] Code follows style guidelines
- [ ] Tests pass (`npm test` and `./gradlew test`)
- [ ] No linter errors
- [ ] Documentation updated if needed
- [ ] Commit messages are clear
- [ ] Branch is up to date with main

### PR Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
Describe testing done

## Screenshots (if applicable)
Add screenshots for UI changes
```

## Resources

- [ExoPlayer Guide](https://exoplayer.dev/guide.html)
- [Socket.IO Documentation](https://socket.io/docs/)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material Design 3](https://m3.material.io/)
- [Firebase Auth](https://firebase.google.com/docs/auth)

## Getting Help

- Check existing issues on GitHub
- Review blueprint document
- Email: dev@contentsync.app

---

Happy coding! 🚀

