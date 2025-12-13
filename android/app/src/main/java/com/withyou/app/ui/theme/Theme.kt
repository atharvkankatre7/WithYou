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
// Romantic & Modern theme for couples
// ============================================

// Primary Colors - Rose/Pink tones
val RosePrimary = Color(0xFFE91E63)
val RoseLight = Color(0xFFFF6090)
val RoseDark = Color(0xFFB0003A)

// Secondary Colors - Deep Violet
val VioletSecondary = Color(0xFF7C3AED)
val VioletLight = Color(0xFFA855F7)
val VioletDark = Color(0xFF5B21B6)

// Accent Colors
val AccentGold = Color(0xFFFFD700)
val AccentCoral = Color(0xFFFF6B6B)
val AccentTeal = Color(0xFF14B8A6)

// Background Colors - Dark Mode
val BackgroundDark = Color(0xFF0A0A0F)
val SurfaceDark = Color(0xFF1A1A2E)
val SurfaceVariantDark = Color(0xFF2D2D44)
val CardDark = Color(0xFF16213E)

// Background Colors - Light Mode
val BackgroundLight = Color(0xFFFFFBFE)
val SurfaceLight = Color(0xFFF8F9FA)
val SurfaceVariantLight = Color(0xFFE9ECEF)
val CardLight = Color(0xFFFFFFFF)

// Text Colors
val OnDarkPrimary = Color(0xFFFFFFFF)
val OnDarkSecondary = Color(0xFFB8B8D0)
val OnLightPrimary = Color(0xFF1A1A2E)
val OnLightSecondary = Color(0xFF6C757D)

// Status Colors
val SuccessGreen = Color(0xFF4CAF50)
val WarningOrange = Color(0xFFFF9800)
val ErrorRed = Color(0xFFF44336)
val InfoBlue = Color(0xFF2196F3)

// Gradient Colors
val GradientStart = Color(0xFF1A1A2E)
val GradientMiddle = Color(0xFF16213E)
val GradientEnd = Color(0xFF0F3460)

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
    val violet = VioletSecondary
    val gold = AccentGold
    val coral = AccentCoral
    val teal = AccentTeal
    
    val gradientPrimary = listOf(RosePrimary, VioletSecondary)
    val gradientBackground = listOf(GradientStart, GradientMiddle, GradientEnd)
    val gradientCard = listOf(SurfaceDark, CardDark)
    
    val success = SuccessGreen
    val warning = WarningOrange
    val error = ErrorRed
    val info = InfoBlue
}
