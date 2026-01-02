package com.withyou.app.ui.screens

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.withyou.app.ui.components.LibVLCPlayerView
import com.withyou.app.player.LibVLCVideoPlayer
import com.withyou.app.ui.components.*
import com.withyou.app.ui.components.VideoSettingsSheet
import com.withyou.app.ui.components.AspectRatioOption
import org.videolan.libvlc.MediaPlayer
import com.withyou.app.ui.theme.*
import com.withyou.app.viewmodel.RoomViewModel
import com.withyou.app.viewmodel.VideoPlayerViewModel
import com.withyou.app.player.AspectMode
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import com.withyou.app.ui.utils.AutoRotationHelper
import com.withyou.app.service.RoomService
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * Room screen - Main playback and sync interface with enhanced video player
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    roomId: String,
    isHost: Boolean,
    viewModel: RoomViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    
    // Debounce job for double tap seeks to prevent spamming
    var doubleTapSeekJob by remember { mutableStateOf<Job?>(null) }
    var pendingSeekSeconds by remember { mutableIntStateOf(0) }
    
    // Create VideoPlayerViewModel for player control
    // This manages PlayerEngine and exposes PlayerUiState
    val playerViewModel: VideoPlayerViewModel = viewModel()
    
    // Collect player state from VideoPlayerViewModel (single source of truth)
    val playerUiState by playerViewModel.uiState.collectAsState()
    
    // Room-related state from RoomViewModel
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    
    // UI state (controls visibility, dialogs, etc.)
    // Note: showControls is now managed by VideoPlayerViewModel (PlayerUiState.showControls)
    var showShareDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showLeaveDialog by remember { mutableStateOf(false) }
    var showChat by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(true) } // Start in immersive fullscreen
    var showLockIndicator by remember { mutableStateOf(false) }
    var isUserInteractingWithControls by remember { mutableStateOf(false) } // Keep controls visible while user is interacting
    var isRotationLocked by remember { mutableStateOf(false) } // Rotation lock state
    var lockedOrientation by remember { mutableIntStateOf(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) } // Saved orientation when locked
    
    // Auto-rotation helper - detects device tilt and rotates even when system auto-rotate is off
    val autoRotationHelper = remember(activity) { 
        activity?.let { 
            try { AutoRotationHelper(it) } catch (e: Exception) { null }
        }
    }
    
    // Enable auto-rotation when entering room, disable when leaving
    DisposableEffect(autoRotationHelper) {
        try {
            autoRotationHelper?.enable()
        } catch (e: Exception) {
            Timber.e(e, "RoomScreen: Failed to enable auto-rotation")
        }
        onDispose {
            try {
                autoRotationHelper?.disable()
            } catch (e: Exception) {
                Timber.e(e, "RoomScreen: Failed to disable auto-rotation")
            }
        }
    }
    
    // Helper functions for user interaction - now delegate to VideoPlayerViewModel
    fun userInteractionStart() {
        isUserInteractingWithControls = true
        playerViewModel.onUserInteraction() // Show controls and reset auto-hide
    }
    
    fun userInteractionEnd() {
        isUserInteractingWithControls = false
        // Auto-hide is handled by VideoPlayerViewModel
    }
    var isPiPMode by remember { mutableStateOf(false) }
    
    // Aspect ratio state (as string for VLC controls) - derived from PlayerUiState
    val currentAspectRatio = remember(playerUiState.aspectMode) {
        when (playerUiState.aspectMode) {
            AspectMode.FIT -> "Fit"
            AspectMode.FIT_SCREEN -> "Fit Screen"
            AspectMode.FILL -> "Fill"
            AspectMode.ORIGINAL -> "Original"
            AspectMode.CUSTOM -> "Custom"
        }
    }
    
    // Chat state - observe from ViewModel
    val chatMessages by viewModel.chatMessages.collectAsState()
    var unreadCount by remember { mutableIntStateOf(0) }
    var lastSeenMessageCount by remember { mutableIntStateOf(0) }
    
    // Shared message text state for chat input (shared between ChatOverlay and ChatInputOnly)
    var sharedMessageText by remember { mutableStateOf("") }
    
    // Track unread messages when chat is closed
    LaunchedEffect(chatMessages.size, showChat) {
        if (showChat) {
            // Chat is open - mark all messages as read
            lastSeenMessageCount = chatMessages.size
            unreadCount = 0
        } else {
            // Chat is closed - count new messages since last seen
            val newMessages = chatMessages.size - lastSeenMessageCount
            if (newMessages > 0) {
                unreadCount = newMessages
            }
        }
    }
    
    // Floating reactions state - local add + remove when expired with user-specific colors
    var floatingReactions by remember { mutableStateOf<List<FloatingReaction>>(emptyList()) }
    
    // Floating message notification state (when chat is closed)
    var floatingMessage by remember { mutableStateOf<String?>(null) }
    
    // Define emoji colors for different users
    val myEmojiColor = RosePrimary // Pink/rose for current user
    val partnerEmojiColor = VioletSecondary // Blue/violet for partner
    
    // Collect partner reactions from ViewModel and display them
    val partnerReaction by viewModel.partnerReaction.collectAsState()
    LaunchedEffect(partnerReaction) {
        partnerReaction?.let { emoji ->
            // Add partner's emoji with partner color
            floatingReactions = floatingReactions + FloatingReaction(
                emoji = emoji,
                isMe = false,
                color = partnerEmojiColor
            )
            
            // Auto-remove after 2 seconds (animation duration)
            scope.launch {
                delay(2000)
                if (floatingReactions.isNotEmpty()) {
                    floatingReactions = floatingReactions.drop(1)
                }
            }
        }
    }
    
    // Check if PiP is supported
    val isPiPSupported = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        } else false
    }
    
    // Track if video was playing before going to background
    var wasPlayingBeforeBackground by remember { mutableStateOf(false) }
    
    // --- ROOM BACKGROUND PERSISTENCE ---
    // Start foreground service when entering room (keeps room alive in background)
    LaunchedEffect(roomId) {
        RoomService.start(context, roomId, isHost)
    }
    
    // Stop foreground service when leaving room
    DisposableEffect(Unit) {
        onDispose {
            RoomService.stop(context)
        }
    }
    
    // Handle app going to background/foreground using LifecycleEventObserver
    // - Background: Pause video (but keep socket connected via RoomService)
    // - Foreground: Resume video if it was playing
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    // App going to background
                    if (playerUiState.isPlaying) {
                        wasPlayingBeforeBackground = true
                        playerViewModel.getPlayerEngine()?.pause()
                        Timber.i("RoomScreen: App in background - pausing video")
                    }
                }
                Lifecycle.Event.ON_START -> {
                    // App returning to foreground
                    if (wasPlayingBeforeBackground) {
                        wasPlayingBeforeBackground = false
                        playerViewModel.getPlayerEngine()?.play()
                        Timber.i("RoomScreen: App in foreground - resuming video")
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // Handle room permissions - lock controls for non-host users
    // Maps to: VLC's permission system (if it had one)
    LaunchedEffect(isHost) {
        // Non-host users are externally locked (cannot control playback)
        playerViewModel.setExternalLocked(!isHost)
        Timber.d("RoomScreen: setExternalLocked(!isHost=$isHost) -> ${!isHost}")
    }
    
    // Load media when RoomViewModel loads a video
    // This coordinates between RoomViewModel (for sync) and VideoPlayerViewModel (for playback)
    LaunchedEffect(viewModel.uiState.value) {
        val roomState = viewModel.uiState.value
        if (roomState is com.withyou.app.viewmodel.RoomUiState.FileLoaded) {
            // RoomViewModel has loaded a file - load it into VideoPlayerViewModel too
            // Note: RoomViewModel still manages its own player for sync, but VideoPlayerViewModel
            // manages the UI-facing player. They should use the same VLCVideoLayout.
            // For now, we'll let RoomViewModel handle the initial load, and VideoPlayerViewModel
            // will be initialized when the layout is created.
        }
    }
    
    // Function to build audio label
    fun buildAudioLabel(language: String?, channels: Int, mimeType: String?): String {
        val parts = mutableListOf<String>()
        
        language?.let { 
            parts.add(it.uppercase())
        }
        
        when (channels) {
            1 -> parts.add("Mono")
            2 -> parts.add("Stereo")
            6 -> parts.add("5.1")
            8 -> parts.add("7.1")
            else -> if (channels > 0) parts.add("${channels}ch")
        }
        
        mimeType?.let { mime ->
            when {
                mime.contains("ac3") -> parts.add("AC3")
                mime.contains("eac3") -> parts.add("EAC3")
                mime.contains("dts") -> parts.add("DTS")
                mime.contains("aac") -> parts.add("AAC")
                mime.contains("opus") -> parts.add("Opus")
                mime.contains("mp3") -> parts.add("MP3")
                mime.contains("flac") -> parts.add("FLAC")
                else -> {} // Unknown codec
            }
        }
        
        return if (parts.isEmpty()) "Audio" else parts.joinToString(" • ")
    }
    
    // Audio track selection function - now handled by VLCPlayerControls directly
    // Removed - VLCPlayerControls handles this via onAudioTrackChange callback
    
    // Auto-hide controls is now managed by VideoPlayerViewModel
    // Controls visibility is in PlayerUiState.showControls
    // VideoPlayerViewModel handles auto-hide logic internally
    
    // Auto-hide lock indicator
    LaunchedEffect(showLockIndicator) {
        if (showLockIndicator) {
            delay(2000)
            showLockIndicator = false
        }
    }
    
    // Handle fullscreen - enhanced with immersive mode
    // Separate effects to avoid interfering with player during orientation changes
    LaunchedEffect(isFullscreen, isRotationLocked, lockedOrientation) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            
            if (isFullscreen) {
                // Handle orientation based on rotation lock (only when in fullscreen)
                if (isRotationLocked) {
                    // If rotation is locked, use the saved orientation
                    act.requestedOrientation = lockedOrientation
                } else {
                    // Allow free rotation even in fullscreen (don't force landscape)
                    act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                
                // Hide system bars with immersive mode
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.hide(WindowInsetsCompat.Type.navigationBars())
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.systemBarsBehavior = 
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                
                // Enable immersive sticky mode for better fullscreen experience
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.setDecorFitsSystemWindows(false)
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    )
                }
                
                // Keep screen on during playback
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                
                // Make status bar transparent
                window.statusBarColor = android.graphics.Color.TRANSPARENT
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            } else {
                // Not in fullscreen - handle orientation based on rotation lock
                if (isRotationLocked) {
                    // If rotation is locked, use the saved orientation
                    act.requestedOrientation = lockedOrientation
                } else {
                    // Allow rotation when not locked
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
                
                // Show system bars initially
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                insetsController.show(WindowInsetsCompat.Type.navigationBars())
                insetsController.show(WindowInsetsCompat.Type.statusBars())
                
                // Auto-hide status bar after 3 seconds when not in fullscreen
                scope.launch {
                    delay(3000)
                    if (!isFullscreen) {
                        insetsController.hide(WindowInsetsCompat.Type.statusBars())
                        insetsController.systemBarsBehavior = 
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    window.setDecorFitsSystemWindows(false) // Keep edge-to-edge even when not fullscreen
                } else {
                    @Suppress("DEPRECATION")
                    window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                }
                
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    
    // Separate effect for handling navigation bar visibility based on orientation
    // This doesn't interfere with player state
    LaunchedEffect(isLandscape) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            
            if (isLandscape) {
                // Hide navigation bars in landscape (works in both fullscreen and normal mode)
                insetsController.hide(WindowInsetsCompat.Type.navigationBars())
                insetsController.systemBarsBehavior = 
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            } else {
                // Show navigation bars in portrait
                insetsController.show(WindowInsetsCompat.Type.navigationBars())
                window.navigationBarColor = android.graphics.Color.TRANSPARENT
            }
        }
    }
    
    // Enter PiP mode function
    fun enterPiPMode() {
        if (isPiPSupported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.let { act ->
                try {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(Rational(16, 9))
                        .build()
                    act.enterPictureInPictureMode(params)
                    isPiPMode = true
                } catch (e: Exception) {
                    Timber.e(e, "Failed to enter PiP mode")
                }
            }
        }
    }
    
    // Handle back button
    BackHandler {
        when {
            showChat -> showChat = false
            showSettingsSheet -> showSettingsSheet = false
            isFullscreen -> isFullscreen = false
            playerUiState.isLocked -> showLockIndicator = true // Use PlayerUiState lock
            isPiPSupported -> enterPiPMode()
            else -> showLeaveDialog = true
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
    
    // Handle reaction - send emoji with user-specific color
    fun handleReaction(type: String) {
        val emoji = availableReactions.find { it.name == type }?.emoji ?: "❤️"
        // Add local emoji with "my" color (will also receive from server)
        floatingReactions = floatingReactions + FloatingReaction(
            emoji = emoji,
            isMe = true,
            color = myEmojiColor
        )
        
        // Auto-remove after 2 seconds (animation duration)
        scope.launch {
            delay(2000)
            if (floatingReactions.isNotEmpty()) {
                floatingReactions = floatingReactions.drop(1)
            }
        }
        
        // Send to server (will echo back to both users)
        viewModel.sendReaction(roomId, type)
    }
    
    // Handle chat message
    // Note: Message will appear in chatMessages when server echoes it back via SocketEvent.ChatMessage
    // This ensures proper message ordering and confirms delivery
    fun handleSendMessage(text: String) {
        Timber.i("🔴 [UI DEBUG] handleSendMessage called: text='$text', roomId=$roomId")
        Timber.i("🔴 [UI DEBUG] Current chatMessages count: ${chatMessages.size}")
        viewModel.sendChatMessage(roomId, text)
        Timber.i("🔴 [UI DEBUG] handleSendMessage completed")
    }
    
    // Listen for incoming chat messages when chat is closed
    LaunchedEffect(chatMessages.size, showChat) {
        if (!showChat && chatMessages.isNotEmpty()) {
            val lastMessage = chatMessages.lastOrNull()
            // Only show floating notification for partner's messages
            if (lastMessage != null && !lastMessage.isMe) {
                floatingMessage = lastMessage.text
                // Auto-dismiss after 3 seconds
                delay(3000)
                floatingMessage = null
            }
        }
    }
    
    // Handle external subtitle loading
    fun loadExternalSubtitle(uri: Uri) {
        Timber.d("Loading external subtitle: $uri")
        // Would need to implement subtitle loading from external file
    }
    
    // Main content - SINGLE video player that resizes when chat opens
    // Video is ALWAYS rendered (never recreated), only its size animates
    // Chat panel slides in/out using AnimatedVisibility
    
    // Animate video weight based on chat state
    val videoWeight by animateFloatAsState(
        targetValue = when {
            showChat && isLandscape -> 0.70f  // Landscape with chat: 70% video, 30% chat
            showChat && !isLandscape -> 0.5f  // Portrait with chat: 50% video
            else -> 1f                         // No chat: full screen
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow  // Faster animation for quick close
        ),
        label = "videoWeight"
    )
    
    // Use Row for landscape, Column for portrait
    if (isLandscape) {
        // Landscape layout: Video on left, Chat on right (side-by-side)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Video section - shrinks smoothly when chat opens
            // CRITICAL: key() ensures the video player composition is NEVER recreated
            // This prevents black screen when opening/closing chat
            Box(
                modifier = Modifier
                    .weight(videoWeight)
                    .fillMaxHeight()
            ) {
                key("video-player") {
                    VideoContentWithControls(
                    viewModel = viewModel,
                    playerViewModel = playerViewModel,
                    roomId = roomId,
                    isHost = isHost,
                    playerUiState = playerUiState,
                    showLockIndicator = showLockIndicator,
                    isFullscreen = isFullscreen,
                    isLandscape = isLandscape,
                    showChat = showChat,
                    connectionStatus = connectionStatus,
                    syncStatus = syncStatus,
                    floatingReactions = floatingReactions,
                    floatingMessage = floatingMessage,
                    unreadCount = unreadCount,
                    isPiPSupported = isPiPSupported,
                    isRotationLocked = isRotationLocked,
                    onToggleRotationLock = {
                        val act = activity
                        if (act != null) {
                            isRotationLocked = !isRotationLocked
                            if (isRotationLocked) {
                                lockedOrientation = when (configuration.orientation) {
                                    Configuration.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    Configuration.ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    else -> act.requestedOrientation
                                }
                                act.requestedOrientation = lockedOrientation
                            } else {
                                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }
                    },
                    onToggleControls = { 
                        if (playerUiState.isLocked) {
                            showLockIndicator = true
                        } else {
                            playerViewModel.toggleControls()
                        }
                    },
                    onDoubleTapSeek = { seconds ->
                        playerViewModel.hideControls()
                        doubleTapSeekJob?.cancel()
                        pendingSeekSeconds = seconds
                        doubleTapSeekJob = scope.launch {
                            delay(100)
                            val seekAmount = pendingSeekSeconds
                            if (seekAmount != 0) {
                                if (seekAmount > 0) {
                                    playerViewModel.seekForward(seekAmount, isHost = isHost)
                                } else {
                                    playerViewModel.seekBackward(-seekAmount, isHost = isHost)
                                }
                            }
                            delay(50)
                            playerViewModel.hideControls()
                        }
                    },
                    onSeek = { 
                        playerViewModel.onUserInteraction()
                        playerViewModel.onUserScrubbingStart()
                        playerViewModel.seekTo(it, isHost = isHost, fromUser = true)
                        playerViewModel.onUserScrubbingEnd()
                    },
                    onPlayPause = { 
                        playerViewModel.togglePlayPause(isHost = isHost)
                    },
                    onSeekForward = { seconds -> 
                        playerViewModel.seekForward(seconds, isHost = isHost) 
                    },
                    onSeekBackward = { seconds -> 
                        playerViewModel.seekBackward(seconds, isHost = isHost) 
                    },
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    onSpeedChange = { speed ->
                        playerViewModel.setPlaybackRate(speed, isHost = isHost)
                    },
                    onAspectRatioChange = { ratio ->
                        val mode = when (ratio) {
                            "Fit" -> AspectMode.FIT
                            "Fit Screen" -> AspectMode.FIT_SCREEN
                            "Fill" -> AspectMode.FILL
                            "Original" -> AspectMode.ORIGINAL
                            "16:9" -> AspectMode.CUSTOM
                            "4:3" -> AspectMode.CUSTOM
                            "21:9" -> AspectMode.CUSTOM
                            "1:1" -> AspectMode.CUSTOM
                            else -> AspectMode.FIT
                        }
                        playerViewModel.setAspectMode(mode, customRatio = if (mode == AspectMode.CUSTOM) ratio else null, isHost = isHost)
                    },
                    onAudioTrackChange = { trackId ->
                        playerViewModel.setAudioTrack(trackId, isHost = isHost)
                    },
                    onSubtitleTrackChange = { trackId ->
                        playerViewModel.setSubtitleTrack(trackId, isHost = isHost)
                    },
                    onLockControls = { 
                        playerViewModel.toggleLock()
                    },
                    onUnlock = { 
                        playerViewModel.toggleLock()
                        showLockIndicator = false
                    },
                    onShowShare = { showShareDialog = true },
                    onShowSettings = { showSettingsSheet = true },
                    onShowChat = { showChat = true; unreadCount = 0 },
                    onShowLeave = { showLeaveDialog = true },
                    onEnterPiP = { enterPiPMode() },
                    onReaction = { handleReaction(it) },
                    onBack = {
                        if (isFullscreen) isFullscreen = false
                        else if (isPiPSupported) enterPiPMode()
                        else showLeaveDialog = true
                    },
                    onUserInteractionStart = { userInteractionStart() },
                    onUserInteractionEnd = { userInteractionEnd() },
                    onDismissFloatingMessage = { floatingMessage = null }
                )
                } // Close key("video-player")
            }
            
            // Chat panel - slides in from right
            AnimatedVisibility(
                visible = showChat,
                enter = slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(),
                exit = slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.30f) // Fixed 30% width for chat panel
                ) {
                    SideChatPanel(
                        messages = chatMessages,
                        onSendMessage = { handleSendMessage(it) },
                        onClose = { showChat = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    } else {
        // Portrait layout: Video on top, Chat overlay below
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Video section - ALWAYS rendered, shrinks when chat opens
            // CRITICAL: key() ensures the video player composition is NEVER recreated
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(videoWeight)
                    .align(Alignment.TopCenter)
            ) {
                key("video-player-portrait") {
                    VideoContentWithControls(
                    viewModel = viewModel,
                    playerViewModel = playerViewModel,
                    roomId = roomId,
                    isHost = isHost,
                    playerUiState = playerUiState,
                    showLockIndicator = showLockIndicator,
                    isFullscreen = isFullscreen,
                    isLandscape = isLandscape,
                    showChat = showChat,
                    connectionStatus = connectionStatus,
                    syncStatus = syncStatus,
                    floatingReactions = floatingReactions,
                    floatingMessage = floatingMessage,
                    unreadCount = unreadCount,
                    isPiPSupported = isPiPSupported,
                    isRotationLocked = isRotationLocked,
                    onToggleRotationLock = {
                        val act = activity
                        if (act != null) {
                            isRotationLocked = !isRotationLocked
                            if (isRotationLocked) {
                                lockedOrientation = when (configuration.orientation) {
                                    Configuration.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    Configuration.ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                    else -> act.requestedOrientation
                                }
                                act.requestedOrientation = lockedOrientation
                            } else {
                                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }
                    },
                    onToggleControls = { 
                        if (playerUiState.isLocked) {
                            showLockIndicator = true
                        } else {
                            playerViewModel.toggleControls()
                        }
                    },
                    onDoubleTapSeek = { seconds ->
                        playerViewModel.hideControls()
                        doubleTapSeekJob?.cancel()
                        pendingSeekSeconds = seconds
                        doubleTapSeekJob = scope.launch {
                            delay(100)
                            val seekAmount = pendingSeekSeconds
                            if (seekAmount != 0) {
                                if (seekAmount > 0) {
                                    playerViewModel.seekForward(seekAmount, isHost = isHost)
                                } else {
                                    playerViewModel.seekBackward(-seekAmount, isHost = isHost)
                                }
                            }
                            delay(50)
                            playerViewModel.hideControls()
                        }
                    },
                    onSeek = { 
                        playerViewModel.onUserInteraction()
                        playerViewModel.onUserScrubbingStart()
                        playerViewModel.seekTo(it, isHost = isHost, fromUser = true)
                        playerViewModel.onUserScrubbingEnd()
                    },
                    onPlayPause = { 
                        playerViewModel.togglePlayPause(isHost = isHost)
                    },
                    onSeekForward = { seconds -> 
                        playerViewModel.seekForward(seconds, isHost = isHost) 
                    },
                    onSeekBackward = { seconds -> 
                        playerViewModel.seekBackward(seconds, isHost = isHost) 
                    },
                    onToggleFullscreen = { isFullscreen = !isFullscreen },
                    onSpeedChange = { speed ->
                        playerViewModel.setPlaybackRate(speed, isHost = isHost)
                    },
                    onAspectRatioChange = { ratio ->
                        val mode = when (ratio) {
                            "Fit" -> AspectMode.FIT
                            "Fit Screen" -> AspectMode.FIT_SCREEN
                            "Fill" -> AspectMode.FILL
                            "Original" -> AspectMode.ORIGINAL
                            "16:9" -> AspectMode.CUSTOM
                            "4:3" -> AspectMode.CUSTOM
                            "21:9" -> AspectMode.CUSTOM
                            "1:1" -> AspectMode.CUSTOM
                            else -> AspectMode.FIT
                        }
                        playerViewModel.setAspectMode(mode, customRatio = if (mode == AspectMode.CUSTOM) ratio else null, isHost = isHost)
                    },
                    onAudioTrackChange = { trackId ->
                        playerViewModel.setAudioTrack(trackId, isHost = isHost)
                    },
                    onSubtitleTrackChange = { trackId ->
                        playerViewModel.setSubtitleTrack(trackId, isHost = isHost)
                    },
                    onLockControls = { 
                        playerViewModel.toggleLock()
                    },
                    onUnlock = { 
                        playerViewModel.toggleLock()
                        showLockIndicator = false
                    },
                    onShowShare = { showShareDialog = true },
                    onShowSettings = { showSettingsSheet = true },
                    onShowChat = { showChat = true; unreadCount = 0 },
                    onShowLeave = { showLeaveDialog = true },
                    onEnterPiP = { enterPiPMode() },
                    onReaction = { handleReaction(it) },
                    onBack = {
                        if (isFullscreen) isFullscreen = false
                        else if (isPiPSupported) enterPiPMode()
                        else showLeaveDialog = true
                    },
                    onUserInteractionStart = { userInteractionStart() },
                    onUserInteractionEnd = { userInteractionEnd() },
                    onDismissFloatingMessage = { floatingMessage = null }
                )
                } // Close key("video-player-portrait")
            }
            
            // Chat panel - slides up from bottom in portrait
            AnimatedVisibility(
                visible = showChat,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.5f) // Fixed 50% height for chat panel
                ) {
                    ChatOverlay(
                        messages = chatMessages,
                        onSendMessage = { handleSendMessage(it) },
                        onClose = { showChat = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
    
    // Share dialog
    if (showShareDialog) {
        ShareRoomDialog(
            roomId = roomId,
            onDismiss = { showShareDialog = false },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Room ID", roomId))
            },
            onShare = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Join me on WithYou!")
                    putExtra(Intent.EXTRA_TEXT, "Join me on WithYou! 🎬\n\nRoom Code: $roomId\n\nDownload the app and enter this code to watch together!")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Room"))
                showShareDialog = false
            }
        )
    }
    
    // Leave room dialog
    if (showLeaveDialog) {
        AlertDialog(
            onDismissRequest = { showLeaveDialog = false },
            containerColor = SurfaceDark,
            icon = { Icon(Icons.Default.ExitToApp, null, tint = ErrorRed) },
            title = { Text("Leave Room?", color = Color.White) },
            text = { Text("Are you sure you want to leave?", color = OnDarkSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveDialog = false
                        viewModel.leaveRoom()
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed)
                ) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveDialog = false }) {
                    Text("Stay", color = Color.White)
                }
            }
        )
    }
    
    // Settings bottom sheet with all options
    // Now uses VideoPlayerViewModel for all player settings
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            containerColor = SurfaceDark
        ) {
            // Settings sheet - uses PlayerUiState from VideoPlayerViewModel
            VideoSettingsSheet(
                playbackSpeed = playerUiState.playbackRate, // From PlayerUiState
                currentAspectRatio = when (playerUiState.aspectMode) {
                    AspectMode.FIT -> AspectRatioOption.FIT
                    AspectMode.FIT_SCREEN -> AspectRatioOption.FIT_SCREEN
                    AspectMode.FILL -> AspectRatioOption.FILL
                    AspectMode.ORIGINAL -> AspectRatioOption.FIT // Map to FIT for settings
                    AspectMode.CUSTOM -> AspectRatioOption.RATIO_16_9 // Default for custom
                },
                subtitleTracks = playerUiState.subtitleTracks.map { 
                    com.withyou.app.ui.components.SubtitleTrack(
                        id = it.id,
                        language = it.name,
                        label = it.name
                    )
                },
                audioTracks = playerUiState.audioTracks.map {
                    com.withyou.app.ui.components.AudioTrack(
                        id = it.id,
                        language = it.name,
                        label = it.name,
                        channels = null
                    )
                },
                currentSubtitleId = playerUiState.currentSubtitleTrack?.id,
                currentAudioId = playerUiState.currentAudioTrack?.id,
                onSpeedChange = { speed ->
                    playerViewModel.setPlaybackRate(speed, isHost = isHost)
                },
                onAspectRatioChange = { ratio ->
                    // Convert AspectRatioOption to AspectMode
                    val mode = when (ratio) {
                        AspectRatioOption.FIT -> AspectMode.FIT
                        AspectRatioOption.FIT_SCREEN -> AspectMode.FIT_SCREEN
                        AspectRatioOption.FILL -> AspectMode.FILL
                        AspectRatioOption.RATIO_16_9 -> AspectMode.CUSTOM
                        AspectRatioOption.RATIO_4_3 -> AspectMode.CUSTOM
                        AspectRatioOption.RATIO_21_9 -> AspectMode.CUSTOM
                        AspectRatioOption.RATIO_1_1 -> AspectMode.CUSTOM
                    }
                    val customRatio = when (ratio) {
                        AspectRatioOption.RATIO_16_9 -> "16:9"
                        AspectRatioOption.RATIO_4_3 -> "4:3"
                        AspectRatioOption.RATIO_21_9 -> "21:9"
                        AspectRatioOption.RATIO_1_1 -> "1:1"
                        else -> null
                    }
                    playerViewModel.setAspectMode(mode, customRatio = customRatio, isHost = isHost)
                },
                onSubtitleSelect = { trackId ->
                    if (trackId != null) {
                        playerViewModel.setSubtitleTrack(trackId, isHost = isHost)
                    }
                },
                onAudioSelect = { trackId ->
                    if (trackId != null) {
                        playerViewModel.setAudioTrack(trackId, isHost = isHost)
                    }
                },
                onLoadExternalSubtitle = { /* TODO: Implement external subtitle loading */ },
                isLocked = playerUiState.isLocked,
                onLockControls = { 
                    playerViewModel.toggleLock()
                },
                onClose = { showSettingsSheet = false }
            )
        }
    }
}

/**
 * Video content with all controls
 * 
 * Now uses VideoPlayerViewModel for player state and control.
 * RoomViewModel is still used for sync/socket communication.
 * 
 * Architecture:
 * - VideoPlayerViewModel: Manages PlayerEngine, exposes PlayerUiState
 * - RoomViewModel: Manages sync/socket, still needs player for sync events
 * - Both ViewModels coordinate: VideoPlayerViewModel for UI, RoomViewModel for sync
 */
@Composable
private fun VideoContentWithControls(
    viewModel: RoomViewModel, // For sync/socket
    playerViewModel: VideoPlayerViewModel, // For playback control
    roomId: String,
    isHost: Boolean,
    playerUiState: com.withyou.app.player.PlayerUiState, // Single source of truth for player state (includes showControls)
    showLockIndicator: Boolean,
    isFullscreen: Boolean,
    isLandscape: Boolean,
    showChat: Boolean,
    connectionStatus: String,
    syncStatus: String,
    floatingReactions: List<FloatingReaction>,
    floatingMessage: String?, // Floating message notification when chat is closed
    unreadCount: Int,
    isPiPSupported: Boolean,
    isRotationLocked: Boolean,
    onToggleRotationLock: () -> Unit,
    onToggleControls: () -> Unit,
    onDoubleTapSeek: (Int) -> Unit,
    onSeek: (Long) -> Unit,
    onPlayPause: () -> Unit,
    onSeekForward: (Int) -> Unit,
    onSeekBackward: (Int) -> Unit,
    onToggleFullscreen: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onAspectRatioChange: (String) -> Unit,
    onAudioTrackChange: (Int) -> Unit,
    onSubtitleTrackChange: (Int) -> Unit,
    onLockControls: () -> Unit,
    onUnlock: () -> Unit,
    onShowShare: () -> Unit,
    onShowSettings: () -> Unit,
    onShowChat: () -> Unit,
    onShowLeave: () -> Unit,
    onEnterPiP: () -> Unit,
    onReaction: (String) -> Unit,
    onBack: () -> Unit,
    onUserInteractionStart: () -> Unit,
    onUserInteractionEnd: () -> Unit,
    onDismissFloatingMessage: () -> Unit // Callback to dismiss floating message
) {
    // Observe video size for aspect ratio (from RoomViewModel for sync)
    val videoSize by viewModel.videoSize.collectAsState()
    
    BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // VLCVideoLayout should fill the parent - it handles aspect ratio internally via ScaleType
        val videoModifier = Modifier.fillMaxSize()

        // Update surface dimensions in VideoPlayerViewModel for aspect ratio calculations
        LaunchedEffect(constraints.maxWidth, constraints.maxHeight) {
            playerViewModel.updateSurfaceDimensions(constraints.maxWidth, constraints.maxHeight)
        }

        // Set scale mode based on PlayerUiState.aspectMode
        // This coordinates with VideoPlayerViewModel's aspect ratio management
        LaunchedEffect(playerUiState.aspectMode, constraints.maxWidth, constraints.maxHeight) {
            // VideoPlayerViewModel already handles aspect ratio, but we also update RoomViewModel
            // for sync purposes (if needed). For now, VideoPlayerViewModel is the source of truth.
            // RoomViewModel's setScaleMode is kept for backward compatibility but may not be needed.
        }
        
        VideoGestureHandler(
            isHost = isHost, // Pass raw isHost - gesture handler will check lock
            currentPosition = playerUiState.position, // From PlayerUiState
            duration = playerUiState.duration, // From PlayerUiState
            onSeek = onSeek,
            onToggleControls = onToggleControls,
            onDoubleTapSeek = onDoubleTapSeek,
            onCenterDoubleTap = if (isHost && !playerUiState.isLocked) onPlayPause else null,
            isLocked = playerUiState.isLocked // Pass lock state to disable gestures
        ) {
            LibVLCPlayerView(
                onLayoutCreated = { videoLayout ->
                    // Initialize VideoPlayerViewModel with the layout (creates PlayerEngine)
                    playerViewModel.initPlayer(videoLayout)
                    
                    // Once PlayerEngine is created, share it with RoomViewModel for sync
                    // This ensures there's only ONE player instance
                    playerViewModel.getPlayerEngine()?.let { engine ->
                        viewModel.setSharedPlayerEngine(engine)
                        
                        // Wire PlaybackSyncController back to VideoPlayerViewModel
                        // This allows VideoPlayerViewModel to notify sync when host performs actions
                        val syncController = viewModel.getSyncController()
                        if (syncController != null) {
                            playerViewModel.setSyncController(syncController)
                            Timber.d("RoomScreen: Wired PlaybackSyncController to VideoPlayerViewModel")
                        }
                    }
                },
                modifier = videoModifier
            )
        }
        
        // Buffering overlay with animated embers
        BufferingEmberOverlay(
            isBuffering = playerUiState.isBuffering,
            modifier = Modifier.fillMaxSize()
        )
        
        // Unlock overlay - always visible when locked (VLC pattern: show unlock UI even when controls hidden)
        if (playerUiState.isLocked) {
            UnlockOverlay(
                onUnlock = onUnlock,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Controls overlays - split into separate groups to avoid blocking gestures
        // Each overlay only occupies its actual visual space, allowing touches in empty areas
        // to pass through to VideoGestureHandler
        // Note: Controls are hidden when locked (VLC pattern)
        if (!playerUiState.isLocked && playerUiState.showControls) {
            // Top overlay - top bar, role badge, sync status
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(250)),
                exit = fadeOut(animationSpec = tween(250)),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    VideoTopBar(
                        roomId = roomId,
                        connectionStatus = connectionStatus,
                        isHost = isHost,
                        onBack = onBack,
                        onShare = onShowShare,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                    
                    // Role badge - responsive
                    BoxWithConstraints {
                        val density = LocalDensity.current
                        val screenWidthDp = with(density) { constraints.maxWidth.toDp() }
                        val scaleFactor = (screenWidthDp.value / 360.dp.value).coerceIn(0.85f, 1.4f)
                        
                        val topPadding = ((72.dp.value * scaleFactor).coerceIn(60f, 90f)).dp
                        val horizontalPadding = ((16.dp.value * scaleFactor).coerceIn(12f, 24f)).dp
                        val badgeSpacing = ((8.dp.value * scaleFactor).coerceIn(6f, 12f)).dp
                        val badgeTextSize = ((12.sp.value * scaleFactor).coerceIn(10f, 16f)).sp
                        val badgePadding = ((10.dp.value * scaleFactor).coerceIn(8f, 14f)).dp
                        val dotSize = ((8.dp.value * scaleFactor).coerceIn(6f, 10f)).dp
                        
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                                .padding(start = horizontalPadding, top = topPadding),
                            horizontalArrangement = Arrangement.spacedBy(badgeSpacing),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RoleBadge(isHost = isHost)
                        
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.6f)
                        ) {
                            Row(
                                    modifier = Modifier.padding(horizontal = badgePadding, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                    modifier = Modifier
                                            .size(dotSize)
                                        .clip(CircleShape)
                        .background(
                                            if (connectionStatus.contains("Room")) SuccessGreen
                                            else WarningOrange
                                        )
                                )
                                    Text("You", color = Color.White, fontSize = badgeTextSize, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                    
                    // Sync status for followers - responsive
                    if (!isHost) {
                        BoxWithConstraints {
                            val density = LocalDensity.current
                            val screenWidthDp = with(density) { constraints.maxWidth.toDp() }
                            val scaleFactor = (screenWidthDp.value / 360.dp.value).coerceIn(0.85f, 1.4f)
                            
                            val topPadding = ((72.dp.value * scaleFactor).coerceIn(60f, 90f)).dp
                            val horizontalPadding = ((16.dp.value * scaleFactor).coerceIn(12f, 24f)).dp
                            
                        SyncStatusCard(
                            syncStatus = syncStatus,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                    .padding(end = horizontalPadding, top = topPadding)
                        )
                        }
                    }
                }
            }
            
            // Right side buttons overlay - responsive
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(250)),
                exit = fadeOut(animationSpec = tween(250)),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                BoxWithConstraints {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val screenWidthDp = with(density) { constraints.maxWidth.toDp() }
                    val scaleFactor = (screenWidthDp.value / 360.dp.value).coerceIn(0.85f, 1.4f)
                    
                    val buttonSize = ((44.dp.value * scaleFactor).coerceIn(40f, 56f)).dp
                    val iconSize = ((20.dp.value * scaleFactor).coerceIn(18f, 26f)).dp
                    val buttonSpacing = ((8.dp.value * scaleFactor).coerceIn(6f, 12f)).dp
                    val horizontalPadding = ((12.dp.value * scaleFactor).coerceIn(10f, 16f)).dp
                    val bottomPadding = if (isLandscape) ((70.dp.value * scaleFactor).coerceIn(60f, 90f)).dp else 0.dp
                    
                Column(
                    modifier = Modifier
                            .padding(end = horizontalPadding, bottom = bottomPadding),
                        verticalArrangement = Arrangement.spacedBy(buttonSpacing)
                ) {
                    ReactionsBar(onReaction = onReaction)
                    
                    ChatButton(unreadCount = unreadCount, onClick = onShowChat)
                    
                    if (isPiPSupported) {
                        FloatingActionButton(
                            onClick = onEnterPiP,
                            containerColor = Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier.size(buttonSize)
                        ) {
                                Icon(Icons.Default.PictureInPicture, "PiP", tint = Color.White, modifier = Modifier.size(iconSize))
                        }
                    }
                    
                    FloatingActionButton(
                        onClick = onShowLeave,
                        containerColor = ErrorRed.copy(alpha = 0.9f),
                            modifier = Modifier.size(buttonSize)
                    ) {
                            Icon(Icons.Default.ExitToApp, "Leave", tint = Color.White, modifier = Modifier.size(iconSize))
                        }
                    }
                }
            }
            
            // Bottom VLC controls overlay - anchored to bottom
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(250)),
                exit = fadeOut(animationSpec = tween(250)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                VLCPlayerControls(
                    uiState = playerUiState,
                    isHost = isHost,
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
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // Floating reactions overlay - shows emojis floating up over video
        FloatingReactionsOverlay(
            reactions = floatingReactions,
            modifier = Modifier.fillMaxSize()
        )
        
        // Floating message notification - shows chat messages when chat is closed  
        floatingMessage?.let { message ->
            FloatingMessageNotification(
                message = message,
                isLandscape = isLandscape,
                onDismiss = onDismissFloatingMessage,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}

/**
 * Unlock overlay - shown when screen is locked
 * Shows a lock icon that toggles an unlock box when tapped
 */
@Composable
private fun UnlockOverlay(
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showLockIcon by remember { mutableStateOf(false) }
    var showUnlockBox by remember { mutableStateOf(false) }
    
    // Auto-hide lock icon after 2 seconds if not interacted with
    // Reset timer when lock icon appears or when unlock box is shown
    LaunchedEffect(showLockIcon, showUnlockBox) {
        if (showLockIcon && !showUnlockBox) {
            delay(2000) // 2 seconds
            // Only hide if still showing lock icon and unlock box wasn't opened
            if (showLockIcon && !showUnlockBox) {
                showLockIcon = false
            }
        }
    }
    
    BoxWithConstraints(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { _ ->
                        if (showUnlockBox) {
                            // If unlock box is visible, tap outside hides both
                            showUnlockBox = false
                            showLockIcon = false
                        } else if (showLockIcon) {
                            // If only lock icon is visible, tap outside hides it
                            showLockIcon = false
                        } else {
                            // If nothing is visible, tap shows lock icon
                            showLockIcon = true
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Calculate responsive sizes
        val density = androidx.compose.ui.platform.LocalDensity.current
        val screenWidthDp = with(density) { constraints.maxWidth.toDp() }
        val scaleFactor = (screenWidthDp.value / 360.dp.value).coerceIn(0.85f, 1.4f)
        
        val lockIconSize = ((56.dp.value * scaleFactor).coerceIn(48f, 72f)).dp
        val lockIconInnerSize = ((28.dp.value * scaleFactor).coerceIn(24f, 36f)).dp
        val unlockBoxWidth = ((200.dp.value * scaleFactor).coerceIn(180f, 280f)).dp
        val unlockBoxPadding = ((24.dp.value * scaleFactor).coerceIn(20f, 32f)).dp
        val unlockIconSize = ((40.dp.value * scaleFactor).coerceIn(36f, 52f)).dp
        val unlockTextSize = ((16.sp.value * scaleFactor).coerceIn(14f, 20f)).sp
        val unlockBoxOffset = ((80.dp.value * scaleFactor).coerceIn(70f, 100f)).dp
        val cardPadding = ((16.dp.value * scaleFactor).coerceIn(12f, 20f)).dp
        
        // Transparent overlay to catch taps outside unlock box
        // Only visible when unlock box is shown
        if (showUnlockBox) {
            Box(
                    modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Transparent)
                    .clickable { 
                        // Tap outside -> hide both unlock box and lock icon
                        showUnlockBox = false
                        showLockIcon = false
                    }
            )
        }
        
        // Lock icon - appears when user taps screen
        AnimatedVisibility(
            visible = showLockIcon,
            enter = scaleIn(animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            IconButton(
                onClick = { 
                    // Toggle unlock box visibility
                    showUnlockBox = !showUnlockBox
                },
                modifier = Modifier
                    .size(lockIconSize)
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Lock",
                    tint = Color.White,
                    modifier = Modifier.size(lockIconInnerSize)
                )
            }
        }
        
        // Unlock box - appears when lock icon is tapped
        AnimatedVisibility(
            visible = showUnlockBox,
            enter = scaleIn(animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier
                .offset(y = unlockBoxOffset) // Position below lock icon
        ) {
            Card(
                modifier = Modifier
                    .padding(cardPadding)
                    .border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clickable { 
                        onUnlock()
                        showUnlockBox = false
                        showLockIcon = false
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.9f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(unlockBoxPadding)
                        .width(unlockBoxWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.LockOpen,
                        contentDescription = "Unlock",
                        tint = Color.White,
                        modifier = Modifier.size(unlockIconSize)
                    )
                    Text(
                        text = "Tap to Unlock",
                        color = Color.White,
                        fontSize = unlockTextSize,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncStatusCard(syncStatus: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(Icons.Default.Sync, null, tint = SuccessGreen, modifier = Modifier.size(14.dp))
            Text(syncStatus, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ShareRoomDialog(
    roomId: String,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
        AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Share, null, tint = RosePrimary)
                Text("Share Room", color = Color.White)
            }
        },
            text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Share this room code:", color = OnDarkSecondary)
                Surface(shape = RoundedCornerShape(16.dp), color = SurfaceVariantDark) {
                    Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        roomId.forEach { char ->
                            Surface(shape = RoundedCornerShape(8.dp), color = CardDark) {
                        Text(
                                    char.toString(),
                                    style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                                    color = RosePrimary,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                            }
                        }
                    }
                    }
                }
            },
            confirmButton = {
            Button(onClick = onShare, colors = ButtonDefaults.buttonColors(containerColor = RosePrimary)) {
                Icon(Icons.Default.Share, null)
                Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
            },
            dismissButton = {
            OutlinedButton(onClick = { onCopy(); onDismiss() }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                Icon(Icons.Default.ContentCopy, null)
                Spacer(Modifier.width(8.dp))
                Text("Copy")
            }
        }
    )
}

// Helper function at file level
private fun buildAudioLabel(language: String?, channels: Int, mimeType: String?): String {
    val parts = mutableListOf<String>()
    
    language?.let { 
        parts.add(it.uppercase())
    }
    
    when (channels) {
        1 -> parts.add("Mono")
        2 -> parts.add("Stereo")
        6 -> parts.add("5.1")
        8 -> parts.add("7.1")
        else -> if (channels > 0) parts.add("${channels}ch")
    }
    
    mimeType?.let { mime ->
        when {
            mime.contains("ac3") -> parts.add("AC3")
            mime.contains("eac3") -> parts.add("EAC3")
            mime.contains("dts") -> parts.add("DTS")
            mime.contains("aac") -> parts.add("AAC")
            mime.contains("opus") -> parts.add("Opus")
            mime.contains("mp3") -> parts.add("MP3")
            mime.contains("flac") -> parts.add("FLAC")
            else -> {} // Unknown codec
        }
    }
    
    return if (parts.isEmpty()) "Audio" else parts.joinToString(" • ")
}
