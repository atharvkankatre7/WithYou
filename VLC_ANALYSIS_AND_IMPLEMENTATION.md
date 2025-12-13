# VLC Android Player Architecture Analysis & Implementation Guide

## 1. High-Level Architecture Analysis

### VLC's Structure

**Key Components:**
1. **VideoPlayerActivity** (`org.videolan.vlc.gui.video.VideoPlayerActivity`)
   - Main video player Activity
   - Handles UI interactions, gestures, and delegates to service
   - Uses XML layouts with Data Binding

2. **PlaybackService** (`org.videolan.vlc.PlaybackService`)
   - Android Service that manages playback lifecycle
   - Bridges UI actions to PlayerController
   - Handles MediaSession, notifications, audio focus

3. **PlayerController** (`org.videolan.vlc.media.PlayerController`)
   - Direct wrapper around LibVLC's `MediaPlayer`
   - Exposes simple methods: `play()`, `pause()`, `setTime()`, `setRate()`, etc.
   - Listens to MediaPlayer events and updates state

4. **VideoTouchDelegate** (`org.videolan.vlc.gui.video.VideoTouchDelegate`)
   - Handles all touch gestures (taps, double-taps, drags)
   - Manages brightness/volume vertical drags
   - Handles horizontal swipe-to-seek

5. **VideoPlayerOverlayDelegate** (`org.videolan.vlc.gui.video.VideoPlayerOverlayDelegate`)
   - Manages bottom HUD (controls overlay)
   - Handles seek bar, play/pause button, forward/backward buttons
   - Manages visibility and auto-hide

### Control Flow Mapping

#### Play/Pause Button
- **UI**: `player_overlay_play` ImageView in `player_hud.xml`
- **Click Handler**: `android:onClick="@{(v) -> player.doPlayPause()}"`
- **Activity Method**: `doPlayPause()` → calls `service?.play()` or `service?.pause()`
- **Service Method**: `PlaybackService.play()` → `playlistManager.play()` → `player.play()`
- **PlayerController**: `player.play()` → `mediaplayer.play()`
- **LibVLC**: `MediaPlayer.play()` / `MediaPlayer.pause()`

#### Seek Bar
- **UI**: `player_overlay_seekbar` (AccessibleSeekBar) in `player_hud.xml`
- **Binding**: `android:progress="@{player.service.getTime(progress.time)}"`
- **Seek Handler**: `OnSeekBarChangeListener` → `player.seek(position, fromUser = true)`
- **Activity Method**: `seek(position, length, fromUser, fast)` → `service.seek(position, length.toDouble(), fromUser, fast)`
- **Service Method**: `PlaybackService.seek()` → `setTime(time, fast)` → `playlistManager.player.setTime(time, fast)`
- **PlayerController**: `setTime(time, fast)` → `mediaplayer.setTime(time, fast)`
- **LibVLC**: `MediaPlayer.setTime(time: Long, fast: Boolean)`

#### Forward/Backward 10s Buttons
- **UI**: `player_overlay_forward` / `player_overlay_rewind` ImageViews
- **Click Handler**: `OnClickListener` → calculates new position → calls `player.seek()`
- **Flow**: Same as seek bar above

#### Double-Tap Seek
- **Gesture**: Detected in `VideoTouchDelegate.onTouchEvent()`
- **Logic**: Left 1/3 = -10s, Right 1/3 = +10s, Center = play/pause
- **Implementation**: Calculates new position → `player.seek(newPosition)`

#### Horizontal Swipe-to-Seek
- **Gesture**: Detected in `VideoTouchDelegate.onTouchEvent()` as `TOUCH_MOVE`
- **Logic**: Horizontal drag distance → percentage of screen → seek delta
- **Implementation**: `doSeekTouch()` → `player.seek(position, fromUser = true)`

#### Vertical Drag (Brightness/Volume)
- **Gesture**: Detected in `VideoTouchDelegate.onTouchEvent()`
- **Logic**: Left side = brightness, Right side = volume
- **Brightness**: `Settings.putSingle(BRIGHTNESS_VALUE, newBrightness)` + `WindowManager.LayoutParams.screenBrightness`
- **Volume**: `audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)`

#### Aspect Ratio / Resize
- **UI**: `player_resize` ImageView
- **Click Handler**: `player.resizeVideo()` → cycles through aspect ratios
- **Service**: `service?.setVideoAspectRatio(ratio)` → `player.setVideoAspectRatio(ratio)`
- **PlayerController**: `setVideoAspectRatio(aspect: String?)` → `mediaplayer.aspectRatio = aspect`
- **LibVLC**: `MediaPlayer.aspectRatio = "16:9"` or `null` for auto

#### Playback Speed
- **UI**: Advanced options menu
- **Service**: `setRate(rate: Float, save: Boolean)` → `playlistManager.player.setRate(rate, save)`
- **PlayerController**: `setRate(rate: Float, save: Boolean)` → `mediaplayer.rate = rate`
- **LibVLC**: `MediaPlayer.rate = 1.5f` (1.0 = normal)

#### Audio/Subtitle Tracks
- **UI**: `player_overlay_tracks` ImageView → opens dialog
- **Service**: `setAudioTrack(index: String)` → `playlistManager.player.setAudioTrack(index)`
- **PlayerController**: `setAudioTrack(index: String)` → `mediaplayer.setAudioTrack(index)`
- **LibVLC**: `MediaPlayer.setAudioTrack(trackId: String)`

#### Lock Screen
- **UI**: Lock button (when locked, shows swipe-to-unlock overlay)
- **State**: `isLocked` boolean in Activity
- **Effect**: Disables most gestures and controls when locked

---

## 2. Proposed Architecture for Your App

### Folder Structure
```
app/src/main/java/com/withyou/app/
├── player/
│   ├── LibVLCPlayerEngine.kt      # Direct LibVLC wrapper (like PlayerController)
│   └── PlayerState.kt              # Data classes for state
├── viewmodel/
│   └── VideoPlayerViewModel.kt     # ViewModel (like PlaybackService bridge)
└── ui/
    └── player/
        ├── VideoPlayerScreen.kt    # Main Compose screen (like VideoPlayerActivity)
        ├── PlayerControls.kt        # Bottom controls overlay (like VideoPlayerOverlayDelegate)
        ├── VideoGestureHandler.kt   # Gesture detection (like VideoTouchDelegate)
        └── PlayerOverlays.kt        # Lock, brightness, volume overlays
```

### State Management
- **PlayerUiState**: `isPlaying`, `position`, `duration`, `buffered`, `isLocked`, `aspectMode`, `playbackRate`, `audioTracks`, `subtitleTracks`
- **ViewModel**: Holds state, subscribes to player events, exposes intent-style methods
- **Compose UI**: Observes state, calls ViewModel methods on user actions

---

## 3. Implementation Code

### Assumptions
- Package: `com.withyou.app`
- Using Jetpack Compose
- LibVLC already integrated (you have `LibVLCVideoPlayer`)

---

## 4. Complete Implementation Code

### 4.1 PlayerEngine.kt
**Location**: `android/app/src/main/java/com/withyou/app/player/PlayerEngine.kt`

This is your direct LibVLC wrapper, equivalent to VLC's `PlayerController`.

**Key Methods:**
- `play()` → `MediaPlayer.play()`
- `pause()` → `MediaPlayer.pause()`
- `seekTo(timeMs, fast)` → `MediaPlayer.setTime(time, fast)`
- `setRate(rate)` → `MediaPlayer.rate = rate`
- `setAspectRatio(ratio)` → `MediaPlayer.aspectRatio = ratio`
- `setAudioTrack(trackId)` → `MediaPlayer.setAudioTrack(trackId)`
- `setSubtitleTrack(trackId)` → `MediaPlayer.setSpuTrack(trackId)`

**State Flows:**
- `isPlaying: StateFlow<Boolean>`
- `position: StateFlow<Long>`
- `duration: StateFlow<Long>`
- `isBuffering: StateFlow<Boolean>`
- `playbackRate: StateFlow<Float>`

### 4.2 VideoPlayerViewModel.kt
**Location**: `android/app/src/main/java/com/withyou/app/viewmodel/VideoPlayerViewModel.kt`

This bridges UI actions to PlayerEngine, similar to VLC's `PlaybackService`.

**Key Methods:**
- `togglePlayPause()` → `playerEngine.togglePlayPause()`
- `seekTo(timeMs, fast)` → `playerEngine.seekTo(timeMs, fast)`
- `seekForward(seconds)` → calculates new position → `seekTo()`
- `seekBackward(seconds)` → calculates new position → `seekTo()`
- `setPlaybackRate(rate)` → `playerEngine.setRate(rate)`
- `setAspectMode(mode)` → `playerEngine.setScaleMode()` or `setAspectRatio()`
- `setAudioTrack(trackId)` → `playerEngine.setAudioTrack(trackId)`
- `setSubtitleTrack(trackId)` → `playerEngine.setSubtitleTrack(trackId)`

**State:**
- `uiState: StateFlow<PlayerUiState>` - Contains all UI-relevant state

### 4.3 Compose UI Components

You already have `VLCPlayerControls.kt` which handles the bottom controls overlay. The structure should be:

**VideoPlayerScreen.kt** (Main screen):
```kotlin
@Composable
fun VideoPlayerScreen(
    viewModel: VideoPlayerViewModel,
    videoUri: String,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Video surface
        VLCVideoLayout(...) { layout ->
            LaunchedEffect(layout) {
                viewModel.initPlayer(layout)
                viewModel.loadMedia(videoUri)
            }
        }
        
        // Gesture handler (wraps video)
        VideoGestureHandler(
            isHost = true,
            isLocked = uiState.isLocked,
            currentPosition = uiState.position,
            duration = uiState.duration,
            onToggleControls = { /* show/hide controls */ },
            onPlayPause = { viewModel.togglePlayPause() },
            onSeek = { time -> viewModel.seekTo(time) },
            onSeekForward = { viewModel.seekForward(10) },
            onSeekBackward = { viewModel.seekBackward(10) },
            onDoubleTapSeek = { seconds -> 
                viewModel.seekForward(seconds) // or backward
            }
        ) {
            // Video content
        }
        
        // Controls overlay
        AnimatedVisibility(visible = showControls && !uiState.isLocked) {
            VLCPlayerControls(
                isPlaying = uiState.isPlaying,
                currentPosition = uiState.position,
                duration = uiState.duration,
                isHost = true,
                onPlayPause = { viewModel.togglePlayPause() },
                onSeek = { time -> viewModel.seekTo(time) },
                onSeekForward = { viewModel.seekForward(10) },
                onSeekBackward = { viewModel.seekBackward(10) },
                onSpeedChange = { rate -> viewModel.setPlaybackRate(rate) },
                onAspectRatioChange = { mode, ratio -> 
                    viewModel.setAspectMode(mode, ratio)
                },
                onAudioTrackChange = { trackId -> 
                    viewModel.setAudioTrack(trackId)
                },
                onSubtitleTrackChange = { trackId -> 
                    viewModel.setSubtitleTrack(trackId)
                },
                onLockControls = { viewModel.toggleLock() }
            )
        }
    }
}
```

---

## 5. Complete Mapping: VLC → Your Implementation

### 5.1 Play/Pause Button

**VLC:**
1. XML: `player_overlay_play` ImageView with `onClick="@{(v) -> player.doPlayPause()}"`
2. Activity: `doPlayPause()` checks `service?.isPlaying` → calls `play()` or `pause()`
3. Service: `PlaybackService.play()` → `playlistManager.play()` → `player.play()`
4. Controller: `PlayerController.play()` → `mediaplayer.play()`

**Your Implementation:**
1. Compose: `VLCPlayerControls` has play/pause button with `onPlayPause` callback
2. Screen: `VideoPlayerScreen` calls `viewModel.togglePlayPause()`
3. ViewModel: `VideoPlayerViewModel.togglePlayPause()` → `playerEngine.togglePlayPause()`
4. Engine: `PlayerEngine.togglePlayPause()` → `player.play()` or `player.pause()`
5. LibVLC: `LibVLCVideoPlayer.play()` → `MediaPlayer.play()`

### 5.2 Seek Bar

**VLC:**
1. XML: `player_overlay_seekbar` (AccessibleSeekBar) with Data Binding
2. Binding: `android:progress="@{player.service.getTime(progress.time)}"`
3. Listener: `OnSeekBarChangeListener` → `player.seek(position, fromUser = true)`
4. Activity: `seek(position, length, fromUser, fast)` → `service.seek(position, length.toDouble(), fromUser, fast)`
5. Service: `PlaybackService.seek()` → `setTime(time, fast)` → `playlistManager.player.setTime(time, fast)`
6. Controller: `PlayerController.setTime(time, fast)` → `mediaplayer.setTime(time, fast)`

**Your Implementation:**
1. Compose: `VLCPlayerControls` has `Slider` with `onValueChange` and `onValueChangeFinished`
2. Callback: `onSeek(time)` → `viewModel.seekTo(time)`
3. ViewModel: `VideoPlayerViewModel.seekTo(timeMs, fast)` → `playerEngine.seekTo(timeMs, fast)`
4. Engine: `PlayerEngine.seekTo(timeMs, fast)` → `player.seekTo(timeMs)`
5. LibVLC: `LibVLCVideoPlayer.seekTo(timeMs)` → `MediaPlayer.setTime(time, false)`

### 5.3 Forward/Backward 10s Buttons

**VLC:**
1. XML: `player_overlay_forward` / `player_overlay_rewind` ImageViews
2. Click: `OnClickListener` → calculates `newPosition = currentPosition ± 10s` → `player.seek(newPosition)`
3. Flow: Same as seek bar (5.2)

**Your Implementation:**
1. Compose: `VLCPlayerControls` has forward/backward buttons
2. Callback: `onSeekForward()` / `onSeekBackward()` → `viewModel.seekForward(10)` / `viewModel.seekBackward(10)`
3. ViewModel: Calculates new position → `seekTo(newPosition)`
4. Flow: Same as seek bar (5.2)

### 5.4 Double-Tap Seek

**VLC:**
1. Gesture: `VideoTouchDelegate.onTouchEvent()` detects double tap
2. Logic: Left 1/3 = -10s, Right 1/3 = +10s, Center = play/pause
3. Implementation: `player.seek(newPosition)` with visual indicator

**Your Implementation:**
1. Gesture: `VideoGestureHandler` detects double tap (you already have this)
2. Logic: Same as VLC (left/right/center zones)
3. Callback: `onDoubleTapSeek(seconds)` → `viewModel.seekForward(seconds)` or `seekBackward()`
4. Flow: Same as seek bar (5.2)

### 5.5 Horizontal Swipe-to-Seek

**VLC:**
1. Gesture: `VideoTouchDelegate.onTouchEvent()` detects horizontal drag
2. Logic: `doSeekTouch(coef, gesturesize, seek)` calculates percentage → seek delta
3. Implementation: `player.seek(position, fromUser = true)` with visual indicator

**Your Implementation:**
1. Gesture: `VideoGestureHandler` detects horizontal drag (you already have this)
2. Logic: Drag distance → percentage → seek delta → `onSeek(newPosition)`
3. Callback: `onSeek(time)` → `viewModel.seekTo(time)`
4. Flow: Same as seek bar (5.2)

### 5.6 Vertical Drag (Brightness/Volume)

**VLC:**
1. Gesture: `VideoTouchDelegate.onTouchEvent()` detects vertical drag
2. Logic: Left side = brightness, Right side = volume
3. Brightness: `Settings.putSingle(BRIGHTNESS_VALUE, newBrightness)` + `WindowManager.LayoutParams.screenBrightness`
4. Volume: `audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)`

**Your Implementation:**
1. Gesture: `VideoGestureHandler` detects vertical drag (you already have this)
2. Logic: Same as VLC (left = brightness, right = volume)
3. Brightness: Direct `WindowManager` access (same as VLC)
4. Volume: Direct `AudioManager` access (same as VLC)
5. Note: These don't go through ViewModel/Engine as they're system-level controls

### 5.7 Aspect Ratio / Resize

**VLC:**
1. XML: `player_resize` ImageView with `onClick="@{(v) -> player.resizeVideo()}"`
2. Activity: `resizeVideo()` cycles through modes → `service?.setVideoAspectRatio(ratio)`
3. Service: `PlaybackService.setVideoAspectRatio()` → `playlistManager.player.setVideoAspectRatio(ratio)`
4. Controller: `PlayerController.setVideoAspectRatio(aspect)` → `mediaplayer.aspectRatio = aspect`

**Your Implementation:**
1. Compose: `VLCPlayerControls` has aspect ratio dropdown
2. Callback: `onAspectRatioChange(mode, ratio)` → `viewModel.setAspectMode(mode, ratio)`
3. ViewModel: `setAspectMode(mode, ratio)` → `playerEngine.setScaleMode()` or `setAspectRatio()`
4. Engine: `setScaleMode()` or `setAspectRatio()` → `player.setScaleMode()` or `player.setCustomAspectRatio()`
5. LibVLC: `LibVLCVideoPlayer.setScaleMode()` → `MediaPlayer.setVideoScale()` or `MediaPlayer.aspectRatio = ratio`

### 5.8 Playback Speed

**VLC:**
1. UI: Advanced options menu → speed selector
2. Service: `setRate(rate: Float, save: Boolean)` → `playlistManager.player.setRate(rate, save)`
3. Controller: `PlayerController.setRate(rate, save)` → `mediaplayer.rate = rate`

**Your Implementation:**
1. Compose: `VLCPlayerControls` has speed dropdown
2. Callback: `onSpeedChange(rate)` → `viewModel.setPlaybackRate(rate)`
3. ViewModel: `setPlaybackRate(rate)` → `playerEngine.setRate(rate)`
4. Engine: `setRate(rate)` → `player.setRate(rate)`
5. LibVLC: `LibVLCVideoPlayer.setRate(rate)` → `MediaPlayer.rate = rate`

### 5.9 Audio/Subtitle Tracks

**VLC:**
1. XML: `player_overlay_tracks` ImageView → opens dialog
2. Dialog: Shows tracks from `service.getAllTracks()` → `service.setAudioTrack(index)` or `setSpuTrack(index)`
3. Service: `setAudioTrack(index)` → `playlistManager.player.setAudioTrack(index)`
4. Controller: `PlayerController.setAudioTrack(index)` → `mediaplayer.setAudioTrack(index)`

**Your Implementation:**
1. Compose: `VLCPlayerControls` has audio/subtitle dropdowns
2. Callback: `onAudioTrackChange(trackId)` / `onSubtitleTrackChange(trackId)` → `viewModel.setAudioTrack(trackId)` / `setSubtitleTrack(trackId)`
3. ViewModel: `setAudioTrack(trackId)` → `playerEngine.setAudioTrack(trackId)` + refresh tracks
4. Engine: `setAudioTrack(trackId)` → `player.setAudioTrack(trackId)`
5. LibVLC: `LibVLCVideoPlayer.setAudioTrack(trackId)` → `MediaPlayer.setAudioTrack(trackId)`

### 5.10 Lock Screen

**VLC:**
1. UI: Lock button → sets `isLocked = true`
2. Effect: When locked, most gestures disabled, shows swipe-to-unlock overlay
3. Unlock: Swipe gesture → `isLocked = false`

**Your Implementation:**
1. Compose: Lock button in controls → `viewModel.toggleLock()`
2. ViewModel: `toggleLock()` → updates `uiState.isLocked`
3. Effect: `VideoGestureHandler` checks `isLocked` and disables gestures when true
4. Unlock: Swipe gesture (you already have this) → `viewModel.toggleLock()`

---

## 6. Summary

### Architecture Comparison

| VLC Component | Your Equivalent | Responsibility |
|--------------|----------------|----------------|
| `VideoPlayerActivity` | `VideoPlayerScreen` (Compose) | Main UI, gesture handling, controls visibility |
| `PlaybackService` | `VideoPlayerViewModel` | Bridge UI → Player, state management |
| `PlayerController` | `PlayerEngine` | Direct LibVLC wrapper |
| `VideoTouchDelegate` | `VideoGestureHandler` | Touch gesture detection |
| `VideoPlayerOverlayDelegate` | `VLCPlayerControls` | Bottom controls overlay |
| `MediaPlayer` (LibVLC) | `LibVLCVideoPlayer` | Your existing LibVLC wrapper |

### Key Differences

1. **UI Framework**: VLC uses XML + Data Binding, you use Jetpack Compose
2. **State Management**: VLC uses LiveData + Data Binding, you use StateFlow + Compose
3. **Service vs ViewModel**: VLC uses Android Service for background playback, you use ViewModel (can add Service later if needed)
4. **Architecture**: VLC has more layers (Activity → Service → PlaylistManager → PlayerController), you have fewer (Screen → ViewModel → Engine)

### Benefits of Your Architecture

1. **Simpler**: Fewer layers, easier to understand
2. **Modern**: Jetpack Compose is more declarative and easier to maintain
3. **Testable**: ViewModel is easier to test than Service
4. **Flexible**: Can add Service layer later if needed for background playback

---

## 7. Next Steps

1. **Integrate PlayerEngine**: Use your existing `LibVLCVideoPlayer` as the underlying implementation
2. **Wire ViewModel**: Connect `VideoPlayerViewModel` to your existing `RoomViewModel` or create a new one
3. **Update UI**: Ensure `VideoPlayerScreen` uses the ViewModel methods
4. **Test**: Verify all controls work as expected

The code provided is original and follows VLC's architecture patterns without copying GPL-licensed code.

