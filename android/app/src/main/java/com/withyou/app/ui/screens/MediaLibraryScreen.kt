package com.withyou.app.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import android.graphics.BitmapFactory
import com.withyou.app.utils.ThumbnailExtractor
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.withyou.app.service.BackgroundAudioConverterService
import com.withyou.app.ui.theme.*
import com.withyou.app.utils.*
import com.withyou.app.viewmodel.MediaLibraryViewModel
import com.withyou.app.viewmodel.RoomViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Sort order enum
 */
enum class SortOrder {
    NAME_ASC,
    NAME_DESC,
    DATE_ADDED_DESC,
    DATE_ADDED_ASC,
    DURATION_DESC,
    DURATION_ASC,
    SIZE_DESC,
    SIZE_ASC
}

/**
 * Media Library Screen - Browse all videos grouped by naming patterns
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun MediaLibraryScreen(
    roomViewModel: RoomViewModel,
    mediaLibraryViewModel: MediaLibraryViewModel = viewModel(),
    onNavigateBack: () -> Unit = {},
    onVideoSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    val videos by mediaLibraryViewModel.videos.collectAsState()
    val groups by mediaLibraryViewModel.groups.collectAsState()
    val isScanning by mediaLibraryViewModel.isScanning.collectAsState()
    val scanProgress by mediaLibraryViewModel.scanProgress.collectAsState()
    val conversionStatus by mediaLibraryViewModel.conversionStatus.collectAsState()
    
    var selectedGroup by remember { mutableStateOf<VideoGroup?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    
    // Request permissions for Android 13+
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        listOf(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    
    val permissionsState = rememberMultiplePermissionsState(permissions)
    
    // Track if we've already requested permissions
    var hasRequestedPermissions by remember { mutableStateOf(false) }
    var lastPermissionState by remember { mutableStateOf(false) }
    
    // Request permissions on first load
    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted && !hasRequestedPermissions) {
            hasRequestedPermissions = true
            Timber.d("Requesting permissions on first load...")
            permissionsState.launchMultiplePermissionRequest()
        }
    }
    
    // Watch permission state changes more carefully
    LaunchedEffect(permissionsState.permissions) {
        val allGranted = permissionsState.permissions.all { it.status is PermissionStatus.Granted }
        val currentState = permissionsState.allPermissionsGranted
        
        Timber.d("Permission state changed - allGranted: $allGranted, allPermissionsGranted: $currentState, videos: ${videos.size}, scanning: $isScanning")
        Timber.d("Individual permissions: ${permissionsState.permissions.map { "${it.permission}=${it.status}" }}")
        
        // Check if permission state actually changed from false to true
        if (allGranted && currentState && !lastPermissionState && videos.isEmpty() && !isScanning) {
            lastPermissionState = true
            // Small delay to ensure permissions are fully applied by the system
            delay(500)
            Timber.i("✅ Permissions granted - starting video scan...")
            mediaLibraryViewModel.scanVideos()
        } else if (allGranted && currentState && videos.isEmpty() && !isScanning && !lastPermissionState) {
            // Backup: if we somehow missed the state change
            lastPermissionState = true
            delay(500)
            Timber.i("✅ Permissions granted (backup) - starting video scan...")
            mediaLibraryViewModel.scanVideos()
        }
        
        if (!allGranted) {
            lastPermissionState = false
        }
    }
    
    // Also watch allPermissionsGranted as a backup trigger
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted && videos.isEmpty() && !isScanning && !lastPermissionState) {
            lastPermissionState = true
            delay(500)
            Timber.i("✅ All permissions granted (LaunchedEffect backup) - starting video scan...")
            mediaLibraryViewModel.scanVideos()
        }
    }
    
    // Sorting state
    var sortOrder by remember { mutableStateOf(SortOrder.DATE_ADDED_DESC) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    // Apply sorting to groups
    val sortedGroups = remember(groups, sortOrder) {
        when (sortOrder) {
            SortOrder.NAME_ASC -> groups.sortedBy { it.title }
            SortOrder.NAME_DESC -> groups.sortedByDescending { it.title }
            SortOrder.DATE_ADDED_DESC -> groups.sortedByDescending { 
                it.videos.maxOfOrNull { video -> video.dateAdded } ?: 0L 
            }
            SortOrder.DATE_ADDED_ASC -> groups.sortedBy { 
                it.videos.minOfOrNull { video -> video.dateAdded } ?: Long.MAX_VALUE 
            }
            SortOrder.DURATION_DESC -> groups.sortedByDescending { 
                it.videos.sumOf { video -> video.duration } 
            }
            SortOrder.DURATION_ASC -> groups.sortedBy { 
                it.videos.sumOf { video -> video.duration } 
            }
            SortOrder.SIZE_DESC -> groups.sortedByDescending { 
                it.videos.sumOf { video -> video.size } 
            }
            SortOrder.SIZE_ASC -> groups.sortedBy { 
                it.videos.sumOf { video -> video.size } 
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Media Library") },
                navigationIcon = {
                    // No back button - this is the home screen
                },
                actions = {
                    // Sort menu
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Name (A-Z)") },
                            onClick = { sortOrder = SortOrder.NAME_ASC; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Name (Z-A)") },
                            onClick = { sortOrder = SortOrder.NAME_DESC; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Date Added (Newest)") },
                            onClick = { sortOrder = SortOrder.DATE_ADDED_DESC; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Date Added (Oldest)") },
                            onClick = { sortOrder = SortOrder.DATE_ADDED_ASC; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Duration (Longest)") },
                            onClick = { sortOrder = SortOrder.DURATION_DESC; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Duration (Shortest)") },
                            onClick = { sortOrder = SortOrder.DURATION_ASC; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Size (Largest)") },
                            onClick = { sortOrder = SortOrder.SIZE_DESC; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Size (Smallest)") },
                            onClick = { sortOrder = SortOrder.SIZE_ASC; showSortMenu = false }
                        )
                    }
                    IconButton(onClick = { mediaLibraryViewModel.scanVideos() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CardDark
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(BackgroundDark, SurfaceDark)
                    )
                )
        ) {
            when {
                isScanning -> {
                    // Scanning progress
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Scanning videos...", color = Color.White)
                        Text("$scanProgress%", color = Color.Gray, fontSize = 12.sp)
                    }
                }
                
                !permissionsState.allPermissionsGranted -> {
                    // Permission not granted
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
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Permission Required",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "We need access to your videos to show them in the media library.",
                            color = Color.Gray,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { permissionsState.launchMultiplePermissionRequest() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RosePrimary
                            )
                        ) {
                            Text("Grant Permission")
                        }
                    }
                }
                
                groups.isEmpty() && !isScanning -> {
                    // Empty state
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Outlined.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("No videos found", color = Color.White, fontSize = 18.sp)
                        Text(
                            "Scan your device for videos",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { 
                                // Force re-check permissions and scan
                                if (permissionsState.allPermissionsGranted) {
                                    mediaLibraryViewModel.scanVideos()
                                } else {
                                    permissionsState.launchMultiplePermissionRequest()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RosePrimary
                            )
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan Again")
                        }
                    }
                }
                
                else -> {
                    // Grouped video list
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Filter groups by search query
                        val filteredGroups = if (searchQuery.isBlank()) {
                            sortedGroups
                        } else {
                            sortedGroups.filter { group ->
                                group.title.contains(searchQuery, ignoreCase = true) ||
                                group.videos.any { it.name.contains(searchQuery, ignoreCase = true) }
                            }
                        }
                        
                        items(filteredGroups) { group ->
                            VideoGroupCard(
                                group = group,
                                conversionStatus = conversionStatus,
                                onClick = { selectedGroup = group },
                                onVideoClick = { video ->
                                    // Navigate to video player screen with options
                                    onVideoSelected(video.uri)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Group detail sheet
    selectedGroup?.let { group ->
        GroupDetailSheet(
            group = group,
            conversionStatus = conversionStatus,
            onDismiss = { selectedGroup = null },
            onVideoClick = { video ->
                // Navigate to video player screen with options
                onVideoSelected(video.uri)
            }
        )
    }
}

/**
 * Video group card
 */
@Composable
private fun VideoGroupCard(
    group: VideoGroup,
    conversionStatus: Map<String, MediaLibraryViewModel.ConversionStatus>,
    onClick: () -> Unit,
    onVideoClick: (VideoFile) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Group header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = group.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (group.type) {
                            VideoGroupType.RECENTLY_ADDED -> "${group.videos.size} recent videos"
                            VideoGroupType.TV_SHOW -> {
                                if (group.season != null) {
                                    "Season ${group.season} • ${group.videos.size} episodes"
                                } else {
                                    "${group.videos.size} episodes"
                                }
                            }
                            VideoGroupType.MOVIE -> "Movie"
                            VideoGroupType.CAMERA -> "${group.videos.size} videos"
                            VideoGroupType.SCREEN_RECORDING -> "${group.videos.size} recordings"
                            VideoGroupType.WHATSAPP -> "${group.videos.size} videos"
                            VideoGroupType.TELEGRAM -> "${group.videos.size} videos"
                            VideoGroupType.INSTAGRAM -> "${group.videos.size} videos"
                            VideoGroupType.DOWNLOADS -> "${group.videos.size} videos"
                            VideoGroupType.OTHER -> "${group.videos.size} videos"
                            VideoGroupType.UNKNOWN -> "${group.videos.size} videos" // Should never appear, but required for exhaustive when
                        },
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
                
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Video thumbnails row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(group.videos.take(5)) { video ->
                    VideoThumbnail(
                        video = video,
                        conversionStatus = conversionStatus[video.path],
                        onClick = { onVideoClick(video) }
                    )
                }
                
                if (group.videos.size > 5) {
                    item {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Gray.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+${group.videos.size - 5}",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Video thumbnail with actual video frame
 */
@Composable
private fun VideoThumbnail(
    video: VideoFile,
    conversionStatus: MediaLibraryViewModel.ConversionStatus?,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 80.dp
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var thumbnailBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoadingThumbnail by remember { mutableStateOf(true) }
    var showTooltip by remember { mutableStateOf(false) }
    var isLongPressing by remember { mutableStateOf(false) }
    
    // Load thumbnail
    LaunchedEffect(video.uri) {
        isLoadingThumbnail = true
        // Try cache first
        val cached = ThumbnailExtractor.getThumbnailFromCache(context, video.uri)
        if (cached != null) {
            thumbnailBitmap = cached
            isLoadingThumbnail = false
        } else {
            // Extract and cache in background
            scope.launch {
                val bitmap = ThumbnailExtractor.extractThumbnail(context, video.uri)
                if (bitmap != null) {
                    thumbnailBitmap = bitmap
                    ThumbnailExtractor.saveThumbnailToCache(context, video.uri, bitmap)
                }
                isLoadingThumbnail = false
            }
        }
    }
    
    // Auto-dismiss tooltip when long press is released
    LaunchedEffect(isLongPressing) {
        if (!isLongPressing && showTooltip) {
            kotlinx.coroutines.delay(100) // Small delay for smooth transition
            showTooltip = false
        }
    }
    
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Gray.copy(alpha = 0.2f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        isLongPressing = true
                        showTooltip = true
                    },
                    onTap = {
                        if (!showTooltip) {
                            onClick()
                        } else {
                            showTooltip = false
                            isLongPressing = false
                        }
                    },
                    onPress = {
                        // Track press state
                        isLongPressing = true
                        awaitRelease()
                        isLongPressing = false
                    }
                )
            }
    ) {
        // Thumbnail image or placeholder
        if (thumbnailBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = thumbnailBitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else if (isLoadingThumbnail) {
            // Loading indicator
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        } else {
            // Fallback icon
            Icon(
                Icons.Outlined.VideoFile,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp),
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
        
        // Status indicator
        conversionStatus?.let { status ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                when {
                    status.isConverting -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    status.isComplete -> {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Converted",
                            modifier = Modifier.size(16.dp),
                            tint = Color.Green
                        )
                    }
                }
            }
        }
        
        // Play icon overlay
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Play",
            modifier = Modifier
                .align(Alignment.Center)
                .size(24.dp)
                .background(
                    Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(4.dp),
            tint = Color.White
        )
        
        // Video details tooltip on long press with smooth animation
        AnimatedVisibility(
            visible = showTooltip,
            enter = fadeIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + scaleIn(
                initialScale = 0.8f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = fadeOut(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioHighBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            ) + scaleOut(
                targetScale = 0.8f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioHighBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            VideoDetailsTooltip(
                video = video,
                onDismiss = { 
                    showTooltip = false
                    isLongPressing = false
                },
                modifier = Modifier
            )
        }
    }
}

/**
 * Tooltip showing video details (name, size, duration)
 */
@Composable
private fun VideoDetailsTooltip(
    video: VideoFile,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .padding(bottom = 8.dp)
            .widthIn(max = 280.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.95f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onDismiss() }
                    )
                }
        ) {
            // Full video name
            Text(
                text = video.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Divider(color = Color.White.copy(alpha = 0.2f))
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Duration
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatDuration(video.duration),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // File size
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatFileSize(video.size),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp
                )
            }
        }
    }
}

/**
 * Group detail bottom sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDetailSheet(
    group: VideoGroup,
    conversionStatus: Map<String, MediaLibraryViewModel.ConversionStatus>,
    onDismiss: () -> Unit,
    onVideoClick: (VideoFile) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CardDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = group.title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(group.videos) { video ->
                    VideoListItem(
                        video = video,
                        conversionStatus = conversionStatus[video.path],
                        onClick = { onVideoClick(video) }
                    )
                }
            }
        }
    }
}

/**
 * Video list item
 */
@Composable
private fun VideoListItem(
    video: VideoFile,
    conversionStatus: MediaLibraryViewModel.ConversionStatus?,
    onClick: () -> Unit
) {
    var showTooltip by remember { mutableStateOf(false) }
    var isLongPressing by remember { mutableStateOf(false) }
    
    // Auto-dismiss tooltip when long press is released
    LaunchedEffect(isLongPressing) {
        if (!isLongPressing && showTooltip) {
            kotlinx.coroutines.delay(100) // Small delay for smooth transition
            showTooltip = false
        }
    }
    
    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            isLongPressing = true
                            showTooltip = true
                        },
                        onTap = {
                            if (!showTooltip) {
                                onClick()
                            } else {
                                showTooltip = false
                                isLongPressing = false
                            }
                        },
                        onPress = {
                            // Track press state
                            isLongPressing = true
                            awaitRelease()
                            isLongPressing = false
                        }
                    )
                },
            colors = CardDefaults.cardColors(containerColor = SurfaceDark)
        ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail with actual video frame
            VideoThumbnail(
                video = video,
                conversionStatus = conversionStatus,
                onClick = onClick,
                size = 60.dp
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Video info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatDuration(video.duration)} • ${formatFileSize(video.size)}",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
            
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        }
        
        // Video details tooltip on long press with smooth animation
        AnimatedVisibility(
            visible = showTooltip,
            enter = fadeIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + scaleIn(
                initialScale = 0.8f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = fadeOut(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioHighBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            ) + scaleOut(
                targetScale = 0.8f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioHighBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
        ) {
            VideoDetailsTooltip(
                video = video,
                onDismiss = { 
                    showTooltip = false
                    isLongPressing = false
                },
                modifier = Modifier
            )
        }
    }
}

