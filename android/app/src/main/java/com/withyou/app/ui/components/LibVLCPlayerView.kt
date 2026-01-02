package com.withyou.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.util.VLCVideoLayout
import timber.log.Timber

/**
 * Compose wrapper for VLCVideoLayout for LibVLC
 * VLCVideoLayout handles video rendering and aspect ratio properly
 * 
 * CRITICAL: This view must NEVER be recreated during chat animations
 * Uses remember + DisposableEffect to ensure the same VLCVideoLayout instance
 * is used across all recompositions, preventing black screens
 */
@Composable
fun LibVLCPlayerView(
    onLayoutCreated: (VLCVideoLayout) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Remember the video layout across ALL recompositions
    // This ensures the same instance is used even when parent size changes
    val videoLayout = remember {
        VLCVideoLayout(context).apply {
            Timber.d("LibVLCPlayerView: VLCVideoLayout created (ONCE)")
            // Make the view non-interactive so gestures can pass through to VideoGestureHandler
            isClickable = false
            isFocusable = false
            // Keep view alive during recomposition
            keepScreenOn = true
        }
    }
    
    // Call onLayoutCreated only once when the layout is first created
    DisposableEffect(Unit) {
        Timber.d("LibVLCPlayerView: Calling onLayoutCreated")
        onLayoutCreated(videoLayout)
        
        onDispose {
            Timber.d("LibVLCPlayerView: Disposing (should only happen on screen exit)")
            // Don't clean up the layout here - let PlayerEngine manage it
        }
    }
    
    // AndroidView that reuses the same view instance
    // onReset is called when modifier changes but view is NOT recreated
    AndroidView(
        factory = { videoLayout }, // Returns the remembered instance
        modifier = modifier,
        onReset = {
            // This is called when modifier changes (e.g., size during animation)
            // but the view is NOT destroyed/recreated
            Timber.d("LibVLCPlayerView: View reset (size changed, but view NOT recreated)")
        },
        update = { view ->
            // Ensure view properties remain correct on every recomposition
            view.isClickable = false
            view.isFocusable = false
            view.keepScreenOn = true
        }
    )
}

