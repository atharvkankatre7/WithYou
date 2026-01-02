package com.withyou.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Floating message notification that appears when a chat message is received
 * while the chat window is closed
 * 
 * @param message The message text to display
 * @param isLandscape Whether the screen is in landscape orientation
 * @param onDismiss Callback when the message should be dismissed
 */
@Composable
fun FloatingMessageNotification(
    message: String,
    isLandscape: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(false) }
    
    // Auto-dismiss after 3 seconds
    LaunchedEffect(message) {
        isVisible = true
        delay(3000)
        isVisible = false
        delay(300) // Wait for animation to complete
        onDismiss()
    }
    
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(300)
        ) + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 24.dp,
                    vertical = if (isLandscape) 16.dp else 24.dp
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.95f),
                fontSize = if (isLandscape) 14.sp else 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
            )
        }
    }
}
