# ContentSync Testing Guide

Comprehensive testing procedures for ContentSync.

## Table of Contents

1. [Unit Testing](#unit-testing)
2. [Integration Testing](#integration-testing)
3. [Manual Testing](#manual-testing)
4. [Performance Testing](#performance-testing)
5. [Test Scenarios](#test-scenarios)

## Unit Testing

### Server Unit Tests

```bash
cd server
npm test
```

**Test Coverage:**
- ✅ Room creation and validation
- ✅ Authentication token verification
- ✅ Socket event handlers
- ✅ Database queries
- ✅ Hash validation logic

### Android Unit Tests

```bash
cd android
./gradlew test
```

**Test Coverage:**
- ✅ File hashing utility
- ✅ Sync algorithm calculations
- ✅ Time formatting utilities
- ✅ ViewModel state management

## Integration Testing

### Server Integration Tests

Test API endpoints with actual database:

```bash
cd server
npm run test:integration
```

Test cases:
1. POST /api/rooms/create → Returns room ID
2. POST /api/rooms/:id/validate → Validates file hash
3. POST /api/rooms/:id/close → Closes room
4. Socket authentication flow
5. Room join and leave events

### Android Instrumentation Tests

```bash
cd android
./gradlew connectedAndroidTest
```

Test cases:
1. File picker integration
2. ExoPlayer initialization
3. Socket connection flow
4. UI navigation

## Manual Testing

### Pre-Flight Checklist

Before each test session:

- [ ] Server running and healthy (check /health endpoint)
- [ ] Database accessible
- [ ] Firebase Auth configured
- [ ] Two test devices ready
- [ ] Same test video on both devices (byte-identical)

### Test Matrix

| Scenario | Device 1 | Device 2 | Expected Result |
|----------|----------|----------|-----------------|
| Create Room | Host creates | - | Room ID shown |
| Join Room | Host in room | Follower joins with same file | Both connected |
| File Mismatch | Host in room | Follower joins with different file | Error: file mismatch |
| Play Sync | Host plays | Follower | Follower plays in sync |
| Pause Sync | Host pauses | Follower | Follower pauses |
| Seek Sync | Host seeks | Follower | Follower seeks to position |
| Disconnect | Host disconnects | Follower | Room closes or host transferred |

## Test Scenarios

### Scenario 1: Basic Sync Flow (Happy Path)

**Objective:** Verify end-to-end sync functionality

**Steps:**

1. **Host Device:**
   - Open app
   - Authenticate
   - Click "Create Room"
   - Select video file (e.g., test-video-1080p.mp4)
   - Wait for hash computation (~10-30s depending on file size)
   - Room created → Note room ID
   - Video player loads

2. **Follower Device:**
   - Open app
   - Authenticate
   - Click "Join Room"
   - Enter room ID from host
   - Select SAME video file
   - Wait for hash computation
   - Room joined → Video player loads

3. **Host Device:**
   - Press Play
   - Observe playback starts

4. **Follower Device:**
   - Verify playback automatically starts
   - Check sync status shows "Synced"
   - Verify position matches host (±1 second acceptable)

5. **Host Device:**
   - Pause at 00:30
   - Seek to 01:00
   - Play again

6. **Follower Device:**
   - Verify pause occurred
   - Verify seek to 01:00
   - Verify play resumed
   - All within 500ms of host action

**Expected Results:**
- ✅ Both devices playing same position
- ✅ Sync status "Synced"
- ✅ Position difference < 1 second
- ✅ No buffering on follower

### Scenario 2: File Mismatch Detection

**Objective:** Verify file hash validation

**Steps:**

1. Host creates room with `video-A.mp4`
2. Follower attempts to join with `video-B.mp4` (different file)
3. App computes hashes
4. Server compares hashes

**Expected Results:**
- ✅ Error dialog: "File mismatch"
- ✅ Suggested action: "Select the same file as host"
- ✅ Follower not allowed to join

### Scenario 3: Network Interruption

**Objective:** Test reconnection logic

**Steps:**

1. Host and follower successfully synced
2. Video playing
3. Disable WiFi on follower device (5 seconds)
4. Re-enable WiFi

**Expected Results:**
- ✅ Connection status shows "Disconnected"
- ✅ Automatic reconnection within 10 seconds
- ✅ Resync to host's current position
- ✅ Playback resumes smoothly

### Scenario 4: High Latency Network

**Objective:** Test sync algorithm under poor network

**Steps:**

1. Use network throttling (Android Dev Tools)
2. Set latency to 500ms
3. Host plays, pauses, seeks

**Expected Results:**
- ✅ Follower still syncs (with expected delay)
- ✅ Sync algorithm compensates for RTT
- ✅ No excessive seeking or jitter

### Scenario 5: Different File Encodings

**Objective:** Verify same content, different encoding fails

**Steps:**

1. Host uses `original.mp4` (H.264)
2. Follower uses `re-encoded.mp4` (same content, re-encoded)
3. Attempt to join

**Expected Results:**
- ✅ Hash mismatch detected
- ✅ Join prevented

**Note:** This is expected behavior. Files must be byte-identical.

### Scenario 6: Large File (4K, 10GB+)

**Objective:** Test with large files

**Steps:**

1. Select 10GB 4K video
2. Observe hash computation (may take 1-5 minutes)
3. Create room
4. Follower selects same file
5. Test sync

**Expected Results:**
- ✅ Hash computation shows progress
- ✅ Room creation succeeds
- ✅ Playback smooth (hardware acceleration)
- ✅ Sync works correctly

### Scenario 7: Multiple Seeks in Quick Succession

**Objective:** Stress test sync algorithm

**Steps:**

1. Host and follower synced
2. Host rapidly seeks: 00:10 → 01:00 → 00:30 → 02:00 (4 seeks in 5 seconds)

**Expected Results:**
- ✅ Follower syncs to final position
- ✅ No crash or excessive lag
- ✅ Sync stabilizes within 2 seconds

### Scenario 8: Room Expiry

**Objective:** Test automatic room cleanup

**Steps:**

1. Create room (default expiry: 7 days)
2. Check database for room record
3. Manually update `expires_at` to past date
4. Run cleanup job or wait for cron

**Expected Results:**
- ✅ Room marked inactive
- ✅ Cannot join expired room
- ✅ Error: "Room has expired"

### Scenario 9: Concurrent Rooms

**Objective:** Test server handling multiple rooms

**Steps:**

1. Create 5 rooms simultaneously (5 pairs of devices)
2. All playing different videos

**Expected Results:**
- ✅ All rooms function independently
- ✅ No cross-room interference
- ✅ Server handles load (check CPU/memory)

### Scenario 10: Battery and Performance

**Objective:** Measure battery drain and CPU usage

**Setup:**
- Fully charged device
- 30-minute video playback while synced

**Measure:**
- Battery % before and after
- CPU usage (Android Profiler)
- Memory usage
- Network data usage

**Acceptable Results:**
- Battery drain: < 15% for 30 min
- CPU: < 30% average
- Memory: < 200MB
- Network: < 5MB data (for sync events)

## Performance Testing

### Sync Accuracy Test

**Objective:** Measure sync precision

**Tools:**
- High-speed camera or screen recording (both devices)
- Video with frame counter or timecode

**Method:**
1. Play video with visible timecode
2. Record both screens simultaneously
3. Analyze frame-by-frame difference

**Target:**
- Sync within 100ms (3-4 frames at 30fps)

### RTT Measurement

**Objective:** Verify ping/pong accuracy

**Method:**
- Check app logs for RTT values
- Typical RTT on WiFi: 10-50ms
- Typical RTT on LTE: 50-150ms

**Test:**
- Verify sync algorithm uses RTT correctly
- Compare expected position calculation with actual

### Load Testing

**Server Load Test:**

```bash
# Using Artillery or similar tool
npm install -g artillery
artillery quick --count 100 --num 10 http://your-server.com/health
```

**Target:**
- 100 concurrent rooms
- < 200ms response time
- No dropped connections

## Automated Testing

### Continuous Integration

GitHub Actions runs automatically on push:

```bash
# Server tests
.github/workflows/server-ci.yml

# Android tests
.github/workflows/android-ci.yml
```

### Regression Testing

Before each release:

1. Run full test suite
2. Manual test matrix (all scenarios)
3. Performance benchmarks
4. Security audit (dependencies)

## Bug Reporting

When filing a bug, include:

1. **Device Info:** Model, OS version, app version
2. **Steps to Reproduce:** Detailed steps
3. **Expected vs Actual:** What should vs did happen
4. **Logs:** Android Logcat, server logs
5. **Screenshots/Video:** If UI-related

## Test Devices

Recommended test device matrix:

- **High-end:** Samsung S23, Pixel 8
- **Mid-range:** Samsung A54, Pixel 6a
- **Low-end:** Budget device with API 24
- **Emulator:** Android 13, 14

Test on:
- Different screen sizes
- Different Android versions (API 24-34)
- WiFi and mobile data
- Various video formats (H.264, H.265, VP9)

## Conclusion

Thorough testing ensures:
- ✅ Reliable synchronization
- ✅ Good user experience
- ✅ Minimal crashes
- ✅ Efficient performance

Test early, test often! 🧪
