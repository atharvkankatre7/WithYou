package com.withyou.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.withyou.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding

/**
 * Available reactions
 */
data class Reaction(
    val emoji: String,
    val name: String
)

val availableReactions = listOf(
    Reaction("❤️", "love"),
    Reaction("😂", "laugh"),
    Reaction("😮", "wow"),
    Reaction("😢", "sad"),
    Reaction("👏", "clap"),
    Reaction("🔥", "fire"),
    Reaction("😍", "heart_eyes"),
    Reaction("🎉", "party")
)

/**
 * Chat message data class
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isMe: Boolean = false
)

/**
 * Floating reaction data
 */
data class FloatingReaction(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String,
    val startX: Float = (Math.random() * 0.6 + 0.2).toFloat() // Random position 20-80%
)

/**
 * Reactions bar - Quick emoji reactions
 */
@Composable
fun ReactionsBar(
    onReaction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End
    ) {
        // Expanded reactions
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.8f)
                ),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    availableReactions.chunked(4).forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            row.forEach { reaction ->
                                ReactionButton(
                                    emoji = reaction.emoji,
                                    onClick = {
                                        onReaction(reaction.name)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        
        // Toggle button
        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = if (expanded) RosePrimary else Color.Black.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.EmojiEmotions,
                contentDescription = "Reactions",
                tint = Color.White
            )
        }
    }
}

/**
 * Individual reaction button
 */
@Composable
private fun ReactionButton(
    emoji: String,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.3f else 1f,
        animationSpec = spring(dampingRatio = 0.3f),
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable {
                isPressed = true
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = 24.sp
        )
    }
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(200)
            isPressed = false
        }
    }
}

/**
 * Floating reactions overlay
 */
@Composable
fun FloatingReactionsOverlay(
    reactions: List<FloatingReaction>,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        reactions.forEach { reaction ->
            FloatingEmojiAnimation(
                key = reaction.id,
                emoji = reaction.emoji,
                startXPercent = reaction.startX
            )
        }
    }
}

/**
 * Single floating emoji animation
 */
@Composable
private fun FloatingEmojiAnimation(
    key: String,
    emoji: String,
    startXPercent: Float
) {
    var isVisible by remember { mutableStateOf(true) }
    
    val animatedY by animateFloatAsState(
        targetValue = if (isVisible) 0f else -500f,
        animationSpec = tween(2500, easing = EaseOut),
        label = "y"
    )
    
    val animatedAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0f else 1f,
        animationSpec = tween(2500),
        label = "alpha"
    )
    
    val animatedScale by animateFloatAsState(
        targetValue = if (isVisible) 0.5f else 1.5f,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "scale"
    )
    
    LaunchedEffect(key) {
        isVisible = false
    }
    
    if (animatedY > -400) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val xOffset = maxWidth * startXPercent
            
            Text(
                text = emoji,
                fontSize = 36.sp,
                modifier = Modifier
                    .offset(x = xOffset, y = maxHeight + animatedY.dp)
                    .alpha(1f - (animatedAlpha * 0.8f))
                    .scale(animatedScale)
            )
        }
    }
}

/**
 * Simple chat input box for portrait mode when keyboard is open
 * Shows only the input field at the top, no messages list
 */
@Composable
fun ChatInputOnly(
    onSendMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    messageText: String = "",
    onMessageTextChange: ((String) -> Unit)? = null
) {
    val focusManager = LocalFocusManager.current
    val internalText = remember { mutableStateOf("") }
    val textToUse = if (onMessageTextChange != null) messageText else internalText.value
    val onTextChange: (String) -> Unit = if (onMessageTextChange != null) {
        { onMessageTextChange(it) }
    } else {
        { internalText.value = it }
    }
    
    // Don't auto-request focus - let the stable layout maintain focus naturally
    // The input field is always rendered, just hidden/shown with alpha, so focus is preserved
    
    // Get IME insets to position input at top
    val imeInsets = WindowInsets.ime
    
    Surface(
        color = SurfaceDark.copy(alpha = 0.95f),
        modifier = modifier
            .fillMaxWidth()
            // Position input box at top, above keyboard
            .windowInsetsPadding(imeInsets)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceVariantDark)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                OutlinedTextField(
                    value = textToUse,
                    onValueChange = onTextChange,
                    placeholder = { Text("Type a message...", color = OnDarkSecondary) },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 44.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RosePrimary,
                    unfocusedBorderColor = Color.Transparent,
                    focusedContainerColor = Color.White.copy(alpha = 0.1f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                    cursorColor = RosePrimary,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (textToUse.isNotBlank()) {
                            onSendMessage(textToUse)
                            onTextChange("")
                            focusManager.clearFocus()
                        }
                    }
                )
            )
            
            FloatingActionButton(
                onClick = {
                    if (textToUse.isNotBlank()) {
                        onSendMessage(textToUse)
                        onTextChange("")
                        focusManager.clearFocus()
                    }
                },
                containerColor = RosePrimary,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Chat overlay for portrait mode (normal layout with messages)
 */
@Composable
fun ChatOverlay(
    messages: List<ChatMessage>,
    onSendMessage: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showInputAtTop: Boolean = false,
    messageText: String = "",
    onMessageTextChange: ((String) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val internalText = remember { mutableStateOf("") }
    val textToUse = if (onMessageTextChange != null) messageText else internalText.value
    val onTextChange: (String) -> Unit = if (onMessageTextChange != null) {
        { onMessageTextChange(it) }
    } else {
        { internalText.value = it }
    }
    
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
    
    // Get IME insets for proper keyboard handling
    val imeInsets = WindowInsets.ime
    
    Card(
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = SurfaceDark.copy(alpha = 0.95f)
        ),
        modifier = modifier
            .fillMaxWidth()
            // Remove fillMaxHeight - let Card size naturally, keyboard will push it up
            // Apply IME padding ONLY on Card - this pushes entire chat above keyboard
            .windowInsetsPadding(imeInsets)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Chat",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
            
            Divider(color = Color.White.copy(alpha = 0.1f))
            
            // Messages list
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(messages) { message ->
                    ChatMessageBubble(message = message)
                }
            }
            
            // Input field - always rendered, but hidden with alpha when keyboard is open
            // This prevents layout changes that cause focus loss
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceVariantDark)
                    .padding(12.dp)
                    .alpha(if (showInputAtTop) 0f else 1f), // Hide with alpha instead of conditional rendering
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = textToUse,
                    onValueChange = onTextChange,
                    placeholder = { Text("Type a message...", color = OnDarkSecondary) },
                    modifier = Modifier
                        .weight(1f)
                        // Remove height constraints to allow text to be fully visible
                        .defaultMinSize(minHeight = 44.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RosePrimary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.White.copy(alpha = 0.1f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                        cursorColor = RosePrimary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (textToUse.isNotBlank()) {
                                onSendMessage(textToUse)
                                onTextChange("")
                                focusManager.clearFocus()
                            }
                        }
                    )
                )
                
                FloatingActionButton(
                    onClick = {
                        if (textToUse.isNotBlank()) {
                            onSendMessage(textToUse)
                            onTextChange("")
                            focusManager.clearFocus()
                        }
                    },
                    containerColor = RosePrimary,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Chat message bubble
 */
@Composable
private fun ChatMessageBubble(message: ChatMessage) {
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
            color = if (isMe) RosePrimary else SurfaceVariantDark
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
                    color = Color.White
                )
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = dateFormat.format(Date(message.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

/**
 * Quick chat button
 */
@Composable
fun ChatButton(
    unreadCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = "Chat",
                tint = Color.White
            )
        }
        
        // Unread badge
        if (unreadCount > 0) {
            Badge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp),
                containerColor = RosePrimary
            ) {
                Text(
                    text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/**
 * Participant indicator showing sync status
 */
@Composable
fun ParticipantIndicator(
    participantCount: Int,
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Black.copy(alpha = 0.6f),
        modifier = modifier.clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) SuccessGreen else WarningOrange)
            )
            
            // Participant count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.People,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "$participantCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

