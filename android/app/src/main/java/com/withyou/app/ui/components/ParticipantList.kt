package com.withyou.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.withyou.app.ui.theme.*
import java.security.MessageDigest

/**
 * Participant data class
 */
data class Participant(
    val id: String,
    val userId: String,
    val role: String, // "host" or "follower"
    val isConnected: Boolean = true,
    val syncStatus: String = "Synced",
    val rtt: Long = 0
)

/**
 * Participant list bottom sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantListSheet(
    participants: List<Participant>,
    currentUserId: String,
    roomId: String,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Participants",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = VioletSecondary.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "${participants.size} in room",
                        style = MaterialTheme.typography.labelMedium,
                        color = VioletSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Room code
            Text(
                text = "Room: $roomId",
                style = MaterialTheme.typography.bodyMedium,
                color = OnDarkSecondary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Participant list
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(participants) { participant ->
                    ParticipantItem(
                        participant = participant,
                        isCurrentUser = participant.userId == currentUserId
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Individual participant item
 */
@Composable
private fun ParticipantItem(
    participant: Participant,
    isCurrentUser: Boolean
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (participant.role == "host") 
                RosePrimary.copy(alpha = 0.1f) 
            else 
                SurfaceVariantDark
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = getAvatarColors(participant.userId)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getInitials(participant.userId),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isCurrentUser) "You" else "Partner",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    
                    // Role badge
                    if (participant.role == "host") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = RosePrimary
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Host",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (participant.isConnected) SuccessGreen else WarningOrange
                            )
                    )
                    Text(
                        text = participant.syncStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = OnDarkSecondary
                    )
                    
                    if (participant.rtt > 0) {
                        Text(
                            text = "• ${participant.rtt}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnDarkSecondary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
            
            // Connection indicator
            if (participant.isConnected) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "alpha"
                )
                
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = SuccessGreen.copy(alpha = alpha),
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = ErrorRed,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Generate avatar colors based on user ID
 */
private fun getAvatarColors(userId: String): List<Color> {
    val hash = userId.hashCode()
    val colors = listOf(
        listOf(RosePrimary, VioletSecondary),
        listOf(VioletSecondary, AccentTeal),
        listOf(AccentCoral, RosePrimary),
        listOf(AccentTeal, VioletLight),
        listOf(RoseLight, VioletLight)
    )
    return colors[Math.abs(hash) % colors.size]
}

/**
 * Get initials from user ID
 */
private fun getInitials(userId: String): String {
    return userId.take(2).uppercase()
}

/**
 * Compact participant count indicator
 */
@Composable
fun ParticipantCountBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = VioletSecondary.copy(alpha = 0.2f),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.People,
                contentDescription = null,
                tint = VioletSecondary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                color = VioletSecondary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

