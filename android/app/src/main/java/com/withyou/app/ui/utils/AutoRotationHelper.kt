package com.withyou.app.ui.utils

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.OrientationEventListener
import android.view.Surface
import timber.log.Timber

/**
 * Auto-rotation helper that detects device tilt and rotates the screen accordingly.
 * This bypasses the system auto-rotate setting, allowing rotation even when system
 * auto-rotate is turned off.
 * 
 * Usage:
 * ```
 * val autoRotation = remember { AutoRotationHelper(activity) }
 * DisposableEffect(Unit) {
 *     autoRotation.enable()
 *     onDispose { autoRotation.disable() }
 * }
 * ```
 */
class AutoRotationHelper(private val activity: Activity) {
    
    private var orientationListener: OrientationEventListener? = null
    private var currentOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var isEnabled = false
    private var isLocked = false
    
    // Threshold degrees for orientation change (prevents jittery switching)
    private val landscapeThreshold = 70  // 70-110 or 250-290 degrees
    private val portraitThreshold = 25   // 0-25 or 155-205 or 335-360 degrees
    
    /**
     * Enable auto-rotation detection.
     * When enabled, the screen will rotate based on device tilt even if system auto-rotate is off.
     */
    fun enable() {
        if (isEnabled) return
        isEnabled = true
        
        orientationListener = object : OrientationEventListener(activity) {
            override fun onOrientationChanged(orientation: Int) {
                if (isLocked || orientation == ORIENTATION_UNKNOWN) return
                
                val newOrientation = detectOrientation(orientation)
                if (newOrientation != currentOrientation && newOrientation != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
                    currentOrientation = newOrientation
                    activity.requestedOrientation = newOrientation
                    Timber.d("AutoRotation: Setting orientation to ${orientationToString(newOrientation)}")
                }
            }
        }
        
        if (orientationListener?.canDetectOrientation() == true) {
            orientationListener?.enable()
            Timber.d("AutoRotation: Enabled")
        } else {
            Timber.w("AutoRotation: Orientation detection not available")
        }
    }
    
    /**
     * Disable auto-rotation detection and reset to unspecified (follow system setting).
     */
    fun disable() {
        isEnabled = false
        orientationListener?.disable()
        orientationListener = null
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        Timber.d("AutoRotation: Disabled")
    }
    
    /**
     * Lock the current orientation (stop auto-rotation).
     */
    fun lock() {
        isLocked = true
        Timber.d("AutoRotation: Locked at ${orientationToString(currentOrientation)}")
    }
    
    /**
     * Unlock and resume auto-rotation.
     */
    fun unlock() {
        isLocked = false
        Timber.d("AutoRotation: Unlocked")
    }
    
    /**
     * Toggle lock state.
     */
    fun toggleLock(): Boolean {
        if (isLocked) unlock() else lock()
        return isLocked
    }
    
    /**
     * Check if rotation is currently locked.
     */
    fun isRotationLocked(): Boolean = isLocked
    
    private fun detectOrientation(degrees: Int): Int {
        // Natural portrait (device upright)
        if (degrees in 0..portraitThreshold || degrees >= (360 - portraitThreshold)) {
            return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        
        // Landscape left (device rotated 90° counter-clockwise)
        if (degrees in landscapeThreshold..(180 - landscapeThreshold)) {
            return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }
        
        // Upside down portrait
        if (degrees in (180 - portraitThreshold)..(180 + portraitThreshold)) {
            return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        }
        
        // Landscape right (device rotated 90° clockwise)
        if (degrees in (180 + landscapeThreshold)..(360 - landscapeThreshold)) {
            return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        
        return ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    
    private fun orientationToString(orientation: Int): String = when (orientation) {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> "PORTRAIT"
        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> "LANDSCAPE"
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> "REVERSE_PORTRAIT"
        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> "REVERSE_LANDSCAPE"
        else -> "UNSPECIFIED"
    }
}
