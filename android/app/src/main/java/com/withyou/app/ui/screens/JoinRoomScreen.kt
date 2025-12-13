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
import timber.log.Timber

/**
 * Join Room Screen - Handles room joining after video is selected
 * Playback should start only after room is joined
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinRoomScreen(
    roomId: String,
    viewModel: RoomViewModel,
    onNavigateBack: () -> Unit,
    onRoomJoined: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Auto-join room when screen appears (video should already be loaded)
    LaunchedEffect(Unit) {
        if (uiState is RoomUiState.FileLoaded) {
            Timber.d("JoinRoomScreen: File already loaded, joining room $roomId...")
            viewModel.joinRoom(roomId)
        } else {
            Timber.w("JoinRoomScreen: File not loaded yet, waiting...")
        }
    }
    
    // Navigate to room when ready
    LaunchedEffect(uiState) {
        if (uiState is RoomUiState.RoomReady) {
            val state = uiState as RoomUiState.RoomReady
            val isHost = viewModel.isHost.value
            if (!isHost) {
                Timber.d("JoinRoomScreen: Room joined, navigating to room screen")
                onRoomJoined(state.roomId)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Joining Room") },
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
                    // Show file info and join button
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Ready to Join Room",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Room: $roomId",
                            style = MaterialTheme.typography.bodyLarge,
                            color = VioletSecondary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = state.metadata.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnDarkSecondary
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = { viewModel.joinRoom(roomId) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VioletSecondary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Join Room")
                        }
                    }
                }
                is RoomUiState.JoiningRoom -> {
                    // Show loading
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = VioletSecondary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Joining room...",
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
                        CircularProgressIndicator(color = VioletSecondary)
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

