package com.withyou.app.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.core.view.WindowCompat
import com.withyou.app.ui.navigation.AppNavigation
import com.withyou.app.ui.theme.ContentSyncTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Make the window draw edge-to-edge (behind system bars)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // Set system bars to black so any uncovered area is black, not white/gray
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        
        setContent {
            ContentSyncTheme {
                // IMPORTANT: Top level box must fill whole window & be black
                // This ensures no white/gray strips show behind system bars
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ComposeColor.Black)
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

