# Shared Engine & Sync Integration Summary

## Overview

The architecture now uses a **single PlayerEngine instance** shared between UI control (VideoPlayerViewModel) and sync (RoomViewModel). This eliminates duplicate player instances and ensures all playback operations go through one source of truth.

## Architecture

### Single Player Instance Flow

```
RoomScreen
    ├─ VideoPlayerViewModel (UI control & state)
    │   └─ PlayerEngine (OWNER - creates and manages)
    │       └─ LibVLCVideoPlayer
    │           └─ MediaPlayer (LibVLC) ← SINGLE INSTANCE
    │
    └─ RoomViewModel (sync/socket)
        └─ PlaybackSyncController
            └─ PlayerEngine (SHARED REFERENCE - from VideoPlayerViewModel)
                └─ Same LibVLCVideoPlayer instance above
```

**Key Points**:
- **VideoPlayerViewModel** owns and creates the `PlayerEngine` instance
- **RoomViewModel** receives a shared reference via `setSharedPlayerEngine()`
- **PlaybackSyncController** uses the same shared `PlayerEngine` for all sync operations
- **There is exactly ONE MediaPlayer instance** per room session

## Initialization Flow

1. **RoomScreen** creates `VideoPlayerViewModel` and `RoomViewModel`
2. **VideoPlayerViewModel.initPlayer()** is called when `VLCVideoLayout` is created
   - Creates `PlayerEngine` with the layout
   - `PlayerEngine` creates `LibVLCVideoPlayer` which creates `MediaPlayer`
3. **RoomScreen** calls `viewModel.setSharedPlayerEngine(playerViewModel.getPlayerEngine())`
   - RoomViewModel stores the shared reference
   - RoomViewModel creates `PlaybackSyncController` with the shared engine
4. **RoomScreen** wires sync controller back to VideoPlayerViewModel
   - `playerViewModel.setSyncController(viewModel.getSyncController())`
   - This allows VideoPlayerViewModel to notify sync when host performs actions

## Sync Message Flow

### Outgoing (Host → Server)

**Who**: `PlaybackSyncController` observes `PlayerEngine` StateFlows

**When**:
- **Play/Pause**: `PlayerEngine.isPlaying` StateFlow changes → sends `hostPlay`/`hostPause`
- **Position**: Periodic loop (every 1 second) when playing → sends `hostTimeSync`
- **Seek**: `VideoPlayerViewModel.seekTo()` with `fromUser=true` → calls `syncController.sendSeekEvent()` → sends `hostSeek`
- **Speed**: `PlayerEngine.playbackRate` StateFlow changes → sends `hostSpeedChange`

**Code Path**:
```kotlin
// User action in UI
RoomScreen.onSeek() 
  → VideoPlayerViewModel.seekTo(timeMs, fromUser=true, isHost=true)
    → syncController?.sendSeekEvent(timeMs)  // If host
      → PlaybackSyncController.sendSeekEvent()
        → socketManager.hostSeek(roomId, positionSec)
```

### Incoming (Server → Follower)

**Who**: `RoomViewModel` receives socket events, delegates to `PlaybackSyncController`

**When**:
- **HostPlay**: `SocketEvent.HostPlay` → `syncController.applyRemotePlay()` → `LibVLCSyncEngine.handleHostPlay()` → `PlayerEngine.play()`
- **HostPause**: `SocketEvent.HostPause` → `syncController.applyRemotePause()` → `LibVLCSyncEngine.handleHostPause()` → `PlayerEngine.pause()`
- **HostSeek**: `SocketEvent.HostSeek` → `syncController.applyRemoteSeek()` → `LibVLCSyncEngine.handleHostSeek()` → `PlayerEngine.seekTo()`
- **HostSpeedChange**: `SocketEvent.HostSpeedChange` → `syncController.applyRemoteRate()` → `PlayerEngine.setRate()`
- **HostTimeSync**: `SocketEvent.HostTimeSync` → `syncController.applyTimeSync()` → `LibVLCSyncEngine.handleTimeSync()` → position adjustment

**Code Path**:
```kotlin
// Socket event received
RoomViewModel.handleSocketEvent(SocketEvent.HostSeek)
  → syncController?.applyRemoteSeek(positionSec, timestamp)
    → LibVLCSyncEngine.handleHostSeek()
      → PlayerEngine.seekTo(positionMs)  // Same instance used by UI
```

## Conflict Handling

### User Scrubbing vs Incoming Seeks

**Problem**: When user drags the seek bar, incoming sync seeks should be temporarily ignored to avoid conflicts.

**Solution**:
1. **User starts scrubbing**: `VideoPlayerViewModel.onUserScrubbingStart()` → `PlaybackSyncController.onUserScrubbingStart()`
   - Sets `isUserScrubbing = true`
2. **During scrub**: Incoming `applyRemoteSeek()` and `applyTimeSync()` are blocked
3. **User finishes scrubbing**: `VideoPlayerViewModel.onUserScrubbingEnd()` → `PlaybackSyncController.onUserScrubbingEnd()`
   - Sets `isUserScrubbing = false`
   - Sets `scrubbingEndTime = now`
   - Incoming seeks remain blocked for 2 seconds after scrub ends

**Code**:
```kotlin
// PlaybackSyncController.kt
fun applyRemoteSeek(positionSec: Double, hostTimestampMs: Long) {
    val now = System.currentTimeMillis()
    if (isUserScrubbing || (now - scrubbingEndTime) < SCRUBBING_BLOCK_WINDOW_MS) {
        Timber.d("Sync: Ignored applyRemoteSeek - user is scrubbing")
        return
    }
    // Apply seek...
}
```

### Host vs Non-Host Permissions

**Rules**:
- **If `isHost == false`**: 
  - Never send outgoing play/pause/seek events
  - Only apply incoming commands from host
  - Local user actions are blocked (controls disabled)
- **If `isHost == true`**:
  - Outgoing actions are sent to server
  - Incoming actions may be ignored if they conflict (e.g., during local scrubbing)

**Code**:
```kotlin
// VideoPlayerViewModel.kt
fun seekTo(timeMs: Long, fromUser: Boolean = true, isHost: Boolean = true) {
    if (!canControlPlayback(isHost)) return  // Blocked if not host or locked
    
    // If user-initiated seek from host, notify sync
    if (fromUser && isHost) {
        syncController?.sendSeekEvent(timeMs)
    }
    
    playerEngine?.seekTo(timeMs, fast)
}
```

## Key Components

### 1. PlaybackSyncController
**Location**: `android/app/src/main/java/com/withyou/app/sync/PlaybackSyncController.kt`

**Responsibilities**:
- Observes `PlayerEngine` StateFlows for outgoing sync events
- Applies incoming sync commands to `PlayerEngine`
- Handles conflict detection (user scrubbing)
- Manages sync state (host vs follower)

**Key Methods**:
- `initialize(socketManager, roomId, isHost)` - Setup sync
- `sendSeekEvent(positionMs)` - Send seek event (host only)
- `applyRemotePlay(positionSec, timestamp, rate)` - Apply play command
- `applyRemotePause(positionSec, timestamp)` - Apply pause command
- `applyRemoteSeek(positionSec, timestamp)` - Apply seek command
- `applyRemoteRate(rate)` - Apply speed change
- `applyTimeSync(positionSec, timestamp)` - Continuous position sync
- `onUserScrubbingStart()` / `onUserScrubbingEnd()` - Conflict handling

### 2. LibVLCSyncEngine
**Location**: `android/app/src/main/java/com/withyou/app/sync/LibVLCSyncEngine.kt`

**Responsibilities**:
- Implements sync algorithm (nudge vs hard seek)
- Handles position compensation for network delay
- Manages playback rate adjustments for nudging

**Key Methods**:
- `handleHostPlay()` - Sync play with position
- `handleHostPause()` - Sync pause with position
- `handleHostSeek()` - Apply seek
- `handleTimeSync()` - Continuous position sync

### 3. RoomViewModel
**Location**: `android/app/src/main/java/com/withyou/app/viewmodel/RoomViewModel.kt`

**Responsibilities**:
- Socket communication
- Room state management
- Delegates sync operations to `PlaybackSyncController`

**Key Methods**:
- `setSharedPlayerEngine(playerEngine)` - Receive shared engine from VideoPlayerViewModel
- `getSyncController()` - Expose sync controller for wiring
- `handleSocketEvent()` - Route socket events to sync controller

### 4. VideoPlayerViewModel
**Location**: `android/app/src/main/java/com/withyou/app/viewmodel/VideoPlayerViewModel.kt`

**Responsibilities**:
- Owns and creates `PlayerEngine`
- Manages UI state (`PlayerUiState`)
- Notifies sync controller when host performs actions

**Key Methods**:
- `initPlayer(videoLayout)` - Creates PlayerEngine
- `getPlayerEngine()` - Expose engine for sharing
- `setSyncController(controller)` - Wire sync controller
- `seekTo(timeMs, fromUser)` - Seek with sync notification
- `onUserScrubbingStart()` / `onUserScrubbingEnd()` - Conflict handling

## Sync Logging

All sync operations are logged with the prefix "Sync:":

```kotlin
Timber.i("Sync: Host playing - sending hostPlay: position=$positionSec, rate=$playbackRate")
Timber.i("Sync: applyRemotePlay(positionSec=$positionSec, rate=$playbackRate)")
Timber.d("Sync: Ignored applyRemoteSeek - user is scrubbing")
Timber.v("Sync: applyTimeSync(positionSec=$positionSec)")
```

**Usage**: Filter logs with `grep "Sync:"` to see all sync operations.

## Verification Checklist

- [x] Only ONE PlayerEngine instance exists (created by VideoPlayerViewModel)
- [x] RoomViewModel uses shared reference (no duplicate player)
- [x] PlaybackSyncController uses shared PlayerEngine
- [x] All outgoing sync events read from PlayerEngine StateFlows
- [x] All incoming sync events apply to PlayerEngine
- [x] User scrubbing blocks incoming seeks (2 second window)
- [x] Host actions notify sync controller
- [x] Non-host users cannot send sync events
- [x] Sync controller is wired to VideoPlayerViewModel
- [x] All sync operations are logged

## Files Updated

1. **RoomViewModel.kt**
   - ✅ Removed second LibVLCVideoPlayer instance
   - ✅ Uses `sharedPlayerEngine` from VideoPlayerViewModel
   - ✅ Creates `PlaybackSyncController` with shared engine
   - ✅ Routes socket events to sync controller
   - ✅ Exposes `getSyncController()` for wiring

2. **VideoPlayerViewModel.kt**
   - ✅ Owns and creates `PlayerEngine`
   - ✅ Exposes `getPlayerEngine()` for sharing
   - ✅ Accepts `setSyncController()` for sync notification
   - ✅ Notifies sync controller when host seeks (`fromUser=true`)
   - ✅ Provides scrubbing detection methods

3. **PlaybackSyncController.kt**
   - ✅ Uses shared `PlayerEngine` (no duplicate player)
   - ✅ Observes PlayerEngine StateFlows for outgoing events
   - ✅ Applies incoming commands to PlayerEngine
   - ✅ Handles conflict detection (user scrubbing)

4. **RoomScreen.kt**
   - ✅ Wires shared engine: `viewModel.setSharedPlayerEngine(playerViewModel.getPlayerEngine())`
   - ✅ Wires sync controller: `playerViewModel.setSyncController(viewModel.getSyncController())`
   - ✅ Calls scrubbing detection: `playerViewModel.onUserScrubbingStart/End()`

5. **LibVLCSyncEngine.kt**
   - ✅ Uses `PlayerEngine` instead of `LibVLCVideoPlayer` directly
   - ✅ All sync operations go through PlayerEngine

## Summary

✅ **Single Player Instance**: Only one `PlayerEngine` (and thus one `MediaPlayer`) exists per room session.

✅ **Shared Architecture**: VideoPlayerViewModel owns the engine, RoomViewModel uses it via reference.

✅ **Clean Sync Interface**: PlaybackSyncController decouples sync logic from ViewModels.

✅ **Conflict Handling**: User scrubbing temporarily blocks incoming sync seeks.

✅ **Permission-Based**: Non-host users cannot send sync events, only receive them.

✅ **Well-Logged**: All sync operations are logged with "Sync:" prefix for easy debugging.

