package com.withyou.app.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.withyou.app.player.AspectMode
import com.withyou.app.player.PlayerUiState
import com.withyou.app.ui.components.VLCPlayerControls
import org.videolan.libvlc.MediaPlayer

/**
 * Preview composable for VLCPlayerControls
 * 
 * This allows visual testing of the controls overlay without real LibVLC.
 * Useful for:
 * - Testing UI layout and animations
 * - Verifying control states (locked, disabled, etc.)
 * - Previewing different aspect ratios and track selections
 * 
 * Usage: Add @Preview annotation and view in Android Studio's preview panel
 */
@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun FakeVideoPlayerControlsPreview() {
    // Create fake PlayerUiState for preview
    val fakeUiState = PlayerUiState(
        isPlaying = true,
        position = 125000L, // 2:05
        duration = 360000L, // 6:00
        buffered = 200000L,
        isBuffering = false,
        playbackRate = 1.0f,
        isLocked = false,
        aspectMode = AspectMode.FIT,
        audioTracks = emptyList(), // Empty for preview - tracks not needed for UI testing
        subtitleTracks = emptyList(), // Empty for preview - tracks not needed for UI testing
        currentAudioTrack = null,
        currentSubtitleTrack = null,
        hasError = false,
        errorMessage = null
    )
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            VLCPlayerControls(
                uiState = fakeUiState,
                isHost = true,
                onPlayPause = { },
                onSeek = { },
                onSeekForward = { },
                onSeekBackward = { },
                onSpeedChange = { },
                onAspectRatioChange = { },
                onAudioTrackChange = { },
                onSubtitleTrackChange = { },
                onLockControls = { },
                isRotationLocked = false,
                onToggleRotationLock = { },
                onUserInteractionStart = { },
                onUserInteractionEnd = { }
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, name = "Locked Controls")
@Composable
private fun FakeVideoPlayerControlsLockedPreview() {
    val fakeUiState = PlayerUiState(
        isPlaying = false,
        position = 125000L,
        duration = 360000L,
        isLocked = true, // Locked state
        aspectMode = AspectMode.FIT
    )
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            VLCPlayerControls(
                uiState = fakeUiState,
                isHost = true,
                onPlayPause = { },
                onSeek = { },
                onSeekForward = { },
                onSeekBackward = { },
                onSpeedChange = { },
                onAspectRatioChange = { },
                onAudioTrackChange = { },
                onSubtitleTrackChange = { },
                onLockControls = { },
                isRotationLocked = false,
                onToggleRotationLock = { },
                onUserInteractionStart = { },
                onUserInteractionEnd = { }
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000, name = "Non-Host (Viewer)")
@Composable
private fun FakeVideoPlayerControlsViewerPreview() {
    val fakeUiState = PlayerUiState(
        isPlaying = true,
        position = 125000L,
        duration = 360000L,
        isLocked = false,
        aspectMode = AspectMode.FIT
    )
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Bottom
        ) {
            // isHost = false simulates viewer mode (controls disabled)
            VLCPlayerControls(
                uiState = fakeUiState,
                isHost = false, // Non-host = controls disabled
                onPlayPause = { },
                onSeek = { },
                onSeekForward = { },
                onSeekBackward = { },
                onSpeedChange = { },
                onAspectRatioChange = { },
                onAudioTrackChange = { },
                onSubtitleTrackChange = { },
                onLockControls = { },
                isRotationLocked = false,
                onToggleRotationLock = { },
                onUserInteractionStart = { },
                onUserInteractionEnd = { }
            )
        }
    }
}

