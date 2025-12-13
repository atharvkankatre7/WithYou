# Integration Plan: PlayerEngine + VideoPlayerViewModel

## Current Architecture Analysis

### Files That Render Video
- **`LibVLCPlayerView.kt`**: Compose wrapper that creates `VLCVideoLayout`
- **`VideoContentWithControls.kt`**: Main composable that contains video + controls + gestures
- **`RoomScreen.kt`**: Main screen that orchestrates everything

### Files That Handle Gestures
- **`VideoGestureHandler.kt`**: Handles all touch gestures (taps, double-taps, drags)
  - Single tap → toggle controls
  - Double tap left/right → seek ±10s
  - Double tap center → play/pause
  - Horizontal drag → seek
  - Vertical drag left → brightness
  - Vertical drag right → volume

### Files That Directly Control LibVLC
- **`RoomViewModel.kt`**: Currently directly manages `LibVLCVideoPlayer` instance
  - Methods: `togglePlayPause()`, `seekTo()`, `setScaleMode()`, `setAudioTrack()`, `setSubtitleTrack()`
- **`LibVLCVideoPlayer.kt`**: Direct LibVLC wrapper
  - Exposes: `play()`, `pause()`, `seekTo()`, `setRate()`, etc.
  - Uses callbacks: `onStateChanged`, `onPositionChanged`

### Current Data Flow
```
RoomScreen (UI state: isPlaying, currentPosition, duration)
    ↓
RoomViewModel (manages LibVLCVideoPlayer directly)
    ↓
LibVLCVideoPlayer (wraps MediaPlayer)
    ↓
MediaPlayer (LibVLC)
```

## Target Architecture

### New Data Flow
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

## Integration Steps

### 1. Update PlayerEngine.kt
- Wrap `LibVLCVideoPlayer` (already done)
- Create StateFlows that poll `LibVLCVideoPlayer` properties (since it doesn't expose flows)
- Map LibVLCVideoPlayer.State to PlayerEngine.State
- Expose all required methods

### 2. Update VideoPlayerViewModel.kt
- Own `PlayerEngine` instance
- Subscribe to PlayerEngine StateFlows
- Map to `PlayerUiState`
- Expose intent-style methods
- Handle surface dimensions for aspect ratio

### 3. Integrate with RoomViewModel
- Option A: Replace RoomViewModel's direct LibVLCVideoPlayer usage with VideoPlayerViewModel
- Option B: Keep RoomViewModel for sync/socket logic, add VideoPlayerViewModel for player control
- **Chosen: Option B** - Keep both, RoomViewModel delegates player control to VideoPlayerViewModel

### 4. Update RoomScreen.kt
- Create/obtain VideoPlayerViewModel instance
- Collect `PlayerUiState` from VideoPlayerViewModel
- Pass state to `VideoContentWithControls`
- Wire callbacks to ViewModel methods

### 5. Update VLCPlayerControls.kt
- Accept `PlayerUiState` instead of individual parameters
- Extract values from state
- Keep all callbacks (onPlayPause, onSeek, etc.)

### 6. Update VideoGestureHandler.kt
- Already uses callbacks - no changes needed
- Ensure callbacks are wired to ViewModel methods

## Files to Update

1. ✅ `PlayerEngine.kt` - Update to properly wrap LibVLCVideoPlayer with StateFlows
2. ✅ `VideoPlayerViewModel.kt` - Complete implementation with state management
3. ✅ `PlayerState.kt` - Already complete
4. ⚠️ `RoomScreen.kt` - Integrate VideoPlayerViewModel
5. ⚠️ `VLCPlayerControls.kt` - Accept PlayerUiState
6. ✅ `VideoGestureHandler.kt` - No changes (already callback-based)

## Key Design Decisions

1. **StateFlows in PlayerEngine**: Since LibVLCVideoPlayer doesn't expose StateFlows, PlayerEngine will poll or use callbacks to update StateFlows
2. **Dual ViewModel Approach**: Keep RoomViewModel for sync/socket, add VideoPlayerViewModel for player control
3. **Backward Compatibility**: Ensure existing features (sync, socket) continue working
4. **VLC Behavior Matching**: Follow VLC_ANALYSIS_AND_IMPLEMENTATION.md for UX patterns

