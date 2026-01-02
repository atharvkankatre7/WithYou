package com.withyou.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import java.util.concurrent.TimeUnit
import com.withyou.app.ui.theme.RosePrimary

/**
 * VLC-style video player controls with animations
 * Features:
 * - Play/Pause with animation
 * - Forward/Backward 10s with multi-tap (10, 20, 30...)
 * - Lock screen
 * - Aspect ratio selector
 * - Audio track selector
 * - Subtitle track selector
 * 
 * This version accepts PlayerUiState for cleaner integration with VideoPlayerViewModel.
 * Maps to: VLC's VideoPlayerOverlayDelegate managing player_hud.xml
 */
@Composable
fun VLCPlayerControls(
    uiState: com.withyou.app.player.PlayerUiState,
    isHost: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekForward: (Int) -> Unit,
    onSeekBackward: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onAspectRatioChange: (String) -> Unit,
    onAudioTrackChange: (Int) -> Unit,
    onSubtitleTrackChange: (Int) -> Unit,
    onLockControls: () -> Unit,
    isRotationLocked: Boolean,
    onToggleRotationLock: () -> Unit,
    onUserInteractionStart: () -> Unit,
    onUserInteractionEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Extract values from PlayerUiState and delegate to original implementation
    VLCPlayerControls(
        isPlaying = uiState.isPlaying,
        currentPosition = uiState.position,
        duration = uiState.duration,
        isHost = isHost,
        playbackSpeed = uiState.playbackRate,
        aspectRatio = when (uiState.aspectMode) {
            com.withyou.app.player.AspectMode.FIT -> "Fit"
            com.withyou.app.player.AspectMode.FIT_SCREEN -> "Fit Screen"
            com.withyou.app.player.AspectMode.FILL -> "Fill"
            com.withyou.app.player.AspectMode.ORIGINAL -> "Original"
            com.withyou.app.player.AspectMode.CUSTOM -> "Fit" // Default for CUSTOM, actual ratio handled separately
        },
        audioTracks = uiState.audioTracks,
        subtitleTracks = uiState.subtitleTracks,
        currentAudioTrack = uiState.currentAudioTrack?.id ?: -1,
        currentSubtitleTrack = uiState.currentSubtitleTrack?.id ?: -1,
        isLocked = uiState.isLocked, // Pass lock state for controls disabling
        onPlayPause = onPlayPause,
        onSeek = onSeek,
        onSeekForward = onSeekForward,
        onSeekBackward = onSeekBackward,
        onSpeedChange = onSpeedChange,
        onAspectRatioChange = onAspectRatioChange,
        onAudioTrackChange = onAudioTrackChange,
        onSubtitleTrackChange = onSubtitleTrackChange,
        onLockControls = onLockControls,
        isRotationLocked = isRotationLocked,
        onToggleRotationLock = onToggleRotationLock,
        onUserInteractionStart = onUserInteractionStart,
        onUserInteractionEnd = onUserInteractionEnd,
        modifier = modifier
    )
}

/**
 * VLC-style video player controls with animations (original version with individual parameters)
 * This version is kept for backward compatibility.
 * 
 * Maps to: VLC's VideoPlayerOverlayDelegate managing player_hud.xml
 * 
 * Note: Controls are disabled when isHost=false OR when locked (from PlayerUiState).
 * This ensures non-host users and locked screens cannot control playback.
 */
@Composable
fun VLCPlayerControls(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    isHost: Boolean,
    playbackSpeed: Float,
    aspectRatio: String,
    audioTracks: List<MediaPlayer.TrackDescription>,
    subtitleTracks: List<MediaPlayer.TrackDescription>,
    currentAudioTrack: Int,
    currentSubtitleTrack: Int,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekForward: (Int) -> Unit,
    onSeekBackward: (Int) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onAspectRatioChange: (String) -> Unit,
    onAudioTrackChange: (Int) -> Unit,
    onSubtitleTrackChange: (Int) -> Unit,
    onLockControls: () -> Unit,
    isRotationLocked: Boolean,
    onToggleRotationLock: () -> Unit,
    onUserInteractionStart: () -> Unit,
    onUserInteractionEnd: () -> Unit,
    modifier: Modifier = Modifier,
    isLocked: Boolean = false // Optional lock state for backward compatibility
) {
    // Controls are enabled only if user is host AND not locked (for seek, speed, etc.)
    val controlsEnabled = isHost && !isLocked
    // Play/Pause is allowed for ALL users (host and partner) when not locked
    val playPauseEnabled = !isLocked
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showAspectRatioMenu by remember { mutableStateOf(false) }
    var showAudioMenu by remember { mutableStateOf(false) }
    var showSubtitleMenu by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    // Multi-tap seek state
    var forwardTapCount by remember { mutableIntStateOf(0) }
    var backwardTapCount by remember { mutableIntStateOf(0) }
    var showForwardIndicator by remember { mutableStateOf(false) }
    var showBackwardIndicator by remember { mutableStateOf(false) }
    
    // Update slider when not dragging
    LaunchedEffect(currentPosition, duration) {
        if (!isDragging && duration > 0) {
            sliderPosition = currentPosition.toFloat() / duration.toFloat()
        }
    }
    
    // Reset tap counts after delay
    LaunchedEffect(forwardTapCount) {
        if (forwardTapCount > 0) {
            showForwardIndicator = true
            delay(600)
            forwardTapCount = 0
            showBackwardIndicator = false
        }
    }
    
    LaunchedEffect(backwardTapCount) {
        if (backwardTapCount > 0) {
            showBackwardIndicator = true
            delay(600)
            backwardTapCount = 0
            showBackwardIndicator = false
        }
    }
    
    BoxWithConstraints(modifier = modifier) {
        // Calculate responsive sizes based on screen width
        val density = androidx.compose.ui.platform.LocalDensity.current
        val screenWidthDp = with(density) { constraints.maxWidth.toDp() }
        val screenHeightDp = with(density) { constraints.maxHeight.toDp() }
        
        // Responsive scaling factors (base: 360dp width for phones)
        val baseWidth = 360.dp
        val scaleFactor = (screenWidthDp.value / baseWidth.value).coerceIn(0.85f, 1.4f)
        
        // Responsive sizes - scale based on screen width (reduced sizes for smaller icons)
        val horizontalPadding = ((16.dp.value * scaleFactor).coerceIn(12f, 24f)).dp
        val verticalPadding = ((12.dp.value * scaleFactor).coerceIn(10f, 18f)).dp
        val controlSpacing = ((12.dp.value * scaleFactor).coerceIn(10f, 18f)).dp
        val centerSpacing = ((20.dp.value * scaleFactor).coerceIn(16f, 28f)).dp
        val timeTextSize = ((13.sp.value * scaleFactor).coerceIn(11f, 17f)).sp
        val speedTextSize = ((14.sp.value * scaleFactor).coerceIn(12f, 19f)).sp
        val iconSize = ((20.dp.value * scaleFactor).coerceIn(18f, 26f)).dp // Reduced from 24dp
        val seekButtonSize = ((36.dp.value * scaleFactor).coerceIn(32f, 44f)).dp // Reduced from 40dp
        val playPauseSize = ((56.dp.value * scaleFactor).coerceIn(50f, 72f)).dp // Reduced from 64dp
        val playPauseIconSize = ((28.dp.value * scaleFactor).coerceIn(24f, 36f)).dp // Reduced from 32dp
        val sliderHeight = ((36.dp.value * scaleFactor).coerceIn(32f, 48f)).dp
        val sectionSpacing = ((12.dp.value * scaleFactor).coerceIn(10f, 18f)).dp
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f),
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
                .padding(
                    start = horizontalPadding,
                    top = verticalPadding,
                    end = horizontalPadding,
                    bottom = 0.dp
                )
        ) {
            // Progress bar with time
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(if (isDragging) (sliderPosition * duration).toLong() else currentPosition),
                        color = Color.White,
                        fontSize = timeTextSize,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formatTime(duration),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = timeTextSize
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Custom slider with VLC-style design
                Slider(
                    value = sliderPosition,
                    onValueChange = { value ->
                        if (!controlsEnabled) return@Slider // Disabled if not host or locked
                        
                        // If duration is not ready, don't seek yet – just move thumb visually
                        if (duration <= 0L) {
                            if (!isDragging) {
                                isDragging = true
                                onUserInteractionStart()
                            }
                            sliderPosition = value.coerceIn(0f, 1f)
                            return@Slider
                        }
                        
                        if (!isDragging) {
                            isDragging = true
                            onUserInteractionStart()
                            // Notify that scrubbing started (blocks incoming sync seeks)
                            // Note: This will be called from RoomScreen's onSeek callback
                        }
                        sliderPosition = value.coerceIn(0f, 1f)
                        // Don't seek during drag - only update visual position
                    },
                    onValueChangeFinished = {
                        if (isDragging) {
                            isDragging = false
                        }
                        
                        // Final seek only if duration is valid and controls are enabled
                        if (duration > 0L && controlsEnabled) {
                            val finalPosition = (sliderPosition * duration).toLong().coerceIn(0, duration)
                            onSeek(finalPosition)
                        }
                        onUserInteractionEnd()
                    },
                    enabled = controlsEnabled, // Disabled if not host or locked
                    colors = SliderDefaults.colors(
                        thumbColor = RosePrimary,
                        activeTrackColor = RosePrimary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                        disabledThumbColor = Color.Gray,
                        disabledActiveTrackColor = Color.Gray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(sliderHeight)      // Responsive touch target
                        .padding(vertical = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(sectionSpacing))
            
            // Control buttons - use Box to allow overlays
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(controlSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        // Rotation lock button
                        AnimatedControlButton(
                            icon = if (isRotationLocked) Icons.Default.Lock else Icons.Default.Sync,
                            contentDescription = if (isRotationLocked) "Unlock rotation" else "Lock rotation",
                            onClick = onToggleRotationLock,
                            size = iconSize
                        )
                        
                        // Aspect ratio
                        Box {
                            AnimatedControlButton(
                                icon = Icons.Default.AspectRatio,
                                contentDescription = "Aspect ratio",
                                onClick = {
                                    if (!controlsEnabled) return@AnimatedControlButton
                                    onUserInteractionStart()
                                    showAspectRatioMenu = true
                                },
                                enabled = controlsEnabled, // Disabled if not host or locked
                                size = iconSize
                            )
                            
                            DropdownMenu(
                                expanded = showAspectRatioMenu,
                                onDismissRequest = {
                                    showAspectRatioMenu = false
                                    onUserInteractionEnd()
                                }
                            ) {
                                listOf("Fit", "Fill", "Fit Screen", "16:9", "4:3", "21:9", "1:1").forEach { ratio ->
                                    DropdownMenuItem(
                                        text = { Text(ratio) },
                                        onClick = {
                                            onAspectRatioChange(ratio)
                                            showAspectRatioMenu = false
                                        },
                                        leadingIcon = {
                                            if (ratio == aspectRatio) {
                                                Icon(Icons.Default.Check, null, tint = RosePrimary)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Center controls (Play/Pause with seek) - centered
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(centerSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.wrapContentSize()
                    ) {
                        // Rewind with multi-tap
                        AnimatedSeekButton(
                            icon = Icons.Default.Replay10,
                            contentDescription = "Rewind",
                            onClick = {
                                if (!controlsEnabled) return@AnimatedSeekButton
                                backwardTapCount++
                                val seconds = backwardTapCount * 10
                                onSeekBackward(seconds)
                            },
                            enabled = controlsEnabled, // Disabled if not host or locked
                            size = seekButtonSize
                        )
                        
                        // Play/Pause button with animation - ALLOWED for ALL users (host AND partner)
                        AnimatedPlayPauseButton(
                            isPlaying = isPlaying,
                            onClick = {
                                if (!playPauseEnabled) return@AnimatedPlayPauseButton
                                onUserInteractionStart()
                                onPlayPause()
                                onUserInteractionEnd()
                            },
                            enabled = playPauseEnabled, // Partner can pause too!
                            modifier = Modifier.size(playPauseSize),
                            iconSize = playPauseIconSize
                        )
                        
                        // Forward with multi-tap
                        AnimatedSeekButton(
                            icon = Icons.Default.Forward10,
                            contentDescription = "Forward",
                            onClick = {
                                if (!controlsEnabled) return@AnimatedSeekButton
                                forwardTapCount++
                                val seconds = forwardTapCount * 10
                                onSeekForward(seconds)
                            },
                            enabled = controlsEnabled, // Disabled if not host or locked
                            size = seekButtonSize
                        )
                    }
                    
                    // Right controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(controlSpacing),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        // Audio track
                        Box {
                            AnimatedControlButton(
                                icon = Icons.Default.AudioFile,
                                contentDescription = "Audio track",
                                onClick = {
                                    if (!controlsEnabled) return@AnimatedControlButton
                                    onUserInteractionStart()
                                    showAudioMenu = true
                                },
                                enabled = controlsEnabled && audioTracks.isNotEmpty(), // Disabled if not host or locked
                                size = iconSize
                            )
                            
                            DropdownMenu(
                                expanded = showAudioMenu,
                                onDismissRequest = {
                                    showAudioMenu = false
                                    onUserInteractionEnd()
                                }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Disable") },
                                    onClick = {
                                        onAudioTrackChange(-1)
                                        showAudioMenu = false
                                    },
                                    leadingIcon = {
                                        if (currentAudioTrack == -1) {
                                            Icon(Icons.Default.Check, null, tint = RosePrimary)
                                        }
                                    }
                                )
                                audioTracks.forEach { track ->
                                    DropdownMenuItem(
                                        text = { Text(track.name ?: "Track ${track.id}") },
                                        onClick = {
                                            onAudioTrackChange(track.id)
                                            showAudioMenu = false
                                        },
                                        leadingIcon = {
                                            if (track.id == currentAudioTrack) {
                                                Icon(Icons.Default.Check, null, tint = RosePrimary)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        
                        // Subtitle track
                        Box {
                            AnimatedControlButton(
                                icon = Icons.Default.Subtitles,
                                contentDescription = "Subtitles",
                                onClick = {
                                    if (!controlsEnabled) return@AnimatedControlButton
                                    onUserInteractionStart()
                                    showSubtitleMenu = true
                                },
                                enabled = controlsEnabled && subtitleTracks.isNotEmpty(), // Disabled if not host or locked
                                size = iconSize
                            )
                            
                            DropdownMenu(
                                expanded = showSubtitleMenu,
                                onDismissRequest = {
                                    showSubtitleMenu = false
                                    onUserInteractionEnd()
                                }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Disable") },
                                    onClick = {
                                        onSubtitleTrackChange(-1)
                                        showSubtitleMenu = false
                                    },
                                    leadingIcon = {
                                        if (currentSubtitleTrack == -1) {
                                            Icon(Icons.Default.Check, null, tint = RosePrimary)
                                        }
                                    }
                                )
                                subtitleTracks.forEach { track ->
                                    DropdownMenuItem(
                                        text = { Text(track.name ?: "Track ${track.id}") },
                                        onClick = {
                                            onSubtitleTrackChange(track.id)
                                            showSubtitleMenu = false
                                        },
                                        leadingIcon = {
                                            if (track.id == currentSubtitleTrack) {
                                                Icon(Icons.Default.Check, null, tint = RosePrimary)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        
                        // Playback speed
                        Box {
                            TextButton(
                                onClick = {
                                    if (!controlsEnabled) return@TextButton
                                    onUserInteractionStart()
                                    showSpeedMenu = true
                                },
                                enabled = controlsEnabled // Disabled if not host or locked
                            ) {
                                Text(
                                    text = "${playbackSpeed}x",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = speedTextSize
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showSpeedMenu,
                                onDismissRequest = {
                                    showSpeedMenu = false
                                    onUserInteractionEnd()
                                }
                            ) {
                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                    DropdownMenuItem(
                                        text = { Text("${speed}x") },
                                        onClick = {
                                            onSpeedChange(speed)
                                            showSpeedMenu = false
                                        },
                                        leadingIcon = {
                                            if (speed == playbackSpeed) {
                                                Icon(Icons.Default.Check, null, tint = RosePrimary)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Multi-tap indicators (overlay on top of Row)
                // Backward indicator
                if (showBackwardIndicator) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = (-120).dp, y = (-60).dp)
                    ) {
                        SeekIndicator(
                            seconds = -backwardTapCount * 10,
                            modifier = Modifier
                        )
                    }
                }
                
                // Forward indicator
                if (showForwardIndicator) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = 120.dp, y = (-60).dp)
                    ) {
                        SeekIndicator(
                            seconds = forwardTapCount * 10,
                            modifier = Modifier
                        )
                    }
                }
            }
            
            // Add bottom padding to ensure content is visible above navigation bar
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/**
 * Animated play/pause button
 */
@Composable
private fun AnimatedPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 32.dp
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(RosePrimary)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        val scale by animateFloatAsState(
            targetValue = if (isPlaying) 1.1f else 1f,
            animationSpec = spring(dampingRatio = 0.6f),
            label = "playPauseScale"
        )
        
        AnimatedContent(
            targetState = isPlaying,
            transitionSpec = {
                fadeIn(tween(150)) + scaleIn(initialScale = 0.8f) togetherWith
                fadeOut(tween(150)) + scaleOut(targetScale = 1.2f)
            },
            label = "playPauseIcon"
        ) { playing ->
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = Color.White.copy(alpha = 0.9f), // Slightly transparent for cool look
                modifier = Modifier
                    .size(iconSize)
                    .scale(scale)
            )
        }
    }
}

/**
 * Animated seek button with ripple effect
 */
@Composable
private fun AnimatedSeekButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    size: androidx.compose.ui.unit.Dp
) {
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "seekButtonScale"
    )
    
    IconButton(
        onClick = {
            isPressed = true
            onClick()
            scope.launch {
                delay(100)
                isPressed = false
            }
        },
        enabled = enabled,
        modifier = Modifier.scale(scale)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(size)
        )
    }
}

/**
 * Animated control button
 */
@Composable
private fun AnimatedControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 24.dp
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "controlButtonScale"
    )
    
    val scope = rememberCoroutineScope()
    
    IconButton(
        onClick = {
            isPressed = true
            onClick()
            scope.launch {
                delay(100)
                isPressed = false
            }
        },
        enabled = enabled,
        modifier = modifier.scale(scale)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Color.White.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.size(size)
        )
    }
}

/**
 * Seek indicator showing accumulated seconds
 */
@Composable
private fun SeekIndicator(
    seconds: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "indicatorAlpha"
    )
    
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = alpha)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (seconds > 0) Icons.Default.FastForward else Icons.Default.FastRewind,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "${kotlin.math.abs(seconds)}s",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}

/**
 * Format milliseconds to MM:SS or HH:MM:SS
 */
private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    val minutes = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val hours = TimeUnit.MILLISECONDS.toHours(ms)
    
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

