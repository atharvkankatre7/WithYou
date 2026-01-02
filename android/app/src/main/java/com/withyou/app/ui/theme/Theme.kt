package com.withyou.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ============================================
// WithYou Custom Color Palette
// Bold Red & Black Premium Theme
// ============================================

// Primary Colors - Crimson Red tones
val RosePrimary = Color(0xFFDC143C)      // Crimson Red
val RoseLight = Color(0xFFFF4444)        // Bright Red
val RoseDark = Color(0xFF8B0000)         // Dark Ruby

// Secondary Colors - Deep Black with Red accents
val VioletSecondary = Color(0xFF2A2A2A)  // Charcoal Black
val VioletLight = Color(0xFFFF2D2D)      // Neon Red
val VioletDark = Color(0xFF0D0D0D)       // Deep Black

// Accent Colors
val AccentGold = Color(0xFFFFD700)       // Gold (kept)
val AccentCoral = Color(0xFFFF6B35)      // Ember Orange
val AccentTeal = Color(0xFFFF4757)       // Bright Coral Red

// Background Colors - Dark Mode (Pure Black)
val BackgroundDark = Color(0xFF0D0D0D)   // Jet Black
val SurfaceDark = Color(0xFF141414)      // Obsidian
val SurfaceVariantDark = Color(0xFF1C1C1E) // Dark Charcoal
val CardDark = Color(0xFF1A1A1A)         // Smoky Black

// Background Colors - Light Mode (Dark theme only for this app)
val BackgroundLight = Color(0xFF121212)
val SurfaceLight = Color(0xFF1E1E1E)
val SurfaceVariantLight = Color(0xFF2A2A2A)
val CardLight = Color(0xFF252525)

// Text Colors
val OnDarkPrimary = Color(0xFFFFFFFF)
val OnDarkSecondary = Color(0xFFB0B0B0)
val OnLightPrimary = Color(0xFFFFFFFF)
val OnLightSecondary = Color(0xFF909090)

// Status Colors
val SuccessGreen = Color(0xFF4CAF50)
val WarningOrange = Color(0xFFFF9800)
val ErrorRed = Color(0xFFFF4444)         // Brighter red for errors
val InfoBlue = Color(0xFF2196F3)

// Gradient Colors - Red to Black
val GradientStart = Color(0xFF0D0D0D)    // Pure Black
val GradientMiddle = Color(0xFF1A0A0A)   // Black with red tint
val GradientEnd = Color(0xFF2D0A0A)      // Dark red tint

// New: Ember/Particle Colors
val EmberRed = Color(0xFFFF6B6B)
val EmberOrange = Color(0xFFFF8C42)
val GlowRed = Color(0xFFFF4444)

// ============================================
// Color Schemes
// ============================================

private val DarkColorScheme = darkColorScheme(
    primary = RosePrimary,
    onPrimary = OnDarkPrimary,
    primaryContainer = RoseDark,
    onPrimaryContainer = RoseLight,
    
    secondary = VioletSecondary,
    onSecondary = OnDarkPrimary,
    secondaryContainer = VioletDark,
    onSecondaryContainer = VioletLight,
    
    tertiary = AccentTeal,
    onTertiary = OnDarkPrimary,
    
    background = BackgroundDark,
    onBackground = OnDarkPrimary,
    
    surface = SurfaceDark,
    onSurface = OnDarkPrimary,
    
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnDarkSecondary,
    
    error = ErrorRed,
    onError = OnDarkPrimary,
    
    outline = Color(0xFF3D3D5C),
    outlineVariant = Color(0xFF2D2D44)
)

private val LightColorScheme = lightColorScheme(
    primary = RosePrimary,
    onPrimary = OnDarkPrimary,
    primaryContainer = RoseLight.copy(alpha = 0.3f),
    onPrimaryContainer = RoseDark,
    
    secondary = VioletSecondary,
    onSecondary = OnDarkPrimary,
    secondaryContainer = VioletLight.copy(alpha = 0.3f),
    onSecondaryContainer = VioletDark,
    
    tertiary = AccentTeal,
    onTertiary = OnDarkPrimary,
    
    background = BackgroundLight,
    onBackground = OnLightPrimary,
    
    surface = SurfaceLight,
    onSurface = OnLightPrimary,
    
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnLightSecondary,
    
    error = ErrorRed,
    onError = OnDarkPrimary,
    
    outline = Color(0xFFDEE2E6),
    outlineVariant = Color(0xFFE9ECEF)
)

// ============================================
// Theme Composable
// ============================================

@Composable
fun ContentSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to use our custom colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    
    // Animate color changes
    val animatedColorScheme = colorScheme.copy(
        primary = animateColorAsState(
            targetValue = colorScheme.primary,
            animationSpec = tween(300),
            label = "primary"
        ).value,
        background = animateColorAsState(
            targetValue = colorScheme.background,
            animationSpec = tween(300),
            label = "background"
        ).value,
        surface = animateColorAsState(
            targetValue = colorScheme.surface,
            animationSpec = tween(300),
            label = "surface"
        ).value
    )
    
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = animatedColorScheme.background.toArgb()
            window.navigationBarColor = animatedColorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = animatedColorScheme,
        typography = Typography,
        content = content
    )
}

// ============================================
// Theme Extensions
// ============================================

// Custom color properties
object WithYouColors {
    val rose = RosePrimary
    val crimson = RosePrimary
    val black = VioletSecondary
    val gold = AccentGold
    val coral = AccentCoral
    val ember = AccentTeal
    
    // Red-Black gradients
    val gradientPrimary = listOf(RosePrimary, RoseDark)
    val gradientBackground = listOf(GradientStart, GradientMiddle, GradientEnd)
    val gradientCard = listOf(SurfaceDark, CardDark)
    val gradientEmber = listOf(EmberRed, EmberOrange)
    val gradientNeon = listOf(RoseLight, RosePrimary)
    
    val success = SuccessGreen
    val warning = WarningOrange
    val error = ErrorRed
    val info = InfoBlue
    
    // Ember particle colors
    val emberColors = listOf(EmberRed, EmberOrange, RoseLight, GlowRed)
}
