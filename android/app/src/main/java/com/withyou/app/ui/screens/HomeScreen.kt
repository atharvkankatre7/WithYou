package com.withyou.app.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.withyou.app.ui.theme.*
import com.withyou.app.viewmodel.RoomViewModel
import com.withyou.app.viewmodel.RoomUiState
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Home screen - Create or join a room with beautiful animations
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun HomeScreen(
    viewModel: RoomViewModel = viewModel(),
    onNavigateToRoom: (String, Boolean) -> Unit,
    onNavigateToMediaLibrary: () -> Unit = {}
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinRoomId by remember { mutableStateOf("") }
    var pendingJoinRoomId by remember { mutableStateOf<String?>(null) }
    
    // Request permissions for video access (Android 13+)
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    val permissionsState = rememberMultiplePermissionsState(permissions)
    
    // Request permissions automatically on first launch
    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            // Small delay to let UI render first
            delay(500)
            permissionsState.launchMultiplePermissionRequest()
        }
    }
    
    // Animation states
    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }
    
    // File picker launcher
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            Timber.d("File selected: $uri")
            viewModel.loadVideo(uri)
        }
    }
    
    // Navigate to room when ready or Handle Re-Sync
    LaunchedEffect(uiState) {
        if (uiState is RoomUiState.RoomReady) {
            val state = uiState as RoomUiState.RoomReady
            val isHost = viewModel.isHost.value
            onNavigateToRoom(state.roomId, isHost)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        BackgroundDark,
                        SurfaceDark,
                        CardDark
                    )
                )
            )
    ) {
        // Animated background circles
        AnimatedBackgroundElements()
        
        // Show permission request if needed
        if (!permissionsState.allPermissionsGranted && uiState is RoomUiState.Idle) {
            PermissionRequestContent(
                onRequestPermission = { permissionsState.launchMultiplePermissionRequest() }
            )
        } else {
            // Main content
            when (val state = uiState) {
                is RoomUiState.Idle -> {
                    WelcomeContent(
                        isVisible = isVisible,
                        onCreateRoom = { 
                            pendingJoinRoomId = null
                            filePicker.launch("video/*") 
                        },
                        onJoinRoom = { showJoinDialog = true },
                        onNavigateToMediaLibrary = onNavigateToMediaLibrary
                    )
                }
            is RoomUiState.LoadingFile -> {
                LoadingContent(
                    message = "Processing your video...",
                    progress = state.progress
                )
            }
            is RoomUiState.CheckingAudio -> {
                LoadingContent(
                    message = "Checking audio format...",
                    progress = null
                )
            }
            is RoomUiState.ConvertingAudio -> {
                AudioConversionContent(
                    progress = state.progress
                )
            }
            is RoomUiState.ExtractingAllAudioTracks -> {
                ExtractingAllAudioTracksContent(
                    progress = state.progress,
                    totalTracks = state.totalTracks
                )
            }
            is RoomUiState.FileLoaded -> {
                FileLoadedContent(
                    metadata = state.metadata,
                    isJoining = pendingJoinRoomId != null,
                    roomIdToJoin = pendingJoinRoomId,
                    onCreateRoom = { viewModel.createRoom() },
                    onJoinRoom = { roomId -> viewModel.joinRoom(roomId) },
                    onCancel = { 
                        pendingJoinRoomId = null
                        viewModel.resetState()
                    }
                )
            }
            is RoomUiState.CreatingRoom -> {
                LoadingContent(message = "Creating your room...")
            }
            is RoomUiState.JoiningRoom -> {
                LoadingContent(message = "Joining room...")
            }
            is RoomUiState.Error -> {
                ErrorContent(
                    message = state.message,
                    onDismiss = { viewModel.resetState() }
                )
            }
                else -> {}
            }
        }
    }
    
    // Join room dialog
    if (showJoinDialog) {
        JoinRoomDialog(
            roomId = joinRoomId,
            onRoomIdChange = { joinRoomId = it.uppercase() },
            onConfirm = {
                if (joinRoomId.isNotBlank()) {
                    pendingJoinRoomId = joinRoomId.trim().uppercase()
                    showJoinDialog = false
                    joinRoomId = ""
                    filePicker.launch("video/*")
                }
            },
            onDismiss = { 
                showJoinDialog = false
                joinRoomId = ""
            }
        )
    }
}

/**
 * Animated background circles with red-black theme
 */
@Composable
private fun AnimatedBackgroundElements() {
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    
    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset1"
    )
    
    val offset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -15f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset2"
    )
    
    // Primary red glow
    Box(
        modifier = Modifier
            .offset(x = (-50).dp + offset1.dp, y = 100.dp + offset2.dp)
            .size(200.dp)
            .blur(60.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        RosePrimary.copy(alpha = 0.35f),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
    )
    
    // Dark ruby glow
    Box(
        modifier = Modifier
            .offset(x = 250.dp + offset2.dp, y = 300.dp + offset1.dp)
            .size(180.dp)
            .blur(50.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        RoseDark.copy(alpha = 0.4f),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
    )
    
    // Ember orange accent
    Box(
        modifier = Modifier
            .offset(x = 50.dp + offset1.dp, y = 600.dp - offset2.dp)
            .size(150.dp)
            .blur(40.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentCoral.copy(alpha = 0.25f),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
    )
}

/**
 * Welcome screen with animated elements
 */
@Composable
private fun WelcomeContent(
    isVisible: Boolean,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onNavigateToMediaLibrary: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated logo
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(500)) + scaleIn(
                initialScale = 0.5f,
                animationSpec = spring(dampingRatio = 0.6f)
            )
        ) {
            AnimatedLogo()
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Title with animation
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(500, delayMillis = 200)) + 
                   slideInVertically(initialOffsetY = { 50 })
        ) {
            Text(
                text = "WithYou",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Subtitle
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(500, delayMillis = 400)) + 
                   slideInVertically(initialOffsetY = { 30 })
        ) {
            Text(
                text = "Watch Together, Even Apart",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = OnDarkSecondary
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        
        // Create Room button
        
        // Create Room button
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(500, delayMillis = 600)) + 
                   slideInVertically(initialOffsetY = { 50 })
        ) {
            GradientButton(
                text = "Create Room",
                icon = Icons.Default.Add,
                onClick = onCreateRoom
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Join Room button
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(500, delayMillis = 800)) + 
                   slideInVertically(initialOffsetY = { 50 })
        ) {
            OutlinedGradientButton(
                text = "Join Room",
                icon = Icons.Default.Link,
                onClick = onJoinRoom
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Media Library button
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(500, delayMillis = 1000)) + 
                   slideInVertically(initialOffsetY = { 50 })
        ) {
            OutlinedGradientButton(
                text = "Media Library",
                icon = Icons.Default.VideoLibrary,
                onClick = onNavigateToMediaLibrary
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Features list
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(500, delayMillis = 1000))
        ) {
            FeaturesList()
        }
    }
}

/**
 * Animated logo with film/play icon (Red-Black theme)
 */
@Composable
private fun AnimatedLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "logo")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    Box(
        contentAlignment = Alignment.Center
    ) {
        // Glow effect
        Box(
            modifier = Modifier
                .size(150.dp)
                .blur(40.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            RosePrimary.copy(alpha = glowAlpha),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // Main logo circle
        Box(
            modifier = Modifier
                .size(120.dp)
                .rotate(rotation)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(RosePrimary, RoseDark)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Film/Play icon
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.scale(iconScale)
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier
                        .size(70.dp)
                        .offset(x = (-2).dp, y = (-2).dp)
                )
                
                Icon(
                    imageVector = Icons.Default.PlayCircleFilled,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }
        }
    }
}

/**
 * Gradient button component with press animation
 */
@Composable
private fun GradientButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "buttonScale"
    )
    
    val infiniteTransition = rememberInfiniteTransition(label = "buttonGlow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    Box(contentAlignment = Alignment.Center) {
        // Glow behind button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .scale(1.05f)
                .alpha(glowAlpha)
                .blur(12.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(RosePrimary, RoseDark)
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
        )
        
        Button(
            onClick = {
                isPressed = true
                onClick()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .scale(scale),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(RosePrimary, RoseDark)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(150)
            isPressed = false
        }
    }
}

/**
 * Outlined gradient button
 */
@Composable
private fun OutlinedGradientButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(
                width = 2.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(RosePrimary, VioletSecondary)
                ),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent
        ),
        border = null
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RosePrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Features list
 */
@Composable
private fun FeaturesList() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        FeatureItem(icon = Icons.Outlined.Sync, text = "Real-time Sync")
        FeatureItem(icon = Icons.Outlined.Security, text = "Private Rooms")
        FeatureItem(icon = Icons.Outlined.Favorite, text = "Made for Two")
    }
}

@Composable
private fun FeatureItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = OnDarkSecondary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = OnDarkSecondary
        )
    }
}

/**
 * Loading content with animated progress
 */
@Composable
private fun LoadingContent(message: String, progress: Int? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Animated loading indicator
        val infiniteTransition = rememberInfiniteTransition(label = "loading")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing)
            ),
            label = "rotation"
        )
        
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(80.dp)
                    .rotate(rotation),
                color = RosePrimary,
                strokeWidth = 4.dp
            )
            
            if (progress != null) {
                Text(
                    text = "$progress%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White
        )
    }
}

/**
 * Extracting all audio tracks content - shown while extracting all tracks upfront
 */
@Composable
private fun ExtractingAllAudioTracksContent(progress: Int, totalTracks: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Music note animation
        val infiniteTransition = rememberInfiniteTransition(label = "audio")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        
        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background glow
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                RosePrimary.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            // Multiple music icons (representing multiple tracks)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.scale(scale)
            ) {
                repeat(minOf(3, totalTracks)) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = if (it == 0) RosePrimary else VioletSecondary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "Preparing Audio Tracks",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Extracting all $totalTracks audio track${if (totalTracks > 1) "s" else ""} for instant switching...",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Progress bar
        LinearProgressIndicator(
            progress = { progress / 100f },
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = RosePrimary,
            trackColor = Color.White.copy(alpha = 0.2f)
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "$progress%",
            style = MaterialTheme.typography.titleLarge,
            color = RosePrimary,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "This will enable instant audio track switching during playback",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Audio conversion content - shown while FFmpeg converts audio to AAC
 */
@Composable
private fun AudioConversionContent(progress: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Music note animation
        val infiniteTransition = rememberInfiniteTransition(label = "audio")
        val scale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.2f,
            animationSpec = infiniteRepeatable(
                animation = tween(500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale"
        )
        
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "rotation"
        )
        
        Box(
            modifier = Modifier.size(140.dp),
            contentAlignment = Alignment.Center
        ) {
            // Outer rotating ring
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .rotate(rotation)
                    .border(
                        width = 3.dp,
                        brush = Brush.sweepGradient(
                            colors = listOf(
                                VioletSecondary,
                                RosePrimary,
                                VioletSecondary.copy(alpha = 0.2f)
                            )
                        ),
                        shape = CircleShape
                    )
            )
            
            // Background glow
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                VioletSecondary.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )
            
            // Music icon with arrow
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = VioletSecondary,
                    modifier = Modifier
                        .size(40.dp)
                        .scale(scale)
                )
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = RosePrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Converting Audio",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Dolby/DTS → AAC",
            style = MaterialTheme.typography.titleMedium,
            color = VioletSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = "Converting to a compatible format...",
            style = MaterialTheme.typography.bodyMedium,
            color = OnDarkSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Progress bar with percentage
        Column(
            modifier = Modifier.fillMaxWidth(0.8f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (progress > 0) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = VioletSecondary,
                    trackColor = SurfaceVariantDark
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "$progress%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = VioletSecondary
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp)),
                    color = VioletSecondary,
                    trackColor = SurfaceVariantDark
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Starting...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnDarkSecondary
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Info card
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            colors = CardDefaults.cardColors(
                containerColor = SurfaceDark.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = RosePrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Why is this needed?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Your video uses Dolby/DTS audio which isn't supported on all devices. We're converting it to AAC for universal playback.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkSecondary
                    )
                }
            }
        }
    }
}

/**
 * File loaded content
 */
@Composable
private fun FileLoadedContent(
    metadata: com.withyou.app.utils.FileMetadata,
    isJoining: Boolean = false,
    roomIdToJoin: String? = null,
    onCreateRoom: () -> Unit,
    onJoinRoom: (String) -> Unit = {},
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Success icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(SuccessGreen.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(48.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = if (isJoining) "Ready to Join!" else "Video Ready!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // File info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = SurfaceVariantDark
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                FileInfoRow(
                    icon = Icons.Outlined.VideoFile,
                    label = "Name",
                    value = metadata.displayName
                )
                Spacer(modifier = Modifier.height(12.dp))
                FileInfoRow(
                    icon = Icons.Outlined.Timer,
                    label = "Duration",
                    value = com.withyou.app.utils.formatDuration(metadata.durationMs)
                )
                Spacer(modifier = Modifier.height(12.dp))
                FileInfoRow(
                    icon = Icons.Outlined.Storage,
                    label = "Size",
                    value = com.withyou.app.utils.formatFileSize(metadata.fileSize)
                )
                Spacer(modifier = Modifier.height(12.dp))
                FileInfoRow(
                    icon = Icons.Outlined.HighQuality,
                    label = "Quality",
                    value = metadata.codec.resolution
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (isJoining && roomIdToJoin != null) {
            // Joining badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = VioletSecondary.copy(alpha = 0.2f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        tint = VioletSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Joining: $roomIdToJoin",
                        color = VioletSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            GradientButton(
                text = "Join Room",
                icon = Icons.Default.Link,
                onClick = { onJoinRoom(roomIdToJoin) }
            )
        } else {
            GradientButton(
                text = "Create Room",
                icon = Icons.Default.Add,
                onClick = onCreateRoom
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        TextButton(onClick = onCancel) {
            Text("Cancel", color = OnDarkSecondary)
        }
    }
}

@Composable
private fun FileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = OnDarkSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = OnDarkSecondary,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Error content
 */
@Composable
private fun ErrorContent(message: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(ErrorRed.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = ErrorRed,
                modifier = Modifier.size(48.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Oops!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = OnDarkSecondary
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        GradientButton(
            text = "Try Again",
            icon = Icons.Default.Refresh,
            onClick = onDismiss
        )
    }
}

/**
 * Join room dialog
 */
@Composable
private fun JoinRoomDialog(
    roomId: String,
    onRoomIdChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = VioletSecondary
                )
                Text("Join Room")
            }
        },
        text = {
            Column {
                Text(
                    text = "Enter the 6-character room code",
                    color = OnDarkSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = roomId,
                    onValueChange = { if (it.length <= 6) onRoomIdChange(it) },
                    label = { Text("Room Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VioletSecondary,
                        focusedLabelColor = VioletSecondary,
                        cursorColor = VioletSecondary,
                        unfocusedTextColor = Color.White,
                        focusedTextColor = Color.White
                    ),
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        letterSpacing = 4.sp,
                        textAlign = TextAlign.Center
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = roomId.length >= 4,
                colors = ButtonDefaults.buttonColors(
                    containerColor = VioletSecondary
                )
            ) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = OnDarkSecondary)
            }
        }
    )
}

/**
 * Permission request content - shown when permissions are not granted
 */
@Composable
private fun PermissionRequestContent(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = RosePrimary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Permission Required",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "We need access to your videos to create rooms and browse your media library.",
            style = MaterialTheme.typography.bodyLarge,
            color = OnDarkSecondary,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onRequestPermission,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = RosePrimary
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Grant Permission",
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = SurfaceDark.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = VioletSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Why do we need this?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "To access your video files for creating rooms and browsing your media library.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkSecondary
                    )
                }
            }
        }
    }
}


