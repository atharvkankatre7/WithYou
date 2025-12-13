package com.withyou.app.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.lifecycle.viewmodel.compose.viewModel
import com.withyou.app.ui.theme.*
import com.withyou.app.viewmodel.RoomViewModel
import kotlinx.coroutines.delay

/**
 * Video Player Screen - Shows options when a video is selected
 * Displays: Play Solo / Create Room / Join Room
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    videoUri: Uri,
    roomViewModel: RoomViewModel,
    onNavigateBack: () -> Unit,
    onPlaySolo: () -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: (String) -> Unit
) {
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinRoomId by remember { mutableStateOf("") }
    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(100)
        isVisible = true
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Playback Mode") },
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
                        colors = listOf(BackgroundDark, SurfaceDark, CardDark)
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Video icon
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(animationSpec = tween(500)) + scaleIn(
                        initialScale = 0.5f,
                        animationSpec = spring(dampingRatio = 0.6f)
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(RosePrimary, VioletSecondary)
                                ),
                                shape = RoundedCornerShape(24.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Title
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(animationSpec = tween(500, delayMillis = 200)) +
                           slideInVertically(initialOffsetY = { 50 })
                ) {
                    Text(
                        text = "How would you like to watch?",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }
                
                Spacer(modifier = Modifier.height(48.dp))
                
                // Play Solo button
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(animationSpec = tween(500, delayMillis = 400)) +
                           slideInVertically(initialOffsetY = { 50 })
                ) {
                    Button(
                        onClick = onPlaySolo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
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
                                        colors = listOf(RosePrimary, VioletSecondary)
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
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Play Solo",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Watch offline, no sync",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Create Room button
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(animationSpec = tween(500, delayMillis = 600)) +
                           slideInVertically(initialOffsetY = { 50 })
                ) {
                    OutlinedButton(
                        onClick = onCreateRoom,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
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
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = RosePrimary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Create Room",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Start a watch party",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnDarkSecondary
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Join Room button
                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(animationSpec = tween(500, delayMillis = 800)) +
                           slideInVertically(initialOffsetY = { 50 })
                ) {
                    OutlinedButton(
                        onClick = { showJoinDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
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
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = RosePrimary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Join Room",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Enter room code",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnDarkSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Join room dialog
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
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
                        value = joinRoomId,
                        onValueChange = { if (it.length <= 6) joinRoomId = it.uppercase() },
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
                    onClick = {
                        if (joinRoomId.length >= 4) {
                            showJoinDialog = false
                            onJoinRoom(joinRoomId.trim().uppercase())
                        }
                    },
                    enabled = joinRoomId.length >= 4,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VioletSecondary
                    )
                ) {
                    Text("Join")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("Cancel", color = OnDarkSecondary)
                }
            }
        )
    }
}

