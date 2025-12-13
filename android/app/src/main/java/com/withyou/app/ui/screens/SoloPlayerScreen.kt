package com.withyou.app.ui.screens

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.View
import android.view.WindowManager
import android.app.Activity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.withyou.app.ui.components.LibVLCPlayerView
import com.withyou.app.ui.components.VLCPlayerControls
import com.withyou.app.ui.components.VideoGestureHandler
import com.withyou.app.ui.theme.*
import com.withyou.app.viewmodel.VideoPlayerViewModel
import com.withyou.app.player.AspectMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Solo Player Screen - Offline playback without room/sync logic
 * This is a clean, distraction-free media player
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoloPlayerScreen(
    videoUri: Uri,
    onNavigateBack: () -> Unit
) {
    val playerViewModel: VideoPlayerViewModel = viewModel()
    val playerUiState by playerViewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as? Activity
    
    // Get video name from URI
    val videoName = remember(videoUri) {
        val contentResolver = context.contentResolver
        when (videoUri.scheme) {
            "file" -> {
                val path = videoUri.path
                if (path != null) {
                    java.io.File(path).name
                } else {
                    "Video"
                }
            }
            "content" -> {
                contentResolver.query(videoUri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0 && cursor.moveToFirst()) {
                        cursor.getString(nameIndex) ?: "Video"
                    } else {
                        "Video"
                    }
                } ?: "Video"
            }
            else -> "Video"
        }
    }
    
    // Hide system bars in solo mode (fullscreen-like experience)
    LaunchedEffect(Unit) {
        activity?.let { act ->
            val window = act.window
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            
            // Hide system bars with immersive mode
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.hide(WindowInsetsCompat.Type.navigationBars())
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
            insetsController.systemBarsBehavior = 
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            
            // Enable immersive sticky mode
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
            
            // Make status bar and navigation bar transparent
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }
    
    // Cleanup system bars on exit
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                val window = act.window
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                
                // Show system bars
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                insetsController.show(WindowInsetsCompat.Type.navigationBars())
                insetsController.show(WindowInsetsCompat.Type.statusBars())
                
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    
    // User interaction state for auto-hiding controls
    var userInteractionStart by remember { mutableStateOf(System.currentTimeMillis()) }
    
    // Helper functions for player control
    fun handlePlayPause() {
        playerViewModel.togglePlayPause(isHost = true) // Solo mode acts as host
    }
    
    fun handleSeek(positionMs: Long) {
        playerViewModel.seekTo(positionMs, isHost = true, fromUser = true)
    }
    
    fun handleSeekForward(seconds: Int) {
        playerViewModel.seekForward(seconds, isHost = true)
    }
    
    fun handleSeekBackward(seconds: Int) {
        playerViewModel.seekBackward(seconds, isHost = true)
    }
    
    fun handleSpeedChange(speed: Float) {
        playerViewModel.setPlaybackRate(speed, isHost = true)
    }
    
    fun handleAspectRatioChange(ratio: String) {
        val mode = when (ratio) {
            "Fit" -> AspectMode.FIT
            "Fill" -> AspectMode.FILL
            "Original" -> AspectMode.ORIGINAL
            "16:9" -> AspectMode.CUSTOM
            "4:3" -> AspectMode.CUSTOM
            "21:9" -> AspectMode.CUSTOM
            "1:1" -> AspectMode.CUSTOM
            else -> AspectMode.FIT
        }
        playerViewModel.setAspectMode(mode, customRatio = if (mode == AspectMode.CUSTOM) ratio else null, isHost = true)
    }
    
    fun handleAudioTrackChange(trackId: Int) {
        playerViewModel.setAudioTrack(trackId, isHost = true)
    }
    
    fun handleSubtitleTrackChange(trackId: Int) {
        playerViewModel.setSubtitleTrack(trackId, isHost = true)
    }
    
    fun handleUserInteraction() {
        userInteractionStart = System.currentTimeMillis()
        playerViewModel.onUserInteraction()
    }
    
    fun handleUserInteractionStart() {
        userInteractionStart = System.currentTimeMillis()
        playerViewModel.onUserInteraction()
    }
    
    fun handleUserInteractionEnd() {
        playerViewModel.onUserInteraction()
    }
    
    // Load video after player is initialized
    LaunchedEffect(videoUri) {
        Timber.d("SoloPlayerScreen: Loading video URI: $videoUri")
        // Wait a bit for player to initialize, then load video
        delay(100)
        playerViewModel.getPlayerEngine()?.let { engine ->
            engine.loadMedia(videoUri)
            // Auto-play in solo mode
            engine.play()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video player with gesture handling
        VideoGestureHandler(
            isHost = true, // Solo mode acts as host
            currentPosition = playerUiState.position,
            duration = playerUiState.duration,
            onSeek = { positionMs ->
                handleUserInteraction()
                handleSeek(positionMs)
            },
            onToggleControls = {
                playerViewModel.toggleControls()
            },
            onDoubleTapSeek = { seconds ->
                playerViewModel.hideControls()
                handleSeekForward(seconds)
            },
            onCenterDoubleTap = { handlePlayPause() },
            isLocked = playerUiState.isLocked
        ) {
            LibVLCPlayerView(
                onLayoutCreated = { videoLayout ->
                    Timber.d("SoloPlayerScreen: VLCVideoLayout created")
                    // Initialize player with the layout
                    playerViewModel.initPlayer(videoLayout)
                    
                    // Surface dimensions will be updated automatically by VideoPlayerViewModel
                    
                    // Load and play video after a small delay to ensure player is ready
                    scope.launch {
                        delay(200)
                        playerViewModel.getPlayerEngine()?.let { engine ->
                            engine.loadMedia(videoUri)
                            // Auto-play in solo mode
                            engine.play()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Top bar - shows video name and back button (only when controls are visible)
        AnimatedVisibility(
            visible = playerUiState.showControls && !playerUiState.isLocked,
            enter = fadeIn(animationSpec = tween(250)),
            exit = fadeOut(animationSpec = tween(250)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = videoName,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        
        // Bottom VLC controls overlay
        if (playerUiState.showControls && !playerUiState.isLocked) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(250)),
                exit = fadeOut(animationSpec = tween(250)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                VLCPlayerControls(
                    uiState = playerUiState,
                    isHost = true, // Solo mode acts as host
                    onPlayPause = { handlePlayPause() },
                    onSeek = { positionMs -> handleSeek(positionMs) },
                    onSeekForward = { handleSeekForward(10) },
                    onSeekBackward = { handleSeekBackward(10) },
                    onSpeedChange = { speed -> handleSpeedChange(speed) },
                    onAspectRatioChange = { ratio -> handleAspectRatioChange(ratio) },
                    onAudioTrackChange = { trackId -> handleAudioTrackChange(trackId) },
                    onSubtitleTrackChange = { trackId -> handleSubtitleTrackChange(trackId) },
                    onLockControls = { playerViewModel.toggleLock() },
                    isRotationLocked = false, // No rotation lock in solo mode
                    onToggleRotationLock = {}, // No-op in solo mode
                    onUserInteractionStart = { handleUserInteractionStart() },
                    onUserInteractionEnd = { handleUserInteractionEnd() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    
    // Cleanup on exit
    DisposableEffect(Unit) {
        onDispose {
            Timber.d("SoloPlayerScreen: Cleaning up")
            playerViewModel.getPlayerEngine()?.getPlayer()?.stop()
        }
    }
}

