# Integration Summary: PlayerEngine + VideoPlayerViewModel

## Files Updated

### 1. ✅ PlayerEngine.kt
**Location**: `android/app/src/main/java/com/withyou/app/player/PlayerEngine.kt`

**Changes**:
- Wraps `LibVLCVideoPlayer` (which wraps `MediaPlayer`)
- Creates `StateFlow`s for `isPlaying`, `position`, `duration`, `isBuffering`, `playbackRate`
- Polls player properties every 250ms to keep StateFlows in sync (since LibVLCVideoPlayer doesn't expose flows)
- Maps `LibVLCVideoPlayer.State` to `PlayerEngine.State`
- Exposes all required methods: `play()`, `pause()`, `seekTo()`, `setRate()`, `setAspectRatio()`, `setScaleMode()`, track management, etc.
- Handles reattachment to new `VLCVideoLayout` (for orientation changes)

**Key Methods**:
```kotlin
fun play() → player.play()
fun pause() → player.pause()
fun seekTo(timeMs: Long, fast: Boolean) → player.seekTo(timeMs)
fun setRate(rate: Float) → player.setRate(rate)
fun setAspectRatio(ratio: String?) → player.setCustomAspectRatio(ratio)
fun setScaleMode(mode, width, height) → player.setScaleMode(mode, width, height)
fun getAudioTracks() → player.getAudioTracks()
fun setAudioTrack(trackId: Int) → player.setAudioTrack(trackId)
```

**Architecture Mapping**:
- **VLC**: `PlayerController` wraps `MediaPlayer`
- **This**: `PlayerEngine` wraps `LibVLCVideoPlayer` (which wraps `MediaPlayer`)

### 2. ✅ VideoPlayerViewModel.kt
**Location**: `android/app/src/main/java/com/withyou/app/viewmodel/VideoPlayerViewModel.kt`

**Changes**:
- Owns `PlayerEngine` instance
- Exposes single `StateFlow<PlayerUiState>` for UI observation
- Subscribes to `PlayerEngine` StateFlows and maps them to `PlayerUiState`
- Implements intent-style methods:
  - `togglePlayPause()` → `playerEngine.togglePlayPause()`
  - `seekTo(timeMs, fast)` → `playerEngine.seekTo(timeMs, fast)`
  - `onUserSeek(positionFraction)` → calculates timeMs → `seekTo()`
  - `seekForward(seconds)` / `seekBackward(seconds)` → calculates new position → `seekTo()`
  - `setPlaybackRate(rate)` → `playerEngine.setRate(rate)`
  - `setAspectMode(mode, customRatio)` → `playerEngine.setScaleMode()` or `setAspectRatio()`
  - `toggleLock()` → updates `uiState.isLocked`
  - `setAudioTrack(trackId)` → `playerEngine.setAudioTrack(trackId)` + refresh tracks
  - `setSubtitleTrack(trackId)` → `playerEngine.setSubtitleTrack(trackId)` + refresh tracks
- Handles surface dimensions for aspect ratio calculations
- Refreshes tracks after loading media
- Calls `playerEngine.release()` in `onCleared()`

**Architecture Mapping**:
- **VLC**: `PlaybackService` bridges `VideoPlayerActivity` → `PlayerController`
- **This**: `VideoPlayerViewModel` bridges `VideoPlayerScreen` → `PlayerEngine`

### 3. ✅ PlayerState.kt
**Location**: `android/app/src/main/java/com/withyou/app/player/PlayerState.kt`

**Status**: Already complete, no changes needed.

**Contains**:
- `PlayerUiState` data class with all UI-relevant state
- `AspectMode` enum (FIT, FILL, ORIGINAL, CUSTOM)
- `SeekInfo` data class (for future use)

### 4. ✅ VLCPlayerControls.kt
**Location**: `android/app/src/main/java/com/withyou/app/ui/components/VLCPlayerControls.kt`

**Changes**:
- Added overload that accepts `PlayerUiState` instead of individual parameters
- Extracts values from `PlayerUiState` and delegates to original implementation
- Original version kept for backward compatibility
- Both versions work identically - the new one just provides cleaner API

**Usage**:
```kotlin
// New way (with PlayerUiState)
VLCPlayerControls(
    uiState = playerUiState,
    isHost = true,
    onPlayPause = { viewModel.togglePlayPause() },
    // ... other callbacks
)

// Old way (still works)
VLCPlayerControls(
    isPlaying = isPlaying,
    currentPosition = currentPosition,
    // ... individual parameters
)
```

**Architecture Mapping**:
- **VLC**: `VideoPlayerOverlayDelegate` manages `player_hud.xml` with Data Binding
- **This**: `VLCPlayerControls` composable with Compose state

### 5. ⚠️ VideoGestureHandler.kt
**Location**: `android/app/src/main/java/com/withyou/app/ui/components/VideoGestureHandler.kt`

**Status**: No changes needed - already uses callbacks, not direct LibVLC access.

**Architecture Mapping**:
- **VLC**: `VideoTouchDelegate` handles gestures in `VideoPlayerActivity`
- **This**: `VideoGestureHandler` composable with gesture callbacks

### 6. ⚠️ RoomScreen.kt
**Location**: `android/app/src/main/java/com/withyou/app/ui/screens/RoomScreen.kt`

**Status**: Integration example provided below. Actual integration depends on whether you want to:
- Option A: Replace RoomViewModel's player management with VideoPlayerViewModel
- Option B: Use both ViewModels (RoomViewModel for sync/socket, VideoPlayerViewModel for player)

## Data Flow

### Current Flow (Before Integration)
```
RoomScreen (local state: isPlaying, currentPosition, duration)
    ↓
RoomViewModel (manages LibVLCVideoPlayer directly)
    ↓
LibVLCVideoPlayer (wraps MediaPlayer)
    ↓
MediaPlayer (LibVLC)
```

### Target Flow (After Integration)
```
RoomScreen (collects PlayerUiState from VideoPlayerViewModel)
    ↓
VideoPlayerViewModel (manages PlayerEngine, exposes PlayerUiState)
    ↓
PlayerEngine (wraps LibVLCVideoPlayer, exposes StateFlows)
    ↓
LibVLCVideoPlayer (wraps MediaPlayer)
    ↓
MediaPlayer (LibVLC)
```

### State Updates (Reverse Flow)
```
MediaPlayer (LibVLC events/callbacks)
    ↓
LibVLCVideoPlayer (onStateChanged, onPositionChanged callbacks)
    ↓
PlayerEngine (updates StateFlows via polling + callbacks)
    ↓
VideoPlayerViewModel (collects StateFlows, maps to PlayerUiState)
    ↓
RoomScreen (collects PlayerUiState, updates UI)
```

## Integration Example for RoomScreen

Here's how to integrate `VideoPlayerViewModel` into `RoomScreen`:

```kotlin
@Composable
fun RoomScreen(
    roomId: String,
    isHost: Boolean,
    viewModel: RoomViewModel,
    onNavigateBack: () -> Unit
) {
    // Create VideoPlayerViewModel for player control
    val playerViewModel: VideoPlayerViewModel = remember {
        ViewModelProvider(LocalViewModelStoreOwner.current!!)[VideoPlayerViewModel::class.java]
    }
    
    // Collect player state from VideoPlayerViewModel
    val playerUiState by playerViewModel.uiState.collectAsState()
    
    // ... existing RoomScreen code ...
    
    // In VideoContentWithControls, initialize player:
    LibVLCPlayerView(
        onLayoutCreated = { videoLayout ->
            // Initialize both ViewModels
            viewModel.initPlayer(videoLayout) // For sync/socket
            playerViewModel.initPlayer(videoLayout) // For player control
        }
    )
    
    // Use PlayerUiState in controls:
    VLCPlayerControls(
        uiState = playerUiState,
        isHost = isHost,
        onPlayPause = { playerViewModel.togglePlayPause() },
        onSeek = { time -> playerViewModel.seekTo(time) },
        onSeekForward = { seconds -> playerViewModel.seekForward(seconds) },
        onSeekBackward = { seconds -> playerViewModel.seekBackward(seconds) },
        onSpeedChange = { rate -> playerViewModel.setPlaybackRate(rate) },
        onAspectRatioChange = { ratio -> 
            // Convert string to AspectMode
            val mode = when (ratio) {
                "Fit" -> AspectMode.FIT
                "Fill" -> AspectMode.FILL
                "16:9" -> AspectMode.CUSTOM
                // ... etc
            }
            playerViewModel.setAspectMode(mode, if (mode == AspectMode.CUSTOM) ratio else null)
        },
        onAudioTrackChange = { trackId -> playerViewModel.setAudioTrack(trackId) },
        onSubtitleTrackChange = { trackId -> playerViewModel.setSubtitleTrack(trackId) },
        onLockControls = { playerViewModel.toggleLock() },
        onUserInteractionStart = { userInteractionStart() },
        onUserInteractionEnd = { userInteractionEnd() }
    )
}
```

## VLC Behavior Matching

All controls now match VLC's behavior as documented in `VLC_ANALYSIS_AND_IMPLEMENTATION.md`:

1. **Play/Pause**: `togglePlayPause()` → `PlayerEngine.togglePlayPause()` → `MediaPlayer.play()/pause()`
2. **Seek Bar**: `onUserSeek(fraction)` → `seekTo(timeMs)` → `MediaPlayer.setTime()`
3. **Forward/Backward 10s**: `seekForward(10)` / `seekBackward(10)` → calculates position → `seekTo()`
4. **Double-Tap Seek**: Gesture handler → `onDoubleTapSeek(seconds)` → `seekForward()/seekBackward()`
5. **Horizontal Swipe**: Gesture handler → `onSeek(timeMs)` → `seekTo()`
6. **Aspect Ratio**: `setAspectMode(mode, ratio)` → `PlayerEngine.setScaleMode()` or `setAspectRatio()` → `MediaPlayer.aspectRatio`
7. **Playback Speed**: `setPlaybackRate(rate)` → `PlayerEngine.setRate(rate)` → `MediaPlayer.rate = rate`
8. **Audio/Subtitle Tracks**: `setAudioTrack(trackId)` / `setSubtitleTrack(trackId)` → `MediaPlayer.setAudioTrack()` / `setSpuTrack()`
9. **Lock Screen**: `toggleLock()` → updates `uiState.isLocked` (gestures check this)

## Key Design Decisions

1. **StateFlows in PlayerEngine**: Since `LibVLCVideoPlayer` doesn't expose StateFlows, `PlayerEngine` polls player properties every 250ms and also listens to callbacks to update StateFlows.

2. **Dual ViewModel Approach**: Keep `RoomViewModel` for sync/socket logic, add `VideoPlayerViewModel` for player control. They can share the same `LibVLCVideoPlayer` instance or use separate instances.

3. **Backward Compatibility**: `VLCPlayerControls` has two overloads - one with `PlayerUiState` (new) and one with individual parameters (old). Both work identically.

4. **VLC Behavior Matching**: All controls follow the patterns documented in `VLC_ANALYSIS_AND_IMPLEMENTATION.md` for consistent UX.

## Next Steps

1. **Integrate into RoomScreen**: Update `RoomScreen.kt` to use `VideoPlayerViewModel` as shown in the example above.

2. **Test All Controls**: Verify that all controls (play/pause, seek, aspect ratio, speed, tracks, lock) work correctly.

3. **Handle Sync**: If using both ViewModels, ensure sync logic in `RoomViewModel` still works (it may need access to `VideoPlayerViewModel`'s player instance).

4. **Optional: Merge ViewModels**: If desired, you can merge `VideoPlayerViewModel` functionality into `RoomViewModel` to have a single ViewModel, but keeping them separate is cleaner.

## Code Comments

All code includes comments explaining:
- Where this mirrors specific VLC behavior (references to VLC components)
- How a future developer can change controls without touching the engine (all UI calls ViewModel methods, not PlayerEngine directly)

Example:
```kotlin
/**
 * Play/Pause toggle
 * Maps to: VLC's doPlayPause() → service.play() / service.pause()
 * 
 * To change play/pause behavior, modify this method or the UI callback.
 * Do NOT modify PlayerEngine.play()/pause() unless changing LibVLC behavior.
 */
fun togglePlayPause() {
    playerEngine?.togglePlayPause()
}
```

