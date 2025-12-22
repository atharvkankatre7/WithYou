# ContentSync Real Device Test Suite

**CRITICAL**: Emulator ≠ Real Device. These tests MUST run on physical phones.

---

## Test Environment Setup

### Required
- **Device 1**: Android phone (device specs: ____________)
- **Device 2**: Android phone (device specs: ____________)
- **Video File**: Same video on both phones (verify file hash matches)
- **Network**: Test each scenario separately
- **Server**: Deployed on Render (URL: https://api.contentsync.com)

### Tools
- Timber logs (enable for debug insights)
- RTT measurement visible in UI
- Network throttling (Chrome DevTools for web, or use Android Network Monitor)

---

## 1. CORE FUNCTIONALITY TESTS (Must Pass All)

### Test 1.1: App Launch
**Objective**: Release build launches without crash

**Steps**:
1. Build release APK: `./gradlew assembleRelease`
2. Install on Device 1: `adb install -r app/build/outputs/apk/release/app-release.apk`
3. Open app
4. Watch for crashes or ANR (Application Not Responding)

**Pass Criteria**:
- ✅ App opens cleanly
- ✅ No crash dialogs
- ✅ Main screen loads within 3 seconds

**Result**: ☐ PASS / ☐ FAIL

**Notes**: _____________

---

### Test 1.2: Host Play → Viewer Sync

**Setup**:
- Device 1 (Host): Select video, create room
- Device 2 (Viewer): Join with same video file

**Steps**:
1. Host: Tap play button
2. Watch Viewer screen
3. Note timestamp when viewer starts playing

**Pass Criteria**:
- ✅ Viewer video starts within 2 seconds of host
- ✅ Both show identical position
- ✅ No jitter or position jump

**Result**: ☐ PASS / ☐ FAIL  
**Measured Lag**: _______ ms (should be < 300ms)

**Notes**: _____________

---

### Test 1.3: Host Pause → Viewer Instant Pause

**Setup**: Both playing (from Test 1.2)

**Steps**:
1. Host: Pause video at any point
2. Watch Viewer screen immediately
3. Measure time to pause

**Pass Criteria**:
- ✅ Viewer pauses within 500ms
- ✅ Same position on both screens

**Result**: ☐ PASS / ☐ FAIL  
**Measured Lag**: _______ ms

**Notes**: _____________

---

### Test 1.4: Host Seek → Viewer Position Sync

**Setup**: One paused, one at 0:00

**Steps**:
1. Host: Seek to 2:30 (or random position)
2. Watch Viewer position update

**Pass Criteria**:
- ✅ Viewer jumps to same position
- ✅ Within 500ms
- ✅ Both remain paused

**Result**: ☐ PASS / ☐ FAIL  
**Measured Lag**: _______ ms

**Notes**: _____________

---

### Test 1.5: Playback Speed Change

**Setup**: Both playing

**Steps**:
1. Host: Change speed to 1.25x (or other value)
2. Watch Viewer's playback speed

**Pass Criteria**:
- ✅ Viewer speed matches host
- ✅ Update within 1 second

**Result**: ☐ PASS / ☐ FAIL

**Notes**: _____________

---

### Test 1.6: Long Playback (10+ minutes without desync)

**Setup**: Both playing

**Steps**:
1. Start playback
2. Let it run for 15 minutes
3. Check position difference every 2 minutes
4. Watch for jitter or position jumps

**Pass Criteria**:
- ✅ Positions stay within ±2 seconds throughout
- ✅ No pause/play glitches
- ✅ Smooth playback both sides
- ✅ No crashes

**Result**: ☐ PASS / ☐ FAIL

**Max Position Drift**: _______ seconds  
**Crashes/Glitches**: _____________

**Notes**: _____________

---

### Test 1.7: Background/Foreground Recovery

**Setup**: Both playing

**Steps**:
1. Host: Send app to background (lock phone)
2. Wait 10 seconds
3. Bring app to foreground
4. Check playback continues

**Pass Criteria**:
- ✅ Viewer continues playing smoothly
- ✅ No crash on resume
- ✅ Position correct after resume

**Result**: ☐ PASS / ☐ FAIL

**Notes**: _____________

---

## 2. GESTURE & CONTROL TESTS

### Test 2.1: Center Double-Tap Play/Pause

**Setup**: Any playback state

**Steps**:
1. Tap center of video twice quickly
2. Check playback state toggles

**Pass Criteria**:
- ✅ Toggles between play/pause
- ✅ Works while playing
- ✅ Works while paused

**Result**: ☐ PASS / ☐ FAIL

**Notes**: _____________

---

### Test 2.2: Left Double-Tap Seek Backward

**Setup**: Playing at position > 1:00

**Steps**:
1. Tap left side of video twice
2. Check position moves backward

**Pass Criteria**:
- ✅ Position decreases by ~10-20 seconds
- ✅ Visual indicator shows on screen
- ✅ Position updates smoothly

**Result**: ☐ PASS / ☐ FAIL

**Backward Seek Amount**: _______ seconds

**Notes**: _____________

---

### Test 2.3: Right Double-Tap Seek Forward

**Setup**: Playing at position < 10 min remaining

**Steps**:
1. Tap right side of video twice
2. Check position moves forward

**Pass Criteria**:
- ✅ Position increases by ~10-20 seconds
- ✅ Visual indicator shows
- ✅ Position updates smoothly

**Result**: ☐ PASS / ☐ FAIL

**Forward Seek Amount**: _______ seconds

**Notes**: _____________

---

### Test 2.4: Single Tap Show/Hide Controls

**Setup**: Any playback state

**Steps**:
1. Single tap video surface
2. Controls appear
3. Single tap again
4. Controls hide after 5 seconds of inactivity

**Pass Criteria**:
- ✅ Controls appear/disappear instantly
- ✅ Auto-hide after ~5 seconds
- ✅ Controls sit at bottom (no floating)

**Result**: ☐ PASS / ☐ FAIL

**Notes**: _____________

---

### Test 2.5: Lock Mode Disables Gestures

**Setup**: In room screen

**Steps**:
1. Tap lock icon (bottom right)
2. Try double-tap play/pause
3. Try seeking gestures

**Pass Criteria**:
- ✅ Lock icon shows "locked" state
- ✅ Double-tap play/pause does NOT work
- ✅ Seeking gestures do NOT work
- ✅ Single tap still shows controls (optional)

**Result**: ☐ PASS / ☐ FAIL

**Notes**: _____________

---

### Test 2.6: Non-Host Cannot Control

**Setup**: Device 2 as Viewer/Follower

**Steps**:
1. Device 2 attempts all gestures (double-tap, seeking)
2. Check if any playback commands are sent

**Pass Criteria**:
- ✅ Gestures do NOT affect viewer's own playback
- ✅ Viewer cannot start/pause independently
- ✅ Viewer cannot seek independently

**Result**: ☐ PASS / ☐ FAIL

**Notes**: _____________

---

## 3. SYNC PERFORMANCE TESTS

### Test 3.1: Viewer Lag Measurement

**Setup**: Both playing on same network

**Steps**:
1. Note RTT in UI (should display)
2. Host: Rapid taps play/pause 3 times
3. Measure time from host action to viewer response
4. Repeat 5 times, average results

**Pass Criteria**:
- ✅ Average lag < 300ms (typical: 50-150ms on Wi-Fi)
- ✅ No frame stutters
- ✅ No position jumps

**Average Measured Lag**: _______ ms  
**RTT from Ping**: _______ ms

**Notes**: _____________

---

### Test 3.2: Rapid Scrubbing Stability

**Setup**: Paused or playing

**Steps**:
1. Host: Rapidly scrub left-right on timeline
2. Watch Viewer position

**Pass Criteria**:
- ✅ Viewer doesn't jump back & forth
- ✅ Only final seek position is applied
- ✅ Smooth deceleration, no jitter

**Result**: ☐ PASS / ☐ FAIL

**Notes**: _____________

---

### Test 3.3: Minor Timestamp Drift Tolerance

**Setup**: Long playback (5+ min)

**Steps**:
1. Periodically check position difference
2. Log drift every 30 seconds

**Pass Criteria**:
- ✅ Position difference < ±1 second (normal)
- ✅ No repeated jumps (e.g., no 5s forward then 5s back)

**Max Observed Drift**: _______ seconds

**Notes**: _____________

---

## 4. PLAYERENGINE & RESOURCE SAFETY

### Test 4.1: No Memory Leaks (20+ min)

**Setup**: Playing video

**Steps**:
1. Note device memory usage (Settings > About > Memory)
2. Play for 20+ minutes
3. Check memory again
4. Note any significant increase

**Pass Criteria**:
- ✅ Memory increase < 50MB during session
- ✅ No ANR or slow performance over time

**Initial Memory**: _______ MB  
**Final Memory**: _______ MB  
**Increase**: _______ MB

**Notes**: _____________

---

### Test 4.2: Orientation Change Stability

**Setup**: Playback running

**Steps**:
1. Rotate device 90° (portrait → landscape)
2. Watch video continue
3. Rotate back
4. Repeat 3 times

**Pass Criteria**:
- ✅ No crash on rotation
- ✅ Video surface reattaches correctly
- ✅ Playback continues smoothly
- ✅ No sync interruption

**Result**: ☐ PASS / ☐ FAIL

**Notes**: _____________

---

## 5. SERVER & NETWORK TESTS

### Test 5.1: HTTPS Connection Works

**Objective**: Verify secure WebSocket connection

**Setup**:
1. Open browser
2. Navigate to: https://api.contentsync.com/health

**Pass Criteria**:
- ✅ Shows JSON response: `{"status":"ok",...}`
- ✅ No SSL certificate errors
- ✅ Loads within 2 seconds

**Result**: ☐ PASS / ☐ FAIL

**Notes**: _____________

---

### Test 5.2: Socket Connection from Phone

**Setup**: Device connected to internet

**Steps**:
1. Open app
2. Attempt to join room
3. Check connection status in UI

**Pass Criteria**:
- ✅ "Connected" status appears
- ✅ "In Room" after joining
- ✅ Socket event logs show success

**Result**: ☐ PASS / ☐ FAIL

**Notes**: _____________

---

### Test 5.3: Server Only Forwards (No Delays)

**Setup**: Both devices in room

**Steps**:
1. Host: Play, pause, seek (watch timing)
2. Measure: delay = (viewer response time) - (network RTT)

**Pass Criteria**:
- ✅ Delay < 100ms (should be ~10-50ms)
- ✅ No artificial sleep/delay on server

**Measured Delay**: _______ ms

**Notes**: _____________

---

## 6. REAL DEVICE TEST MATRIX

### Network Scenarios

#### Scenario A: Same Wi-Fi (Most Common)
- Device 1 & 2: Connected to home Wi-Fi 5GHz
- Expected: Smoothest sync, RTT < 50ms

**All Tests A.1-A.7**: ☐ PASS / ☐ FAIL

---

#### Scenario B: Different Wi-Fi
- Device 1: Home Wi-Fi 5GHz
- Device 2: Mobile hotspot (4G)
- Expected: Slightly higher latency, RTT 50-150ms

**All Tests B.1-B.7**: ☐ PASS / ☐ FAIL

---

#### Scenario C: Mobile Data
- Both devices: 4G/LTE cellular
- Expected: Higher jitter, RTT 100-300ms

**All Tests C.1-C.7**: ☐ PASS / ☐ FAIL

---

#### Scenario D: Host ↔ Viewer Role Switch
- Device 1 starts as host
- Device 2 starts as viewer
- After 5 min: Switch roles
- Expected: New host takes control, old host becomes viewer

**All Tests D.1-D.7**: ☐ PASS / ☐ FAIL

---

#### Scenario E: Long Session (20 minutes)
- Both devices playing continuously
- Actions: Random play/pause/seek every 1-2 min
- Expected: No desync, no crashes, no memory bloat

**All Tests E.1-E.7**: ☐ PASS / ☐ FAIL

---

## 7. FAILURE RECOVERY TESTS

### Test 7.1: Viewer Disconnect → Reconnect

**Setup**: Both in room, playing

**Steps**:
1. Device 2: Kill app (swipe from recents)
2. Wait 10 seconds
3. Device 2: Reopen app, rejoin room
4. Check playback sync

**Pass Criteria**:
- ✅ Viewer reconnects to room
- ✅ Sync resumes immediately
- ✅ Position catches up to host
- ✅ No crash on either device

**Result**: ☐ PASS / ☐ FAIL

**Time to Reconnect**: _______ seconds

**Notes**: _____________

---

### Test 7.2: Host Disconnect (Grace Period)

**Setup**: Both in room, playing

**Steps**:
1. Device 1 (Host): Go to background (lock phone)
2. Wait < 5 minutes (grace period)
3. Device 1: Bring back to foreground
4. Check if sync resumes

**Pass Criteria**:
- ✅ Viewer shows "Host Disconnected (Reconnecting...)"
- ✅ Viewer pauses playback (to prevent desync)
- ✅ Host returns, connection re-established
- ✅ Viewer resumes sync with host

**Result**: ☐ PASS / ☐ FAIL

**Notes**: _____________

---

### Test 7.3: Network Drop Recovery

**Setup**: Both playing

**Steps**:
1. Enable Airplane Mode on Device 1 for 10 seconds
2. Disable Airplane Mode
3. Watch for reconnection

**Pass Criteria**:
- ✅ Connection reestablishes (auto-reconnect kicks in)
- ✅ No crash
- ✅ Sync resumes

**Time to Reconnect**: _______ seconds

**Notes**: _____________

---

### Test 7.4: Server Restart Handling

**Setup**: Both in room (requires access to server)

**Steps**:
1. Restart Render server
2. Monitor device logs for errors
3. Check if clients attempt reconnect

**Pass Criteria**:
- ✅ Clients show "Disconnected"
- ✅ Auto-reconnect attempts trigger
- ✅ Connection re-established within 10 seconds

**Result**: ☐ PASS / ☐ FAIL

**Notes**: _____________

---

## 8. SUMMARY & GO/NO-GO DECISION

### Results Overview

| Category | Status | Notes |
|----------|--------|-------|
| **Core Functionality** | ☐ PASS / ☐ FAIL | |
| **Gestures & Controls** | ☐ PASS / ☐ FAIL | |
| **Sync Performance** | ☐ PASS / ☐ FAIL | |
| **Resource Safety** | ☐ PASS / ☐ FAIL | |
| **Server & Network** | ☐ PASS / ☐ FAIL | |
| **Failure Recovery** | ☐ PASS / ☐ FAIL | |
| **Network Scenarios** | ☐ PASS / ☐ FAIL | |

---

### Blocking Issues (If Any)

1. _______________
2. _______________
3. _______________

---

### Final Decision

**Date**: _____________  
**Tester**: _____________  
**Device 1**: _____________ (OS version: _____)  
**Device 2**: _____________ (OS version: _____)

### Go/No-Go

- [ ] **✅ GO TO DEPLOY** - All tests pass, no critical issues
- [ ] **❌ NO-GO** - Blocking issues found, need fixes

**Reason** (if no-go): _____________

---

## Sign-Off

**Tester Name**: _________________  
**Signature**: _________________  
**Date**: _________________

---

**Remember**: 
- Document every result (no "assume it works")
- If ANY core test fails → DO NOT DEPLOY
- Real devices reveal issues emulator never shows
- Network conditions matter - test all scenarios
