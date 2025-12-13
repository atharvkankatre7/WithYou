package com.withyou.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import com.withyou.app.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Split screen layout with video on left and chat on right
 * Video smoothly shrinks when chat opens
 */
@Composable
fun SplitScreenLayout(
    isChatOpen: Boolean,
    onChatToggle: () -> Unit,
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    videoContent: @Composable () -> Unit
) {
    val videoWeight by animateFloatAsState(
        targetValue = if (isChatOpen) 0.55f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "videoWeight"
    )
    
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video section (shrinks when chat opens)
        Box(
            modifier = Modifier
                .weight(videoWeight)
                .fillMaxHeight()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
        ) {
            videoContent()
        }
        
        // Chat section (appears on right)
        AnimatedVisibility(
            visible = isChatOpen,
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
            SideChatPanel(
                messages = messages,
                onSendMessage = onSendMessage,
                onClose = onChatToggle,
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight()
            )
        }
    }
}

/**
 * Side chat panel that appears next to video
 */
@Composable
fun SideChatPanel(
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    var messageText by remember { mutableStateOf("") }
    
    // Get IME (keyboard) insets to handle keyboard properly
    val imePadding = WindowInsets.ime.asPaddingValues()
    
    // Auto-scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // Auto-scroll when keyboard opens to keep input visible
    LaunchedEffect(imePadding.calculateBottomPadding().value) {
        if (imePadding.calculateBottomPadding().value > 0 && messages.isNotEmpty()) {
            // Small delay to ensure keyboard animation completes
            kotlinx.coroutines.delay(100)
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Column(
        modifier = modifier
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        SurfaceDark,
                        CardDark
                    )
                )
            )
            // Apply IME padding to entire Column in BOTH modes
            // This ensures the entire chat panel moves up above keyboard
            // and allows TextField to grow naturally without clipping
            .windowInsetsPadding(WindowInsets.ime)
    ) {
        // Header
        Surface(
            color = SurfaceDark,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = null,
                        tint = RosePrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Chat",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close chat",
                        tint = OnDarkSecondary
                    )
                }
            }
        }
        
        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    EmptyChatState()
                }
            } else {
                items(messages) { message ->
                    CompactChatBubble(message = message)
                }
            }
        }
        
        // Input field
        // NO IME padding here - Column already handles it
        // This allows TextField to grow naturally without height restrictions
        Surface(
            color = SurfaceVariantDark,
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                // Allow Row to resize vertically so TextField can grow
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Text input
                // Remove height constraints to ensure typed text is fully visible
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    placeholder = { 
                        Text(
                            "Type message...", 
                            color = OnDarkSecondary,
                            fontSize = 14.sp
                        ) 
                    },
                    modifier = Modifier
                        .weight(1f)
                        // Use defaultMinSize instead of heightIn to allow text to be fully visible
                        .defaultMinSize(minHeight = 44.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RosePrimary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.White.copy(alpha = 0.08f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.08f),
                        cursorColor = RosePrimary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (messageText.isNotBlank()) {
                                onSendMessage(messageText)
                                messageText = ""
                                focusManager.clearFocus() // Close keyboard after sending
                            }
                        }
                    ),
                    maxLines = 3
                )
                
                // Send button
                FloatingActionButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText)
                            messageText = ""
                            focusManager.clearFocus()
                        }
                    },
                    containerColor = RosePrimary,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Empty chat state
 */
@Composable
private fun EmptyChatState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ChatBubbleOutline,
            contentDescription = null,
            tint = OnDarkSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No messages yet",
            color = OnDarkSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "Start chatting with your partner!",
            color = OnDarkSecondary.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * Compact chat bubble for side panel
 */
@Composable
private fun CompactChatBubble(message: ChatMessage) {
    val isMe = message.isMe
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMe) 16.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 16.dp
            ),
            color = if (isMe) RosePrimary else SurfaceVariantDark,
            modifier = Modifier.widthIn(max = 260.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (!isMe) {
                    Text(
                        text = "Partner",
                        style = MaterialTheme.typography.labelSmall,
                        color = VioletSecondary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    fontSize = 14.sp
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = dateFormat.format(Date(message.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Quick reaction emojis for chat
 */
@Composable
fun QuickReactionBar(
    onReaction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val emojis = listOf("❤️", "😂", "👍", "😮", "🔥")
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        emojis.forEach { emoji ->
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.1f),
                modifier = Modifier
                    .size(36.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = emoji,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

