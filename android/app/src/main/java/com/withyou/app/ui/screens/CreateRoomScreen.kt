package com.withyou.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.withyou.app.ui.theme.*
import com.withyou.app.viewmodel.RoomViewModel
import com.withyou.app.viewmodel.RoomUiState
import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Create Room Screen - Handles room creation after video is selected
 * Playback should start only after room is created
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoomScreen(
    viewModel: RoomViewModel,
    onNavigateBack: () -> Unit,
    onRoomCreated: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Auto-create room when screen appears (video should already be loaded)
    LaunchedEffect(Unit) {
        if (uiState is RoomUiState.FileLoaded) {
            Timber.d("CreateRoomScreen: File already loaded, creating room...")
            viewModel.createRoom()
        } else {
            Timber.w("CreateRoomScreen: File not loaded yet, waiting...")
        }
    }
    
    // Navigate to room when ready
    LaunchedEffect(uiState) {
        if (uiState is RoomUiState.RoomReady) {
            val state = uiState as RoomUiState.RoomReady
            val isHost = viewModel.isHost.value
            if (isHost) {
                Timber.d("CreateRoomScreen: Room created, navigating to room screen")
                onRoomCreated(state.roomId)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Creating Room") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            when (val state = uiState) {
                is RoomUiState.FileLoaded -> {
                    // Show file info and create button
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Ready to Create Room",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = state.metadata.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnDarkSecondary
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = { viewModel.createRoom() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RosePrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Create Room")
                        }
                    }
                }
                is RoomUiState.CreatingRoom -> {
                    // Show loading
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = RosePrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Creating your room...",
                            color = Color.White
                        )
                    }
                }
                is RoomUiState.Error -> {
                    // Show error
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            color = OnDarkSecondary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = onNavigateBack,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = RosePrimary
                            )
                        ) {
                            Text("Go Back")
                        }
                    }
                }
                else -> {
                    // Loading file
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = RosePrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading video...",
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

