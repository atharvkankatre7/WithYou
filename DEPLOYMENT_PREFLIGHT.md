# 🚀 DEPLOYMENT PRE-FLIGHT CHECKLIST

**Release Date Target**: December 22, 2025  
**Status**: PRE-RELEASE VALIDATION IN PROGRESS

---

## ✅ CODE QUALITY CHECKS

- [x] **Timber Logging Gated** (Release tree implemented)
  - Debug: Full logs
  - Release: Warnings + Errors only
  - File: `ContentSyncApplication.kt`

- [x] **ProGuard Rules Enhanced**
  - LibVLC classes preserved
  - All ViewModels, sync, player classes kept
  - Enum obfuscation prevented
  - File: `proguard-rules.pro`

- [x] **Host Disconnect Handling**
  - Viewer pauses on host disconnect
  - Grace period for reconnection (5 min)
  - Auto-resume on host reconnection
  - File: `RoomViewModel.kt`

- [x] **RTT Measurement Added**
  - Ping/pong every 5 seconds
  - Displayed in UI
  - Tracked for sync controller
  - Files: `RoomViewModel.kt`, `SocketManager.kt`, `PlaybackSyncController.kt`

- [x] **Lifecycle Observer Verified**
  - Background/foreground handling
  - Polling suspend/resume
  - Clean resource cleanup
  - File: `RoomViewModel.kt`

---

## 📦 BUILD CONFIGURATION

### Android Release Build

```bash
# Generate release APK
./gradlew assembleRelease

# Output: android/app/build/outputs/apk/release/app-release.apk
```

**Checklist**:
- [ ] Release build succeeds without warnings
- [ ] APK size is reasonable (< 100MB)
- [ ] Signed with keystore
- [ ] ProGuard enabled (minifyEnabled = true)
- [ ] Keystore backed up and secured

### Release Build Config
- [x] SOCKET_URL: `wss://api.contentsync.com`
- [x] SERVER_URL: `https://api.contentsync.com`
- [x] CORS_ORIGIN: `https://app.contentsync.com`

---

## 🌐 SERVER DEPLOYMENT

### Render.com Configuration
- [x] Service Type: **Web** (not Worker)
- [x] Node Environment: 18.x or higher
- [x] Health Check: `/health` endpoint
- [x] PORT: Uses `process.env.PORT`
- [x] Hot Start: < 3 seconds for first connection

**Server Files**:
- [x] `server/src/index.js` - Main entry point
- [x] `server/src/socket/handlers.js` - Event handling
- [x] `server/render.yaml` - Deployment config

**Environment Variables** (set on Render):
- [ ] `NODE_ENV=production`
- [ ] `FIREBASE_PROJECT_ID=<your-project>`
- [ ] `FIREBASE_CLIENT_EMAIL=<service-account-email>`
- [ ] `FIREBASE_PRIVATE_KEY=<service-account-key>`
- [ ] `CORS_ORIGIN=https://app.contentsync.com`
- [ ] `LOG_LEVEL=info` (reduce verbose logging in production)

---

## 📱 ANDROID RELEASE CHECKLIST

### Manifest & Permissions
- [x] INTERNET permission present
- [x] FOREGROUND_SERVICE permission present
- [x] Activity configChanges include orientation|screenSize
- [x] No debug-only features in release build

### Resource Safety
- [x] PlayerEngine.release() in ViewModel.onCleared()
- [x] Coroutines properly cancelled
- [x] No background job leaks
- [x] Polling stops on screen gone

### Networking
- [x] Socket.IO reconnection: automatic (Int.MAX_VALUE attempts)
- [x] Timeout: 20 seconds
- [x] Transports: websocket + polling fallback
- [x] Certificate pinning: Not required (trusted CA)

---

## 🧪 TESTING REQUIREMENTS

### Real Device Testing (MUST DO)
Must test on **2+ physical Android phones** on different networks:

- [ ] **Device 1**: _____________ (Android ___._)
- [ ] **Device 2**: _____________ (Android ___._)

### Test Scenarios (All MUST PASS)

#### 1. Core Functionality
- [ ] App launches without crash
- [ ] Host play → Viewer syncs
- [ ] Host pause → Viewer pauses
- [ ] Host seek → Viewer position updates
- [ ] Speed change syncs
- [ ] 15+ min session without desync
- [ ] Background/foreground doesn't break sync

#### 2. Gestures
- [ ] Center double-tap → play/pause (host only)
- [ ] Left double-tap → seek backward
- [ ] Right double-tap → seek forward
- [ ] Single tap → show/hide controls
- [ ] Lock mode disables gestures
- [ ] All work while playing AND paused

#### 3. Sync Quality
- [ ] Viewer lag < 300ms
- [ ] Rapid scrubbing → smooth sync
- [ ] Minor drift ignored (< 1 second)
- [ ] Only final seek sent (debounced)

#### 4. Network Scenarios
- [ ] **Same Wi-Fi**: Both devices, same network (RTT ~20-50ms)
- [ ] **Different Wi-Fi**: Host on 5G, Viewer on hotspot (RTT ~50-150ms)
- [ ] **Mobile Data**: Both on 4G/LTE (RTT ~100-300ms)
- [ ] **Host ↔ Viewer Switch**: Toggle roles, verify new host takes control
- [ ] **Long Session**: 20 min continuous, random actions every 1-2 min

#### 5. Failure Recovery
- [ ] Viewer disconnect → reconnect works
- [ ] Host disconnect → Viewer pauses, resumes on reconnect
- [ ] Network drop → Auto-reconnect (< 10 seconds)
- [ ] Server restart → Clients reconnect

### Test Documentation
- [ ] Complete [TEST_SUITE.md](TEST_SUITE.md) with results
- [ ] Document any issues found
- [ ] Sign off on pass/fail decision

---

## 🔒 SECURITY CHECKLIST

- [x] Firebase Auth tokens validated on server
- [x] No hardcoded secrets in code
- [x] API routes rate-limited
- [x] CORS configured correctly
- [ ] Keystore secured and backed up (not in repo)
- [ ] Firebase service account not committed to Git

---

## 📊 PERFORMANCE TARGETS

### Acceptable Metrics
| Metric | Target | Measured |
|--------|--------|----------|
| App Launch | < 3s | _______ |
| Host → Viewer Lag | < 300ms | _______ |
| RTT (Wi-Fi) | < 50ms | _______ |
| RTT (4G) | < 300ms | _______ |
| Memory Leak (20 min) | < 50MB | _______ |
| Battery Impact | Minimal | _______ |

---

## 🚨 BLOCKER ISSUES

Check these before deployment:

- [ ] ✅ No crashes on release build
- [ ] ✅ No excessive logging (release tree active)
- [ ] ✅ Core sync works on real devices
- [ ] ✅ Viewer lag acceptable (< 300ms typical)
- [ ] ✅ Network recovery works
- [ ] ✅ No memory leaks detected
- [ ] ✅ Server reachable from phone

---

## 📋 DEPLOYMENT STEPS

### 1. Final Code Review
```bash
# Check for any remaining debug code
grep -r "TODO\|FIXME\|DEBUG" android/app/src/main
grep -r "console.log" server/src
```

### 2. Build & Sign Release APK
```bash
# Clean build
./gradlew clean

# Generate release APK
./gradlew assembleRelease

# Output location:
# android/app/build/outputs/apk/release/app-release.apk
```

### 3. Verify Server is Ready
```bash
# Check server health
curl https://api.contentsync.com/health

# Expected response:
# {"status":"ok","timestamp":"...","uptime":...}
```

### 4. Deploy APK
- [ ] Upload to Google Play Store (internal testing track first)
- [ ] Or: Distribute via Firebase App Distribution
- [ ] Or: Share APK directly to testers

### 5. Run Full Test Suite
- [ ] Complete [TEST_SUITE.md](TEST_SUITE.md)
- [ ] Document results
- [ ] Get sign-off from tester

### 6. Post-Deployment Monitoring
- [ ] Monitor server logs for errors
- [ ] Check for user-reported crashes (Firebase Crashlytics)
- [ ] Monitor sync performance metrics
- [ ] Be ready for rollback if critical issues found

---

## 🎯 GO/NO-GO DECISION

### Pre-Deployment Sign-Off

**Completed By**: _______________________  
**Date**: _______________________  
**Sign-Off**: _______________________

### Decision

**Choose One**:

- [ ] ✅ **GO TO PRODUCTION** 
  - All tests pass
  - No blockers
  - Release APK ready
  - Server verified

- [ ] ❌ **HOLD - NEEDS FIXES**
  - Blockers: _______________________
  - Estimated fix time: _______________________
  - Re-test date: _______________________

---

## 📞 SUPPORT & ROLLBACK

### If Issues Found Post-Deployment

1. **Disable new users** (pause public link sharing)
2. **Provide workaround** (if possible)
3. **Rollback APK** (pull from store)
4. **Fix on server** (live patching for socket handlers)
5. **Test in staging first** before re-deploy

### Rollback Procedure
```bash
# Revert to previous working commit
git revert <broken-commit-hash>

# Rebuild and deploy
./gradlew clean assembleRelease
# Upload to Play Store or distribute new APK
```

---

## ✨ NEXT MILESTONES

After Deployment v1.0:

- [ ] User feedback collection (1-2 weeks)
- [ ] Performance optimization if needed
- [ ] Additional features (audio track switching, subtitles)
- [ ] iOS version (if applicable)
- [ ] Scaling to production server tier

---

**Last Updated**: December 22, 2025  
**Maintainer**: Your Team  
**Contact**: support@contentsync.com
