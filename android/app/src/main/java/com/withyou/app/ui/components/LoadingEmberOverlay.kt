package com.withyou.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.withyou.app.ui.theme.RosePrimary
import com.withyou.app.ui.theme.EmberRed
import com.withyou.app.ui.theme.EmberOrange
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Ember particle data class for loading animation
 */
private data class LoadingEmber(
    val id: Int,
    var x: Float,
    var y: Float,
    val size: Float,
    val speed: Float,
    val alpha: Float,
    val color: Color,
    val sway: Float
)

/**
 * Animated loading overlay with floating ember particles
 * Displayed during video buffering/loading states
 */
@Composable
fun LoadingEmberOverlay(
    visible: Boolean,
    message: String = "Loading...",
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            // Ember particle system
            val emberColors = listOf(EmberRed, EmberOrange, RosePrimary)
            val embers = remember {
                mutableStateListOf<LoadingEmber>().apply {
                    repeat(20) { id ->
                        add(
                            LoadingEmber(
                                id = id,
                                x = Random.nextFloat(),
                                y = Random.nextFloat() * 1.5f + 0.5f, // Start below screen
                                size = Random.nextFloat() * 6f + 3f,
                                speed = Random.nextFloat() * 0.003f + 0.002f,
                                alpha = Random.nextFloat() * 0.6f + 0.3f,
                                color = emberColors.random(),
                                sway = Random.nextFloat() * 0.001f
                            )
                        )
                    }
                }
            }
            
            // Animation time for ember movement
            val infiniteTransition = rememberInfiniteTransition(label = "embers")
            val animationProgress by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(16, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "emberProgress"
            )
            
            // Update ember positions
            LaunchedEffect(animationProgress) {
                embers.forEachIndexed { index, ember ->
                    // Move upward
                    ember.y -= ember.speed * 16f
                    // Add sway
                    ember.x += kotlin.math.sin((ember.y * 10f + ember.id).toDouble()).toFloat() * ember.sway
                    
                    // Reset if gone off screen
                    if (ember.y < -0.1f) {
                        embers[index] = ember.copy(
                            y = 1.1f,
                            x = Random.nextFloat()
                        )
                    }
                }
            }
            
            // Draw embers
            Canvas(modifier = Modifier.fillMaxSize()) {
                embers.forEach { ember ->
                    val x = ember.x * size.width
                    val y = ember.y * size.height
                    
                    // Glow effect
                    drawCircle(
                        color = ember.color.copy(alpha = ember.alpha * 0.3f),
                        radius = ember.size * 3f,
                        center = Offset(x, y)
                    )
                    
                    // Core ember
                    drawCircle(
                        color = ember.color.copy(alpha = ember.alpha),
                        radius = ember.size,
                        center = Offset(x, y)
                    )
                }
            }
            
            // Center content - loading indicator and message
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Animated circular progress with theme color
                CircularProgressIndicator(
                    color = RosePrimary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
                
                // Loading message
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * Buffering overlay - simpler version with just embers and no text
 */
@Composable
fun BufferingEmberOverlay(
    isBuffering: Boolean,
    modifier: Modifier = Modifier
) {
    LoadingEmberOverlay(
        visible = isBuffering,
        message = "Buffering...",
        modifier = modifier
    )
}
