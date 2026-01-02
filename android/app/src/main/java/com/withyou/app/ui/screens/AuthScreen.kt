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
import androidx.compose.ui.draw.alpha
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
import kotlin.random.Random

/**
 * Authentication screen with dramatic red-black splash animation
 */
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var showContent by remember { mutableStateOf(false) }
    var showLogo by remember { mutableStateOf(false) }
    var showText by remember { mutableStateOf(false) }
    var showButton by remember { mutableStateOf(false) }
    var animationComplete by remember { mutableStateOf(false) } // Track when all animations are done
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    // Particle system state
    var particles by remember { mutableStateOf(listOf<EmberParticle>()) }
    
    // Sequential animation timeline
    LaunchedEffect(Unit) {
        delay(200)
        showLogo = true
        delay(600)
        showText = true
        delay(400)
        showButton = true
        showContent = true
        delay(1500) // Wait for typewriter effect and button animation to complete
        animationComplete = true // Now safe to navigate
        
        // Start spawning particles
        while (true) {
            if (particles.size < 20) {
                particles = particles + EmberParticle()
            }
            // Remove old particles
            particles = particles.filter { !it.isExpired() }
            delay(200)
        }
    }
    
    // Auto sign-in if already authenticated (wait for animation to complete)
    LaunchedEffect(animationComplete) {
        if (animationComplete) {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                onAuthSuccess()
            }
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        BackgroundDark,
                        GradientMiddle,
                        GradientEnd
                    )
                )
            )
    ) {
        // Animated ember particles
        EmberParticleSystem(particles = particles)
        
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
            
            // Animated logo with explosion effect
            AnimatedVisibility(
                visible = showLogo,
                enter = fadeIn(animationSpec = tween(400)) + 
                       scaleIn(initialScale = 0f, animationSpec = spring(
                           dampingRatio = 0.5f,
                           stiffness = Spring.StiffnessLow
                       ))
            ) {
                SplashLogo()
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // App name with typewriter effect
            AnimatedVisibility(
                visible = showText,
                enter = fadeIn(animationSpec = tween(300))
            ) {
                TypewriterText(
                    text = "WithYou",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Tagline
            AnimatedVisibility(
                visible = showText,
                enter = fadeIn(animationSpec = tween(500, delayMillis = 400)) + 
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
            
            // Sign in button with breathing glow
            AnimatedVisibility(
                visible = showButton,
                enter = fadeIn(animationSpec = tween(500)) + 
                       slideInVertically(initialOffsetY = { 50 })
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Continue button with glow
                    GlowingButton(
                        text = "Get Started",
                        isLoading = isLoading,
                        enabled = animationComplete, // Only enable after animation completes
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                try {
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
                        }
                    )
                    
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
                enter = fadeIn(animationSpec = tween(500, delayMillis = 200))
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
 * Ember particle data class
 */
data class EmberParticle(
    val id: String = java.util.UUID.randomUUID().toString(),
    val startX: Float = Random.nextFloat(),
    val startY: Float = 0.9f + Random.nextFloat() * 0.1f,
    val size: Float = 4f + Random.nextFloat() * 8f,
    val speed: Float = 0.3f + Random.nextFloat() * 0.4f,
    val color: Color = listOf(EmberRed, EmberOrange, RoseLight, GlowRed).random(),
    val createdAt: Long = System.currentTimeMillis()
) {
    fun isExpired(): Boolean = System.currentTimeMillis() - createdAt > 4000
}

/**
 * Ember particle system - floating red/orange embers
 */
@Composable
private fun EmberParticleSystem(particles: List<EmberParticle>) {
    Box(modifier = Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            EmberParticleView(particle = particle)
        }
    }
}

@Composable
private fun EmberParticleView(particle: EmberParticle) {
    val infiniteTransition = rememberInfiniteTransition(label = "ember_${particle.id}")
    
    val yOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween((4000 / particle.speed).toInt(), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "y_${particle.id}"
    )
    
    val xSway by infiniteTransition.animateFloat(
        initialValue = -20f,
        targetValue = 20f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "x_${particle.id}"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween((4000 / particle.speed).toInt(), easing = EaseOut),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha_${particle.id}"
    )
    
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_${particle.id}"
    )
    
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val xPos = maxWidth * particle.startX + xSway.dp
        val yPos = maxHeight * (particle.startY + yOffset)
        
        // Glow effect
        Box(
            modifier = Modifier
                .offset(x = xPos, y = yPos)
                .size((particle.size * glowPulse * 2).dp)
                .alpha(alpha * 0.5f)
                .blur(8.dp)
                .background(
                    color = particle.color.copy(alpha = 0.6f),
                    shape = CircleShape
                )
        )
        
        // Core ember
        Box(
            modifier = Modifier
                .offset(x = xPos + (particle.size / 2).dp, y = yPos + (particle.size / 2).dp)
                .size(particle.size.dp)
                .alpha(alpha)
                .background(
                    color = particle.color,
                    shape = CircleShape
                )
        )
    }
}

/**
 * Typewriter text effect
 */
@Composable
private fun TypewriterText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    fontWeight: FontWeight,
    color: Color
) {
    var displayedText by remember { mutableStateOf("") }
    
    LaunchedEffect(text) {
        displayedText = ""
        text.forEachIndexed { index, _ ->
            delay(80)
            displayedText = text.take(index + 1)
        }
    }
    
    Row {
        Text(
            text = displayedText,
            style = style,
            fontWeight = fontWeight,
            color = color
        )
        
        // Blinking cursor
        val infiniteTransition = rememberInfiniteTransition(label = "cursor")
        val cursorAlpha by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(500),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cursorAlpha"
        )
        
        if (displayedText.length < text.length) {
            Text(
                text = "|",
                style = style,
                fontWeight = fontWeight,
                color = RosePrimary.copy(alpha = cursorAlpha)
            )
        }
    }
}

/**
 * Glowing button with breathing animation
 */
@Composable
private fun GlowingButton(
    text: String,
    isLoading: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isLoading) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.7f),
        label = "scale"
    )
    
    // Animate button alpha based on enabled state
    val buttonAlpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.5f,
        animationSpec = tween(300),
        label = "buttonAlpha"
    )
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.alpha(buttonAlpha)
    ) {
        // Glow effect behind button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .scale(1.1f)
                .alpha(glowAlpha)
                .blur(16.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(RosePrimary, RoseDark)
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
        )
        
        // Main button
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .scale(scale),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(0.dp),
            enabled = enabled && !isLoading
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(RosePrimary, RoseDark)
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
                            text = text,
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
    }
}

/**
 * Animated background with floating red circles
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
    
    // Red glow circle
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
    
    // Dark red circle
    Box(
        modifier = Modifier
            .offset(x = 200.dp + offset2.dp, y = 250.dp + offset1.dp)
            .size(200.dp)
            .blur(70.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        RoseDark.copy(alpha = 0.5f),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
    )
    
    // Ember orange circle
    Box(
        modifier = Modifier
            .offset(x = 50.dp + offset3.dp, y = 550.dp - offset1.dp)
            .size(180.dp)
            .blur(60.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        EmberOrange.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                ),
                shape = CircleShape
            )
    )
}

/**
 * Splash logo with film/play icon and animations
 */
@Composable
private fun SplashLogo() {
    val infiniteTransition = rememberInfiniteTransition(label = "logo")
    
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "iconScale"
    )
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rotation"
    )
    
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )
    
    Box(
        contentAlignment = Alignment.Center
    ) {
        // Outer glow ring
        Box(
            modifier = Modifier
                .size(180.dp)
                .rotate(ringRotation)
                .blur(30.dp)
                .background(
                    brush = Brush.sweepGradient(
                        colors = listOf(
                            RosePrimary.copy(alpha = glowAlpha),
                            Color.Transparent,
                            RoseDark.copy(alpha = glowAlpha),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )
        
        // Glow effect
        Box(
            modifier = Modifier
                .size(160.dp)
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
                        colors = listOf(RosePrimary, RoseDark)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Film/Play icon instead of heart
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.scale(iconScale)
            ) {
                // Film strip effect (two rectangles)
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier
                        .size(80.dp)
                        .offset(x = (-2).dp, y = (-2).dp)
                )
                
                // Play icon overlay
                Icon(
                    imageVector = Icons.Default.PlayCircleFilled,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }
        }
    }
}
