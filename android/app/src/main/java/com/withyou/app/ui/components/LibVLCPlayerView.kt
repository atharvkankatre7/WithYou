package com.withyou.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.videolan.libvlc.util.VLCVideoLayout
import timber.log.Timber

/**
 * Compose wrapper for VLCVideoLayout for LibVLC
 * VLCVideoLayout handles video rendering and aspect ratio properly
 */
@Composable
fun LibVLCPlayerView(
    onLayoutCreated: (VLCVideoLayout) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            VLCVideoLayout(ctx).apply {
                Timber.d("LibVLCPlayerView: VLCVideoLayout created")
                // Make the view non-interactive so gestures can pass through to VideoGestureHandler
                isClickable = false
                isFocusable = false
                onLayoutCreated(this)
            }
        },
        // VLCVideoLayout should fill the parent - it handles aspect ratio internally
        modifier = modifier,
        update = { view ->
            // Ensure view remains non-interactive on updates
            view.isClickable = false
            view.isFocusable = false
        }
    )
}
