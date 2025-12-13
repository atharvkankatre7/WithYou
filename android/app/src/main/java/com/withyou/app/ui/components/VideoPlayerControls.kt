package com.withyou.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Custom video player controls with modern design
 */
@Composable
fun VideoPlayerControls(
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    isHost: Boolean,
    isFullscreen: Boolean,
    playbackSpeed: Float,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekForward: () -> Unit,
    onSeekBackward: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onLockControls: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSpeedMenu by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    
    // Update slider when not dragging
    LaunchedEffect(currentPosition, duration) {
        if (!isDragging && duration > 0) {
            sliderPosition = currentPosition.toFloat() / duration.toFloat()
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.7f),
                        Color.Black.copy(alpha = 0.9f)
                    )
                )
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Progress bar with time
        Column {
            // Time display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(if (isDragging) (sliderPosition * duration).toLong() else currentPosition),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatTime(duration),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Custom slider
            Slider(
                value = sliderPosition,
                onValueChange = { value ->
                    isDragging = true
                    sliderPosition = value
                },
                onValueChangeFinished = {
                    isDragging = false
                    val newPosition = (sliderPosition * duration).toLong()
                    onSeek(newPosition)
                },
                enabled = isHost,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFE91E63),
                    activeTrackColor = Color(0xFFE91E63),
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    disabledThumbColor = Color.Gray,
                    disabledActiveTrackColor = Color.Gray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Control buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Lock button
                ControlButton(
                    icon = Icons.Outlined.Lock,
                    contentDescription = "Lock controls",
                    onClick = onLockControls,
                    size = 20.dp
                )
            }
            
            // Center controls (Play/Pause with seek)
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rewind 10s
                ControlButton(
                    icon = Icons.Default.Replay10,
                    contentDescription = "Rewind 10 seconds",
                    onClick = onSeekBackward,
                    enabled = isHost,
                    size = 32.dp
                )
                
                // Play/Pause button
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE91E63))
                        .clickable(enabled = isHost) { onPlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Forward 10s
                ControlButton(
                    icon = Icons.Default.Forward10,
                    contentDescription = "Forward 10 seconds",
                    onClick = onSeekForward,
                    enabled = isHost,
                    size = 32.dp
                )
            }
            
            // Right controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Playback speed
                Box {
                    TextButton(
                        onClick = { showSpeedMenu = true },
                        enabled = isHost
                    ) {
                        Text(
                            text = "${playbackSpeed}x",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false }
                    ) {
                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "${speed}x",
                                        fontWeight = if (speed == playbackSpeed) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                onClick = {
                                    onSpeedChange(speed)
                                    showSpeedMenu = false
                                },
                                leadingIcon = {
                                    if (speed == playbackSpeed) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color(0xFFE91E63)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
                
                // Fullscreen toggle
                ControlButton(
                    icon = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = if (isFullscreen) "Exit fullscreen" else "Enter fullscreen",
                    onClick = onToggleFullscreen,
                    size = 24.dp
                )
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 24.dp
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(size)
        )
    }
}

/**
 * Format milliseconds to MM:SS or HH:MM:SS
 */
private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    
    val seconds = (ms / 1000) % 60
    val minutes = (ms / 1000 / 60) % 60
    val hours = ms / 1000 / 3600
    
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/**
 * Buffering indicator
 */
@Composable
fun BufferingIndicator(
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "buffering")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing)
        ),
        label = "rotation"
    )
    
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = Color(0xFFE91E63),
            strokeWidth = 3.dp,
            modifier = Modifier.size(48.dp)
        )
    }
}

/**
 * Top bar for video player
 */
@Composable
fun VideoTopBar(
    roomId: String,
    connectionStatus: String,
    isHost: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.9f),
                        Color.Black.copy(alpha = 0.7f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left - Back button and room info
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            
            Column {
                Text(
                    text = "Room: $roomId",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (connectionStatus.contains("Connected") || 
                                    connectionStatus.contains("Room"))
                                    Color(0xFF4CAF50) else Color.Gray
                            )
                    )
                    Text(
                        text = connectionStatus,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        }
        
        // Right - Actions
        Row {
            if (isHost) {
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = Color.White
                    )
                }
            }
            IconButton(onClick = onSettings) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Role badge (Host/Follower)
 */
@Composable
fun RoleBadge(
    isHost: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isHost) Color(0xFFE91E63) else Color(0xFF7C3AED),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (isHost) Icons.Default.Star else Icons.Default.Person,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = if (isHost) "HOST" else "SYNCED",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}

