package com.withyou.app.ui.navigation

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.net.Uri
import com.withyou.app.ui.screens.AuthScreen
import com.withyou.app.ui.screens.HomeScreen
import com.withyou.app.ui.screens.MediaLibraryScreen
import com.withyou.app.ui.screens.RoomScreen
import com.withyou.app.ui.screens.SettingsScreen
import com.withyou.app.ui.screens.VideoPlayerScreen
import com.withyou.app.ui.screens.SoloPlayerScreen
import com.withyou.app.ui.screens.CreateRoomScreen
import com.withyou.app.ui.screens.JoinRoomScreen
import com.withyou.app.viewmodel.RoomViewModel

/**
 * Main navigation for the app with animations
 */
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    
    // Share RoomViewModel across Home and Room screens
    val sharedRoomViewModel: RoomViewModel = viewModel()
    
    NavHost(
        navController = navController, 
        startDestination = "auth", // After auth, navigates to media_library
        enterTransition = { 
            fadeIn(animationSpec = tween(450, easing = FastOutSlowInEasing)) + 
            slideInHorizontally(initialOffsetX = { 200 }, animationSpec = tween(450, easing = FastOutSlowInEasing))
        },
        exitTransition = { 
            fadeOut(animationSpec = tween(350, easing = FastOutSlowInEasing)) + 
            slideOutHorizontally(targetOffsetX = { -200 }, animationSpec = tween(350, easing = FastOutSlowInEasing))
        },
        popEnterTransition = { 
            fadeIn(animationSpec = tween(450, easing = FastOutSlowInEasing)) + 
            slideInHorizontally(initialOffsetX = { -200 }, animationSpec = tween(450, easing = FastOutSlowInEasing))
        },
        popExitTransition = { 
            fadeOut(animationSpec = tween(350, easing = FastOutSlowInEasing)) + 
            slideOutHorizontally(targetOffsetX = { 200 }, animationSpec = tween(350, easing = FastOutSlowInEasing))
        }
    ) {
        composable("auth") {
            AuthScreen(
                onAuthSuccess = {
                    navController.navigate("media_library") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }
        
        // Media Library is now the default home screen
        composable("media_library") {
            MediaLibraryScreen(
                roomViewModel = sharedRoomViewModel,
                onNavigateBack = {
                    // If we're at the root, do nothing (or exit app)
                    if (navController.previousBackStackEntry == null) {
                        // Could exit app or show exit dialog
                    } else {
                        navController.popBackStack()
                    }
                },
                onNavigateToRoom = { roomId, isHost ->
                    navController.navigate("room/$roomId/$isHost")
                },
                onVideoSelected = { uri ->
                    // Navigate to video player screen with options
                    // Encode URI for navigation (URL encoding)
                    val encodedUri = java.net.URLEncoder.encode(uri.toString(), "UTF-8")
                    navController.navigate("video_player/$encodedUri")
                }
            )
        }
        
        // Video Player Screen - shows Play Solo / Create Room / Join Room options
        composable(
            route = "video_player/{videoUri}",
            arguments = listOf(
                navArgument("videoUri") { 
                    type = NavType.StringType
                    // URI encoding: use URL encoding instead of simple replacement
                }
            )
        ) { backStackEntry ->
            val videoUriString = backStackEntry.arguments?.getString("videoUri")
            if (videoUriString != null) {
                // Decode URI (it was URL-encoded)
                val videoUri = android.net.Uri.parse(java.net.URLDecoder.decode(videoUriString, "UTF-8"))
                VideoPlayerScreen(
                    videoUri = videoUri,
                    roomViewModel = sharedRoomViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onPlaySolo = {
                        // Encode URI for navigation
                        val encodedUri = java.net.URLEncoder.encode(videoUri.toString(), "UTF-8")
                        navController.navigate("play_solo/$encodedUri") {
                            popUpTo("media_library") { inclusive = false }
                        }
                    },
                    onCreateRoom = {
                        sharedRoomViewModel.loadVideo(videoUri)
                        // Wait for file to load, then create room
                        navController.navigate("create_room")
                    },
                    onJoinRoom = { roomId ->
                        sharedRoomViewModel.loadVideo(videoUri)
                        // Wait for file to load, then join room
                        navController.navigate("join_room/$roomId")
                    }
                )
            }
        }
        
        // Play Solo mode - offline playback without room logic
        composable(
            route = "play_solo/{videoUri}",
            arguments = listOf(
                navArgument("videoUri") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val videoUriString = backStackEntry.arguments?.getString("videoUri")
            if (videoUriString != null) {
                // Decode URI
                val videoUri = android.net.Uri.parse(java.net.URLDecoder.decode(videoUriString, "UTF-8"))
                SoloPlayerScreen(
                    videoUri = videoUri,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
        
        // Create Room flow
        composable("create_room") {
            CreateRoomScreen(
                viewModel = sharedRoomViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onRoomCreated = { roomId ->
                    navController.navigate("room/$roomId/true") {
                        popUpTo("media_library") { inclusive = false }
                    }
                }
            )
        }
        
        // Join Room flow
        composable(
            route = "join_room/{roomId}",
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId")
            if (roomId != null) {
                JoinRoomScreen(
                    roomId = roomId,
                    viewModel = sharedRoomViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    },
                    onRoomJoined = { joinedRoomId ->
                        navController.navigate("room/$joinedRoomId/false") {
                            popUpTo("media_library") { inclusive = false }
                        }
                    }
                )
            }
        }
        
        // Keep home route for backward compatibility (optional, can be removed later)
        composable("home") {
            HomeScreen(
                viewModel = sharedRoomViewModel,
                onNavigateToRoom = { roomId, isHost ->
                    navController.navigate("room/$roomId/$isHost")
                },
                onNavigateToMediaLibrary = {
                    navController.navigate("media_library") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            )
        }
        
        composable(
            route = "room/{roomId}/{isHost}",
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType },
                navArgument("isHost") { type = NavType.BoolType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId")
            val isHost = backStackEntry.arguments?.getBoolean("isHost") ?: false
            
            if (roomId != null) {
                RoomScreen(
                    roomId = roomId,
                    isHost = isHost,
                    viewModel = sharedRoomViewModel,
                    onNavigateBack = {
                        navController.popBackStack()
                    }
                )
            }
        }
        
        composable("settings") {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
