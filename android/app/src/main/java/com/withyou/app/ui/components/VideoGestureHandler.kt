package com.withyou.app.ui.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Gesture types for video player
 */
enum class GestureType {
    NONE, BRIGHTNESS, VOLUME
}

/**
 * Data class for gesture state
 */
data class GestureState(
    val type: GestureType = GestureType.NONE,
    val value: Float = 0f,
    val delta: Float = 0f
)

/**
 * Video gesture handler composable
 * Handles:
 * - Double tap left/right to seek ±10s
 * - Vertical swipe on left side for brightness
 * - Vertical swipe on right side for volume
 * - Single tap to toggle controls
 */
/**
 * Video gesture handler composable
 * Handles:
 * - Double tap left/right to seek ±10s
 * - Vertical swipe on left side for brightness
 * - Vertical swipe on right side for volume
 * - Single tap to toggle controls
 * 
 * PERFORMANCE OPTIMIZATIONS:
 * - Uses rememberUpdatedState for position/duration to prevent gesture detector recreation on every frame update
 * - pointerInput keys are minimal (isHost, isLocked, Unit) to prevent unnecessary recreation
 * - Gesture state is remembered across recompositions
 * - Uses mutableStateOf for gesture state to avoid full recomposition of gesture handlers
 * 
 * LOCK MODE: When isHost=false or isLocked=true, gestures are disabled except unlock tap.
 */
@Composable
fun VideoGestureHandler(
    modifier: Modifier = Modifier,
    isHost: Boolean,
    currentPosition: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onToggleControls: () -> Unit,
    onDoubleTapSeek: (Int) -> Unit,
    onCenterDoubleTap: (() -> Unit)? = null, // Center double-tap for play/pause
    isLocked: Boolean = false, // Lock state - disables gestures when true
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val scope = rememberCoroutineScope()
    
    // Keep latest playback position/duration without restarting pointerInput
    // This prevents gesture detector from being recreated on every position update
    val currentPositionState by rememberUpdatedState(currentPosition)
    val durationState by rememberUpdatedState(duration)
    
    var gestureState by remember { mutableStateOf(GestureState()) }
    var showGestureIndicator by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) } // Track if user is currently dragging
    var doubleTapSide by remember { mutableStateOf<String?>(null) }
    var seekAccumulator by remember { mutableIntStateOf(0) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    
    // Multi-tap state for double tap seek accumulation
    var leftTapCount by remember { mutableIntStateOf(0) }
    var rightTapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    
    // Volume and brightness states - use animated values for smooth transitions
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    var targetVolume by remember { mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume) }
    var targetBrightness by remember { mutableFloatStateOf(0.5f) }
    
    // Smooth animated values
    val animatedVolume by animateFloatAsState(
        targetValue = targetVolume,
        animationSpec = tween(durationMillis = 50, easing = LinearEasing),
        label = "volume"
    )
    
    val animatedBrightness by animateFloatAsState(
        targetValue = targetBrightness,
        animationSpec = tween(durationMillis = 50, easing = LinearEasing),
        label = "brightness"
    )
    
    // Apply volume changes when animated value changes
    LaunchedEffect(animatedVolume) {
        val newVolume = (animatedVolume * maxVolume).toInt().coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
    }
    
    // Apply brightness changes
    LaunchedEffect(animatedBrightness) {
        (context as? android.app.Activity)?.window?.let { window ->
            val params = window.attributes
            params.screenBrightness = animatedBrightness.coerceIn(0.01f, 1f)
            window.attributes = params
        }
    }
    
    // Reset double tap indicator after delay
    LaunchedEffect(doubleTapSide, seekAccumulator) {
        if (doubleTapSide != null) {
            delay(800) // Show overlay for 800ms
            doubleTapSide = null
            seekAccumulator = 0
            leftTapCount = 0
            rightTapCount = 0
        }
    }
    
    // Reset tap counts if no tap for 1 second
    LaunchedEffect(lastTapTime) {
        if (lastTapTime > 0) {
            delay(1000)
            if (System.currentTimeMillis() - lastTapTime >= 1000) {
                leftTapCount = 0
                rightTapCount = 0
            }
        }
    }
    
    // Hide indicator when drag ends - always hide after delay
    fun scheduleHideIndicator() {
        hideJob?.cancel()
        hideJob = scope.launch {
            delay(800) // Fade out after 800ms
            if (!isDragging) {
                showGestureIndicator = false
                gestureState = GestureState()
            }
            hideJob = null // Clear job reference
        }
    }
    
    // Ensure indicator hides when not dragging (backup mechanism)
    LaunchedEffect(isDragging, showGestureIndicator) {
        if (!isDragging && showGestureIndicator) {
            // Wait a bit to ensure drag end handlers have run
            delay(600)
            // Double check - if still not dragging and indicator is visible, force hide
            if (!isDragging && showGestureIndicator) {
                hideJob?.cancel()
                showGestureIndicator = false
                gestureState = GestureState()
                hideJob = null
            }
        }
    }
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center  // Center video content (letterboxing will be distributed evenly)
    ) {
        content()
        
        // Tap and double-tap gesture zones - must be on top to intercept touches
        // This Row covers the entire screen to capture gestures even when video is playing
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // LEFT 35% – rewind (single tap + double tap + brightness drag)
            Box(
                modifier = Modifier
                    .weight(3.5f)
                    .fillMaxHeight()
                    .pointerInput(isHost, isLocked) {
                        detectTapGestures(
                            onTap = {
                                // Single tap → toggle controls
                                onToggleControls()
                            },
                            onDoubleTap = {
                                val duration = durationState
                                if (!isHost || duration <= 0L) return@detectTapGestures
                                
                                val currentTime = System.currentTimeMillis()
                                // Reset if too much time passed since last tap
                                if (currentTime - lastTapTime > 1000) {
                                    leftTapCount = 0
                                }
                                
                                leftTapCount++
                                lastTapTime = currentTime
                                
                                val seekSeconds = leftTapCount * 10
                                val seekDelta = seekSeconds * 1000L
                                val currentPos = currentPositionState
                                val newPos = (currentPos - seekDelta)
                                    .coerceAtLeast(0L)
                                
                                // Show overlay with accumulated seconds
                                doubleTapSide = "left"
                                seekAccumulator = -seekSeconds
                                
                                // Actually seek
                                onSeek(newPos)
                                onDoubleTapSeek(-seekSeconds)
                            }
                        )
                    }
                    .pointerInput(isLocked) {
                        // Brightness gesture disabled when locked
                        if (isLocked) return@pointerInput
                        
                        var startBrightness = targetBrightness
                        var totalDrag = 0f
                        
                        detectVerticalDragGestures(
                            onDragStart = {
                                isDragging = true
                                hideJob?.cancel()
                                startBrightness = targetBrightness
                                totalDrag = 0f
                                gestureState = GestureState(
                                    type = GestureType.BRIGHTNESS,
                                    value = targetBrightness
                                )
                                showGestureIndicator = true
                            },
                            onDragEnd = {
                                isDragging = false
                                hideJob?.cancel()
                                scope.launch {
                                    delay(500)
                                    showGestureIndicator = false
                                    gestureState = GestureState()
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                hideJob?.cancel()
                                scope.launch {
                                    delay(500)
                                    showGestureIndicator = false
                                    gestureState = GestureState()
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                                // Sensitivity: 600dp for full range
                                val delta = -totalDrag / 600f
                                val newBrightness = (startBrightness + delta).coerceIn(0.01f, 1f)
                                targetBrightness = newBrightness
                                gestureState = gestureState.copy(value = newBrightness)
                            }
                        )
                    }
            )
            
            // CENTER 30% – single tap + double-tap play/pause
            Box(
                modifier = Modifier
                    .weight(3f)
                    .fillMaxHeight()
                    .pointerInput(isHost) {
                        detectTapGestures(
                            onTap = {
                                // Single tap → toggle controls
                                onToggleControls()
                            },
                            onDoubleTap = {
                                onCenterDoubleTap?.invoke()
                            }
                        )
                    }
            )
            
            // RIGHT 35% – forward (single tap + double tap)
            Box(
                modifier = Modifier
                    .weight(3.5f)
                    .fillMaxHeight()
                    .pointerInput(isHost) {
                        detectTapGestures(
                            onTap = {
                                // Single tap → toggle controls
                                onToggleControls()
                            },
                            onDoubleTap = {
                                val duration = durationState
                                if (!isHost || duration <= 0L) return@detectTapGestures
                                
                                val currentTime = System.currentTimeMillis()
                                // Reset if too much time passed since last tap
                                if (currentTime - lastTapTime > 1000) {
                                    rightTapCount = 0
                                }
                                
                                rightTapCount++
                                lastTapTime = currentTime
                                
                                val seekSeconds = rightTapCount * 10
                                val seekDelta = seekSeconds * 1000L
                                val currentPos = currentPositionState
                                val newPos = if (duration > 0) {
                                    (currentPos + seekDelta).coerceAtMost(duration)
                                } else {
                                    currentPos + seekDelta
                                }
                                
                                // Show overlay with accumulated seconds
                                doubleTapSide = "right"
                                seekAccumulator = seekSeconds
                                
                                // Actually seek
                                onSeek(newPos)
                                onDoubleTapSeek(+seekSeconds)
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        var startVolume = targetVolume
                        var totalDrag = 0f
                        
                        detectVerticalDragGestures(
                            onDragStart = {
                                isDragging = true
                                hideJob?.cancel()
                                // Get current volume
                                startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
                                targetVolume = startVolume
                                totalDrag = 0f
                                gestureState = GestureState(
                                    type = GestureType.VOLUME,
                                    value = targetVolume
                                )
                                showGestureIndicator = true
                            },
                            onDragEnd = {
                                isDragging = false
                                hideJob?.cancel()
                                scope.launch {
                                    delay(500)
                                    showGestureIndicator = false
                                    gestureState = GestureState()
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                hideJob?.cancel()
                                scope.launch {
                                    delay(500)
                                    showGestureIndicator = false
                                    gestureState = GestureState()
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                totalDrag += dragAmount
                                // Sensitivity: 600dp for full range
                                val delta = -totalDrag / 600f
                                val newVolume = (startVolume + delta).coerceIn(0f, 1f)
                                targetVolume = newVolume
                                gestureState = gestureState.copy(value = newVolume)
                            }
                        )
                    }
            )
        }
        
        // Double tap seek indicator - Left
        AnimatedVisibility(
            visible = doubleTapSide == "left",
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 48.dp)
        ) {
            DoubleTapIndicator(
                seconds = seekAccumulator,
                isForward = false
            )
        }
        
        // Double tap seek indicator - Right
        AnimatedVisibility(
            visible = doubleTapSide == "right",
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 48.dp)
        ) {
            DoubleTapIndicator(
                seconds = seekAccumulator,
                isForward = true
            )
        }
        
        // Gesture indicator overlay - visible while dragging or briefly after drag ends
        AnimatedVisibility(
            visible = showGestureIndicator && gestureState.type != GestureType.NONE,
            enter = fadeIn(animationSpec = tween(100)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            GestureIndicator(gestureState = gestureState)
        }
    }
}

/**
 * Double tap seek indicator
 */
@Composable
fun DoubleTapIndicator(
    seconds: Int,
    isForward: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ripple")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = alpha))
            .padding(24.dp)
    ) {
        Icon(
            imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${abs(seconds)} seconds",
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    }
}


/**
 * Gesture indicator for volume/brightness with smooth progress bar
 */
@Composable
fun GestureIndicator(gestureState: GestureState) {
    val icon: ImageVector = when (gestureState.type) {
        GestureType.BRIGHTNESS -> {
            when {
                gestureState.value < 0.33f -> Icons.Default.BrightnessLow
                gestureState.value < 0.66f -> Icons.Default.BrightnessMedium
                else -> Icons.Default.BrightnessHigh
            }
        }
        GestureType.VOLUME -> {
            when {
                gestureState.value <= 0f -> Icons.Default.VolumeOff
                gestureState.value < 0.33f -> Icons.Default.VolumeMute
                gestureState.value < 0.66f -> Icons.Default.VolumeDown
                else -> Icons.Default.VolumeUp
            }
        }
        else -> Icons.Default.Info
    }
    
    // Animated progress value
    val animatedProgress by animateFloatAsState(
        targetValue = gestureState.value,
        animationSpec = tween(durationMillis = 30, easing = LinearEasing),
        label = "progress"
    )
    
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.85f)
        ),
        modifier = Modifier.padding(16.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(20.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Progress bar
            Box(
                modifier = Modifier
                    .width(140.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress.coerceIn(0f, 1f))
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (gestureState.type == GestureType.BRIGHTNESS)
                                Color(0xFFFFD700) // Gold for brightness
                            else
                                Color(0xFF4CAF50) // Green for volume
                        )
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}
