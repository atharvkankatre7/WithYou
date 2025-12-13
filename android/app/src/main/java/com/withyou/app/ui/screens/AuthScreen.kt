package com.withyou.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.withyou.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Authentication screen with beautiful splash animation
 */
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var showContent by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Show content after a brief delay
    LaunchedEffect(Unit) {
        delay(300)
        showContent = true
        
        // Auto sign-in if already authenticated
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            delay(800)
            onAuthSuccess()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        BackgroundDark,
                        SurfaceDark,
                        CardDark
                    )
                )
            )
    ) {
        // Animated background elements
        AnimatedBackground()
        
        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.weight(0.3f))
            
            // Animated logo
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(animationSpec = tween(800)) + 
                       scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = 0.6f))
            ) {
                SplashLogo()
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // App name
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 400)) + 
                       slideInVertically(initialOffsetY = { 50 })
            ) {
                Text(
                    text = "WithYou",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tagline
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 600)) + 
                       slideInVertically(initialOffsetY = { 30 })
            ) {
                Text(
                    text = "Watch Together, Even Apart",
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnDarkSecondary,
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.weight(0.3f))
            
            // Sign in button
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 800)) + 
                       slideInVertically(initialOffsetY = { 50 })
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Continue button
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                try {
                                    // Anonymous sign in
                                    val auth = FirebaseAuth.getInstance()
                                    if (auth.currentUser == null) {
                                        auth.signInAnonymously()
                                            .addOnCompleteListener { task ->
                                                isLoading = false
                                                if (task.isSuccessful) {
                                                    Timber.i("Anonymous sign in successful")
                                                    onAuthSuccess()
                                                } else {
                                                    Timber.e(task.exception, "Sign in failed")
                                                    errorMessage = task.exception?.message ?: "Sign in failed"
                                                }
                                            }
                                    } else {
                                        delay(500)
                                        isLoading = false
                                        onAuthSuccess()
                                    }
                                } catch (e: Exception) {
                                    isLoading = false
                                    errorMessage = e.message
                                    Timber.e(e, "Auth error")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent
                        ),
                        contentPadding = PaddingValues(0.dp),
                        enabled = !isLoading
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
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(24.dp)
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Get Started",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowForward,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                    
                    // Error message
                    AnimatedVisibility(visible = errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = ErrorRed,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Terms text
            AnimatedVisibility(
                visible = showContent,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 1000))
            ) {
                Text(
                    text = "By continuing, you agree to our Terms of Service and Privacy Policy",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnDarkSecondary.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

/**
 * Animated background with floating circles
 */
@Composable
private fun AnimatedBackground() {
    val infiniteTransition = rememberInfiniteTransition(label = "background")
    
    val offset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 30f,
        animationSpec = infiniteRepeatable(
            animation = tween(5000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset1"
    )
    
    val offset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -25f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset2"
    )
    
    val offset3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offset3"
    )
    
    // Pink circle
    Box(
        modifier = Modifier
            .offset(x = (-80).dp + offset1.dp, y = 120.dp + offset2.dp)
            .size(250.dp)
            .blur(80.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        RosePrimary.copy(alpha = 0.4f),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
    )
    
    // Purple circle
    Box(
        modifier = Modifier
            .offset(x = 200.dp + offset2.dp, y = 250.dp + offset1.dp)
            .size(200.dp)
            .blur(70.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        VioletSecondary.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
    )
    
    // Teal circle
    Box(
        modifier = Modifier
            .offset(x = 50.dp + offset3.dp, y = 550.dp - offset1.dp)
            .size(180.dp)
            .blur(60.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AccentTeal.copy(alpha = 0.25f),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
    )
}

/**
 * Splash logo with animations
 */
@Composable
private fun SplashLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "logo")
    
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartScale"
    )
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    Box(
        contentAlignment = Alignment.Center
    ) {
        // Glow effect
        Box(
            modifier = Modifier
                .size(180.dp)
                .blur(40.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            RosePrimary.copy(alpha = glowAlpha),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // Main logo circle
        Box(
            modifier = Modifier
                .size(140.dp)
                .rotate(rotation)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(RosePrimary, VioletSecondary)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Heart icon
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(64.dp)
                    .scale(heartScale)
            )
            
            // Play overlay
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(36.dp)
                    .offset(x = 2.dp)
            )
        }
    }
}
