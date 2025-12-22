# 🚀 ContentSync Deployment Checklist

**Last Updated:** December 22, 2025  
**Target:** Production Release  
**Status:** IN PROGRESS

---

## 🔴 1. CORE FUNCTIONALITY (MUST PASS)

| Item | Status | Notes |
|------|--------|-------|
| App launches without crash (release build) | ⬜ | |
| Host video play → Viewer sync | ⬜ | |
| Host pause → Viewer instant pause | ⬜ | |
| Host seek → Viewer position change | ⬜ | |
| Playback speed change → Viewer speed change | ⬜ | |
| 10+ min playback without desync | ⬜ | |
| App background → foreground (stable/reconnect) | ⬜ | |

**Blocker:** If ANY fail ❌ → DO NOT DEPLOY

---

## 🟠 2. GESTURES & CONTROLS (UX CRITICAL)

| Item | Status | Notes |
|------|--------|-------|
| Center double tap → play/pause | ⬜ | |
| Left double tap → seek -10/20s | ⬜ | |
| Right double tap → seek forward | ⬜ | |
| Single tap → controls show/hide | ⬜ | |
| Gestures work while playing | ⬜ | |
| Gestures work while paused/stopped | ⬜ | |
| VLC controls at true bottom (no float) | ⬜ | |
| Lock mode disables gestures | ⬜ | |
| Non-host cannot control | ⬜ | |

---

## 🟡 3. SYNC PERFORMANCE (REAL-TIME FEEL)

| Item | Status | Notes |
|------|--------|-------|
| Viewer lag < ~300ms | ⬜ | Normal network |
| Rapid scrubbing doesn't break playback | ⬜ | |
| Minor timestamp diff ignored (no jitter) | ⬜ | |
| Host seek sends final position (no spam) | ⬜ | |
| Viewer doesn't jump back & forth | ⬜ | |
| Scrubbing conflict protection | ⬜ | |

**Acceptable:** Slight ms delay OK. Jerky jumps ❌

---

## 🟢 4. PLAYERENGINE & RESOURCE SAFETY

| Item | Status | Notes |
|------|--------|-------|
| ONE PlayerEngine instance per room | ⬜ | |
| ONE LibVLC MediaPlayer instance | ⬜ | |
| PlayerEngine.release() on ViewModel clear | ⬜ | |
| Polling stops when screen gone | ⬜ | |
| No background coroutine leaks | ⬜ | |
| No crash on orientation change | ⬜ | |

---

## 🔵 5. SERVER (RENDER) CHECKLIST

| Item | Status | Notes |
|------|--------|-------|
| Server deployed as Web Service | ⬜ | Not worker |
| Uses process.env.PORT | ⬜ | |
| HTTPS URL works in browser | ⬜ | |
| Socket connection succeeds from phone | ⬜ | |
| Server only forwards events (no delays) | ⬜ | |
| No artificial sleep/timeout | ⬜ | |
| Cold start handled | ⬜ | |

---

## 🟣 6. ANDROID RELEASE CONFIG

| Item | Status | Notes |
|------|--------|-------|
| Release build selected | ⬜ | |
| APK installs without issue | ⬜ | |
| INTERNET permission present | ⬜ | |
| No debug-only crashes | ⬜ | |
| Excessive logs removed/gated | ⬜ | |
| Keystore created & safe | ⬜ | |

---

## 🕵️ 7. REAL DEVICE TEST (MOST IMPORTANT)

### Test Device 1
- **Device:** _____________
- **OS Version:** _____________

| Scenario | Status | Notes |
|----------|--------|-------|
| Same Wi-Fi | ⬜ | |
| Different Wi-Fi | ⬜ | |
| Mobile data | ⬜ | |
| Host ↔ Viewer switching | ⬜ | |
| Long session (15–20 min) | ⬜ | |

### Test Device 2
- **Device:** _____________
- **OS Version:** _____________

| Scenario | Status | Notes |
|----------|--------|-------|
| Same Wi-Fi | ⬜ | |
| Different Wi-Fi | ⬜ | |
| Mobile data | ⬜ | |
| Host ↔ Viewer switching | ⬜ | |
| Long session (15–20 min) | ⬜ | |

**⚠️ Emulator testing ≠ deployment testing**

---

## 🛡️ 8. FAILURE RECOVERY (STABILITY)

| Item | Status | Notes |
|------|--------|-------|
| Viewer disconnect → reconnect works | ⬜ | |
| Host disconnect → viewer handles gracefully | ⬜ | |
| Network drop → app doesn't crash | ⬜ | |
| Server restart → client reconnects/shows state | ⬜ | |

---

## ✅ 9. DEPLOYMENT DECISION

### Pre-Deployment Verification

- [ ] Core functionality 100% working
- [ ] Gestures stable & responsive
- [ ] Sync performance acceptable (<300ms)
- [ ] Zero crashes in testing
- [ ] Server reachable & stable

### Go/No-Go Decision

| Decision | Date | Notes |
|----------|------|-------|
| **READY TO DEPLOY** | ⬜ | All checks green |
| **NEEDS MORE WORK** | ⬜ | List blockers below |

### Blocking Issues (if any)
1. _____
2. _____
3. _____

---

## 📋 Testing Log

```
Date: ___________
Tester: ________
Notes:
- 
- 
```

---

## 🔗 Reference Links

- [DEPLOYMENT.md](docs/DEPLOYMENT.md)
- [DEVELOPMENT.md](docs/DEVELOPMENT.md)
- [FINAL_INTEGRATION_SUMMARY.md](FINAL_INTEGRATION_SUMMARY.md)

---

**Status Summary:**
- ⬜ = Not Started
- 🟨 = In Progress
- ✅ = Complete
- ❌ = Failed
